import * as React from 'react'
import type { TaskViewProps } from './actions'
import { AddTask } from './AddTask'
import { GroupHeader } from './GroupHeader'
import { NewGroup } from './NewGroup'
import { TaskDropZone } from './TaskDropZone'
import { TaskRow } from './TaskRow'
import './SectionsView.css'

/**
 * Vue sections : les groupes empilés verticalement, une ligne par tâche.
 * Mêmes props et mêmes actions que la vue colonnes (Board) : seul l'affichage change.
 */
export class SectionsView extends React.Component<TaskViewProps> {
    render(): React.ReactNode {
        const { board, urgencyLevels, actions, openTaskId, selectedTaskIds, drag } = this.props

        return (
            <div className="SectionsView">
                {board.groups.map(group => (
                    <TaskDropZone
                        key={group.id}
                        className="section"
                        groupId={group.id}
                        tasks={group.tasks}
                        drag={drag}
                        actions={actions}
                        header={<div className="section-head"><GroupHeader group={group} actions={actions} /></div>}
                        footer={<div className="section-foot"><AddTask groupId={group.id} actions={actions} /></div>}
                        renderTask={(task, dragging) => (
                            <TaskRow
                                key={task.id}
                                task={task}
                                urgency={urgencyLevels.find(level => level.id === task.urgency_id)}
                                opened={task.id === openTaskId}
                                selected={selectedTaskIds.includes(task.id)}
                                dragging={dragging}
                                onToggle={actions.toggleTask}
                                onOpen={actions.openTask}
                                onRename={actions.renameTask}
                                onToggleSelection={actions.toggleSelection}
                                onStartEditing={actions.startEditing}
                                onDragStart={actions.startTaskDrag}
                                onDragEnd={actions.endTaskDrag}
                            />
                        )}
                    />
                ))}

                <NewGroup view={this.props} className="section section-new" />
            </div>
        )
    }
}
