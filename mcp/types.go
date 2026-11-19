package main

// Réponses de l'API BetterTodo (seulement les champs utiles ici).
// La référence est api/internal/handlers/*.go.

type apiFolder struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type apiList struct {
	ID        string  `json:"id"`
	FolderID  *string `json:"folder_id"`
	Name      string  `json:"name"`
	Color     string  `json:"color"`
	Icon      string  `json:"icon"`
	OpenTasks int     `json:"open_tasks"`
}

type apiGroup struct {
	ID     string `json:"id"`
	ListID string `json:"list_id"`
	Name   string `json:"name"`
}

type apiTask struct {
	ID        string   `json:"id"`
	GroupID   string   `json:"group_id"`
	Title     string   `json:"title"`
	Notes     string   `json:"notes"`
	DueDate   *string  `json:"due_date"`
	DueTime   *string  `json:"due_time"`
	UrgencyID *string  `json:"urgency_id"`
	Done      bool     `json:"done"`
	Tags      []string `json:"tags"`
}

type apiBoard struct {
	List   apiList `json:"list"`
	Groups []struct {
		ID    string    `json:"id"`
		Name  string    `json:"name"`
		Tasks []apiTask `json:"tasks"`
	} `json:"groups"`
}

type apiDueTask struct {
	apiTask
	List struct {
		Name string `json:"name"`
	} `json:"list"`
	GroupName string `json:"group_name"`
}

type apiUrgencyLevel struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Rank int    `json:"rank"`
}

type apiTag struct {
	Name      string `json:"name"`
	TaskCount int    `json:"task_count"`
}
