import * as React from 'react'
import { Ellipsis, Pencil, Trash2 } from 'lucide-react'
import type { BoardGroup } from '../api/types'
import type { TaskViewActions } from './actions'
import { InlineInput } from './InlineInput'
import { Menu } from './Menu'
import './GroupHeader.css'

interface GroupHeaderProps {
    group: BoardGroup
    actions: TaskViewActions
}

class GroupHeaderState {
    menuOpen: boolean = false
    renaming: boolean = false
}

/** En-tête d'un groupe (colonne ou section) : nom, nombre de tâches, menu « … » (renommer, supprimer). */
export class GroupHeader extends React.Component<GroupHeaderProps, GroupHeaderState> {
    constructor(props: GroupHeaderProps) {
        super(props)
        this.state = new GroupHeaderState()
    }

    render(): React.ReactNode {
        const { group, actions } = this.props

        if (this.state.renaming) {
            return (
                <div className="GroupHeader">
                    <InlineInput
                        placeholder="Nom du groupe"
                        initialValue={group.name}
                        onSubmit={async name => {
                            await actions.renameGroup(group.id, name)
                            this.setState({ renaming: false })
                        }}
                        onCancel={() => this.setState({ renaming: false })}
                    />
                </div>
            )
        }

        return (
            <div className="GroupHeader">
                <span className="group-name">{group.name}</span>
                <span className="group-count">{group.tasks.length}</span>
                <div className="menu-anchor">
                    <button className="icon-btn" title="Options du groupe" onClick={() => this.setState({ menuOpen: true })}>
                        <Ellipsis size={16} />
                    </button>
                    {this.state.menuOpen && (
                        <Menu
                            onClose={() => this.setState({ menuOpen: false })}
                            items={[
                                { label: 'Renommer', icon: Pencil, onSelect: () => this.setState({ renaming: true }) },
                                { label: 'Supprimer le groupe', icon: Trash2, danger: true, onSelect: () => actions.deleteGroup(group) },
                            ]}
                        />
                    )}
                </div>
            </div>
        )
    }
}
