import * as React from 'react'
import { Columns3, Plus, Rows2, Search } from 'lucide-react'
import type { TodoList } from '../api/types'
import { ListIcon } from './ListIcon'
import './ListHeader.css'

interface ListHeaderProps {
    list: TodoList
}

/**
 * En-tête de la liste affichée : icône, nom, nombre de tâches, et les outils
 * (recherche, Colonnes/Sections, nouveau groupe), branchés aux étapes 3b et 3c.
 */
export class ListHeader extends React.Component<ListHeaderProps> {
    render(): React.ReactNode {
        const { list } = this.props
        const taskWord = list.open_tasks > 1 ? 'tâches à faire' : 'tâche à faire'

        return (
            <header className="ListHeader">
                <ListIcon icon={list.icon} color={list.color} size={18} boxed />
                <div className="header-title">
                    <h1>{list.name}</h1>
                    <span>{list.open_tasks} {taskWord}</span>
                </div>

                <div className="header-search" title="Recherche (étape 3c)">
                    <Search size={14} />
                    <input type="search" placeholder="Rechercher…" disabled />
                </div>

                <div className="header-views">
                    <button className="on"><Columns3 size={14} />Colonnes</button>
                    <button title="Vue sections (étape 3c)" disabled><Rows2 size={14} />Sections</button>
                </div>

                <button className="btn-primary" title="Nouveau groupe (étape 3b)" disabled>
                    <Plus size={14} />Nouveau groupe
                </button>
            </header>
        )
    }
}
