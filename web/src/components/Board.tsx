import * as React from 'react'
import type { TaskViewProps } from './actions'
import { AddTask } from './AddTask'
import { GroupHeader } from './GroupHeader'
import { NewGroup } from './NewGroup'
import { TaskCard } from './TaskCard'
import { TaskDropZone } from './TaskDropZone'
import './Board.css'

/** Vue colonnes : un groupe par colonne, ses tâches en cartes. */
export class Board extends React.Component<TaskViewProps> {
    render(): React.ReactNode {
        const { board, urgencyLevels, actions, openTaskId, drag } = this.props

        return (
            <div className="Board">
                {board.groups.map(group => (
                    <TaskDropZone
                        key={group.id}
                        className="board-column"
                        groupId={group.id}
                        tasks={group.tasks}
                        drag={drag}
                        actions={actions}
                        header={<GroupHeader group={group} actions={actions} />}
                        footer={<AddTask groupId={group.id} actions={actions} />}
                        renderTask={(task, dragging) => (
                            <TaskCard
                                key={task.id}
                                task={task}
                                urgency={urgencyLevels.find(level => level.id === task.urgency_id)}
                                selected={task.id === openTaskId}
                                dragging={dragging}
                                onToggle={actions.toggleTask}
                                onOpen={actions.openTask}
                                onDragStart={actions.startTaskDrag}
                                onDragEnd={actions.endTaskDrag}
                            />
                        )}
                    />
                ))}

                <NewGroup view={this.props} className="board-column" />
            </div>
        )
    }
}
