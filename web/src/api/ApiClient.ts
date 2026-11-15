import type {
    Board, DueCounts, DueTask, Folder, Group, ListChanges, NewUser, Session, Tag, Task, TaskChanges, TodoList,
    UrgencyChanges, UrgencyLevel, User, UserChanges,
} from './types'

/** Erreur renvoyée par l'API : le code HTTP et le message de {"error": "..."} */
export class ApiError extends Error {
    status: number

    constructor(status: number, message: string) {
        super(message)
        this.status = status
    }
}

/**
 * Tous les appels à l'API passent par cette classe.
 *
 * La session est dans un cookie HttpOnly posé par le serveur : le navigateur
 * l'envoie tout seul, la page n'a jamais à manipuler le jeton.
 */
export class ApiClient {
    private baseUrl: string

    constructor(baseUrl: string = '/api/v1') {
        this.baseUrl = baseUrl
    }

    // --- Comptes -------------------------------------------------------------

    me(): Promise<User> {
        return this.request('GET', '/me')
    }

    login(email: string, password: string): Promise<Session> {
        return this.request('POST', '/auth/login', { email, password })
    }

    /** true tant qu'aucun compte n'existe : il faut créer le compte administrateur. */
    async needsSetup(): Promise<boolean> {
        const status: { needs_setup: boolean } = await this.request('GET', '/auth/setup')
        return status.needs_setup
    }

    /** Crée le premier compte (admin) et ouvre sa session. Refusé dès qu'un compte existe. */
    setup(user: NewUser): Promise<Session> {
        return this.request('POST', '/auth/setup', user)
    }

    logout(): Promise<void> {
        return this.request('POST', '/auth/logout')
    }

    updateMe(changes: UserChanges): Promise<User> {
        return this.request('PATCH', '/me', changes)
    }

    /** Change son mot de passe (il faut l'actuel). Les autres sessions du compte sont fermées. */
    changePassword(currentPassword: string, newPassword: string): Promise<User> {
        return this.request('POST', '/me/password', { current_password: currentPassword, new_password: newPassword })
    }

    // --- Console admin (réservée à l'admin) ------------------------------------

    getUsers(): Promise<User[]> {
        return this.request('GET', '/admin/users')
    }

    createUser(user: NewUser): Promise<User> {
        return this.request('POST', '/admin/users', user)
    }

    /** Nouveau mot de passe ; les sessions ouvertes du compte sont fermées. */
    resetPassword(userId: string, password: string): Promise<void> {
        return this.request('PATCH', `/admin/users/${userId}`, { password })
    }

    /** Supprime le compte et toutes ses données. */
    deleteUser(userId: string): Promise<void> {
        return this.request('DELETE', `/admin/users/${userId}`)
    }

    // --- Données -------------------------------------------------------------

    getFolders(): Promise<Folder[]> {
        return this.request('GET', '/folders')
    }

    createFolder(name: string): Promise<Folder> {
        return this.request('POST', '/folders', { name })
    }

    updateFolder(id: string, changes: Partial<Pick<Folder, 'name' | 'position' | 'collapsed'>>): Promise<Folder> {
        return this.request('PATCH', `/folders/${id}`, changes)
    }

    deleteFolder(id: string): Promise<void> {
        return this.request('DELETE', `/folders/${id}`)
    }

    getLists(): Promise<TodoList[]> {
        return this.request('GET', '/lists')
    }

    createList(name: string, folderId: string | null): Promise<TodoList> {
        return this.request('POST', '/lists', { name, folder_id: folderId })
    }

    updateList(id: string, changes: ListChanges): Promise<TodoList> {
        return this.request('PATCH', `/lists/${id}`, changes)
    }

    deleteList(id: string): Promise<void> {
        return this.request('DELETE', `/lists/${id}`)
    }

    /** Supprime toutes les tâches faites de la liste. */
    clearDoneTasks(listId: string): Promise<{ deleted: number }> {
        return this.request('DELETE', `/lists/${listId}/done-tasks`)
    }

    getBoard(listId: string): Promise<Board> {
        return this.request('GET', `/lists/${listId}/board`)
    }

    createGroup(listId: string, name: string): Promise<Group> {
        return this.request('POST', `/lists/${listId}/groups`, { name })
    }

    renameGroup(groupId: string, name: string): Promise<Group> {
        return this.request('PATCH', `/groups/${groupId}`, { name })
    }

    deleteGroup(groupId: string): Promise<void> {
        return this.request('DELETE', `/groups/${groupId}`)
    }

    createTask(groupId: string, title: string): Promise<Task> {
        return this.request('POST', `/groups/${groupId}/tasks`, { title })
    }

    updateTask(taskId: string, changes: TaskChanges): Promise<Task> {
        return this.request('PATCH', `/tasks/${taskId}`, changes)
    }

    deleteTask(taskId: string): Promise<void> {
        return this.request('DELETE', `/tasks/${taskId}`)
    }

    /** Tâches à faire avec une échéance, toutes listes confondues (jusqu'à until si donné). */
    getDueTasks(until?: string): Promise<DueTask[]> {
        return this.request('GET', until ? `/tasks/due?until=${until}` : '/tasks/due')
    }

    /** Compteurs « Aujourd'hui » / « Planifié ». today = date locale du jour. */
    getDueCounts(today: string): Promise<DueCounts> {
        return this.request('GET', `/tasks/due/counts?today=${today}`)
    }

    getUrgencyLevels(): Promise<UrgencyLevel[]> {
        return this.request('GET', '/urgency-levels')
    }

    createUrgencyLevel(name: string, color: string): Promise<UrgencyLevel> {
        return this.request('POST', '/urgency-levels', { name, color })
    }

    updateUrgencyLevel(id: string, changes: UrgencyChanges): Promise<UrgencyLevel> {
        return this.request('PATCH', `/urgency-levels/${id}`, changes)
    }

    deleteUrgencyLevel(id: string): Promise<void> {
        return this.request('DELETE', `/urgency-levels/${id}`)
    }

    getTags(): Promise<Tag[]> {
        return this.request('GET', '/tags')
    }

    createTag(name: string): Promise<Tag> {
        return this.request('POST', '/tags', { name })
    }

    renameTag(id: string, name: string): Promise<Tag> {
        return this.request('PATCH', `/tags/${id}`, { name })
    }

    deleteTag(id: string): Promise<void> {
        return this.request('DELETE', `/tags/${id}`)
    }

    // --- Requête générique ---------------------------------------------------

    /**
     * Envoie une requête et retourne la réponse JSON.
     * Lance une ApiError si le serveur répond avec un code d'erreur (4xx, 5xx).
     */
    private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
        const response = await fetch(this.baseUrl + path, {
            method,
            credentials: 'same-origin', // Envoie le cookie de session
            headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
            body: body !== undefined ? JSON.stringify(body) : undefined,
        })

        if (response.status === 204) { // No Content : pas de corps à lire
            return undefined as T
        }

        const data = await response.json().catch(() => null) // null si la réponse n'est pas du JSON
        if (!response.ok) {
            throw new ApiError(response.status, data?.error ?? `Erreur ${response.status}`)
        }
        return data as T
    }
}

/** Instance unique, utilisée par tous les composants. */
export const api = new ApiClient()
