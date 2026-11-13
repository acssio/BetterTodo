import * as React from 'react'
import { Calendar, Check, StickyNote } from 'lucide-react'
import type { Task, UrgencyLevel } from '../api/types'
import { formatDueDate, isDueOrLate } from '../utils/dates'
import './TaskCard.css'

interface TaskCardProps {
    task: Task
    urgency: UrgencyLevel | undefined // undefined = pas d'urgence
}

/** Une carte de tâche dans une colonne : case à cocher, titre, urgence, puis échéance, tags et note. */
export class TaskCard extends React.Component<TaskCardProps> {
    render(): React.ReactNode {
        const { task, urgency } = this.props
        const hasMeta = task.due_date !== null || task.tags.length > 0 || task.notes !== ''

        return (
            <div className={task.done ? 'TaskCard done' : 'TaskCard'}>
                <div className="card-top">
                    <span className={task.done ? 'card-check checked' : 'card-check'}>
                        {task.done && <Check size={10} color="#FFFFFF" strokeWidth={3.5} />}
                    </span>
                    <span className="card-title">{task.title}</span>
                    {urgency && this.renderUrgency(urgency)}
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

    /** Pastille d'urgence : texte et point dans la couleur du niveau, fond très clair. */
    private renderUrgency(urgency: UrgencyLevel): React.ReactNode {
        return (
            <span className="card-urgency" style={{ color: urgency.color, background: urgency.color + '1F' }}>
                <span className="card-urgency-dot" style={{ background: urgency.color }} />
                {urgency.name}
            </span>
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
