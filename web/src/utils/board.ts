// =============================================================================
// Fonctions de modification du board, SANS effet de bord : elles renvoient un
// nouveau board au lieu de modifier l'ancien (React ne voit un changement d'état
// que si l'objet change). Testées dans board.test.ts.
// =============================================================================

import type { Board, BoardGroup, Task } from '../api/types'

/** Cherche une tâche dans tous les groupes. */
export function findTask(board: Board, taskId: string): Task | undefined {
    for (const group of board.groups) {
        const task = group.tasks.find(t => t.id === taskId)
        if (task) return task
    }
    return undefined
}

/** Applique fn à chaque groupe et renvoie un nouveau board. */
function mapGroups(board: Board, fn: (group: BoardGroup) => BoardGroup): Board {
    return { ...board, groups: board.groups.map(fn) }
}

/** Remplace une tâche (même id) par sa nouvelle version, sans changer sa place. */
export function replaceTask(board: Board, task: Task): Board {
    return mapGroups(board, group => ({
        ...group,
        tasks: group.tasks.map(t => (t.id === task.id ? task : t)),
    }))
}

/** Enlève une tâche du board. */
export function removeTask(board: Board, taskId: string): Board {
    return mapGroups(board, group => ({
        ...group,
        tasks: group.tasks.filter(t => t.id !== taskId),
    }))
}

/** Ajoute une tâche à la fin de son groupe (task.group_id). */
export function appendTask(board: Board, task: Task): Board {
    return mapGroups(board, group => (
        group.id === task.group_id ? { ...group, tasks: [...group.tasks, task] } : group
    ))
}

/**
 * Déplace une tâche dans groupId, à l'index donné (compté SANS la tâche déplacée,
 * comme le fait l'API). Un index trop grand la met à la fin.
 */
export function moveTask(board: Board, taskId: string, groupId: string, index: number): Board {
    const task = findTask(board, taskId)
    if (!task) return board

    const moved: Task = { ...task, group_id: groupId }
    const without = removeTask(board, taskId)

    return mapGroups(without, group => {
        if (group.id !== groupId) return group
        const tasks = [...group.tasks]
        tasks.splice(Math.max(0, Math.min(index, tasks.length)), 0, moved)
        return { ...group, tasks }
    })
}

/** Index actuel d'une tâche dans son groupe, ou -1. */
export function indexInGroup(board: Board, taskId: string): number {
    for (const group of board.groups) {
        const index = group.tasks.findIndex(t => t.id === taskId)
        if (index !== -1) return index
    }
    return -1
}

/** Nombre de tâches pas encore faites dans tout le board. */
export function countOpenTasks(board: Board): number {
    return board.groups.reduce((sum, group) => sum + group.tasks.filter(t => !t.done).length, 0)
}

/** Nombre de tâches faites dans tout le board. */
export function countDoneTasks(board: Board): number {
    return board.groups.reduce((sum, group) => sum + group.tasks.filter(t => t.done).length, 0)
}

/** Board sans les tâches faites (affichage quand la liste les masque). */
export function hideDoneTasks(board: Board): Board {
    return mapGroups(board, group => ({ ...group, tasks: group.tasks.filter(t => !t.done) }))
}

/**
 * Quand les tâches faites sont masquées, l'index de dépôt est compté parmi les tâches
 * VISIBLES, mais l'API attend un index parmi TOUTES les tâches du groupe (sans la tâche
 * déplacée). Cette fonction fait la conversion.
 *
 * Exemple : [A, x, B] (x faite, masquée), on dépose C en position visible 1 (avant B)
 * → index réel 2 (après x, avant B).
 */
export function visibleToFullIndex(tasks: Task[], movedId: string, visibleIndex: number): number {
    const others = tasks.filter(t => t.id !== movedId)
    const visible = others.filter(t => !t.done)

    if (visibleIndex < visible.length) {
        return others.indexOf(visible[visibleIndex]) // Juste avant la tâche visible visée
    }
    if (visible.length === 0) {
        return 0 // Aucune tâche visible : en tête
    }
    return others.indexOf(visible[visible.length - 1]) + 1 // Juste après la dernière visible
}
