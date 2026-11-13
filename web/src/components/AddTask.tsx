import * as React from 'react'
import { Plus } from 'lucide-react'
import type { TaskViewActions } from './actions'
import { InlineInput } from './InlineInput'
import './AddTask.css'

interface AddTaskProps {
    groupId: string
    actions: TaskViewActions
}

class AddTaskState {
    adding: boolean = false
}

/** « + Ajouter une tâche » en bas d'un groupe. Le champ reste ouvert pour enchaîner les ajouts. */
export class AddTask extends React.Component<AddTaskProps, AddTaskState> {
    constructor(props: AddTaskProps) {
        super(props)
        this.state = new AddTaskState()
    }

    render(): React.ReactNode {
        if (this.state.adding) {
            return (
                <div className="AddTask-input">
                    <InlineInput
                        placeholder="Titre de la tâche"
                        keepOpen
                        onSubmit={title => this.props.actions.createTask(this.props.groupId, title)}
                        onCancel={() => this.setState({ adding: false })}
                    />
                </div>
            )
        }
        return (
            <button className="AddTask" onClick={() => this.setState({ adding: true })}>
                <Plus size={14} />Ajouter une tâche
            </button>
        )
    }
}
