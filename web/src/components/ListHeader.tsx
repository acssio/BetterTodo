import * as React from 'react'
import { Columns3, Plus, Rows2, Search } from 'lucide-react'
import type { ListView, TodoList } from '../api/types'
import { ListIcon } from './ListIcon'
import './ListHeader.css'

interface ListHeaderProps {
    list: TodoList
    openTasks: number // Calculé à partir du board, donc toujours à jour
    onNewGroup: () => void
    onChangeView: (view: ListView) => void
    onEditList: (anchor: DOMRect) => void
}

/**
 * En-tête de la liste affichée : icône, nom, nombre de tâches, et les outils.
 * La recherche arrivera plus tard. Un clic sur l'icône ouvre « Modifier la liste ».
 *
 * Quand la place manque (voir ListHeader.css), la recherche disparaît, puis les
 * boutons n'affichent plus que leur icône : les textes sont dans des <span className="btn-label">.
 */
export class ListHeader extends React.Component<ListHeaderProps> {
    render(): React.ReactNode {
        const { list, openTasks } = this.props
        const taskWord = openTasks > 1 ? 'tâches à faire' : 'tâche à faire'

        return (
            <header className="ListHeader">
                <button
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

                <button className="btn-primary header-new-group" title="Nouveau groupe" onClick={this.props.onNewGroup}>
                    <Plus size={14} /><span className="btn-label">Nouveau groupe</span>
                </button>
            </header>
        )
    }
}
