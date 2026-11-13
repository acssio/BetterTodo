import * as React from 'react'
import { Columns3, Plus, Rows2, Search } from 'lucide-react'
import type { TodoList } from '../api/types'
import { ListIcon } from './ListIcon'
import './ListHeader.css'

interface ListHeaderProps {
    list: TodoList
    openTasks: number // Calculé à partir du board, donc toujours à jour
    onNewGroup: () => void
}

/**
 * En-tête de la liste affichée : icône, nom, nombre de tâches, et les outils.
 * La recherche et la vue Sections arriveront à l'étape 3c.
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
                <ListIcon icon={list.icon} color={list.color} size={18} boxed />
                <div className="header-title">
                    <h1 title={list.name}>{list.name}</h1>
                    <span>{openTasks} {taskWord}</span>
                </div>

                <div className="header-search" title="Recherche (étape 3c)">
                    <Search size={14} />
                    <input type="search" placeholder="Rechercher…" disabled />
                </div>

                <div className="header-views">
                    <button className="on" title="Vue colonnes">
                        <Columns3 size={14} /><span className="btn-label">Colonnes</span>
                    </button>
                    <button title="Vue sections (étape 3c)" disabled>
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
