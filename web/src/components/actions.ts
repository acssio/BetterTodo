import type { Board, BoardGroup, Task, UrgencyLevel } from '../api/types'
import type { DragState, DropTarget } from '../utils/dragDrop'

/**
 * Ce que les vues d'une liste (colonnes, sections) demandent à Workspace.
 * Workspace est le seul à parler à l'API : les vues ne font qu'appeler ces méthodes.
 */
export interface TaskViewActions {
    toggleTask(task: Task): void
    openTask(task: Task, anchor: DOMRect): void
    renameTask(task: Task, title: string): void

    // Sélection (Cmd+clic / Ctrl+clic) et modification du titre sur place
    toggleSelection(task: Task): void
    startEditing(task: Task): void // Vide la sélection et ferme l'overlay
    createTask(groupId: string, title: string): Promise<void>
    createGroup(listId: string, name: string): Promise<void>
    renameGroup(groupId: string, name: string): Promise<void>
    deleteGroup(group: BoardGroup): void

    // Glisser-déposer
    startTaskDrag(task: Task): void
    endTaskDrag(): void
    setDropTarget(target: DropTarget | null): void
    dropTask(): void

    // Glisser au doigt (mode mobile, après un appui long) : Workspace cherche la cible sous le doigt
    touchDragStart(task: Task, x: number, y: number): void
    touchDragMove(x: number, y: number): void
    touchDragEnd(drop: boolean): void
}

/** Props communes à la vue colonnes (Board) et à la vue sections (SectionsView). */
export interface TaskViewProps {
    board: Board
    urgencyLevels: UrgencyLevel[]
    actions: TaskViewActions
    openTaskId: string | null // Tâche dont l'overlay est ouvert
    selectedTaskIds: string[] // Tâches sélectionnées
    drag: DragState | null    // Glisser-déposer en cours
    addingGroup: boolean      // Champ « nouveau groupe » affiché (bouton de l'en-tête)
    onAddingGroupDone: () => void
    mobile: boolean           // Mode mobile : toucher une tâche ouvre l'overlay, un groupe par écran (colonnes)
}
