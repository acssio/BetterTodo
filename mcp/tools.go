package main

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

// Tools : les outils que Claude peut appeler. Chaque méthode publique est un outil,
// enregistré dans Register avec son nom et sa description.
// Les outils des groupes de listes, listes et groupes sont dans tools_structure.go.
//
// Volontairement, aucun outil ne supprime quoi que ce soit : Claude peut cocher une tâche,
// créer ou renommer une liste, mais rien effacer.
type Tools struct {
	api *ApiClient
}

// NewTools crée les outils, qui passent tous par api.
func NewTools(api *ApiClient) *Tools {
	return &Tools{api: api}
}

// Register ajoute les outils au serveur MCP.
// La description est ce que lit Claude pour décider quand et comment appeler l'outil.
func (t *Tools) Register(server *mcp.Server) {
	readOnly := &mcp.ToolAnnotations{ReadOnlyHint: true}
	notDestructive := false
	write := &mcp.ToolAnnotations{DestructiveHint: &notDestructive}

	mcp.AddTool(server, &mcp.Tool{
		Name:        "list_lists",
		Description: "Liste les groupes de listes (dossiers de la barre latérale) et toutes les listes de tâches BetterTodo de l'utilisateur, avec le nombre de tâches à faire. À appeler d'abord pour connaître les list_id et folder_id.",
		Annotations: readOnly,
	}, t.ListLists)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "get_list",
		Description: "Lit une liste : ses groupes et leurs tâches (titre, échéance, urgence, tags, notes). Les tâches faites sont omises sauf si include_done est vrai.",
		Annotations: readOnly,
	}, t.GetList)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "get_due_tasks",
		Description: "Tâches à faire qui ont une échéance, toutes listes confondues, triées par date. until = \"today\" pour les tâches en retard et du jour (la liste « Aujourd'hui »), une date AAAA-MM-JJ pour s'arrêter à cette date, ou vide pour toutes (la liste « Planifié »).",
		Annotations: readOnly,
	}, t.GetDueTasks)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "list_tags_and_urgency_levels",
		Description: "Liste les tags existants et les niveaux d'urgence de l'utilisateur (du moins au plus urgent). Utile avant de créer ou modifier une tâche.",
		Annotations: readOnly,
	}, t.ListTagsAndUrgencyLevels)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "create_task",
		Description: "Crée une tâche dans une liste. Sans group, elle va dans le premier groupe de la liste.",
		Annotations: write,
	}, t.CreateTask)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "update_task",
		Description: "Modifie une tâche : seuls les champs donnés changent. done = true pour la cocher (faite), false pour la décocher. Il est impossible de supprimer une tâche.",
		Annotations: write,
	}, t.UpdateTask)

	t.registerStructure(server, readOnly, write)
}

// =============================================================================
// Ce que voit Claude
// =============================================================================

// taskOut : une tâche telle que renvoyée à Claude (urgence en clair, champs vides omis).
type taskOut struct {
	ID      string   `json:"id"`
	Title   string   `json:"title"`
	Done    bool     `json:"done"`
	DueDate string   `json:"due_date,omitempty"`
	DueTime string   `json:"due_time,omitempty"` // "14:30"
	Urgency string   `json:"urgency,omitempty"`
	Tags    []string `json:"tags,omitempty"`
	Notes   string   `json:"notes,omitempty"`
	List    string   `json:"list,omitempty"`  // Seulement pour get_due_tasks
	Group   string   `json:"group,omitempty"` // Seulement pour get_due_tasks
}

// toTaskOut convertit une tâche de l'API. urgencyNames : id du niveau → nom.
func toTaskOut(task apiTask, urgencyNames map[string]string) taskOut {
	out := taskOut{ID: task.ID, Title: task.Title, Done: task.Done, Tags: task.Tags, Notes: task.Notes}
	if task.DueDate != nil {
		out.DueDate = *task.DueDate
	}
	if task.DueTime != nil {
		out.DueTime = *task.DueTime
	}
	if task.UrgencyID != nil {
		out.Urgency = urgencyNames[*task.UrgencyID]
	}
	return out
}

// jsonResult renvoie value à Claude sous forme de texte JSON.
func jsonResult(value any) (*mcp.CallToolResult, any, error) {
	data, err := json.Marshal(value)
	if err != nil {
		return nil, nil, err
	}
	return &mcp.CallToolResult{Content: []mcp.Content{&mcp.TextContent{Text: string(data)}}}, nil, nil
}

// =============================================================================
// Lecture
// =============================================================================

type noInput struct{}

// ListLists : outil list_lists
func (t *Tools) ListLists(ctx context.Context, _ *mcp.CallToolRequest, _ noInput) (*mcp.CallToolResult, any, error) {
	var folders []apiFolder
	if err := t.api.Get(ctx, "/folders", &folders); err != nil {
		return nil, nil, err
	}
	var lists []apiList
	if err := t.api.Get(ctx, "/lists", &lists); err != nil {
		return nil, nil, err
	}

	folderNames := map[string]string{}
	for _, f := range folders {
		folderNames[f.ID] = f.Name
	}

	out := struct {
		Folders []apiFolder `json:"folders"` // Groupes de listes, même vides
		Lists   []listOut   `json:"lists"`
	}{Folders: folders, Lists: []listOut{}}

	for _, l := range lists {
		item := listOut{ID: l.ID, Name: l.Name, OpenTasks: l.OpenTasks}
		if l.FolderID != nil {
			item.Folder = folderNames[*l.FolderID]
		}
		out.Lists = append(out.Lists, item)
	}
	return jsonResult(out)
}

type getListInput struct {
	ListID      string `json:"list_id" jsonschema:"id de la liste (voir list_lists)"`
	IncludeDone bool   `json:"include_done,omitempty" jsonschema:"vrai pour inclure aussi les tâches faites"`
}

// GetList : outil get_list
func (t *Tools) GetList(ctx context.Context, _ *mcp.CallToolRequest, in getListInput) (*mcp.CallToolResult, any, error) {
	var board apiBoard
	if err := t.api.Get(ctx, "/lists/"+in.ListID+"/board", &board); err != nil {
		return nil, nil, err
	}
	urgencyNames, err := t.urgencyNames(ctx)
	if err != nil {
		return nil, nil, err
	}

	type groupOut struct {
		ID    string    `json:"id"`
		Name  string    `json:"name"`
		Tasks []taskOut `json:"tasks"`
	}
	out := struct {
		ID     string     `json:"id"`
		Name   string     `json:"name"`
		Groups []groupOut `json:"groups"`
	}{ID: board.List.ID, Name: board.List.Name, Groups: []groupOut{}}

	for _, g := range board.Groups {
		group := groupOut{ID: g.ID, Name: g.Name, Tasks: []taskOut{}}
		for _, task := range g.Tasks {
			if !task.Done || in.IncludeDone {
				group.Tasks = append(group.Tasks, toTaskOut(task, urgencyNames))
			}
		}
		out.Groups = append(out.Groups, group)
	}
	return jsonResult(out)
}

type getDueTasksInput struct {
	Until string `json:"until,omitempty" jsonschema:"\"today\", une date AAAA-MM-JJ incluse, ou vide pour toutes les échéances"`
}

// GetDueTasks : outil get_due_tasks
func (t *Tools) GetDueTasks(ctx context.Context, _ *mcp.CallToolRequest, in getDueTasksInput) (*mcp.CallToolResult, any, error) {
	// Comme la page web : la date du jour est celle de l'ordinateur où tourne ce programme (fuseau local)
	today := time.Now().Format(time.DateOnly)

	path := "/tasks/due"
	switch in.Until {
	case "":
	case "today":
		path += "?until=" + today
	default:
		path += "?until=" + in.Until // L'API vérifie le format
	}

	var tasks []apiDueTask
	if err := t.api.Get(ctx, path, &tasks); err != nil {
		return nil, nil, err
	}
	urgencyNames, err := t.urgencyNames(ctx)
	if err != nil {
		return nil, nil, err
	}

	out := struct {
		Today string    `json:"today"`
		Tasks []taskOut `json:"tasks"`
	}{Today: today, Tasks: []taskOut{}}

	for _, due := range tasks {
		task := toTaskOut(due.apiTask, urgencyNames)
		task.List = due.List.Name
		task.Group = due.GroupName
		out.Tasks = append(out.Tasks, task)
	}
	return jsonResult(out)
}

// ListTagsAndUrgencyLevels : outil list_tags_and_urgency_levels
func (t *Tools) ListTagsAndUrgencyLevels(ctx context.Context, _ *mcp.CallToolRequest, _ noInput) (*mcp.CallToolResult, any, error) {
	var tags []apiTag
	if err := t.api.Get(ctx, "/tags", &tags); err != nil {
		return nil, nil, err
	}
	var levels []apiUrgencyLevel
	if err := t.api.Get(ctx, "/urgency-levels", &levels); err != nil {
		return nil, nil, err
	}

	out := struct {
		Tags          []apiTag `json:"tags"`
		UrgencyLevels []string `json:"urgency_levels"` // Du moins urgent au plus urgent
	}{Tags: tags, UrgencyLevels: []string{}}

	for _, level := range levels { // L'API les renvoie déjà triés par rang
		out.UrgencyLevels = append(out.UrgencyLevels, level.Name)
	}
	return jsonResult(out)
}

// =============================================================================
// Écriture
// =============================================================================

type createTaskInput struct {
	ListID  string   `json:"list_id" jsonschema:"id de la liste (voir list_lists)"`
	Group   string   `json:"group,omitempty" jsonschema:"nom du groupe dans la liste ; vide = premier groupe"`
	Title   string   `json:"title" jsonschema:"titre de la tâche"`
	Notes   string   `json:"notes,omitempty" jsonschema:"notes facultatives"`
	DueDate string   `json:"due_date,omitempty" jsonschema:"échéance AAAA-MM-JJ"`
	DueTime string   `json:"due_time,omitempty" jsonschema:"heure facultative HH:MM (24 h), seulement avec due_date"`
	Urgency string   `json:"urgency,omitempty" jsonschema:"nom d'un niveau d'urgence (voir list_tags_and_urgency_levels)"`
	Tags    []string `json:"tags,omitempty" jsonschema:"noms des tags, sans # ; un tag inconnu est créé"`
}

// CreateTask : outil create_task
func (t *Tools) CreateTask(ctx context.Context, _ *mcp.CallToolRequest, in createTaskInput) (*mcp.CallToolResult, any, error) {
	var board apiBoard
	if err := t.api.Get(ctx, "/lists/"+in.ListID+"/board", &board); err != nil {
		return nil, nil, err
	}

	groupNames := []string{}
	for _, g := range board.Groups {
		groupNames = append(groupNames, g.Name)
	}
	index := findGroup(groupNames, in.Group)
	if index < 0 {
		return nil, nil, fmt.Errorf("groupe « %s » introuvable dans la liste. Groupes : %s", in.Group, strings.Join(groupNames, ", "))
	}

	// L'urgence est vérifiée avant de créer quoi que ce soit (pas de tâche à moitié créée)
	urgencyID := ""
	if in.Urgency != "" {
		id, err := t.urgencyID(ctx, in.Urgency)
		if err != nil {
			return nil, nil, err
		}
		urgencyID = id
	}

	// 1. Création (l'API ne prend que le titre)…
	var created apiTask
	if err := t.api.Send(ctx, "POST", "/groups/"+board.Groups[index].ID+"/tasks", map[string]any{"title": in.Title}, &created); err != nil {
		return nil, nil, err
	}

	// 2. … puis les autres champs, comme le fait l'overlay de la page web
	changes := map[string]any{}
	if in.Notes != "" {
		changes["notes"] = in.Notes
	}
	if in.DueDate != "" {
		changes["due_date"] = in.DueDate
	}
	if in.DueTime != "" {
		changes["due_time"] = in.DueTime
	}
	if len(in.Tags) > 0 {
		changes["tags"] = in.Tags
	}
	if urgencyID != "" {
		changes["urgency_id"] = urgencyID
	}
	return t.patchTask(ctx, created, changes)
}

type updateTaskInput struct {
	TaskID  string    `json:"task_id" jsonschema:"id de la tâche"`
	Title   *string   `json:"title,omitempty" jsonschema:"nouveau titre"`
	Notes   *string   `json:"notes,omitempty" jsonschema:"nouvelles notes (remplacent les anciennes) ; vide = effacer"`
	DueDate *string   `json:"due_date,omitempty" jsonschema:"échéance AAAA-MM-JJ ; vide = enlever l'échéance (et l'heure)"`
	DueTime *string   `json:"due_time,omitempty" jsonschema:"heure HH:MM (24 h), demande une échéance ; vide = enlever l'heure"`
	Urgency *string   `json:"urgency,omitempty" jsonschema:"nom d'un niveau d'urgence ; vide = enlever l'urgence"`
	Tags    *[]string `json:"tags,omitempty" jsonschema:"remplace TOUS les tags de la tâche ; liste vide = aucun tag"`
	Done    *bool     `json:"done,omitempty" jsonschema:"true = cochée (faite), false = à faire"`
}

// UpdateTask : outil update_task
func (t *Tools) UpdateTask(ctx context.Context, _ *mcp.CallToolRequest, in updateTaskInput) (*mcp.CallToolResult, any, error) {
	changes := map[string]any{}
	if in.Title != nil {
		changes["title"] = *in.Title
	}
	if in.Notes != nil {
		changes["notes"] = *in.Notes
	}
	if in.DueDate != nil {
		changes["due_date"] = emptyToNull(*in.DueDate)
	}
	if in.DueTime != nil {
		changes["due_time"] = emptyToNull(*in.DueTime)
	}
	if in.Tags != nil {
		changes["tags"] = *in.Tags
	}
	if in.Done != nil {
		changes["done"] = *in.Done
	}
	if in.Urgency != nil {
		if *in.Urgency == "" {
			changes["urgency_id"] = nil
		} else {
			id, err := t.urgencyID(ctx, *in.Urgency)
			if err != nil {
				return nil, nil, err
			}
			changes["urgency_id"] = id
		}
	}
	if len(changes) == 0 {
		return nil, nil, fmt.Errorf("aucun champ à modifier")
	}
	return t.patchTask(ctx, apiTask{ID: in.TaskID}, changes)
}

// patchTask envoie les changements (s'il y en a) et renvoie la tâche à jour à Claude.
func (t *Tools) patchTask(ctx context.Context, task apiTask, changes map[string]any) (*mcp.CallToolResult, any, error) {
	if len(changes) > 0 {
		if err := t.api.Send(ctx, "PATCH", "/tasks/"+task.ID, changes, &task); err != nil {
			return nil, nil, err
		}
	}
	urgencyNames, err := t.urgencyNames(ctx)
	if err != nil {
		return nil, nil, err
	}
	return jsonResult(toTaskOut(task, urgencyNames))
}

// =============================================================================
// Niveaux d'urgence : Claude utilise les noms, l'API les identifiants
// =============================================================================

func (t *Tools) urgencyLevels(ctx context.Context) ([]apiUrgencyLevel, error) {
	var levels []apiUrgencyLevel
	err := t.api.Get(ctx, "/urgency-levels", &levels)
	return levels, err
}

// urgencyNames : id → nom
func (t *Tools) urgencyNames(ctx context.Context) (map[string]string, error) {
	levels, err := t.urgencyLevels(ctx)
	if err != nil {
		return nil, err
	}
	names := map[string]string{}
	for _, level := range levels {
		names[level.ID] = level.Name
	}
	return names, nil
}

// urgencyID : nom → id (sans tenir compte des majuscules)
func (t *Tools) urgencyID(ctx context.Context, name string) (string, error) {
	levels, err := t.urgencyLevels(ctx)
	if err != nil {
		return "", err
	}
	return findUrgency(levels, name)
}

// =============================================================================
// Fonctions pures (testées dans tools_test.go)
// =============================================================================

// findUrgency retourne l'id du niveau nommé name, ou une erreur qui liste les noms possibles.
func findUrgency(levels []apiUrgencyLevel, name string) (string, error) {
	names := []string{}
	for _, level := range levels {
		if strings.EqualFold(strings.TrimSpace(name), level.Name) {
			return level.ID, nil
		}
		names = append(names, level.Name)
	}
	return "", fmt.Errorf("niveau d'urgence « %s » inconnu. Niveaux : %s", name, strings.Join(names, ", "))
}

// findGroup retourne l'indice du groupe nommé name (sans tenir compte des majuscules),
// 0 si name est vide (premier groupe), ou -1 s'il n'existe pas (ou si la liste n'a aucun groupe).
func findGroup(groupNames []string, name string) int {
	if len(groupNames) == 0 {
		return -1
	}
	if strings.TrimSpace(name) == "" {
		return 0
	}
	for i, groupName := range groupNames {
		if strings.EqualFold(strings.TrimSpace(name), groupName) {
			return i
		}
	}
	return -1
}

// emptyToNull : "" → nil (null en JSON, pour effacer un champ), sinon la valeur.
func emptyToNull(value string) any {
	if value == "" {
		return nil
	}
	return value
}
