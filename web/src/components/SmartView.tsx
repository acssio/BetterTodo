import * as React from 'react'
import { X } from 'lucide-react'
import type { DueTask, SmartList, Task, UrgencyLevel } from '../api/types'
import { isBuiltInSmart, smartListSummary, smartSections } from '../utils/smartLists'
import { BackButton } from './ListHeader'
import { ListIcon } from './ListIcon'
import { SMART_INFO } from './smartInfo'
import { TaskRow } from './TaskRow'
import './ListHeader.css'
import './SectionsView.css'
import './SmartView.css'

interface SmartViewProps {
    paneId: string                    // 'smart:today', 'smart:scheduled' ou 'smart:<id>' (utils/smartLists.ts)
    smartList: SmartList | undefined  // Liste intelligente perso affichée (undefined pour Aujourd'hui / Planifié)
    tasks: DueTask[] | null // null pendant le chargement
    urgencyLevels: UrgencyLevel[]
    openTaskId: string | null
    selectedTaskIds: string[]
    menuTaskId: string | null // Tâche dont le menu du clic droit est ouvert
    leavingTaskIds: string[] // Tâches cochées en train de disparaître
    onToggle: (task: Task) => void
    onOpen: (task: Task, anchor: DOMRect) => void
    onRename: (task: Task, title: string) => void
    onToggleSelection: (task: Task) => void
    onSelectRange: (task: Task) => void
    onOpenMenu: (task: Task, x: number, y: number, anchor: DOMRect) => void
    onStartEditing: (task: Task) => void
    onClosePane?: () => void // Vue scindée
    mobile?: boolean         // Mode mobile : toucher une tâche ouvre l'overlay
    onBack?: () => void      // Mode mobile : flèche ‹ vers l'accueil
}

/**
 * Liste intelligente : les tâches à faire de toutes les listes, rangées en sections (utils/smartLists.ts).
 *   « Aujourd'hui » / « Planifié » : les échéances, une section par date ;
 *   liste perso par tags : une section ; par urgence : une section par niveau.
 * On peut cocher, renommer, sélectionner (pour supprimer) et ouvrir l'overlay, mais pas
 * réordonner, ajouter ni glisser : l'ordre vient des dates ou des niveaux.
 */
export class SmartView extends React.Component<SmartViewProps> {
    render(): React.ReactNode {
        const { paneId, smartList, tasks } = this.props
        const info = isBuiltInSmart(paneId) ? SMART_INFO[paneId] : null // Aujourd'hui / Planifié
        const count = tasks?.length ?? 0
        let subtitle = `${count} ${count > 1 ? 'tâches à faire' : 'tâche à faire'}`
        if (smartList) subtitle += ` · ${smartListSummary(smartList)}`

        return (
            <>
                <header className="ListHeader">
                    {this.props.onBack && <BackButton onBack={this.props.onBack} />}
                    {info ? (
                        <span className="ListIcon-box" style={{ background: info.color + '1A' }}>
                            <info.icon size={18} color={info.color} />
                        </span>
                    ) : (
                        <ListIcon icon={smartList?.icon ?? 'list'} color={smartList?.color ?? '#6B6B6B'} size={18} boxed />
                    )}
                    <div className="header-title">
                        <h1>{info ? info.title : smartList?.name}</h1>
                        <span>{subtitle}</span>
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
        const { paneId } = this.props
        if (tasks.length === 0) {
            const empty = isBuiltInSmart(paneId) ? SMART_INFO[paneId].empty : 'Aucune tâche à faire ne correspond.'
            return <div className="SectionsView"><div className="view-empty">{empty}</div></div>
        }

        return (
            <div className="SectionsView">
                {smartSections(paneId, tasks, this.props.smartList, this.props.urgencyLevels).map(section => (
                    <section key={section.key} className="section">
                        <div className="section-head smart-head">
                            <span className={section.late ? 'smart-title late' : 'smart-title'} style={{ color: section.color }}>
                                {section.title}
                            </span>
                            <span className="group-count">{section.tasks.length}</span>
                        </div>
                        {section.tasks.map(task => (
                            <TaskRow
                                key={task.id}
                                task={task}
                                urgency={this.props.urgencyLevels.find(level => level.id === task.urgency_id)}
                                opened={task.id === this.props.openTaskId}
                                selected={this.props.selectedTaskIds.includes(task.id)}
                                menuOpen={task.id === this.props.menuTaskId}
                                dragging={false}
                                canDrag={false}
                                leaving={this.props.leavingTaskIds.includes(task.id)}
                                tapToOpen={this.props.mobile}
                                selecting={this.props.selectedTaskIds.length > 0}
                                listBadge={task.list}
                                onToggle={this.props.onToggle}
                                onOpen={this.props.onOpen}
                                onRename={this.props.onRename}
                                onToggleSelection={this.props.onToggleSelection}
                                onSelectRange={this.props.onSelectRange}
                                onOpenMenu={this.props.onOpenMenu}
                                onStartEditing={this.props.onStartEditing}
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
