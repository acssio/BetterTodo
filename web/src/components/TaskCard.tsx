import * as React from 'react'
import { Calendar, Check, PanelRightOpen, StickyNote } from 'lucide-react'
import type { Task, UrgencyLevel } from '../api/types'
import { formatDueDate, isDueOrLate } from '../utils/dates'
import { caretOffsetAt } from '../utils/caret'
import { GestureTracker, LongPress } from '../utils/longPress'
import { TitleEditor } from './TitleEditor'
import './TaskCard.css'

interface TaskCardProps {
    task: Task
    urgency: UrgencyLevel | undefined // undefined = pas d'urgence
    opened: boolean                   // Son overlay est ouvert
    selected: boolean                 // Fait partie de la sélection (Cmd+clic)
    menuOpen: boolean                 // Son menu du clic droit est ouvert
    dragging: boolean                 // C'est une carte en train d'être glissée
    onToggle: (task: Task) => void
    onOpen: (task: Task, anchor: DOMRect) => void
    onRename: (task: Task, title: string) => void
    onToggleSelection: (task: Task) => void
    onSelectRange: (task: Task) => void                                  // Maj+clic
    onOpenMenu: (task: Task, x: number, y: number, anchor: DOMRect) => void // Clic droit
    onStartEditing: (task: Task) => void
    onDragStart: (task: Task) => void
    onDragEnd: () => void
    tapToOpen?: boolean // Mode mobile : toucher la carte ouvre l'overlay (pas de titre sur place ni de glisser)
    selecting?: boolean // Sélection en cours : clic (ou toucher) = sélectionner / désélectionner, pas de modification
    // Mode mobile : glisser au doigt après un appui long (absents = pas de glisser, ex. listes intelligentes)
    onTouchDragStart?: (task: Task, x: number, y: number) => void
    onTouchDragMove?: (x: number, y: number) => void
    onTouchDragEnd?: (drop: boolean) => void
}

class TaskCardState {
    lifted: boolean = false      // Mode mobile : appui long, la tâche est « soulevée »
    editing: boolean = false     // Titre en cours de modification
    caret: number | null = null  // Où placer le curseur en commençant
}

/**
 * Une carte de tâche : case à cocher, titre, urgence, puis échéance, tags et note.
 *   clic                      → modifier le titre sur place (curseur là où on a cliqué)
 *   Cmd+clic / Ctrl+clic      → ajouter à la sélection ou l'en retirer
 *   Maj+clic                  → sélectionner une plage
 *   clic droit                → petit menu (sélection, détail, supprimer)
 *   bouton au survol, Entrée  → overlay d'édition
 *   case                      → cocher / décocher
 * En mode mobile (tapToOpen) : toucher → overlay, appui long → sélection, appui long puis
 * glisser → déplacement.
 * Pendant une sélection (selecting, ordinateur et mobile) : un clic ajoute / retire la tâche,
 * la case aussi ; pas de titre sur place ni d'overlay. On en sort par Échap ou en désélectionnant tout.
 * La carte peut être glissée (API glisser-déposer native du navigateur), sauf pendant la modification.
 */
export class TaskCard extends React.Component<TaskCardProps, TaskCardState> {
    state = new TaskCardState()
    private titleRef = React.createRef<HTMLSpanElement>()
    private rootRef = React.createRef<HTMLDivElement>()
    // Mode mobile : appui long → sélection ; appui long puis glisser → déplacement (utils/longPress.ts)
    private longPress = new LongPress({
        onArm: () => {
            navigator.vibrate?.(15) // Petite vibration sur Android (l'iPhone ne la permet pas)
            this.setState({ lifted: true })
        },
        onLongPress: () => {
            this.setState({ lifted: false })
            this.props.onToggleSelection(this.props.task)
        },
        canDrag: () => this.props.onTouchDragStart !== undefined,
        onDragStart: (x, y) => {
            this.setState({ lifted: false })
            this.props.onTouchDragStart?.(this.props.task, x, y)
        },
        onDragMove: (x, y) => this.props.onTouchDragMove?.(x, y),
        onDragEnd: drop => this.props.onTouchDragEnd?.(drop),
    })

    // Suit le doigt sur toute la fenêtre pendant le geste (voir GestureTracker)
    private gesture = new GestureTracker(this.longPress, () => this.setState({ lifted: false }))

    componentDidMount(): void {
        // Écoute « non passive » : la seule façon d'empêcher le défilement pendant un glisser au doigt
        this.rootRef.current?.addEventListener('touchmove', this.onTouchMove, { passive: false })
    }

    componentWillUnmount(): void {
        this.rootRef.current?.removeEventListener('touchmove', this.onTouchMove)
        this.gesture.stop()
        this.longPress.abort()
    }

    /** Tâche soulevée ou glissée : le doigt ne fait pas défiler la page. */
    private onTouchMove = (event: TouchEvent): void => {
        if (this.longPress.blocksScroll()) event.preventDefault()
    }

    onClick = (event: React.MouseEvent<HTMLDivElement>): void => {
        if (this.state.editing) return
        if (this.longPress.consumeClick()) return // Ce clic termine un appui long ou un glisser : déjà traité
        if (event.shiftKey && !this.props.tapToOpen) {
            this.props.onSelectRange(this.props.task) // Maj+clic : plage depuis la dernière tâche cliquée
            return
        }
        if (event.metaKey || event.ctrlKey || this.props.selecting) {
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
        if (event.key === 'Enter' && !this.state.editing && !this.props.selecting) {
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
        if (this.longPress.consumeClick()) return
        if (this.props.selecting) {
            this.props.onToggleSelection(this.props.task) // En sélection, toute la tâche sert à sélectionner
        } else {
            this.props.onToggle(this.props.task)
        }
    }

    onTitleSave = (title: string): void => {
        this.setState({ editing: false })
        this.props.onRename(this.props.task, title)
    }

    onTitleCancel = (): void => {
        this.setState({ editing: false })
    }

    // --- Appui long (mode mobile) -----------------------------------------------

    /** Doigt posé : la suite du geste (bouger, lever) est suivie sur toute la fenêtre. */
    onPointerDown = (event: React.PointerEvent<HTMLDivElement>): void => {
        if (this.props.tapToOpen && event.isPrimary) this.gesture.start(event.pointerId, event.clientX, event.clientY)
    }

    /** Clic droit : notre menu à la place de celui du navigateur. Android ouvre un menu sur un appui long : pas sur une tâche. */
    onContextMenu = (event: React.MouseEvent<HTMLDivElement>): void => {
        if (this.props.tapToOpen) {
            event.preventDefault()
            return
        }
        if (this.state.editing) return // Titre en cours de modification : menu du navigateur (copier, coller…)
        event.preventDefault()
        this.props.onOpenMenu(this.props.task, event.clientX, event.clientY, event.currentTarget.getBoundingClientRect())
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
        if (this.props.menuOpen) classes.push('menu-on')
        if (dragging) classes.push('dragging')
        if (editing) classes.push('editing')
        if (this.state.lifted) classes.push('lifted')

        return (
            <div
                className={classes.join(' ')}
                ref={this.rootRef}
                data-task-id={task.id}
                role="button"
                tabIndex={0}
                draggable={!editing && !this.props.tapToOpen}
                onClick={this.onClick}
                onKeyDown={this.onKeyDown}
                onDragStart={this.onDragStart}
                onDragEnd={this.props.onDragEnd}
                onPointerDown={this.onPointerDown}
                onContextMenu={this.onContextMenu}
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

                {!editing && !this.props.selecting && (
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
