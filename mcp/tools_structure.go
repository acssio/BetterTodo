package main

import (
	"context"
	"fmt"
	"strings"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

// Outils de l'organisation : groupes de listes (folders), listes et groupes de tâches.
// Créer et renommer seulement : aucun outil ne supprime.

// registerStructure ajoute ces outils au serveur (appelé par Tools.Register).
func (t *Tools) registerStructure(server *mcp.Server, readOnly, write *mcp.ToolAnnotations) {
	mcp.AddTool(server, &mcp.Tool{
		Name:        "list_colors_and_icons",
		Description: "Couleurs et icônes Lucide proposées pour une liste (avec des mots-clés en français pour choisir l'icône). À consulter avant create_list si on veut choisir l'apparence.",
		Annotations: readOnly,
	}, t.ListColorsAndIcons)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "create_folder",
		Description: "Crée un groupe de listes (un dossier repliable de la barre latérale, qui contient des listes).",
		Annotations: write,
	}, t.CreateFolder)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "rename_folder",
		Description: "Renomme un groupe de listes.",
		Annotations: write,
	}, t.RenameFolder)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "create_list",
		Description: "Crée une liste de tâches, dans un groupe de listes ou non. Le serveur lui ajoute un premier groupe de tâches (« Tâches »). Sans couleur ni icône : gris et icône « list ».",
		Annotations: write,
	}, t.CreateList)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "rename_list",
		Description: "Renomme une liste.",
		Annotations: write,
	}, t.RenameList)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "create_group",
		Description: "Crée un groupe de tâches à la fin d'une liste (une colonne en vue colonnes, une section en vue sections).",
		Annotations: write,
	}, t.CreateGroup)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "rename_group",
		Description: "Renomme un groupe de tâches (son id est donné par get_list).",
		Annotations: write,
	}, t.RenameGroup)
}

// ListColorsAndIcons : outil list_colors_and_icons
func (t *Tools) ListColorsAndIcons(ctx context.Context, _ *mcp.CallToolRequest, _ noInput) (*mcp.CallToolResult, any, error) {
	return jsonResult(struct {
		Colors []listColor `json:"colors"`
		Icons  []listIcon  `json:"icons"`
	}{listColors, listIcons})
}

// =============================================================================
// Groupes de listes
// =============================================================================

type createFolderInput struct {
	Name string `json:"name" jsonschema:"nom du groupe de listes"`
}

// CreateFolder : outil create_folder
func (t *Tools) CreateFolder(ctx context.Context, _ *mcp.CallToolRequest, in createFolderInput) (*mcp.CallToolResult, any, error) {
	var folder apiFolder
	if err := t.api.Send(ctx, "POST", "/folders", map[string]any{"name": in.Name}, &folder); err != nil {
		return nil, nil, err
	}
	return jsonResult(folder)
}

type renameFolderInput struct {
	FolderID string `json:"folder_id" jsonschema:"id du groupe de listes (voir list_lists)"`
	Name     string `json:"name" jsonschema:"nouveau nom"`
}

// RenameFolder : outil rename_folder
func (t *Tools) RenameFolder(ctx context.Context, _ *mcp.CallToolRequest, in renameFolderInput) (*mcp.CallToolResult, any, error) {
	var folder apiFolder
	if err := t.api.Send(ctx, "PATCH", "/folders/"+in.FolderID, map[string]any{"name": in.Name}, &folder); err != nil {
		return nil, nil, err
	}
	return jsonResult(folder)
}

// =============================================================================
// Listes
// =============================================================================

// listOut : une liste créée ou renommée, telle que renvoyée à Claude.
type listOut struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Folder    string `json:"folder,omitempty"`
	Color     string `json:"color,omitempty"`
	Icon      string `json:"icon,omitempty"`
	OpenTasks int    `json:"open_tasks"`
}

type createListInput struct {
	Name   string `json:"name" jsonschema:"nom de la liste"`
	Folder string `json:"folder,omitempty" jsonschema:"nom d'un groupe de listes existant ; vide = hors groupe"`
	Color  string `json:"color,omitempty" jsonschema:"nom d'une couleur de la palette (rouge, orange, vert, turquoise, bleu, violet, rose, gris)"`
	Icon   string `json:"icon,omitempty" jsonschema:"nom d'une icône de list_colors_and_icons, ex. shopping-cart"`
}

// CreateList : outil create_list
func (t *Tools) CreateList(ctx context.Context, _ *mcp.CallToolRequest, in createListInput) (*mcp.CallToolResult, any, error) {
	body := map[string]any{"name": in.Name}

	// Tout est vérifié avant de créer, pour ne pas laisser une liste à moitié faite
	if in.Color != "" {
		hex, ok := findColor(in.Color)
		if !ok {
			names := []string{}
			for _, c := range listColors {
				names = append(names, c.Name)
			}
			return nil, nil, fmt.Errorf("couleur « %s » hors palette. Couleurs : %s", in.Color, strings.Join(names, ", "))
		}
		body["color"] = hex
	}
	if in.Icon != "" {
		if !isListIcon(in.Icon) {
			return nil, nil, fmt.Errorf("icône « %s » inconnue : choisir un nom donné par list_colors_and_icons", in.Icon)
		}
		body["icon"] = in.Icon
	}
	folderName := ""
	if in.Folder != "" {
		var folders []apiFolder
		if err := t.api.Get(ctx, "/folders", &folders); err != nil {
			return nil, nil, err
		}
		folder, err := findFolder(folders, in.Folder)
		if err != nil {
			return nil, nil, err
		}
		body["folder_id"] = folder.ID
		folderName = folder.Name
	}

	var created apiList
	if err := t.api.Send(ctx, "POST", "/lists", body, &created); err != nil {
		return nil, nil, err
	}
	return jsonResult(listOut{ID: created.ID, Name: created.Name, Folder: folderName,
		Color: created.Color, Icon: created.Icon, OpenTasks: created.OpenTasks})
}

type renameListInput struct {
	ListID string `json:"list_id" jsonschema:"id de la liste (voir list_lists)"`
	Name   string `json:"name" jsonschema:"nouveau nom"`
}

// RenameList : outil rename_list
func (t *Tools) RenameList(ctx context.Context, _ *mcp.CallToolRequest, in renameListInput) (*mcp.CallToolResult, any, error) {
	var list apiList
	if err := t.api.Send(ctx, "PATCH", "/lists/"+in.ListID, map[string]any{"name": in.Name}, &list); err != nil {
		return nil, nil, err
	}
	return jsonResult(listOut{ID: list.ID, Name: list.Name, Color: list.Color, Icon: list.Icon, OpenTasks: list.OpenTasks})
}

// =============================================================================
// Groupes de tâches
// =============================================================================

type createGroupInput struct {
	ListID string `json:"list_id" jsonschema:"id de la liste (voir list_lists)"`
	Name   string `json:"name" jsonschema:"nom du groupe"`
}

// CreateGroup : outil create_group
func (t *Tools) CreateGroup(ctx context.Context, _ *mcp.CallToolRequest, in createGroupInput) (*mcp.CallToolResult, any, error) {
	var group apiGroup
	if err := t.api.Send(ctx, "POST", "/lists/"+in.ListID+"/groups", map[string]any{"name": in.Name}, &group); err != nil {
		return nil, nil, err
	}
	return jsonResult(group)
}

type renameGroupInput struct {
	GroupID string `json:"group_id" jsonschema:"id du groupe (voir get_list)"`
	Name    string `json:"name" jsonschema:"nouveau nom"`
}

// RenameGroup : outil rename_group
func (t *Tools) RenameGroup(ctx context.Context, _ *mcp.CallToolRequest, in renameGroupInput) (*mcp.CallToolResult, any, error) {
	var group apiGroup
	if err := t.api.Send(ctx, "PATCH", "/groups/"+in.GroupID, map[string]any{"name": in.Name}, &group); err != nil {
		return nil, nil, err
	}
	return jsonResult(group)
}

// findFolder retourne le groupe de listes nommé name (sans tenir compte des majuscules),
// ou une erreur qui liste les noms existants.
func findFolder(folders []apiFolder, name string) (apiFolder, error) {
	names := []string{}
	for _, f := range folders {
		if strings.EqualFold(strings.TrimSpace(name), f.Name) {
			return f, nil
		}
		names = append(names, f.Name)
	}
	if len(names) == 0 {
		return apiFolder{}, fmt.Errorf("groupe de listes « %s » introuvable : il n'y en a aucun (create_folder pour en créer un)", name)
	}
	return apiFolder{}, fmt.Errorf("groupe de listes « %s » introuvable. Groupes de listes : %s", name, strings.Join(names, ", "))
}
