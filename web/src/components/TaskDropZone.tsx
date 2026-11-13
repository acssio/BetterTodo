import * as React from 'react'
import type { Task } from '../api/types'
import { computeDropIndex, type DragState } from '../utils/dragDrop'
import type { TaskViewActions } from './actions'
import './TaskDropZone.css'

interface TaskDropZoneProps {
    groupId: string
    tasks: Task[]
    drag: DragState | null
    actions: TaskViewActions
    className: string        // "board-column" (colonnes) ou "section" (sections)
    header: React.ReactNode  // En-tête du groupe
    footer: React.ReactNode  // « Ajouter une tâche »
    renderTask: (task: Task, dragging: boolean) => React.ReactNode
}

/**
 * Un groupe dans lequel on peut déposer une tâche. Utilisé par les DEUX vues :
 * colonnes (TaskCard) et sections (TaskRow), puisque dans les deux cas les
 * tâches sont empilées verticalement.
 *
 *   dragover → calcule l'index sous la souris → actions.setDropTarget → ligne bleue
 *   drop     → actions.dropTask()
 */
export class TaskDropZone extends React.Component<TaskDropZoneProps> {
    onDragOver = (event: React.DragEvent<HTMLElement>): void => {
        const { drag, groupId } = this.props
        if (drag === null) return // Pas une de nos tâches (un fichier, un texte…)
        event.preventDefault()    // Sans ça, le navigateur refuse le dépôt
        event.dataTransfer.dropEffect = 'move'

        const index = computeDropIndex(event.currentTarget, event.clientY, drag.taskId)
        const target = drag.target
        if (!target || target.groupId !== groupId || target.index !== index) {
            this.props.actions.setDropTarget({ groupId, index })
        }
    }

    onDragLeave = (event: React.DragEvent<HTMLElement>): void => {
        // dragleave se déclenche aussi en passant sur un enfant : on ne réagit qu'en sortant vraiment
        if (event.currentTarget.contains(event.relatedTarget as Node | null)) return
        if (this.props.drag?.target?.groupId === this.props.groupId) {
            this.props.actions.setDropTarget(null)
        }
    }

    onDrop = (event: React.DragEvent<HTMLElement>): void => {
        event.preventDefault()
        this.props.actions.dropTask()
    }

    render(): React.ReactNode {
        const { drag, groupId, tasks } = this.props
        const target = drag?.target
        const isDropZone = target !== undefined && target !== null && target.groupId === groupId

        // On insère la ligne de dépôt à l'index visé, en sautant la tâche glissée
        const items: React.ReactNode[] = []
        let visibleIndex = 0
        for (const task of tasks) {
            const dragging = task.id === drag?.taskId
            if (!dragging) {
                if (isDropZone && target.index === visibleIndex) {
                    items.push(<div key="drop" className="drop-indicator" />)
                }
                visibleIndex++
            }
            items.push(this.props.renderTask(task, dragging))
        }
        if (isDropZone && target.index === visibleIndex) {
            items.push(<div key="drop" className="drop-indicator" />)
        }

        return (
            <section
                className={this.props.className + (isDropZone ? ' drop-over' : '')}
                onDragOver={this.onDragOver}
                onDragLeave={this.onDragLeave}
                onDrop={this.onDrop}
            >
                {this.props.header}
                {items}
                {this.props.footer}
            </section>
        )
    }
}
