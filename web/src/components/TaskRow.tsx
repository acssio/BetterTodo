import * as React from 'react'
import { Check, PanelRightOpen, StickyNote } from 'lucide-react'
import type { Task, UrgencyLevel } from '../api/types'
import { ListIcon } from './ListIcon'
import { DueDate, UrgencyPill } from './TaskCard'
import { caretOffsetAt } from '../utils/caret'
import { LongPress } from '../utils/longPress'
import { TitleEditor } from './TitleEditor'
import './TaskRow.css'

interface TaskRowProps {
    task: Task
    urgency: UrgencyLevel | undefined
    opened: boolean   // Son overlay est ouvert
    selected: boolean // Fait partie de la sélection (Cmd+clic)
    dragging: boolean
    onToggle: (task: Task) => void
    onOpen: (task: Task, anchor: DOMRect) => void
    onRename: (task: Task, title: string) => void
    onToggleSelection: (task: Task) => void
    onStartEditing: (task: Task) => void
    onDragStart: (task: Task) => void
    onDragEnd: () => void
    listBadge?: { name: string, color: string, icon: string } // Listes intelligentes : liste d'origine
    canDrag?: boolean  // false dans les listes intelligentes (l'ordre vient des dates)
    leaving?: boolean  // Tâche cochée qui va disparaître (courte animation)
    tapToOpen?: boolean // Mode mobile : toucher la ligne ouvre l'overlay (pas de titre sur place ni de glisser)
    selecting?: boolean // Mode mobile, sélection en cours : toucher = sélectionner / désélectionner
    // Mode mobile : glisser au doigt après un appui long (absents = pas de glisser, ex. listes intelligentes)
    onTouchDragStart?: (task: Task, x: number, y: number) => void
    onTouchDragMove?: (x: number, y: number) => void
    onTouchDragEnd?: (drop: boolean) => void
}

class TaskRowState {
    lifted: boolean = false      // Mode mobile : appui long, la tâche est « soulevée »
    editing: boolean = false     // Titre en cours de modification
    caret: number | null = null  // Où placer le curseur en commençant
}

/**
 * Une tâche dans la vue sections : une ligne avec, de gauche à droite,
 * case à cocher, titre, note, puis les colonnes tags / échéance / urgence.
 * Même comportement que TaskCard : clic → titre sur place, Cmd+clic → sélection,
 * bouton au survol → overlay, case → cocher, glissable.
 * Mode mobile : même comportement que TaskCard (toucher, appui long, glisser au doigt, sélection).
 */
export class TaskRow extends React.Component<TaskRowProps, TaskRowState> {
    state = new TaskRowState()
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

    componentDidMount(): void {
        // Écoute « non passive » : la seule façon d'empêcher le défilement pendant un glisser au doigt
        this.rootRef.current?.addEventListener('touchmove', this.onTouchMove, { passive: false })
    }

    componentWillUnmount(): void {
        this.rootRef.current?.removeEventListener('touchmove', this.onTouchMove)
        this.longPress.abort()
    }

    /** Tâche soulevée ou glissée : le doigt ne fait pas défiler la page. */
    private onTouchMove = (event: TouchEvent): void => {
        if (this.longPress.blocksScroll()) event.preventDefault()
    }

    onClick = (event: React.MouseEvent<HTMLDivElement>): void => {
        if (this.state.editing) return
        if (this.longPress.consumeClick()) return // Ce clic termine un appui long ou un glisser : déjà traité
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
        if (event.key === 'Enter' && !this.state.editing) {
            this.props.onOpen(this.props.task, event.currentTarget.getBoundingClientRect())
        }
    }

    onOpenClick = (event: React.MouseEvent<HTMLButtonElement>): void => {
        event.stopPropagation() // Ne pas commencer à modifier le titre
        const row = event.currentTarget.closest('.TaskRow')
        if (row) this.props.onOpen(this.props.task, row.getBoundingClientRect())
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

    onPointerDown = (event: React.PointerEvent<HTMLDivElement>): void => {
        if (this.props.tapToOpen && event.isPrimary) this.longPress.down(event.clientX, event.clientY)
    }

    onPointerMove = (event: React.PointerEvent<HTMLDivElement>): void => {
        this.longPress.move(event.clientX, event.clientY)
    }

    onPointerUp = (): void => {
        this.longPress.up()
    }

    /** Le navigateur a repris la main (défilement, appel entrant…) : glisser annulé. */
    onPointerCancel = (): void => {
        this.longPress.abort()
        this.setState({ lifted: false })
    }

    /** Android ouvre un menu sur un appui long : pas sur une tâche. */
    onContextMenu = (event: React.MouseEvent<HTMLDivElement>): void => {
        if (this.props.tapToOpen) event.preventDefault()
    }

    onDragStart = (event: React.DragEvent<HTMLDivElement>): void => {
        event.dataTransfer.setData('text/plain', this.props.task.id)
        event.dataTransfer.effectAllowed = 'move'
        this.props.onDragStart(this.props.task)
    }

    render(): React.ReactNode {
        const { task, urgency, opened, selected, dragging } = this.props
        const { editing } = this.state

        const classes = ['TaskRow']
        if (task.done) classes.push('done')
        if (opened) classes.push('opened')
        if (selected) classes.push('selected')
        if (dragging) classes.push('dragging')
        if (editing) classes.push('editing')
        if (this.state.lifted) classes.push('lifted')
        if (this.props.leaving) classes.push('leaving')

        return (
            <div
                className={classes.join(' ')}
                ref={this.rootRef}
                data-task-id={task.id}
                role="button"
                tabIndex={0}
                draggable={(this.props.canDrag ?? true) && !editing && !this.props.tapToOpen}
                onClick={this.onClick}
                onKeyDown={this.onKeyDown}
                onDragStart={this.onDragStart}
                onDragEnd={this.props.onDragEnd}
                onPointerDown={this.onPointerDown}
                onPointerMove={this.onPointerMove}
                onPointerUp={this.onPointerUp}
                onPointerCancel={this.onPointerCancel}
                onContextMenu={this.onContextMenu}
            >
                <button
                    className={task.done ? 'card-check checked' : 'card-check'}
                    onClick={this.onCheckClick}
                    aria-label={task.done ? 'Marquer comme à faire' : 'Marquer comme faite'}
                >
                    {task.done && <Check size={10} color="#FFFFFF" strokeWidth={3.5} />}
                </button>
                {editing
                    ? <TitleEditor value={task.title} caret={this.state.caret} multiline={false} onSave={this.onTitleSave} onCancel={this.onTitleCancel} />
                    : <span className="row-title" ref={this.titleRef}>{task.title}</span>}
                {task.notes !== '' && <span className="card-note" title={task.notes}><StickyNote size={12} /></span>}
                {this.props.listBadge && (
                    <span className="row-list" title={`Liste « ${this.props.listBadge.name} »`}>
                        <ListIcon icon={this.props.listBadge.icon} color={this.props.listBadge.color} size={12} />
                        {this.props.listBadge.name}
                    </span>
                )}
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
                <button className="task-open" onClick={this.onOpenClick} title="Ouvrir le détail" aria-label="Ouvrir le détail">
                    <PanelRightOpen size={14} />
                </button>
            </div>
        )
    }
}
