import * as React from 'react'
import { ChevronLeft, Columns3, Ellipsis, Eye, EyeOff, Pencil, Plus, Rows2, Search, Trash2, X } from 'lucide-react'
import type { ListView, TodoList } from '../api/types'
import { ListIcon } from './ListIcon'
import { Menu } from './Menu'
import './ListHeader.css'

interface ListHeaderProps {
    list: TodoList
    openTasks: number // Calculé à partir du board, donc toujours à jour
    onNewGroup: () => void
    doneTasks: number // Tâches faites dans la liste (visibles ou non)
    onChangeView: (view: ListView) => void
    onEditList: (anchor: DOMRect) => void
    onToggleShowDone: () => void
    onClearDone: () => void
    onClosePane?: () => void // Seulement en vue scindée : ferme cette moitié
    onBack?: () => void      // Seulement en mode mobile : flèche ‹ vers l'accueil
}

class ListHeaderState {
    menuOpen: boolean = false
}

/**
 * En-tête de la liste affichée : icône, nom, nombre de tâches, et les outils.
 * La recherche arrivera plus tard. Un clic sur l'icône ouvre « Modifier la liste ».
 *
 * Quand la place manque (voir ListHeader.css), la recherche disparaît, puis les
 * boutons n'affichent plus que leur icône : les textes sont dans des <span className="btn-label">.
 */
export class ListHeader extends React.Component<ListHeaderProps, ListHeaderState> {
    private iconRef = React.createRef<HTMLButtonElement>()

    constructor(props: ListHeaderProps) {
        super(props)
        this.state = new ListHeaderState()
    }

    /** Position de l'icône de la liste : l'overlay « Modifier la liste » s'ouvre à côté. */
    private headerIconRect(): DOMRect {
        return this.iconRef.current?.getBoundingClientRect() ?? new DOMRect(0, 0, 0, 0)
    }

    render(): React.ReactNode {
        const { list, openTasks, doneTasks } = this.props
        const DoneIcon = list.show_done ? Eye : EyeOff
        const taskWord = openTasks > 1 ? 'tâches à faire' : 'tâche à faire'

        return (
            <header className="ListHeader">
                {this.props.onBack && <BackButton onBack={this.props.onBack} />}
                <button
                    ref={this.iconRef}
                    className="header-icon"
                    title="Modifier la liste"
                    data-list-edit
                    onClick={event => this.props.onEditList(event.currentTarget.getBoundingClientRect())}
                >
                    <ListIcon icon={list.icon} color={list.color} size={18} boxed />
                </button>
                <div className="header-title">
                    <h1 title={list.name}>{list.name}</h1>
                    <span>{openTasks} {taskWord}</span>
                </div>

                <div className="header-search" title="Recherche (bientôt)">
                    <Search size={14} />
                    <input type="search" placeholder="Rechercher…" disabled />
                </div>

                <div className="header-views">
                    <button
                        className={list.default_view === 'columns' ? 'on' : ''}
                        title="Vue colonnes"
                        onClick={() => this.props.onChangeView('columns')}
                    >
                        <Columns3 size={14} /><span className="btn-label">Colonnes</span>
                    </button>
                    <button
                        className={list.default_view === 'sections' ? 'on' : ''}
                        title="Vue sections"
                        onClick={() => this.props.onChangeView('sections')}
                    >
                        <Rows2 size={14} /><span className="btn-label">Sections</span>
                    </button>
                </div>

                <button
                    className={list.show_done ? 'icon-btn header-tool header-done' : 'icon-btn header-tool header-done on'}
                    title={list.show_done ? 'Masquer les tâches faites' : `Afficher les tâches faites (${doneTasks})`}
                    onClick={this.props.onToggleShowDone}
                >
                    <DoneIcon size={16} />
                </button>

                <div className="menu-anchor">
                    <button className="icon-btn header-tool" title="Options de la liste" onClick={() => this.setState({ menuOpen: true })}>
                        <Ellipsis size={16} />
                    </button>
                    {this.state.menuOpen && (
                        <Menu
                            onClose={() => this.setState({ menuOpen: false })}
                            items={[
                                {
                                    label: 'Modifier la liste', icon: Pencil,
                                    onSelect: () => this.props.onEditList(this.headerIconRect()),
                                },
                                {
                                    label: list.show_done ? 'Masquer les tâches faites' : 'Afficher les tâches faites',
                                    icon: list.show_done ? EyeOff : Eye,
                                    onSelect: this.props.onToggleShowDone,
                                },
                                {
                                    label: `Vider les tâches faites (${doneTasks})`, icon: Trash2, danger: true,
                                    onSelect: this.props.onClearDone,
                                },
                            ]}
                        />
                    )}
                </div>

                {this.props.onClosePane && (
                    <button className="icon-btn header-tool" title="Fermer cette moitié" onClick={this.props.onClosePane}>
                        <X size={16} />
                    </button>
                )}

                <button className="btn-primary header-new-group" title="Nouveau groupe" onClick={this.props.onNewGroup}>
                    <Plus size={14} /><span className="btn-label">Nouveau groupe</span>
                </button>
            </header>
        )
    }
}

/** Flèche ‹ du mode mobile, au début de l'en-tête (liste ou liste intelligente). */
export class BackButton extends React.Component<{ onBack: () => void }> {
    render(): React.ReactNode {
        return (
            <button className="header-back" onClick={this.props.onBack} aria-label="Retour aux listes">
                <ChevronLeft size={24} />
            </button>
        )
    }
}
