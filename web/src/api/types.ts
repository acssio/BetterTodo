// =============================================================================
// Types des données renvoyées par l'API (voir docs/etapes/02b-api-donnees.md).
// Les noms des champs sont ceux du JSON, en snake_case.
// =============================================================================

/** Langue du compte. Sert pour l'instant au nom du groupe créé avec chaque nouvelle liste. */
export type Language = 'fr' | 'en'

export interface User {
    id: string
    email: string
    display_name: string
    default_view: ListView // Vue des nouvelles listes (Paramètres)
    language: Language
    created_at: string
}

/** Champs modifiables du compte (PATCH /me). */
export interface UserChanges {
    display_name?: string
    default_view?: ListView
    language?: Language
}

/** Réponse à une connexion ou une inscription. */
export interface Session {
    user: User
    token: string // Inutile pour la page web : elle utilise le cookie
    expires_at: string
}

/** Groupe de listes (« Personnel »…). */
export interface Folder {
    id: string
    name: string
    position: number
    collapsed: boolean
}

export type ListView = 'columns' | 'sections'

/** Une liste. Nommée TodoList pour ne pas confondre avec les listes JavaScript. */
export interface TodoList {
    id: string
    folder_id: string | null // null = hors groupe de listes
    name: string
    color: string            // "#RRGGBB"
    icon: string             // Nom d'icône Lucide, ex. "briefcase"
    default_view: ListView
    show_done: boolean       // false = tâches faites masquées
    position: number
    open_tasks: number       // Tâches pas encore faites
}

/** Champs modifiables d'une liste (PATCH /lists/{id}). folder_id: null = hors groupe de listes. */
export interface ListChanges {
    name?: string
    folder_id?: string | null
    color?: string
    icon?: string
    default_view?: ListView
    show_done?: boolean
    position?: number
}

/** Groupe de tâches dans une liste (une colonne). */
export interface Group {
    id: string
    list_id: string
    name: string
    position: number
}

export interface Task {
    id: string
    group_id: string
    title: string
    notes: string
    due_date: string | null   // "2026-11-18"
    urgency_id: string | null
    done: boolean
    done_at: string | null
    position: number
    tags: string[]
    created_at: string
    updated_at: string
}

export interface BoardGroup extends Group {
    tasks: Task[]
}

/** Réponse de GET /lists/{id}/board : tout ce qu'il faut pour afficher une liste. */
export interface Board {
    list: TodoList
    groups: BoardGroup[]
}

/**
 * Champs modifiables d'une tâche (PATCH /tasks/{id}).
 * Un champ absent n'est pas modifié ; null efface l'échéance ou l'urgence.
 */
export interface TaskChanges {
    title?: string
    notes?: string
    due_date?: string | null
    urgency_id?: string | null
    done?: boolean
    group_id?: string
    position?: number
    tags?: string[] // Remplace tous les tags
}

export interface UrgencyLevel {
    id: string
    name: string
    color: string
    rank: number // 0 = le moins urgent
}

/** Champs modifiables d'un niveau d'urgence (PATCH /urgency-levels/{id}). */
export interface UrgencyChanges {
    name?: string
    color?: string
    rank?: number
}

/** Un tag et le nombre de tâches qui l'utilisent. */
export interface Tag {
    id: string
    name: string
    task_count: number
}
