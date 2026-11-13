import * as React from 'react'
import { Check, ChevronDown, ChevronRight, FolderPlus, LogOut, Plus, Settings } from 'lucide-react'
import type { Folder, TodoList, User } from '../api/types'
import { ListIcon } from './ListIcon'
import './Sidebar.css'

interface SidebarProps {
    user: User
    folders: Folder[]
    lists: TodoList[]
    selectedListId: string | null
    onSelectList: (listId: string) => void
    onToggleFolder: (folder: Folder) => void
    onLogout: () => void
}

/**
 * Barre latérale : les listes hors groupe en haut, puis chaque groupe de listes
 * (repliable) avec ses listes.
 */
export class Sidebar extends React.Component<SidebarProps> {
    render(): React.ReactNode {
        const { folders, lists, user } = this.props
        const ungrouped = lists.filter(list => list.folder_id === null)

        return (
            <aside className="Sidebar">
                <div className="sidebar-brand">
                    <span className="sidebar-logo"><Check size={14} color="#FFFFFF" strokeWidth={3} /></span>
                    BetterTodo
                </div>

                <div className="sidebar-label">
                    <span>Listes</span>
                    <button className="icon-btn" title="Nouveau groupe de listes (étape 3b)" disabled>
                        <FolderPlus size={14} />
                    </button>
                    <button className="icon-btn" title="Nouvelle liste (étape 3b)" disabled>
                        <Plus size={14} />
                    </button>
                </div>

                <nav className="sidebar-lists">
                    {ungrouped.map(list => this.renderList(list, false))}

                    {folders.map(folder => this.renderFolder(
                        folder,
                        lists.filter(list => list.folder_id === folder.id),
                    ))}

                    {lists.length === 0 && (
                        <p className="sidebar-empty">Aucune liste pour l'instant.</p>
                    )}
                </nav>

                <div className="sidebar-footer">
                    <button className="sidebar-item" title="Paramètres (étape 3c)" disabled>
                        <Settings size={16} />
                        <span className="sidebar-item-name">Paramètres</span>
                    </button>
                    <div className="sidebar-user">
                        <span className="sidebar-user-name" title={user.email}>{user.display_name}</span>
                        <button className="icon-btn" title="Se déconnecter" onClick={this.props.onLogout}>
                            <LogOut size={14} />
                        </button>
                    </div>
                </div>
            </aside>
        )
    }

    /** Un groupe de listes : son en-tête cliquable, puis ses listes s'il est ouvert. */
    private renderFolder(folder: Folder, folderLists: TodoList[]): React.ReactNode {
        const Chevron = folder.collapsed ? ChevronRight : ChevronDown

        return (
            <div key={folder.id} className="sidebar-folder">
                <button className="sidebar-folder-head" onClick={() => this.props.onToggleFolder(folder)}>
                    <Chevron size={14} />
                    <span className="sidebar-folder-name">{folder.name}</span>
                    {folder.collapsed && (
                        <span className="sidebar-count">
                            {folderLists.length} {folderLists.length > 1 ? 'listes' : 'liste'}
                        </span>
                    )}
                </button>

                {!folder.collapsed && folderLists.map(list => this.renderList(list, true))}
            </div>
        )
    }

    /** Une liste : icône, nom, nombre de tâches à faire. */
    private renderList(list: TodoList, indented: boolean): React.ReactNode {
        const classes = ['sidebar-item']
        if (indented) classes.push('indented')
        if (list.id === this.props.selectedListId) classes.push('active')

        return (
            <button key={list.id} className={classes.join(' ')} onClick={() => this.props.onSelectList(list.id)}>
                <ListIcon icon={list.icon} color={list.color} />
                <span className="sidebar-item-name">{list.name}</span>
                {list.open_tasks > 0 && <span className="sidebar-count">{list.open_tasks}</span>}
            </button>
        )
    }
}
