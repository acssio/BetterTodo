import * as React from 'react'
import { Ellipsis, Pencil, Plus, Trash2 } from 'lucide-react'
import type { Board as BoardData, BoardGroup, Task, UrgencyLevel } from '../api/types'
import { InlineInput } from './InlineInput'
import { TaskCard } from './TaskCard'
import './Board.css'

/** Les actions que le board demande à Workspace (qui parle à l'API). */
export interface BoardActions {
    toggleTask(task: Task): void
    openTask(task: Task, anchor: DOMRect): void
    createTask(groupId: string, title: string): Promise<void>
    moveTask(taskId: string, groupId: string, index: number): void
    createGroup(name: string): Promise<void>
    renameGroup(groupId: string, name: string): Promise<void>
    deleteGroup(group: BoardGroup): void
}

interface BoardProps {
    board: BoardData
    urgencyLevels: UrgencyLevel[]
    actions: BoardActions
    openTaskId: string | null // Tâche dont l'overlay est ouvert
    addingGroup: boolean      // Champ « nouveau groupe » affiché (bouton de l'en-tête)
    onAddingGroupDone: () => void
}

/** Où la tâche glissée serait déposée : groupe + index (compté sans la tâche glissée). */
interface DropTarget {
    groupId: string
    index: number
}

class BoardState {
    addingTaskGroupId: string | null = null // Colonne où l'on ajoute une tâche
    menuGroupId: string | null = null       // Colonne dont le menu « … » est ouvert
    renamingGroupId: string | null = null
    dragTaskId: string | null = null
    dropTarget: DropTarget | null = null
}

/**
 * Vue colonnes : un groupe par colonne, ses tâches en cartes.
 *
 * Glisser-déposer (API native) :
 *   dragstart (carte) → dragover (colonne) calcule l'index sous la souris
 *   → affiche une ligne bleue → drop : actions.moveTask(…)
 */
export class Board extends React.Component<BoardProps, BoardState> {
    constructor(props: BoardProps) {
        super(props)
        this.state = new BoardState()
    }

    // --- Glisser-déposer -------------------------------------------------------

    onCardDragStart = (task: Task): void => {
        // setTimeout : laisse le navigateur faire sa « photo » de la carte avant de la griser
        setTimeout(() => this.setState({ dragTaskId: task.id }), 0)
    }

    onCardDragEnd = (): void => {
        this.setState({ dragTaskId: null, dropTarget: null })
    }

    onColumnDragOver = (event: React.DragEvent<HTMLElement>, groupId: string): void => {
        if (this.state.dragTaskId === null) return // Pas une de nos cartes
        event.preventDefault() // Indique au navigateur qu'on accepte le dépôt ici
        event.dataTransfer.dropEffect = 'move'

        const index = this.dropIndex(event.currentTarget, event.clientY)
        const current = this.state.dropTarget
        if (!current || current.groupId !== groupId || current.index !== index) {
            this.setState({ dropTarget: { groupId, index } })
        }
    }

    onColumnDragLeave = (event: React.DragEvent<HTMLElement>): void => {
        // dragleave se déclenche aussi en passant sur un enfant : on ne réagit qu'en sortant vraiment
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
            this.setState({ dropTarget: null })
        }
    }

    onColumnDrop = (event: React.DragEvent<HTMLElement>): void => {
        event.preventDefault()
        const { dragTaskId, dropTarget } = this.state
        if (dragTaskId && dropTarget) {
            this.props.actions.moveTask(dragTaskId, dropTarget.groupId, dropTarget.index)
        }
        this.setState({ dragTaskId: null, dropTarget: null })
    }

    /** Index de dépôt : avant la première carte dont le milieu est sous la souris. */
    private dropIndex(column: HTMLElement, mouseY: number): number {
        const cards = Array.from(column.querySelectorAll<HTMLElement>('[data-task-id]'))
            .filter(card => card.dataset.taskId !== this.state.dragTaskId)

        for (let i = 0; i < cards.length; i++) {
            const rect = cards[i].getBoundingClientRect()
            if (mouseY < rect.top + rect.height / 2) {
                return i
            }
        }
        return cards.length
    }

    // --- Rendu -----------------------------------------------------------------

    render(): React.ReactNode {
        const { groups } = this.props.board

        return (
            <div className="Board">
                {groups.map(group => this.renderColumn(group))}

                {this.props.addingGroup ? (
                    <div className="board-new-group">
                        <InlineInput
                            placeholder="Nom du groupe"
                            onSubmit={async name => {
                                await this.props.actions.createGroup(name)
                                this.props.onAddingGroupDone()
                            }}
                            onCancel={this.props.onAddingGroupDone}
                        />
                    </div>
                ) : groups.length === 0 && (
                    <div className="Board-empty">
                        Cette liste n'a pas encore de groupe : clique sur « Nouveau groupe ».
                    </div>
                )}
            </div>
        )
    }

    private renderColumn(group: BoardGroup): React.ReactNode {
        const { dropTarget, dragTaskId } = this.state
        const isDropColumn = dropTarget !== null && dropTarget.groupId === group.id

        // On insère la ligne de dépôt à l'index visé, en sautant la carte glissée
        const items: React.ReactNode[] = []
        let visibleIndex = 0
        for (const task of group.tasks) {
            if (task.id !== dragTaskId) {
                if (isDropColumn && dropTarget.index === visibleIndex) {
                    items.push(<div key="drop" className="drop-indicator" />)
                }
                visibleIndex++
            }
            items.push(this.renderCard(task))
        }
        if (isDropColumn && dropTarget.index === visibleIndex) {
            items.push(<div key="drop" className="drop-indicator" />)
        }

        return (
            <section
                key={group.id}
                className={isDropColumn ? 'board-column drop-over' : 'board-column'}
                onDragOver={event => this.onColumnDragOver(event, group.id)}
                onDragLeave={this.onColumnDragLeave}
                onDrop={this.onColumnDrop}
            >
                {this.renderColumnHead(group)}
                {items}
                {this.renderAddTask(group)}
            </section>
        )
    }

    private renderColumnHead(group: BoardGroup): React.ReactNode {
        if (this.state.renamingGroupId === group.id) {
            return (
                <div className="column-head">
                    <InlineInput
                        placeholder="Nom du groupe"
                        initialValue={group.name}
                        onSubmit={async name => {
                            await this.props.actions.renameGroup(group.id, name)
                            this.setState({ renamingGroupId: null })
                        }}
                        onCancel={() => this.setState({ renamingGroupId: null })}
                    />
                </div>
            )
        }

        return (
            <div className="column-head">
                <span className="column-name">{group.name}</span>
                <span className="column-count">{group.tasks.length}</span>
                <div className="column-menu-anchor">
                    <button
                        className="icon-btn"
                        title="Options du groupe"
                        onClick={() => this.setState({ menuGroupId: group.id })}
                    >
                        <Ellipsis size={16} />
                    </button>
                    {this.state.menuGroupId === group.id && this.renderMenu(group)}
                </div>
            </div>
        )
    }

    /** Petit menu « … » : renommer / supprimer. Un fond invisible le ferme au clic à côté. */
    private renderMenu(group: BoardGroup): React.ReactNode {
        const close = () => this.setState({ menuGroupId: null })
        return (
            <>
                <div className="menu-backdrop" onClick={close} />
                <div className="column-menu">
                    <button onClick={() => this.setState({ menuGroupId: null, renamingGroupId: group.id })}>
                        <Pencil size={14} />Renommer
                    </button>
                    <button
                        className="danger"
                        onClick={() => {
                            close()
                            this.props.actions.deleteGroup(group)
                        }}
                    >
                        <Trash2 size={14} />Supprimer le groupe
                    </button>
                </div>
            </>
        )
    }

    private renderAddTask(group: BoardGroup): React.ReactNode {
        if (this.state.addingTaskGroupId === group.id) {
            return (
                <InlineInput
                    placeholder="Titre de la tâche"
                    keepOpen
                    onSubmit={title => this.props.actions.createTask(group.id, title)}
                    onCancel={() => this.setState({ addingTaskGroupId: null })}
                />
            )
        }
        return (
            <button className="column-add" onClick={() => this.setState({ addingTaskGroupId: group.id })}>
                <Plus size={14} />Ajouter une tâche
            </button>
        )
    }

    private renderCard(task: Task): React.ReactNode {
        return (
            <TaskCard
                key={task.id}
                task={task}
                urgency={this.props.urgencyLevels.find(level => level.id === task.urgency_id)}
                selected={task.id === this.props.openTaskId}
                dragging={task.id === this.state.dragTaskId}
                onToggle={this.props.actions.toggleTask}
                onOpen={this.props.actions.openTask}
                onDragStart={this.onCardDragStart}
                onDragEnd={this.onCardDragEnd}
            />
        )
    }
}
