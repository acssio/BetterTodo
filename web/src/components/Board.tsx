import * as React from 'react'
import { Ellipsis, Plus } from 'lucide-react'
import type { Board as BoardData, BoardGroup, UrgencyLevel } from '../api/types'
import { TaskCard } from './TaskCard'
import './Board.css'

interface BoardProps {
    board: BoardData
    urgencyLevels: UrgencyLevel[]
}

/** Vue colonnes : un groupe par colonne, ses tâches en cartes. */
export class Board extends React.Component<BoardProps> {
    render(): React.ReactNode {
        const { groups } = this.props.board

        if (groups.length === 0) {
            return (
                <div className="Board-empty">
                    Cette liste n'a pas encore de groupe.
                </div>
            )
        }

        return (
            <div className="Board">
                {groups.map(group => this.renderColumn(group))}
            </div>
        )
    }

    private renderColumn(group: BoardGroup): React.ReactNode {
        return (
            <section key={group.id} className="board-column">
                <div className="column-head">
                    <span className="column-name">{group.name}</span>
                    <span className="column-count">{group.tasks.length}</span>
                    <button className="icon-btn" title="Options du groupe (étape 3b)" disabled>
                        <Ellipsis size={16} />
                    </button>
                </div>

                {group.tasks.map(task => (
                    <TaskCard key={task.id} task={task} urgency={this.findUrgency(task.urgency_id)} />
                ))}

                <button className="column-add" title="Ajouter une tâche (étape 3b)" disabled>
                    <Plus size={14} />Ajouter une tâche
                </button>
            </section>
        )
    }

    private findUrgency(urgencyId: string | null): UrgencyLevel | undefined {
        return this.props.urgencyLevels.find(level => level.id === urgencyId)
    }
}
