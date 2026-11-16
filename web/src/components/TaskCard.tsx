import * as React from 'react'
import { Calendar, Check, PanelRightOpen, StickyNote } from 'lucide-react'
import type { Task, UrgencyLevel } from '../api/types'
import { formatDueDate, isDueOrLate } from '../utils/dates'
import { caretOffsetAt } from '../utils/caret'
import { TitleEditor } from './TitleEditor'
import './TaskCard.css'

interface TaskCardProps {
    task: Task
    urgency: UrgencyLevel | undefined // undefined = pas d'urgence
    opened: boolean                   // Son overlay est ouvert
    selected: boolean                 // Fait partie de la sélection (Cmd+clic)
    dragging: boolean                 // C'est une carte en train d'être glissée
    onToggle: (task: Task) => void
    onOpen: (task: Task, anchor: DOMRect) => void
    onRename: (task: Task, title: string) => void
    onToggleSelection: (task: Task) => void
    onStartEditing: (task: Task) => void
    onDragStart: (task: Task) => void
    onDragEnd: () => void
    tapToOpen?: boolean // Mode mobile : toucher la carte ouvre l'overlay (pas de titre sur place ni de glisser)
}

class TaskCardState {
    editing: boolean = false     // Titre en cours de modification
    caret: number | null = null  // Où placer le curseur en commençant
}

/**
 * Une carte de tâche : case à cocher, titre, urgence, puis échéance, tags et note.
 *   clic                      → modifier le titre sur place (curseur là où on a cliqué)
 *   Cmd+clic / Ctrl+clic      → ajouter à la sélection ou l'en retirer
 *   bouton au survol, Entrée  → overlay d'édition
 *   case                      → cocher / décocher
 * La carte peut être glissée (API glisser-déposer native du navigateur), sauf pendant la modification.
 */
export class TaskCard extends React.Component<TaskCardProps, TaskCardState> {
    state = new TaskCardState()
    private titleRef = React.createRef<HTMLSpanElement>()

    onClick = (event: React.MouseEvent<HTMLDivElement>): void => {
        if (this.state.editing) return
        if (event.metaKey || event.ctrlKey) {
            this.props.onToggleSelection(this.props.task)
            return
        }
        if (this.props.tapToOpen) {
            this.props.onOpen(this.props.task, event.currentTarget.getBoundingClientRect())
            return
        }
        this.props.onStartEditing(this.props.task)
        this.setState({ editing: true, caret: caretOffsetAt(this.titleRef.current, event.clientX, event.clientY) })
    }

    onKeyDown = (event: React.KeyboardEvent<HTMLDivElement>): void => {
        if (event.key === 'Enter' && !this.state.editing) {
            this.props.onOpen(this.props.task, event.currentTarget.getBoundingClientRect())
        }
    }

    onOpenClick = (event: React.MouseEvent<HTMLButtonElement>): void => {
        event.stopPropagation() // Ne pas commencer à modifier le titre
        const card = event.currentTarget.closest('.TaskCard')
        if (card) this.props.onOpen(this.props.task, card.getBoundingClientRect())
    }

    onCheckClick = (event: React.MouseEvent<HTMLButtonElement>): void => {
        event.stopPropagation() // Ne pas modifier le titre en même temps
        this.props.onToggle(this.props.task)
    }

    onTitleSave = (title: string): void => {
        this.setState({ editing: false })
        this.props.onRename(this.props.task, title)
    }

    onTitleCancel = (): void => {
        this.setState({ editing: false })
    }

    onDragStart = (event: React.DragEvent<HTMLDivElement>): void => {
        event.dataTransfer.setData('text/plain', this.props.task.id) // Obligatoire pour Firefox
        event.dataTransfer.effectAllowed = 'move'
        this.props.onDragStart(this.props.task)
    }

    render(): React.ReactNode {
        const { task, urgency, opened, selected, dragging } = this.props
        const { editing } = this.state
        const hasMeta = task.due_date !== null || task.tags.length > 0 || task.notes !== ''

        const classes = ['TaskCard']
        if (task.done) classes.push('done')
        if (opened) classes.push('opened')
        if (selected) classes.push('selected')
        if (dragging) classes.push('dragging')
        if (editing) classes.push('editing')

        return (
            <div
                className={classes.join(' ')}
                data-task-id={task.id}
                role="button"
                tabIndex={0}
                draggable={!editing && !this.props.tapToOpen}
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
                    {editing
                        ? <TitleEditor value={task.title} caret={this.state.caret} multiline onSave={this.onTitleSave} onCancel={this.onTitleCancel} />
                        : <span className="card-title" ref={this.titleRef}>{task.title}</span>}
                    {urgency && <UrgencyPill urgency={urgency} />}
                </div>

                {hasMeta && (
                    <div className="card-meta">
                        {task.due_date && <DueDate dueDate={task.due_date} done={task.done} />}
                        {task.tags.map(tag => <span key={tag} className="card-tag">#{tag}</span>)}
                        {task.notes !== '' && (
                            <span className="card-note" title={task.notes}><StickyNote size={12} /></span>
                        )}
                    </div>
                )}

                {!editing && (
                    <button className="task-open" onClick={this.onOpenClick} title="Ouvrir le détail" aria-label="Ouvrir le détail">
                        <PanelRightOpen size={14} />
                    </button>
                )}
            </div>
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

/** Échéance, en rouge si c'est aujourd'hui ou en retard (sauf si la tâche est faite). */
export class DueDate extends React.Component<{ dueDate: string, done: boolean }> {
    render(): React.ReactNode {
        const { dueDate, done } = this.props
        const late = !done && isDueOrLate(dueDate)
        return (
            <span className={late ? 'card-due late' : 'card-due'}>
                <Calendar size={12} />
                {formatDueDate(dueDate)}
            </span>
        )
    }
}
