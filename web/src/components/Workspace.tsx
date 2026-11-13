import * as React from 'react'
import { api, ApiError } from '../api/ApiClient'
import type {
    Board as BoardData, BoardGroup, Folder, Task, TaskChanges, TodoList, UrgencyLevel, User,
} from '../api/types'
import {
    appendTask, countOpenTasks, findTask, indexInGroup, moveTask, removeTask, replaceTask,
} from '../utils/board'
import { SIDEBAR, sidebarMode } from '../utils/sidebarLayout'
import { Board, type BoardActions } from './Board'
import { ListHeader } from './ListHeader'
import { Sidebar } from './Sidebar'
import { SidebarResizer } from './SidebarResizer'
import { TaskPopover } from './TaskPopover'
import './Workspace.css'

// Clés du navigateur : dernière liste consultée, largeur de la barre latérale
const SELECTED_LIST_KEY = 'bettertodo.selectedList'
const SIDEBAR_WIDTH_KEY = 'bettertodo.sidebarWidth'

interface WorkspaceProps {
    user: User
    onLogout: () => void
    onSessionExpired: () => void
}

/** Overlay ouvert : quelle tâche, et où était sa carte à l'écran. */
interface OpenTask {
    id: string
    anchor: DOMRect
}

class WorkspaceState {
    folders: Folder[] = []
    lists: TodoList[] = []
    urgencyLevels: UrgencyLevel[] = []
    selectedListId: string | null = null
    board: BoardData | null = null // Contenu de la liste sélectionnée
    openTask: OpenTask | null = null
    addingGroup: boolean = false
    loading: boolean = true        // Premier chargement
    error: string = ''

    // Barre latérale
    sidebarWidth: number = readSidebarWidth()
    // Largeur à retrouver en quittant le mode icônes
    lastExpandedWidth: number = this.sidebarWidth >= SIDEBAR.compactBelow ? this.sidebarWidth : SIDEBAR.default
    windowWidth: number = window.innerWidth
    sidebarPeek: boolean = false                // Ouverte par-dessus le contenu (fenêtre étroite)
}

/**
 * L'écran principal une fois connecté : barre latérale + liste sélectionnée.
 *
 * C'est le SEUL composant (avec App) qui parle à l'API. Pour chaque modification :
 *   1. l'écran est mis à jour tout de suite (« mise à jour optimiste ») ;
 *   2. la requête part vers l'API ;
 *   3. la réponse du serveur remplace la version locale ;
 *      en cas d'erreur, on recharge le board pour revenir à la vérité du serveur.
 */
export class Workspace extends React.Component<WorkspaceProps, WorkspaceState> implements BoardActions {
    constructor(props: WorkspaceProps) {
        super(props)
        this.state = new WorkspaceState()
    }

    async componentDidMount(): Promise<void> {
        window.addEventListener('resize', this.onWindowResize)
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

    componentWillUnmount(): void {
        window.removeEventListener('resize', this.onWindowResize)
    }

    // =========================================================================
    // Barre latérale : largeur et mode icônes
    // =========================================================================

    onWindowResize = (): void => {
        this.setState({ windowWidth: window.innerWidth })
    }

    /** Pendant qu'on tire la poignée : pas encore enregistré. */
    onSidebarResize = (width: number): void => {
        this.setState({ sidebarWidth: width })
    }

    /** Au relâchement : largeur enregistrée dans le navigateur. */
    onSidebarResizeEnd = (width: number): void => {
        this.setSidebarWidth(width)
    }

    /** Bouton « Agrandir » : en fenêtre étroite on ouvre par-dessus, sinon on élargit. */
    onSidebarExpand = (): void => {
        if (sidebarMode(this.state.windowWidth, this.state.sidebarWidth) === 'narrow') {
            this.setState({ sidebarPeek: true })
        } else {
            this.setSidebarWidth(this.state.lastExpandedWidth)
        }
    }

    onSidebarCollapse = (): void => {
        if (this.state.sidebarPeek) {
            this.setState({ sidebarPeek: false })
        } else {
            this.setSidebarWidth(SIDEBAR.min)
        }
    }

    /** Double-clic sur la poignée : icônes ↔ normale. */
    onSidebarToggle = (): void => {
        if (this.state.sidebarWidth < SIDEBAR.compactBelow) {
            this.onSidebarExpand()
        } else {
            this.onSidebarCollapse()
        }
    }

    private setSidebarWidth(width: number): void {
        const changes: Partial<WorkspaceState> = { sidebarWidth: width }
        if (width >= SIDEBAR.compactBelow) {
            changes.lastExpandedWidth = width
        }
        this.setState(changes as WorkspaceState)
        writeStorage(SIDEBAR_WIDTH_KEY, String(width))
    }

    // =========================================================================
    // Navigation
    // =========================================================================

    /** Affiche une liste : charge son board. */
    selectList = async (listId: string): Promise<void> => {
        this.setState({ selectedListId: listId, openTask: null, addingGroup: false, error: '', sidebarPeek: false })
        writeStorage(SELECTED_LIST_KEY, listId)
        // On passe listId : this.state n'est PAS encore à jour juste après setState
        await this.reloadBoard(listId)
    }

    /** Recharge le board d'une liste (par défaut, la liste sélectionnée) depuis le serveur. */
    private async reloadBoard(listId: string | null = this.state.selectedListId): Promise<void> {
        if (!listId) return

        try {
            const board = await api.getBoard(listId)
            // Si on a changé de liste entre-temps, on ignore cette réponse
            if (this.state.selectedListId === listId) {
                this.setState({ board })
            }
        } catch (error) {
            this.handleError(error)
        }
    }

    /** Recharge les listes (pour les compteurs de la barre latérale). */
    private async refreshLists(): Promise<void> {
        try {
            this.setState({ lists: await api.getLists() })
        } catch (error) {
            this.handleError(error)
        }
    }

    /** Modifie le board local avec une fonction de utils/board.ts. */
    private updateBoard(change: (board: BoardData) => BoardData): void {
        this.setState(state => (state.board ? { board: change(state.board) } : null))
    }

    // =========================================================================
    // Barre latérale
    // =========================================================================

    /** Replie / déplie un groupe de listes. */
    onToggleFolder = async (folder: Folder): Promise<void> => {
        const collapsed = !folder.collapsed
        this.setState(state => ({
            folders: state.folders.map(f => (f.id === folder.id ? { ...f, collapsed } : f)),
        }))

        try {
            await api.updateFolder(folder.id, { collapsed })
        } catch (error) {
            this.handleError(error)
        }
    }

    onCreateFolder = async (name: string): Promise<void> => {
        try {
            const folder = await api.createFolder(name)
            this.setState(state => ({ folders: [...state.folders, folder] }))
        } catch (error) {
            this.handleError(error)
        }
    }

    /** Crée une liste et l'ouvre aussitôt. */
    onCreateList = async (name: string, folderId: string | null): Promise<void> => {
        try {
            const list = await api.createList(name, folderId)
            await this.refreshLists()
            await this.selectList(list.id)
        } catch (error) {
            this.handleError(error)
        }
    }

    // =========================================================================
    // Board (BoardActions) : appelées par Board et TaskCard
    // =========================================================================

    toggleTask = async (task: Task): Promise<void> => {
        this.updateBoard(board => replaceTask(board, { ...task, done: !task.done }))
        try {
            const saved = await api.updateTask(task.id, { done: !task.done })
            this.updateBoard(board => replaceTask(board, saved))
            void this.refreshLists()
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard()
        }
    }

    openTask = (task: Task, anchor: DOMRect): void => {
        this.setState({ openTask: { id: task.id, anchor } })
    }

    createTask = async (groupId: string, title: string): Promise<void> => {
        try {
            const task = await api.createTask(groupId, title)
            this.updateBoard(board => appendTask(board, task))
            void this.refreshLists()
        } catch (error) {
            this.handleError(error)
        }
    }

    /** Glisser-déposer : index compté sans la tâche déplacée (comme l'API). */
    moveTask = async (taskId: string, groupId: string, index: number): Promise<void> => {
        const board = this.state.board
        const task = board ? findTask(board, taskId) : undefined
        if (!board || !task) return

        const sameGroup = task.group_id === groupId
        if (sameGroup && indexInGroup(board, taskId) === index) return // Reposée à sa place

        this.updateBoard(b => moveTask(b, taskId, groupId, index))
        const changes: TaskChanges = { position: index }
        if (!sameGroup) changes.group_id = groupId

        try {
            await api.updateTask(taskId, changes)
        } catch (error) {
            this.handleError(error)
        }
        void this.reloadBoard() // Récupère les positions renumérotées par le serveur
    }

    createGroup = async (name: string): Promise<void> => {
        const listId = this.state.selectedListId
        if (!listId) return

        try {
            const group = await api.createGroup(listId, name)
            this.updateBoard(board => ({ ...board, groups: [...board.groups, { ...group, tasks: [] }] }))
        } catch (error) {
            this.handleError(error)
        }
    }

    renameGroup = async (groupId: string, name: string): Promise<void> => {
        this.updateBoard(board => ({
            ...board,
            groups: board.groups.map(g => (g.id === groupId ? { ...g, name } : g)),
        }))
        try {
            await api.renameGroup(groupId, name)
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard()
        }
    }

    deleteGroup = async (group: BoardGroup): Promise<void> => {
        const count = group.tasks.length
        let message = `Supprimer le groupe « ${group.name} » ?`
        if (count === 1) message = `Supprimer le groupe « ${group.name} » et sa tâche ?`
        if (count > 1) message = `Supprimer le groupe « ${group.name} » et ses ${count} tâches ?`
        if (!window.confirm(message)) return

        this.updateBoard(board => ({ ...board, groups: board.groups.filter(g => g.id !== group.id) }))
        try {
            await api.deleteGroup(group.id)
            void this.refreshLists()
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard()
        }
    }

    // =========================================================================
    // Overlay d'une tâche
    // =========================================================================

    private async updateTask(taskId: string, changes: TaskChanges): Promise<void> {
        // Changement de groupe : la tâche part tout de suite à la fin du nouveau groupe
        if (changes.group_id) {
            const groupId = changes.group_id
            this.updateBoard(board => moveTask(board, taskId, groupId, Number.MAX_SAFE_INTEGER))
        }

        try {
            const saved = await api.updateTask(taskId, changes)
            this.updateBoard(board => replaceTask(board, saved))
            if (changes.done !== undefined) void this.refreshLists()
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard()
        }
    }

    private async deleteTask(taskId: string): Promise<void> {
        this.setState({ openTask: null })
        this.updateBoard(board => removeTask(board, taskId))
        try {
            await api.deleteTask(taskId)
            void this.refreshLists()
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard()
        }
    }

    // =========================================================================
    // Erreurs et rendu
    // =========================================================================

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
        const { lists, selectedListId, board, urgencyLevels, error, loading } = this.state
        const showBoard = board !== null && board.list.id === selectedListId

        return (
            <div className="Workspace">
                {this.renderSidebar()}

                <main className="workspace-main">
                    {error && (
                        <div className="workspace-error">
                            {error}
                            <button onClick={() => this.setState({ error: '' })}>Fermer</button>
                        </div>
                    )}

                    {showBoard && (
                        <>
                            <ListHeader
                                list={board.list}
                                openTasks={countOpenTasks(board)}
                                onNewGroup={() => this.setState({ addingGroup: true })}
                            />
                            <Board
                                board={board}
                                urgencyLevels={urgencyLevels}
                                actions={this}
                                openTaskId={this.state.openTask?.id ?? null}
                                addingGroup={this.state.addingGroup}
                                onAddingGroupDone={() => this.setState({ addingGroup: false })}
                            />
                        </>
                    )}

                    {!loading && lists.length === 0 && (
                        <div className="workspace-empty">Crée ta première liste avec le + de la barre latérale.</div>
                    )}
                </main>

                {showBoard && this.renderPopover(board)}
            </div>
        )
    }

    /**
     * Trois cas :
     *   - fenêtre large : barre à la largeur choisie + poignée pour la redimensionner ;
     *   - fenêtre étroite : barre en icônes (60 px) ;
     *   - fenêtre étroite + « Agrandir » : barre normale par-dessus le contenu,
     *     avec un fond qui la referme au clic. Un espace de 60 px garde la place.
     */
    private renderSidebar(): React.ReactNode {
        const { windowWidth, sidebarWidth, sidebarPeek } = this.state
        const mode = sidebarMode(windowWidth, sidebarWidth)
        const peek = mode === 'narrow' && sidebarPeek

        let width = sidebarWidth
        if (mode === 'narrow') width = peek ? SIDEBAR.default : SIDEBAR.min

        const sidebar = (
            <Sidebar
                user={this.props.user}
                folders={this.state.folders}
                lists={this.state.lists}
                selectedListId={this.state.selectedListId}
                width={width}
                compact={mode !== 'expanded' && !peek}
                peek={peek}
                onExpand={this.onSidebarExpand}
                onCollapse={this.onSidebarCollapse}
                onSelectList={this.selectList}
                onToggleFolder={this.onToggleFolder}
                onCreateList={this.onCreateList}
                onCreateFolder={this.onCreateFolder}
                onLogout={this.props.onLogout}
            />
        )

        if (peek) {
            return (
                <>
                    <div className="sidebar-spacer" style={{ width: SIDEBAR.min }} />
                    <div className="sidebar-backdrop" onClick={this.onSidebarCollapse} />
                    {sidebar}
                </>
            )
        }

        return (
            <>
                {sidebar}
                {mode !== 'narrow' && (
                    <SidebarResizer
                        width={sidebarWidth}
                        onResize={this.onSidebarResize}
                        onResizeEnd={this.onSidebarResizeEnd}
                        onToggle={this.onSidebarToggle}
                    />
                )}
            </>
        )
    }

    private renderPopover(board: BoardData): React.ReactNode {
        const open = this.state.openTask
        const task = open ? findTask(board, open.id) : undefined
        if (!open || !task) return null

        return (
            <TaskPopover
                key={task.id} // Nouvelle tâche → nouveau composant (repart de son titre et ses notes)
                task={task}
                groups={board.groups}
                urgencyLevels={this.state.urgencyLevels}
                anchor={open.anchor}
                onUpdate={changes => this.updateTask(task.id, changes)}
                onDelete={() => void this.deleteTask(task.id)}
                onClose={() => this.setState({ openTask: null })}
            />
        )
    }
}

/** Largeur enregistrée de la barre latérale, ou la largeur par défaut. */
function readSidebarWidth(): number {
    const width = Number(readStorage(SIDEBAR_WIDTH_KEY))
    return width >= SIDEBAR.min && width <= SIDEBAR.max ? width : SIDEBAR.default
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
