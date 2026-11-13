import * as React from 'react'
import type { TaskViewProps } from './actions'
import { InlineInput } from './InlineInput'

interface NewGroupProps {
    view: TaskViewProps
    className: string // Même allure qu'un groupe de la vue
}

/**
 * Fin d'une vue : le champ « nouveau groupe » (bouton de l'en-tête),
 * ou un message si la liste n'a encore aucun groupe.
 */
export class NewGroup extends React.Component<NewGroupProps> {
    render(): React.ReactNode {
        const { view } = this.props

        if (view.addingGroup) {
            return (
                <div className={this.props.className}>
                    <InlineInput
                        placeholder="Nom du groupe"
                        onSubmit={async name => {
                            await view.actions.createGroup(name)
                            view.onAddingGroupDone()
                        }}
                        onCancel={view.onAddingGroupDone}
                    />
                </div>
            )
        }

        if (view.board.groups.length === 0) {
            return <div className="view-empty">Cette liste n'a pas encore de groupe : clique sur « Nouveau groupe ».</div>
        }
        return null
    }
}
