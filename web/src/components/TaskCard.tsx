import * as React from 'react'
import { Calendar, Check, StickyNote } from 'lucide-react'
import type { Task, UrgencyLevel } from '../api/types'
import { formatDueDate, isDueOrLate } from '../utils/dates'
import './TaskCard.css'

interface TaskCardProps {
    task: Task
    urgency: UrgencyLevel | undefined // undefined = pas d'urgence
    selected: boolean                 // Son overlay est ouvert
    dragging: boolean                 // C'est la carte en train d'être glissée
    onToggle: (task: Task) => void
    onOpen: (task: Task, anchor: DOMRect) => void
    onDragStart: (task: Task) => void
    onDragEnd: () => void
}

/**
 * Une carte de tâche : case à cocher, titre, urgence, puis échéance, tags et note.
 * Clic sur la carte → overlay d'édition. Clic sur la case → cocher / décocher.
 * La carte peut être glissée (API glisser-déposer native du navigateur).
 */
export class TaskCard extends React.Component<TaskCardProps> {
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
        event.dataTransfer.setData('text/plain', this.props.task.id) // Obligatoire pour Firefox
        event.dataTransfer.effectAllowed = 'move'
        this.props.onDragStart(this.props.task)
    }

    render(): React.ReactNode {
        const { task, urgency, selected, dragging } = this.props
        const hasMeta = task.due_date !== null || task.tags.length > 0 || task.notes !== ''

        const classes = ['TaskCard']
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
                <div className="card-top">
                    <button
                        className={task.done ? 'card-check checked' : 'card-check'}
                        onClick={this.onCheckClick}
                        aria-label={task.done ? 'Marquer comme à faire' : 'Marquer comme faite'}
                    >
                        {task.done && <Check size={10} color="#FFFFFF" strokeWidth={3.5} />}
                    </button>
                    <span className="card-title">{task.title}</span>
                    {urgency && <UrgencyPill urgency={urgency} />}
                </div>

                {hasMeta && (
                    <div className="card-meta">
                        {task.due_date && this.renderDueDate(task.due_date)}
                        {task.tags.map(tag => <span key={tag} className="card-tag">#{tag}</span>)}
                        {task.notes !== '' && (
                            <span className="card-note" title={task.notes}><StickyNote size={12} /></span>
                        )}
                    </div>
                )}
            </div>
        )
    }

    /** Échéance, en rouge si c'est aujourd'hui ou en retard (sauf si la tâche est faite). */
    private renderDueDate(dueDate: string): React.ReactNode {
        const late = !this.props.task.done && isDueOrLate(dueDate)
        return (
            <span className={late ? 'card-due late' : 'card-due'}>
                <Calendar size={12} />
                {formatDueDate(dueDate)}
            </span>
        )
    }
}

/** Pastille d'urgence : texte et point dans la couleur du niveau, fond très clair. */
export class UrgencyPill extends React.Component<{ urgency: UrgencyLevel }> {
    render(): React.ReactNode {
        const { urgency } = this.props
        return (
            <span className="card-urgency" style={{ color: urgency.color, background: urgency.color + '1F' }}>
                <span className="card-urgency-dot" style={{ background: urgency.color }} />
                {urgency.name}
            </span>
        )
    }
}
