import * as React from 'react'
import { X } from 'lucide-react'
import type { Folder, Group, TodoList } from '../api/types'
import { DismissListener } from '../utils/popover'
import { ListGroupPicker, type PickedPlace } from './ListGroupPicker'
import './QuickTaskSheet.css'

interface MoveTasksSheetProps {
    count: number              // Nombre de tâches sélectionnées
    folders: Folder[]
    lists: TodoList[]
    initialListId: string      // Proposé : la liste et le groupe de la première tâche sélectionnée
    initialGroupId: string
    onLoadGroups: (listId: string) => Promise<Group[]>
    onMove: (place: PickedPlace) => Promise<void>
    onClose: () => void
}

class MoveTasksSheetState {
    place: PickedPlace | null = null
    busy: boolean = false
}

/**
 * « Déplacer » (mode mobile) : choisir la liste et le groupe où envoyer les tâches
 * sélectionnées. Elles arrivent à la fin du groupe, dans leur ordre d'affichage.
 * Même allure que la Quick task (QuickTaskSheet.css). Ne parle pas à l'API (Workspace.moveSelection).
 */
export class MoveTasksSheet extends React.Component<MoveTasksSheetProps, MoveTasksSheetState> {
    private rootRef = React.createRef<HTMLFormElement>()
    private dismiss = new DismissListener(this.rootRef, () => this.props.onClose())

    constructor(props: MoveTasksSheetProps) {
        super(props)
        this.state = new MoveTasksSheetState()
    }

    componentDidMount(): void {
        this.dismiss.start()
    }

    componentWillUnmount(): void {
        this.dismiss.stop()
    }

    onPlaceChanged = (place: PickedPlace | null): void => {
        this.setState({ place })
    }

    onSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault()
        const { place, busy } = this.state
        if (!place || busy) return
        this.setState({ busy: true })
        await this.props.onMove(place) // Workspace ferme le panneau
    }

    render(): React.ReactNode {
        const { count } = this.props
        const { place, busy } = this.state

        return (
            <>
                {/* Fond grisé (style dans TaskPopover.css) ; le toucher ferme (clic en dehors, DismissListener) */}
                <div className="sheet-backdrop" />
                <form className="QuickTaskSheet" ref={this.rootRef} style={{ bottom: 0 }} onSubmit={this.onSubmit}>
                    <div className="sheet-handle" />

                    <div className="qt-head">
                        <h2>{count > 1 ? `Déplacer ${count} tâches vers…` : 'Déplacer la tâche vers…'}</h2>
                        <button type="button" className="qt-close" onClick={this.props.onClose} aria-label="Fermer">
                            <X size={20} />
                        </button>
                    </div>

                    <ListGroupPicker
                        folders={this.props.folders}
                        lists={this.props.lists}
                        initialListId={this.props.initialListId}
                        preferredGroupId={this.props.initialGroupId}
                        onLoadGroups={this.props.onLoadGroups}
                        onChange={this.onPlaceChanged}
                    />
                    <span className="qt-hint">Les tâches arrivent à la fin du groupe.</span>

                    <button type="submit" className="btn-primary qt-create" disabled={!place || busy}>
                        {busy ? 'Déplacement…' : 'Déplacer'}
                    </button>
                </form>
            </>
        )
    }
}
