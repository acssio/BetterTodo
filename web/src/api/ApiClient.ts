import type { Board, Folder, Session, TodoList, UrgencyLevel, User } from './types'

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

    register(email: string, password: string, displayName: string): Promise<Session> {
        return this.request('POST', '/auth/register', { email, password, display_name: displayName })
    }

    logout(): Promise<void> {
        return this.request('POST', '/auth/logout')
    }

    // --- Données -------------------------------------------------------------

    getFolders(): Promise<Folder[]> {
        return this.request('GET', '/folders')
    }

    updateFolder(id: string, changes: Partial<Pick<Folder, 'name' | 'position' | 'collapsed'>>): Promise<Folder> {
        return this.request('PATCH', `/folders/${id}`, changes)
    }

    getLists(): Promise<TodoList[]> {
        return this.request('GET', '/lists')
    }

    getBoard(listId: string): Promise<Board> {
        return this.request('GET', `/lists/${listId}/board`)
    }

    getUrgencyLevels(): Promise<UrgencyLevel[]> {
        return this.request('GET', '/urgency-levels')
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
