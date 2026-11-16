import * as React from 'react'
import { ChevronDown, Layers, List } from 'lucide-react'
import type { Folder, Group, TodoList } from '../api/types'
import { chooseGroup } from '../utils/quickTask'
import './ListGroupPicker.css'

/** Endroit choisi : la liste, le groupe, et « Liste › Groupe » pour les messages. */
export interface PickedPlace {
    listId: string
    groupId: string
    where: string
}

interface ListGroupPickerProps {
    folders: Folder[]
    lists: TodoList[]
    initialListId: string | null
    preferredGroupId: string | null  // Choisi s'il est dans la liste, sinon son premier groupe
    onLoadGroups: (listId: string) => Promise<Group[]>
    onChange: (place: PickedPlace | null) => void // null pendant un chargement ou sans groupe
}

class ListGroupPickerState {
    listId: string | null = null
    groupId: string | null = null
    groups: Group[] = []      // Groupes de la liste choisie
    loading: boolean = false
}

/**
 * Choix d'une liste puis d'un de ses groupes, dans deux menus déroulants natifs
 * (sur téléphone : la roue de sélection du système).
 * Utilisé par la Quick task et par « Déplacer » (mode mobile).
 * Ne parle pas à l'API : les groupes arrivent par onLoadGroups (Workspace).
 */
export class ListGroupPicker extends React.Component<ListGroupPickerProps, ListGroupPickerState> {
    private rootRef = React.createRef<HTMLDivElement>()
    private currentListId: string | null // Liste dont on attend les groupes (une réponse plus ancienne est ignorée)

    constructor(props: ListGroupPickerProps) {
        super(props)
        this.state = { ...new ListGroupPickerState(), listId: props.initialListId }
        this.currentListId = props.initialListId
    }

    componentDidMount(): void {
        if (this.state.listId) void this.loadGroups(this.state.listId)
    }

    /** Charge les groupes d'une liste, choisit le groupe, et prévient le parent. */
    private async loadGroups(listId: string): Promise<void> {
        this.setState({ loading: true })
        this.props.onChange(null)
        const groups = await this.props.onLoadGroups(listId)
        if (!this.rootRef.current || this.currentListId !== listId) return // Fermé, ou une autre liste a été choisie

        const groupId = chooseGroup(this.props.preferredGroupId, groups)
        this.setState({ groups, groupId, loading: false })
        this.report(listId, groupId, groups)
    }

    /** Prévient le parent (valeurs passées en paramètre : this.state n'est pas encore à jour). */
    private report(listId: string, groupId: string | null, groups: Group[]): void {
        const list = this.props.lists.find(l => l.id === listId)
        const group = groups.find(g => g.id === groupId)
        this.props.onChange(list && group ? { listId, groupId: group.id, where: `${list.name} › ${group.name}` } : null)
    }

    onListChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
        const listId = event.target.value
        this.currentListId = listId
        this.setState({ listId, groups: [], groupId: null })
        void this.loadGroups(listId)
    }

    onGroupChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
        const groupId = event.target.value
        this.setState({ groupId })
        if (this.state.listId) this.report(this.state.listId, groupId, this.state.groups)
    }

    render(): React.ReactNode {
        const { listId, groupId, groups, loading } = this.state

        return (
            <div className="ListGroupPicker" ref={this.rootRef}>
                <label className="lgp-field">
                    <span className="lgp-label"><List size={15} />Liste</span>
                    <span className="lgp-select">
                        <select value={listId ?? ''} onChange={this.onListChanged}>
                            {this.renderListOptions()}
                        </select>
                        <ChevronDown size={16} />
                    </span>
                </label>
                <label className="lgp-field">
                    <span className="lgp-label"><Layers size={15} />Groupe</span>
                    <span className="lgp-select">
                        <select value={groupId ?? ''} onChange={this.onGroupChanged} disabled={loading || groups.length === 0}>
                            {loading && <option value="">Chargement…</option>}
                            {!loading && groups.length === 0 && <option value="">Aucun groupe</option>}
                            {groups.map(group => <option key={group.id} value={group.id}>{group.name}</option>)}
                        </select>
                        <ChevronDown size={16} />
                    </span>
                </label>
            </div>
        )
    }

    /** Listes hors groupe d'abord, puis une rubrique par groupe de listes (comme sur l'accueil). */
    private renderListOptions(): React.ReactNode {
        const { folders, lists } = this.props
        return (
            <>
                {lists.filter(list => list.folder_id === null).map(list => (
                    <option key={list.id} value={list.id}>{list.name}</option>
                ))}
                {folders.map(folder => {
                    const folderLists = lists.filter(list => list.folder_id === folder.id)
                    if (folderLists.length === 0) return null
                    return (
                        <optgroup key={folder.id} label={folder.name}>
                            {folderLists.map(list => <option key={list.id} value={list.id}>{list.name}</option>)}
                        </optgroup>
                    )
                })}
            </>
        )
    }
}
