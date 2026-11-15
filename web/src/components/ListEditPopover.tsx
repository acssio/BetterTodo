import * as React from 'react'
import { ChevronDown, Search, Trash2, X } from 'lucide-react'
import type { Folder, ListChanges, TodoList } from '../api/types'
import { DismissListener, popoverPosition } from '../utils/popover'
import { confirmDialog } from './confirm'
import { ListIcon } from './ListIcon'
import { LIST_COLORS, LIST_ICONS } from './listIcons'
import './TaskPopover.css'
import './ListEditPopover.css'

const POPOVER_WIDTH = 320

interface ListEditPopoverProps {
    list: TodoList
    folders: Folder[]
    anchor: DOMRect // Élément cliqué (ligne de la barre latérale, ou icône de l'en-tête)
    onUpdate: (changes: ListChanges) => void
    onDelete: () => void
    onClose: () => void
}

class ListEditPopoverState {
    name: string = ''      // Modifié localement, envoyé en quittant le champ
    iconQuery: string = '' // Recherche dans les icônes
}

/**
 * Overlay « Modifier une liste » (écran 7 de la maquette) : nom, groupe de listes,
 * couleur, icône, supprimer. Comme l'overlay d'une tâche, tout s'enregistre seul.
 */
export class ListEditPopover extends React.Component<ListEditPopoverProps, ListEditPopoverState> {
    private rootRef = React.createRef<HTMLDivElement>()
    private dismiss = new DismissListener(this.rootRef, () => this.props.onClose(), '[data-list-edit]')

    constructor(props: ListEditPopoverProps) {
        super(props)
        this.state = { ...new ListEditPopoverState(), name: props.list.name }
    }

    componentDidMount(): void {
        this.dismiss.start()
    }

    componentWillUnmount(): void {
        this.dismiss.stop()
        this.saveName()
    }

    private saveName(): void {
        const name = this.state.name.trim()
        if (name === '') {
            this.setState({ name: this.props.list.name }) // Nom vide refusé
        } else if (name !== this.props.list.name) {
            this.props.onUpdate({ name })
        }
    }

    onNameKeyDown = (event: React.KeyboardEvent<HTMLInputElement>): void => {
        if (event.key === 'Enter') event.currentTarget.blur()
    }

    onFolderChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
        this.props.onUpdate({ folder_id: event.target.value || null }) // "" = hors groupe de listes
    }

    onDeleteClick = async (): Promise<void> => {
        if (await confirmDialog.ask(`Supprimer la liste « ${this.props.list.name} » et toutes ses tâches ?`)) {
            this.props.onDelete()
        }
    }

    /** Icônes dont le nom (anglais) ou le libellé (français) contient la recherche. */
    private filteredIcons(): string[] {
        const query = this.state.iconQuery.trim().toLowerCase()
        return Object.entries(LIST_ICONS)
            .filter(([name, { label }]) => query === '' || name.includes(query) || label.includes(query))
            .map(([name]) => name)
    }

    render(): React.ReactNode {
        const { list, folders } = this.props
        const icons = this.filteredIcons()

        return (
            <div
                className="TaskPopover ListEditPopover"
                ref={this.rootRef}
                style={popoverPosition(this.props.anchor, POPOVER_WIDTH, 520)}
            >
                <div className="pop-title">
                    <ListIcon icon={list.icon} color={list.color} size={18} boxed />
                    <span className="list-edit-title">Modifier la liste</span>
                    <button className="icon-btn" onClick={this.props.onClose} aria-label="Fermer">
                        <X size={14} />
                    </button>
                </div>

                <label className="pop-field">
                    <span className="pop-label">Nom</span>
                    <input
                        className="pop-input"
                        value={this.state.name}
                        onChange={event => this.setState({ name: event.target.value })}
                        onKeyDown={this.onNameKeyDown}
                        onBlur={() => this.saveName()}
                    />
                </label>

                <label className="pop-field">
                    <span className="pop-label">Groupe de listes</span>
                    <span className="pop-select">
                        <select className="pop-input" value={list.folder_id ?? ''} onChange={this.onFolderChanged}>
                            <option value="">Aucun</option>
                            {folders.map(folder => <option key={folder.id} value={folder.id}>{folder.name}</option>)}
                        </select>
                        <ChevronDown size={14} />
                    </span>
                </label>

                <div className="pop-field">
                    <span className="pop-label">Couleur</span>
                    <div className="list-colors">
                        {LIST_COLORS.map(color => (
                            <button
                                key={color}
                                className={color === list.color ? 'list-color on' : 'list-color'}
                                style={{ background: color, color }}
                                aria-label={`Couleur ${color}`}
                                onClick={() => this.props.onUpdate({ color })}
                            />
                        ))}
                    </div>
                </div>

                <div className="pop-field">
                    <span className="pop-label">Icône</span>
                    <span className="pop-input">
                        <Search size={14} />
                        <input
                            value={this.state.iconQuery}
                            placeholder="Rechercher une icône…"
                            onChange={event => this.setState({ iconQuery: event.target.value })}
                        />
                    </span>
                    <div className="list-icons">
                        {icons.map(name => {
                            const Icon = LIST_ICONS[name].icon
                            const selected = name === list.icon
                            return (
                                <button
                                    key={name}
                                    className={selected ? 'list-icon on' : 'list-icon'}
                                    style={selected ? { borderColor: list.color, background: list.color + '1A' } : undefined}
                                    title={LIST_ICONS[name].label}
                                    onClick={() => this.props.onUpdate({ icon: name })}
                                >
                                    <Icon size={18} color={selected ? list.color : '#3A3A3A'} />
                                </button>
                            )
                        })}
                        {icons.length === 0 && <span className="pop-status">Aucune icône trouvée</span>}
                    </div>
                </div>

                <div className="pop-foot">
                    <button className="pop-delete" onClick={this.onDeleteClick}>
                        <Trash2 size={14} />Supprimer
                    </button>
                    <span className="pop-status">Icônes Lucide</span>
                </div>
            </div>
        )
    }
}
