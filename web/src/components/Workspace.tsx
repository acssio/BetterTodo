import * as React from 'react'
import { api, ApiError } from '../api/ApiClient'
import type { Board as BoardData, Folder, TodoList, UrgencyLevel, User } from '../api/types'
import { Board } from './Board'
import { ListHeader } from './ListHeader'
import { Sidebar } from './Sidebar'
import './Workspace.css'

// Clé du navigateur pour rouvrir la dernière liste consultée
const SELECTED_LIST_KEY = 'bettertodo.selectedList'

interface WorkspaceProps {
    user: User
    onLogout: () => void
    onSessionExpired: () => void
}

class WorkspaceState {
    folders: Folder[] = []
    lists: TodoList[] = []
    urgencyLevels: UrgencyLevel[] = []
    selectedListId: string | null = null
    board: BoardData | null = null // Contenu de la liste sélectionnée
    loading: boolean = true        // Premier chargement
    error: string = ''
}

/**
 * L'écran principal une fois connecté : barre latérale + liste sélectionnée.
 * C'est lui qui charge les données et les passe aux composants enfants.
 */
export class Workspace extends React.Component<WorkspaceProps, WorkspaceState> {
    constructor(props: WorkspaceProps) {
        super(props)
        this.state = new WorkspaceState()
    }

    async componentDidMount(): Promise<void> {
        try {
            // Les trois appels partent en même temps
            const [folders, lists, urgencyLevels] = await Promise.all([
                api.getFolders(),
                api.getLists(),
                api.getUrgencyLevels(),
            ])
            this.setState({ folders, lists, urgencyLevels, loading: false })

            // Rouvre la dernière liste consultée, sinon la première
            const remembered = readStorage(SELECTED_LIST_KEY)
            const first = lists.find(list => list.id === remembered) ?? lists[0]
            if (first) {
                await this.selectList(first.id)
            }
        } catch (error) {
            this.handleError(error)
        }
    }

    /** Affiche une liste : charge son board. */
    selectList = async (listId: string): Promise<void> => {
        this.setState({ selectedListId: listId, error: '' })
        writeStorage(SELECTED_LIST_KEY, listId)

        try {
            const board = await api.getBoard(listId)
            // Si on a cliqué sur une autre liste entre-temps, on ignore cette réponse
            if (this.state.selectedListId === listId) {
                this.setState({ board })
            }
        } catch (error) {
            this.handleError(error)
        }
    }

    /** Replie / déplie un groupe de listes. L'écran change tout de suite, l'API suit. */
    onToggleFolder = async (folder: Folder): Promise<void> => {
        const collapsed = !folder.collapsed
        this.setState({
            folders: this.state.folders.map(f => (f.id === folder.id ? { ...f, collapsed } : f)),
        })

        try {
            await api.updateFolder(folder.id, { collapsed })
        } catch (error) {
            this.handleError(error)
        }
    }

    /** Session expirée → retour à la connexion. Autre erreur → message en haut de l'écran. */
    private handleError(error: unknown): void {
        if (error instanceof ApiError && error.status === 401) {
            this.props.onSessionExpired()
            return
        }
        const message = error instanceof ApiError ? error.message : 'Impossible de joindre le serveur.'
        this.setState({ error: message, loading: false })
    }

    render(): React.ReactNode {
        const { folders, lists, selectedListId, board, urgencyLevels, error, loading } = this.state

        return (
            <div className="Workspace">
                <Sidebar
                    user={this.props.user}
                    folders={folders}
                    lists={lists}
                    selectedListId={selectedListId}
                    onSelectList={this.selectList}
                    onToggleFolder={this.onToggleFolder}
                    onLogout={this.props.onLogout}
                />

                <main className="workspace-main">
                    {error && <div className="workspace-error">{error}</div>}

                    {board && board.list.id === selectedListId && (
                        <>
                            <ListHeader list={board.list} />
                            <Board board={board} urgencyLevels={urgencyLevels} />
                        </>
                    )}

                    {!loading && lists.length === 0 && (
                        <div className="workspace-empty">Crée ta première liste pour commencer.</div>
                    )}
                </main>
            </div>
        )
    }
}

// localStorage peut être indisponible (navigation privée, stockage bloqué) : on l'ignore alors.
function readStorage(key: string): string | null {
    try {
        return localStorage.getItem(key)
    } catch {
        return null
    }
}

function writeStorage(key: string, value: string): void {
    try {
        localStorage.setItem(key, value)
    } catch {
        // Pas grave : on ne se souviendra simplement pas de la liste
    }
}
