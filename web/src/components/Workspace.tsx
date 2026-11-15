import * as React from 'react'
import { api, ApiError } from '../api/ApiClient'
import type {
    Board as BoardData, BoardGroup, DueCounts, DueTask, Folder, ListChanges, ListView, Task, TaskChanges, TodoList,
    UrgencyLevel, User,
} from '../api/types'
import {
    appendTask, boardWithGroup, boardWithTask, countDoneTasks, countOpenTasks, findTask, hideDoneTasks, indexInGroup,
    moveTask, placeTask, removeTask, replaceTask, visibleToFullIndex,
} from '../utils/board'
import { todayIso } from '../utils/dates'
import type { DragState, DropTarget } from '../utils/dragDrop'
import { isSmart, SMART_TODAY } from '../utils/smartLists'
import { SIDEBAR, sidebarMode } from '../utils/sidebarLayout'
import type { TaskViewActions, TaskViewProps } from './actions'
import { Board } from './Board'
import { confirmDialog } from './confirm'
import { ListEditPopover } from './ListEditPopover'
import { ListHeader } from './ListHeader'
import { SectionsView } from './SectionsView'
import { SettingsDialog } from './SettingsDialog'
import { AdminDialog } from './AdminDialog'
import { SmartView } from './SmartView'
import { Sidebar } from './Sidebar'
import { SidebarResizer } from './SidebarResizer'
import { TaskPopover } from './TaskPopover'
import './Workspace.css'

// Clés du navigateur : listes ouvertes (vue scindée), largeur de la barre latérale
const PANES_KEY = 'bettertodo.panes'
const OLD_SELECTED_LIST_KEY = 'bettertodo.selectedList' // Ancienne clé (avant la vue scindée)
const SIDEBAR_WIDTH_KEY = 'bettertodo.sidebarWidth'

// Place minimale pour les listes (fenêtre moins barre latérale) pour afficher deux moitiés :
// 2 × 320 px. En dessous, on n'affiche que la moitié active.
const SPLIT_MIN_MAIN_WIDTH = 640
const NOTICE_DURATION = 6000 // ms d'affichage d'un message en bas de l'écran

interface WorkspaceProps {
    user: User
    onLogout: () => void
    onUserChanged: (user: User) => void
    onSessionExpired: () => void
}

/** Overlay ouvert : quel élément (tâche ou liste), et où il était à l'écran. */
interface OpenPopover {
    id: string
    anchor: DOMRect
}

/**
 * Une moitié de l'écran : la liste affichée et son contenu (null pendant le chargement).
 * listId peut aussi être une liste intelligente ('smart:today', 'smart:scheduled') :
 * son contenu est alors dans due, et board reste null.
 */
interface Pane {
    listId: string
    board: BoardData | null
    due: DueTask[] | null
}

// Durée de l'animation d'une tâche cochée qui quitte une liste intelligente (voir TaskRow.css)
const LEAVING_DELAY = 650

class WorkspaceState {
    folders: Folder[] = []
    lists: TodoList[] = []
    dueCounts: DueCounts | null = null       // Compteurs « Aujourd'hui » / « Planifié »
    leavingTaskIds: string[] = []            // Tâches cochées qui s'effacent d'une liste intelligente
    urgencyLevels: UrgencyLevel[] = []
    panes: Pane[] = []          // 1 liste affichée, ou 2 en vue scindée (gauche, droite)
    activePane: number = 0      // Moitié où s'ouvre une liste cliquée dans la barre latérale
    openTask: OpenPopover | null = null    // Overlay d'une tâche
    editingList: OpenPopover | null = null // Overlay « Modifier une liste »
    drag: DragState | null = null          // Glisser-déposer d'une tâche en cours
    listDrag: string | null = null         // Liste glissée depuis la barre latérale
    dropPane: number | null = null         // Moitié survolée pendant ce glisser
    addingGroupListId: string | null = null
    settingsOpen: boolean = false
    adminOpen: boolean = false // Console admin
    loading: boolean = true                // Premier chargement
    error: string = ''

    // Barre latérale
    sidebarWidth: number = readSidebarWidth()
    // Largeur à retrouver en quittant le mode icônes
    lastExpandedWidth: number = this.sidebarWidth >= SIDEBAR.compactBelow ? this.sidebarWidth : SIDEBAR.default
    windowWidth: number = window.innerWidth
    sidebarPeek: boolean = false           // Ouverte par-dessus le contenu (fenêtre étroite)
    notice: string = ''                    // Message en bas de l'écran (ex. vue scindée impossible)
}

/**
 * L'écran principal une fois connecté : barre latérale + une ou deux listes (vue scindée).
 *
 * C'est le composant qui parle à l'API pour les listes et les tâches. Pour chaque modification :
 *   1. l'écran est mis à jour tout de suite (« mise à jour optimiste ») ;
 *   2. la requête part vers l'API ;
 *   3. la réponse du serveur remplace la version locale ;
 *      en cas d'erreur, on recharge les listes affichées pour revenir à la vérité du serveur.
 *
 * Vue scindée : chaque moitié (Pane) a son board. Les modifications de tâches sont
 * appliquées à TOUS les boards affichés (updateBoards) : une tâche n'est que dans un
 * board, les autres ne changent pas. C'est ce qui rend le glisser d'une liste à l'autre simple.
 */
export class Workspace extends React.Component<WorkspaceProps, WorkspaceState> implements TaskViewActions {
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
            void this.refreshCounts()

            // Rouvre les listes de la dernière visite (si elles existent encore), sinon la première
            const saved = readSavedPanes().filter(id => isSmart(id) || lists.some(list => list.id === id))
            const listIds = saved.length > 0 ? saved : lists.slice(0, 1).map(list => list.id)
            for (let index = 0; index < listIds.length; index++) {
                await this.openList(listIds[index], index)
            }
        } catch (error) {
            this.handleError(error)
        }
    }

    componentWillUnmount(): void {
        window.removeEventListener('resize', this.onWindowResize)
    }

    /** Les listes ouvertes ont changé → on les retient pour la prochaine visite. */
    componentDidUpdate(_props: WorkspaceProps, previous: WorkspaceState): void {
        const before = previous.panes.map(pane => pane.listId).join()
        const now = this.state.panes.map(pane => pane.listId)
        if (now.join() !== before) savePanes(now)
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
    // Moitiés (vue scindée)
    // =========================================================================

    /** Largeur disponible pour les listes : la fenêtre moins la barre latérale (telle qu'affichée). */
    private mainWidth(): number {
        const { windowWidth, sidebarWidth } = this.state
        const sidebar = sidebarMode(windowWidth, sidebarWidth) === 'narrow' ? SIDEBAR.min : sidebarWidth
        return windowWidth - sidebar
    }

    /** Y a-t-il la place pour deux moitiés ? */
    private canSplit(): boolean {
        return this.mainWidth() >= SPLIT_MIN_MAIN_WIDTH
    }

    /** La vue scindée est-elle affichée ? (2 listes ouvertes ET assez de place) */
    private splitVisible(): boolean {
        return this.state.panes.length === 2 && this.canSplit()
    }

    /** Affiche un message quelques secondes en bas de l'écran. */
    private showNotice(notice: string): void {
        this.setState({ notice })
        window.clearTimeout(this.noticeTimer)
        this.noticeTimer = window.setTimeout(() => this.setState({ notice: '' }), NOTICE_DURATION)
    }

    private noticeTimer: number | undefined

    /** Message quand on demande la vue scindée sans avoir la place. */
    private splitTooNarrow(): void {
        this.showNotice(
            `Pas assez de place pour la vue scindée (il faut ${SPLIT_MIN_MAIN_WIDTH} px pour les listes, ` +
            `il y en a ${this.mainWidth()}). Agrandis la fenêtre ou réduis la barre latérale.`,
        )
    }

    /**
     * Ouvre une liste dans la moitié paneIndex (0 = gauche, 1 = droite ; 1 crée la moitié droite).
     * Si la liste est déjà ouverte dans l'autre moitié, on active simplement celle-ci.
     */
    openList = async (listId: string, paneIndex: number): Promise<void> => {
        // setState(state => …) et non this.state : au démarrage, deux openList se suivent de
        // près, et this.state peut ne pas encore contenir le board rangé par le premier.
        this.setState(state => {
            const already = state.panes.findIndex(pane => pane.listId === listId)
            if (already !== -1) {
                return { ...state, activePane: already, sidebarPeek: false }
            }

            const panes = [...state.panes]
            const index = Math.min(paneIndex, panes.length) // Pas de « trou » : au plus une moitié de plus
            panes[index] = { listId, board: null, due: null }
            return { ...state, panes, activePane: index, openTask: null, addingGroupListId: null, error: '', sidebarPeek: false }
        })
        await this.reloadBoard(listId)
    }

    /** Clic sur une liste : dans la moitié active. Cmd/Ctrl+clic : dans l'autre moitié (créée si besoin). */
    onSelectList = (listId: string, toSide: boolean): void => {
        if (toSide && !this.canSplit()) {
            this.splitTooNarrow() // On explique, puis on ouvre la liste normalement
            void this.openList(listId, this.state.activePane)
        } else if (toSide) {
            void this.openList(listId, this.state.panes.length < 2 ? 1 : 1 - this.state.activePane)
        } else {
            void this.openList(listId, this.state.activePane)
        }
    }

    closePane = (index: number): void => {
        this.setState(state => ({ panes: state.panes.filter((_, i) => i !== index), activePane: 0, openTask: null }))
    }

    // --- Glisser une liste de la barre latérale vers une moitié ----------------

    onListDragStart = (listId: string): void => {
        // setTimeout : afficher les zones de dépôt PENDANT le dragstart peut annuler le glisser
        // dans certains navigateurs (le contenu change sous la souris). On attend la fin de l'événement.
        window.setTimeout(() => this.setState({ listDrag: listId, openTask: null }), 0)
    }

    onListDragEnd = (): void => {
        this.setState({ listDrag: null, dropPane: null })
    }

    onListDrop = (paneIndex: number): void => {
        const listId = this.state.listDrag
        this.setState({ listDrag: null, dropPane: null })
        if (listId) void this.openList(listId, paneIndex)
    }

    // =========================================================================
    // Chargement des données
    // =========================================================================

    /** Recharge depuis le serveur le board d'une liste affichée (par défaut : toutes). */
    private async reloadBoard(listId?: string): Promise<void> {
        const listIds = listId ? [listId] : this.state.panes.map(pane => pane.listId)

        for (const id of listIds) {
            if (isSmart(id)) {
                await this.reloadSmart(id)
                continue
            }
            try {
                const board = await api.getBoard(id)
                // On ne range le board que si la liste est toujours affichée
                this.setState(state => ({
                    panes: state.panes.map(pane => (pane.listId === id ? { ...pane, board } : pane)),
                }))
            } catch (error) {
                this.handleError(error)
            }
        }
    }

    /** Recharge une liste intelligente : « Aujourd'hui » = échéances jusqu'à aujourd'hui (date LOCALE). */
    private async reloadSmart(id: string): Promise<void> {
        try {
            const due = await api.getDueTasks(id === SMART_TODAY ? todayIso() : undefined)
            this.setState(state => ({
                panes: state.panes.map(pane => (pane.listId === id ? { ...pane, due } : pane)),
            }))
        } catch (error) {
            this.handleError(error)
        }
    }

    private async refreshCounts(): Promise<void> {
        try {
            this.setState({ dueCounts: await api.getDueCounts(todayIso()) })
        } catch (error) {
            this.handleError(error)
        }
    }

    /**
     * Après une modification de tâches : recharge les compteurs de la barre latérale
     * et les listes intelligentes affichées (une échéance a pu changer).
     */
    private async refreshLists(): Promise<void> {
        try {
            this.setState({ lists: await api.getLists() })
        } catch (error) {
            this.handleError(error)
        }
        void this.refreshCounts()
        for (const pane of this.state.panes) {
            if (isSmart(pane.listId)) void this.reloadSmart(pane.listId)
        }
    }

    /** Applique une fonction aux tâches des listes intelligentes affichées. */
    private updateDue(change: (tasks: DueTask[]) => DueTask[]): void {
        this.setState(state => ({
            panes: state.panes.map(pane => (pane.due ? { ...pane, due: change(pane.due) } : pane)),
        }))
    }

    /** Applique une fonction de utils/board.ts à chaque board affiché. */
    private updateBoards(change: (board: BoardData) => BoardData): void {
        this.setState(state => ({
            panes: state.panes.map(pane => (pane.board ? { ...pane, board: change(pane.board) } : pane)),
        }))
    }

    /** Les boards déjà chargés. */
    private boards(): BoardData[] {
        return this.state.panes.flatMap(pane => (pane.board ? [pane.board] : []))
    }

    // =========================================================================
    // Barre latérale : groupes de listes et listes
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

    /** Crée une liste et l'ouvre aussitôt dans la moitié active. */
    onCreateList = async (name: string, folderId: string | null): Promise<void> => {
        try {
            const list = await api.createList(name, folderId)
            await this.refreshLists()
            await this.openList(list.id, this.state.activePane)
        } catch (error) {
            this.handleError(error)
        }
    }

    onRenameFolder = async (folderId: string, name: string): Promise<void> => {
        this.setState(state => ({ folders: state.folders.map(f => (f.id === folderId ? { ...f, name } : f)) }))
        try {
            await api.updateFolder(folderId, { name })
        } catch (error) {
            this.handleError(error)
        }
    }

    /** Supprime un groupe de listes : ses listes ne sont pas supprimées, elles passent hors groupe. */
    onDeleteFolder = async (folder: Folder): Promise<void> => {
        if (!(await confirmDialog.ask(`Supprimer le groupe de listes « ${folder.name} » ? Ses listes sont gardées, hors groupe.`))) return

        this.setState(state => ({
            folders: state.folders.filter(f => f.id !== folder.id),
            lists: state.lists.map(l => (l.folder_id === folder.id ? { ...l, folder_id: null } : l)),
        }))
        try {
            await api.deleteFolder(folder.id)
        } catch (error) {
            this.handleError(error)
        }
        void this.refreshLists()
    }

    onOpenSettings = (): void => {
        this.setState({ settingsOpen: true, openTask: null, editingList: null, sidebarPeek: false })
    }

    /** À la fermeture : les listes affichées peuvent contenir un tag renommé ou supprimé. */
    onCloseSettings = (): void => {
        this.setState({ settingsOpen: false })
        void this.reloadBoard()
    }

    onOpenAdmin = (): void => {
        this.setState({ adminOpen: true, openTask: null, editingList: null, sidebarPeek: false })
    }

    onCloseAdmin = (): void => {
        this.setState({ adminOpen: false })
    }

    onEditList = (list: TodoList, anchor: DOMRect): void => {
        this.setState({ editingList: { id: list.id, anchor }, openTask: null })
    }

    /** Modifie une liste (nom, couleur, icône, groupe de listes, vue, tâches faites). */
    updateList = async (listId: string, changes: ListChanges): Promise<void> => {
        const apply = (list: TodoList): TodoList => (list.id === listId ? { ...list, ...changes } : list)
        this.setState(state => ({
            lists: state.lists.map(apply),
            panes: state.panes.map(pane => (pane.board ? { ...pane, board: { ...pane.board, list: apply(pane.board.list) } } : pane)),
        }))

        try {
            await api.updateList(listId, changes)
        } catch (error) {
            this.handleError(error)
        }
        // Changer de groupe de listes change l'ordre de la barre latérale
        if (changes.folder_id !== undefined) void this.refreshLists()
    }

    /** Bascule Colonnes / Sections : enregistrée sur la liste (retrouvée sur tous les appareils). */
    onChangeView = (list: TodoList, view: ListView): void => {
        if (list.default_view !== view) void this.updateList(list.id, { default_view: view })
    }

    deleteList = async (list: TodoList): Promise<void> => {
        const remaining = this.state.lists.filter(l => l.id !== list.id)
        this.setState({ editingList: null, lists: remaining })

        // Si la liste était affichée : on ferme sa moitié (vue scindée) ou on ouvre la première qui reste
        const paneIndex = this.state.panes.findIndex(pane => pane.listId === list.id)
        if (paneIndex !== -1) {
            if (this.state.panes.length === 2) {
                this.closePane(paneIndex)
            } else if (remaining.length > 0) {
                this.setState({ panes: [] })
                void this.openList(remaining[0].id, 0)
            } else {
                this.setState({ panes: [] })
            }
        }

        try {
            await api.deleteList(list.id)
        } catch (error) {
            this.handleError(error)
            void this.refreshLists()
        }
    }

    // =========================================================================
    // Vues d'une liste (TaskViewActions) : appelées par Board, SectionsView, TaskCard, TaskRow
    // =========================================================================

    toggleTask = async (task: Task): Promise<void> => {
        const done = !task.done
        this.updateBoards(board => replaceTask(board, { ...task, done }))
        this.updateDue(tasks => tasks.map(t => (t.id === task.id ? { ...t, done } : t)))

        // Cochée dans une liste intelligente : la ligne s'efface, puis la liste est rechargée
        const inSmart = this.state.panes.some(pane => pane.due?.some(t => t.id === task.id))
        if (done && inSmart) {
            this.setState(state => ({ leavingTaskIds: [...state.leavingTaskIds, task.id] }))
        }

        try {
            const saved = await api.updateTask(task.id, { done })
            this.updateBoards(board => replaceTask(board, saved))
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard()
        }

        if (done && inSmart) {
            window.setTimeout(() => {
                this.setState(state => ({ leavingTaskIds: state.leavingTaskIds.filter(id => id !== task.id) }))
                void this.refreshLists()
            }, LEAVING_DELAY)
        } else {
            void this.refreshLists()
        }
    }

    openTask = (task: Task, anchor: DOMRect): void => {
        this.setState({ openTask: { id: task.id, anchor } })
    }

    createTask = async (groupId: string, title: string): Promise<void> => {
        try {
            const task = await api.createTask(groupId, title)
            this.updateBoards(board => appendTask(board, task))
            void this.refreshLists()
        } catch (error) {
            this.handleError(error)
        }
    }

    // --- Glisser-déposer : l'état est ici, au-dessus des deux moitiés -----------

    startTaskDrag = (task: Task): void => {
        // setTimeout : laisse le navigateur faire sa « photo » de la carte avant de la griser
        window.setTimeout(() => this.setState({ drag: { taskId: task.id, target: null }, openTask: null }), 0)
    }

    endTaskDrag = (): void => {
        this.setState({ drag: null })
    }

    setDropTarget = (target: DropTarget | null): void => {
        this.setState(state => (state.drag ? { drag: { ...state.drag, target } } : null))
    }

    dropTask = (): void => {
        const drag = this.state.drag
        this.setState({ drag: null })
        if (drag?.target) {
            void this.moveTask(drag.taskId, drag.target.groupId, drag.target.index)
        }
    }

    /**
     * Déplace une tâche dans un groupe, éventuellement d'une AUTRE liste (vue scindée).
     * visibleIndex : index sous la souris, compté parmi les tâches affichées sans la tâche déplacée.
     */
    private async moveTask(taskId: string, groupId: string, visibleIndex: number): Promise<void> {
        const boards = this.boards()
        const source = boardWithTask(boards, taskId)
        const target = boardWithGroup(boards, groupId)
        const task = source ? findTask(source, taskId) : undefined
        const group = target?.groups.find(g => g.id === groupId)
        if (!source || !target || !task || !group) return

        // Tâches faites masquées dans la liste cible : l'index est compté parmi les tâches visibles
        const index = target.list.show_done ? visibleIndex : visibleToFullIndex(group.tasks, taskId, visibleIndex)

        const sameGroup = task.group_id === groupId
        if (sameGroup && indexInGroup(source, taskId) === index) return // Reposée à sa place

        this.updateBoards(board => placeTask(board, task, groupId, index))
        const changes: TaskChanges = { position: index }
        if (!sameGroup) changes.group_id = groupId

        try {
            await api.updateTask(taskId, changes)
        } catch (error) {
            this.handleError(error)
        }
        void this.reloadBoard() // Récupère les positions renumérotées par le serveur
        if (source.list.id !== target.list.id) void this.refreshLists() // Compteurs des deux listes
    }

    createGroup = async (listId: string, name: string): Promise<void> => {
        try {
            const group = await api.createGroup(listId, name)
            this.updateBoards(board => (
                board.list.id === listId ? { ...board, groups: [...board.groups, { ...group, tasks: [] }] } : board
            ))
        } catch (error) {
            this.handleError(error)
        }
    }

    renameGroup = async (groupId: string, name: string): Promise<void> => {
        this.updateBoards(board => ({
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
        if (!(await confirmDialog.ask(message))) return

        this.updateBoards(board => ({ ...board, groups: board.groups.filter(g => g.id !== group.id) }))
        try {
            await api.deleteGroup(group.id)
            void this.refreshLists()
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard()
        }
    }

    // --- Tâches faites : masquer / vider ----------------------------------------

    onToggleShowDone = (list: TodoList): void => {
        void this.updateList(list.id, { show_done: !list.show_done })
    }

    onClearDone = async (board: BoardData): Promise<void> => {
        const count = countDoneTasks(board)
        if (count === 0) {
            await confirmDialog.inform('Aucune tâche faite à vider dans cette liste.')
            return
        }
        const word = count > 1 ? `les ${count} tâches faites` : 'la tâche faite'
        if (!(await confirmDialog.ask(`Supprimer définitivement ${word} de « ${board.list.name} » ?`, { confirmLabel: 'Vider' }))) return

        const listId = board.list.id
        this.setState({ openTask: null })
        this.updateBoards(b => (
            b.list.id === listId ? { ...b, groups: b.groups.map(g => ({ ...g, tasks: g.tasks.filter(t => !t.done) })) } : b
        ))
        try {
            await api.clearDoneTasks(listId)
            void this.refreshLists()
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard(listId)
        }
    }

    // =========================================================================
    // Overlay d'une tâche
    // =========================================================================

    private async updateTask(taskId: string, changes: TaskChanges): Promise<void> {
        // Changement de groupe : la tâche part tout de suite à la fin du nouveau groupe
        if (changes.group_id) {
            const groupId = changes.group_id
            this.updateBoards(board => moveTask(board, taskId, groupId, Number.MAX_SAFE_INTEGER))
        }

        try {
            const saved = await api.updateTask(taskId, changes)
            this.updateBoards(board => replaceTask(board, saved))
            // Listes intelligentes : on garde la liste et le groupe d'origine, on met à jour le reste
            this.updateDue(tasks => tasks.map(t => (t.id === saved.id ? { ...t, ...saved } : t)))
            if (changes.done !== undefined || changes.due_date !== undefined) void this.refreshLists()
        } catch (error) {
            this.handleError(error)
            void this.reloadBoard()
        }
    }

    private async deleteTask(taskId: string): Promise<void> {
        this.setState({ openTask: null })
        this.updateBoards(board => removeTask(board, taskId))
        this.updateDue(tasks => tasks.filter(t => t.id !== taskId))
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
        const { lists, panes, activePane, error, loading } = this.state
        // Fenêtre trop étroite : seule la moitié active est affichée (l'autre reste ouverte)
        const shown = this.splitVisible() ? [0, 1] : panes.length > 0 ? [Math.min(activePane, panes.length - 1)] : []

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

                    <div className="panes">
                        {shown.map(index => this.renderPane(index))}
                        {this.state.listDrag && this.renderListDropZones()}
                    </div>

                    {!loading && lists.length === 0 && (
                        <div className="workspace-empty">Crée ta première liste avec le + de la barre latérale.</div>
                    )}
                </main>

                {this.state.notice && (
                    <div className="workspace-notice" role="status">
                        {this.state.notice}
                        <button onClick={() => this.setState({ notice: '' })}>OK</button>
                    </div>
                )}
                {this.renderPopover()}
                {this.renderListEditor()}
                {this.state.settingsOpen && (
                    <SettingsDialog
                        user={this.props.user}
                        urgencyLevels={this.state.urgencyLevels}
                        onUserChanged={this.props.onUserChanged}
                        onUrgencyLevelsChanged={urgencyLevels => this.setState({ urgencyLevels })}
                        onClose={this.onCloseSettings}
                    />
                )}
                {this.state.adminOpen && <AdminDialog user={this.props.user} onClose={this.onCloseAdmin} />}
            </div>
        )
    }

    /** Une moitié : en-tête de la liste + vue colonnes ou sections. */
    private renderPane(index: number): React.ReactNode {
        const pane = this.state.panes[index]
        const board = pane.board
        const split = this.splitVisible()

        const classes = ['pane']
        if (split && index === this.state.activePane) classes.push('active')

        return (
            <section
                key={pane.listId}
                className={classes.join(' ')}
                onMouseDown={() => { if (this.state.activePane !== index) this.setState({ activePane: index }) }}
            >
                {isSmart(pane.listId) && (
                    <SmartView
                        kind={pane.listId}
                        tasks={pane.due}
                        urgencyLevels={this.state.urgencyLevels}
                        openTaskId={this.state.openTask?.id ?? null}
                        leavingTaskIds={this.state.leavingTaskIds}
                        onToggle={this.toggleTask}
                        onOpen={this.openTask}
                        onClosePane={this.state.panes.length === 2 ? () => this.closePane(index) : undefined}
                    />
                )}
                {board && board.list.id === pane.listId && (
                    <>
                        <ListHeader
                            list={board.list}
                            openTasks={countOpenTasks(board)}
                            doneTasks={countDoneTasks(board)}
                            onNewGroup={() => this.setState({ addingGroupListId: board.list.id })}
                            onChangeView={view => this.onChangeView(board.list, view)}
                            onEditList={anchor => this.onEditList(board.list, anchor)}
                            onToggleShowDone={() => this.onToggleShowDone(board.list)}
                            onClearDone={() => void this.onClearDone(board)}
                            onClosePane={this.state.panes.length === 2 ? () => this.closePane(index) : undefined}
                        />
                        {this.renderView(board)}
                    </>
                )}
            </section>
        )
    }

    /**
     * Pendant qu'on glisse une liste depuis la barre latérale : des zones de dépôt
     * par-dessus les moitiés. Une seule liste ouverte → « Ouvrir ici » / « Ouvrir à droite ».
     * Deux listes → « Remplacer » à gauche / à droite.
     */
    private renderListDropZones(): React.ReactNode {
        const canSplit = this.canSplit()
        const twoPanes = this.state.panes.length === 2

        // Fenêtre étroite : une seule moitié est visible, la liste remplace celle-là
        const zones = canSplit
            ? [
                { pane: 0, label: twoPanes ? 'Remplacer à gauche' : 'Ouvrir ici' },
                { pane: 1, label: twoPanes ? 'Remplacer à droite' : 'Ouvrir à droite' },
            ]
            : [{ pane: Math.min(this.state.activePane, Math.max(0, this.state.panes.length - 1)), label: 'Ouvrir ici' }]

        return (
            <div className="list-drop-zones">
                {zones.map(zone => (
                    <div
                        key={zone.pane}
                        className={this.state.dropPane === zone.pane ? 'list-drop-zone over' : 'list-drop-zone'}
                        onDragOver={event => {
                            event.preventDefault() // Accepte le dépôt
                            if (this.state.dropPane !== zone.pane) this.setState({ dropPane: zone.pane })
                        }}
                        onDragLeave={() => this.setState({ dropPane: null })}
                        onDrop={event => {
                            event.preventDefault()
                            this.onListDrop(zone.pane)
                        }}
                    >
                        <span className="list-drop-label">{zone.label}</span>
                        {!canSplit && (
                            <span className="list-drop-hint">
                                Pas assez de place pour la vue scindée : agrandis la fenêtre ou réduis la barre latérale.
                            </span>
                        )}
                    </div>
                ))}
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
        const { windowWidth, sidebarWidth, sidebarPeek, panes, activePane } = this.state
        const mode = sidebarMode(windowWidth, sidebarWidth)
        const peek = mode === 'narrow' && sidebarPeek

        let width = sidebarWidth
        if (mode === 'narrow') width = peek ? SIDEBAR.default : SIDEBAR.min

        const sidebar = (
            <Sidebar
                user={this.props.user}
                folders={this.state.folders}
                lists={this.state.lists}
                dueCounts={this.state.dueCounts}
                selectedListId={panes[activePane]?.listId ?? null}
                secondaryListId={this.splitVisible() ? panes[1 - activePane]?.listId ?? null : null}
                width={width}
                compact={mode !== 'expanded' && !peek}
                peek={peek}
                onExpand={this.onSidebarExpand}
                onCollapse={this.onSidebarCollapse}
                onSelectList={this.onSelectList}
                onListDragStart={this.onListDragStart}
                onListDragEnd={this.onListDragEnd}
                onToggleFolder={this.onToggleFolder}
                onCreateList={this.onCreateList}
                onCreateFolder={this.onCreateFolder}
                onRenameFolder={this.onRenameFolder}
                onDeleteFolder={this.onDeleteFolder}
                onEditList={this.onEditList}
                onOpenSettings={this.onOpenSettings}
                onOpenAdmin={this.onOpenAdmin}
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

    /** Vue colonnes ou sections, selon le choix enregistré sur la liste. */
    private renderView(board: BoardData): React.ReactNode {
        const props: TaskViewProps = {
            board: board.list.show_done ? board : hideDoneTasks(board), // Le board complet reste dans l'état
            urgencyLevels: this.state.urgencyLevels,
            actions: this,
            openTaskId: this.state.openTask?.id ?? null,
            drag: this.state.drag,
            addingGroup: this.state.addingGroupListId === board.list.id,
            onAddingGroupDone: () => this.setState({ addingGroupListId: null }),
        }
        return board.list.default_view === 'sections' ? <SectionsView {...props} /> : <Board {...props} />
    }

    private renderListEditor(): React.ReactNode {
        const editing = this.state.editingList
        const list = editing ? this.state.lists.find(l => l.id === editing.id) : undefined
        if (!editing || !list) return null

        return (
            <ListEditPopover
                key={list.id}
                list={list}
                folders={this.state.folders}
                anchor={editing.anchor}
                onUpdate={changes => void this.updateList(list.id, changes)}
                onDelete={() => void this.deleteList(list)}
                onClose={() => this.setState({ editingList: null })}
            />
        )
    }

    private renderPopover(): React.ReactNode {
        const open = this.state.openTask
        if (!open) return null

        // La tâche vient d'une liste affichée, ou d'une liste intelligente
        const board = boardWithTask(this.boards(), open.id)
        const due = this.state.panes.flatMap(pane => pane.due ?? []).find(t => t.id === open.id)
        const task = board ? findTask(board, open.id) : due
        if (!task) return null

        // Depuis une liste intelligente, on ne connaît que le groupe de la tâche (pas toute sa liste)
        const groups = board
            ? board.groups
            : [{ id: task.group_id, list_id: due!.list.id, name: due!.group_name, position: 0 }]

        return (
            <TaskPopover
                key={task.id} // Nouvelle tâche → nouveau composant (repart de son titre et ses notes)
                task={task}
                groups={groups}
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

/** Listes ouvertes lors de la dernière visite (1 ou 2 identifiants). */
function readSavedPanes(): string[] {
    try {
        const saved = JSON.parse(readStorage(PANES_KEY) ?? '[]')
        if (Array.isArray(saved) && saved.every(id => typeof id === 'string')) return saved.slice(0, 2)
    } catch {
        // Valeur abîmée : on l'ignore
    }
    const old = readStorage(OLD_SELECTED_LIST_KEY) // Avant la vue scindée, une seule liste était retenue
    return old ? [old] : []
}

function savePanes(listIds: string[]): void {
    writeStorage(PANES_KEY, JSON.stringify(listIds))
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
        // Pas grave : on ne se souviendra simplement pas de ce réglage
    }
}
