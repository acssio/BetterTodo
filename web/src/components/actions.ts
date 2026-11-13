import type { Board, BoardGroup, Task, UrgencyLevel } from '../api/types'
import type { DragState, DropTarget } from '../utils/dragDrop'

/**
 * Ce que les vues d'une liste (colonnes, sections) demandent à Workspace.
 * Workspace est le seul à parler à l'API : les vues ne font qu'appeler ces méthodes.
 */
export interface TaskViewActions {
    toggleTask(task: Task): void
    openTask(task: Task, anchor: DOMRect): void
    createTask(groupId: string, title: string): Promise<void>
    createGroup(name: string): Promise<void>
    renameGroup(groupId: string, name: string): Promise<void>
    deleteGroup(group: BoardGroup): void

    // Glisser-déposer
    startTaskDrag(task: Task): void
    endTaskDrag(): void
    setDropTarget(target: DropTarget | null): void
    dropTask(): void
}

/** Props communes à la vue colonnes (Board) et à la vue sections (SectionsView). */
export interface TaskViewProps {
    board: Board
    urgencyLevels: UrgencyLevel[]
    actions: TaskViewActions
    openTaskId: string | null // Tâche dont l'overlay est ouvert
    drag: DragState | null    // Glisser-déposer en cours
    addingGroup: boolean      // Champ « nouveau groupe » affiché (bouton de l'en-tête)
    onAddingGroupDone: () => void
}
