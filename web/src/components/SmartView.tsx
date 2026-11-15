import * as React from 'react'
import { X } from 'lucide-react'
import type { DueTask, Task, UrgencyLevel } from '../api/types'
import { groupDueTasks, type SmartKind } from '../utils/smartLists'
import { SMART_INFO } from './smartInfo'
import { TaskRow } from './TaskRow'
import './ListHeader.css'
import './SectionsView.css'
import './SmartView.css'

interface SmartViewProps {
    kind: SmartKind
    tasks: DueTask[] | null // null pendant le chargement
    urgencyLevels: UrgencyLevel[]
    openTaskId: string | null
    leavingTaskIds: string[] // Tâches cochées en train de disparaître
    onToggle: (task: Task) => void
    onOpen: (task: Task, anchor: DOMRect) => void
    onClosePane?: () => void // Vue scindée
}

/**
 * Liste intelligente « Aujourd'hui » ou « Planifié » : les tâches à faire avec une
 * échéance, toutes listes confondues, rangées en sections par date (utils/smartLists.ts).
 * On peut cocher et ouvrir l'overlay, mais pas réordonner ni ajouter : l'ordre vient des dates.
 */
export class SmartView extends React.Component<SmartViewProps> {
    render(): React.ReactNode {
        const { kind, tasks } = this.props
        const info = SMART_INFO[kind]
        const count = tasks?.length ?? 0

        return (
            <>
                <header className="ListHeader">
                    <span className="ListIcon-box" style={{ background: info.color + '1A' }}>
                        <info.icon size={18} color={info.color} />
                    </span>
                    <div className="header-title">
                        <h1>{info.title}</h1>
                        <span>{count} {count > 1 ? 'tâches à faire' : 'tâche à faire'}</span>
                    </div>
                    {this.props.onClosePane && (
                        <button className="icon-btn header-tool" title="Fermer cette moitié" onClick={this.props.onClosePane}>
                            <X size={16} />
                        </button>
                    )}
                </header>

                {tasks !== null && this.renderBody(tasks)}
            </>
        )
    }

    private renderBody(tasks: DueTask[]): React.ReactNode {
        if (tasks.length === 0) {
            return <div className="SectionsView"><div className="view-empty">{SMART_INFO[this.props.kind].empty}</div></div>
        }

        return (
            <div className="SectionsView">
                {groupDueTasks(tasks, this.props.kind).map(section => (
                    <section key={section.key} className="section">
                        <div className="section-head smart-head">
                            <span className={section.late ? 'smart-title late' : 'smart-title'}>{section.title}</span>
                            <span className="group-count">{section.tasks.length}</span>
                        </div>
                        {section.tasks.map(task => (
                            <TaskRow
                                key={task.id}
                                task={task}
                                urgency={this.props.urgencyLevels.find(level => level.id === task.urgency_id)}
                                selected={task.id === this.props.openTaskId}
                                dragging={false}
                                canDrag={false}
                                leaving={this.props.leavingTaskIds.includes(task.id)}
                                listBadge={task.list}
                                onToggle={this.props.onToggle}
                                onOpen={this.props.onOpen}
                                onDragStart={() => {}}
                                onDragEnd={() => {}}
                            />
                        ))}
                    </section>
                ))}
            </div>
        )
    }
}
