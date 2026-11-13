import * as React from 'react'
import { Check, StickyNote } from 'lucide-react'
import type { Task, UrgencyLevel } from '../api/types'
import { DueDate, UrgencyPill } from './TaskCard'
import './TaskRow.css'

interface TaskRowProps {
    task: Task
    urgency: UrgencyLevel | undefined
    selected: boolean
    dragging: boolean
    onToggle: (task: Task) => void
    onOpen: (task: Task, anchor: DOMRect) => void
    onDragStart: (task: Task) => void
    onDragEnd: () => void
}

/**
 * Une tâche dans la vue sections : une ligne avec, de gauche à droite,
 * case à cocher, titre, note, puis les colonnes tags / échéance / urgence.
 * Même comportement que TaskCard (clic → overlay, case → cocher, glissable).
 */
export class TaskRow extends React.Component<TaskRowProps> {
    onClick = (event: React.MouseEvent<HTMLDivElement>): void => {
        this.props.onOpen(this.props.task, event.currentTarget.getBoundingClientRect())
    }

    onKeyDown = (event: React.KeyboardEvent<HTMLDivElement>): void => {
        if (event.key === 'Enter') {
            this.props.onOpen(this.props.task, event.currentTarget.getBoundingClientRect())
        }
    }

    onCheckClick = (event: React.MouseEvent<HTMLButtonElement>): void => {
        event.stopPropagation() // Ne pas ouvrir l'overlay en même temps
        this.props.onToggle(this.props.task)
    }

    onDragStart = (event: React.DragEvent<HTMLDivElement>): void => {
        event.dataTransfer.setData('text/plain', this.props.task.id)
        event.dataTransfer.effectAllowed = 'move'
        this.props.onDragStart(this.props.task)
    }

    render(): React.ReactNode {
        const { task, urgency, selected, dragging } = this.props

        const classes = ['TaskRow']
        if (task.done) classes.push('done')
        if (selected) classes.push('selected')
        if (dragging) classes.push('dragging')

        return (
            <div
                className={classes.join(' ')}
                data-task-id={task.id}
                role="button"
                tabIndex={0}
                draggable
                onClick={this.onClick}
                onKeyDown={this.onKeyDown}
                onDragStart={this.onDragStart}
                onDragEnd={this.props.onDragEnd}
            >
                <button
                    className={task.done ? 'card-check checked' : 'card-check'}
                    onClick={this.onCheckClick}
                    aria-label={task.done ? 'Marquer comme à faire' : 'Marquer comme faite'}
                >
                    {task.done && <Check size={10} color="#FFFFFF" strokeWidth={3.5} />}
                </button>
                <span className="row-title">{task.title}</span>
                {task.notes !== '' && <span className="card-note" title={task.notes}><StickyNote size={12} /></span>}
                <span className="row-spacer" />

                <span className="row-tags">
                    {task.tags.map(tag => <span key={tag} className="card-tag">#{tag}</span>)}
                </span>
                <span className="row-due">
                    {task.due_date ? <DueDate dueDate={task.due_date} done={task.done} /> : <span className="row-none">—</span>}
                </span>
                <span className="row-urgency">
                    {urgency && <UrgencyPill urgency={urgency} />}
                </span>
            </div>
        )
    }
}
