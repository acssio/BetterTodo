import * as React from 'react'
import {
    Check, ChevronDown, ChevronRight, Ellipsis, FolderPlus, LogOut, PanelLeftClose, PanelLeftOpen,
    Pencil, Plus, Settings, Trash2,
} from 'lucide-react'
import type { Folder, TodoList, User } from '../api/types'
import { InlineInput } from './InlineInput'
import { ListIcon } from './ListIcon'
import { Menu } from './Menu'
import './Sidebar.css'

interface SidebarProps {
    user: User
    folders: Folder[]
    lists: TodoList[]
    selectedListId: string | null
    width: number     // Largeur en pixels
    compact: boolean  // true = icônes seules
    peek: boolean     // true = ouverte par-dessus le contenu (fenêtre étroite)
    onExpand: () => void
    onCollapse: () => void
    onSelectList: (listId: string) => void
    onToggleFolder: (folder: Folder) => void
    onCreateList: (name: string, folderId: string | null) => Promise<void>
    onCreateFolder: (name: string) => Promise<void>
    onRenameFolder: (folderId: string, name: string) => Promise<void>
    onDeleteFolder: (folder: Folder) => void
    onEditList: (list: TodoList, anchor: DOMRect) => void
    onOpenSettings: () => void
    onLogout: () => void
}

/** Ce qu'on est en train d'ajouter : une liste (hors groupe ou dans un groupe de listes), ou un groupe de listes. */
type Adding =
    | { kind: 'list', folderId: string | null }
    | { kind: 'folder' }
    | null

class SidebarState {
    adding: Adding = null
    folderMenuId: string | null = null // Groupe de listes dont le menu « … » est ouvert
    renamingFolderId: string | null = null
}

/**
 * Barre latérale : les listes hors groupe en haut, puis chaque groupe de listes
 * (repliable) avec ses listes.
 *
 * En mode compact (classe CSS "compact"), les textes sont cachés : il reste les icônes,
 * avec le nom en info-bulle. Pour ajouter une liste, on repasse d'abord en mode normal.
 */
export class Sidebar extends React.Component<SidebarProps, SidebarState> {
    constructor(props: SidebarProps) {
        super(props)
        this.state = new SidebarState()
    }

    onAddingDone = (): void => {
        this.setState({ adding: null })
    }

    /** Ouvre un champ d'ajout (en élargissant la barre si elle est en icônes). */
    private startAdding(adding: Adding): void {
        if (this.props.compact) this.props.onExpand()
        this.setState({ adding })
    }

    render(): React.ReactNode {
        const { folders, lists, user, compact, peek, width } = this.props
        const { adding } = this.state
        const ungrouped = lists.filter(list => list.folder_id === null)

        const classes = ['Sidebar']
        if (compact) classes.push('compact')
        if (peek) classes.push('peek')

        return (
            <aside className={classes.join(' ')} style={{ width }}>
                <div className="sidebar-brand">
                    <span className="sidebar-logo"><Check size={14} color="#FFFFFF" strokeWidth={3} /></span>
                    <span className="sidebar-brand-name">BetterTodo</span>
                    {!compact && (
                        <button className="icon-btn" title="Réduire la barre (icônes seules)" onClick={this.props.onCollapse}>
                            <PanelLeftClose size={16} />
                        </button>
                    )}
                </div>

                {compact ? (
                    <div className="sidebar-label">
                        <button className="icon-btn" title="Agrandir la barre" onClick={this.props.onExpand}>
                            <PanelLeftOpen size={16} />
                        </button>
                    </div>
                ) : (
                    <div className="sidebar-label">
                        <span>Listes</span>
                        <button
                            className="icon-btn"
                            title="Nouveau groupe de listes"
                            onClick={() => this.startAdding({ kind: 'folder' })}
                        >
                            <FolderPlus size={14} />
                        </button>
                        <button
                            className="icon-btn"
                            title="Nouvelle liste"
                            onClick={() => this.startAdding({ kind: 'list', folderId: null })}
                        >
                            <Plus size={14} />
                        </button>
                    </div>
                )}

                <nav className="sidebar-lists">
                    {!compact && adding?.kind === 'folder' && (
                        <div className="sidebar-adding">
                            <InlineInput
                                placeholder="Nom du groupe de listes"
                                onSubmit={async name => {
                                    await this.props.onCreateFolder(name)
                                    this.onAddingDone()
                                }}
                                onCancel={this.onAddingDone}
                            />
                        </div>
                    )}

                    {ungrouped.map(list => this.renderList(list, false))}
                    {!compact && adding?.kind === 'list' && adding.folderId === null && this.renderNewList(null)}

                    {folders.map(folder => this.renderFolder(
                        folder,
                        lists.filter(list => list.folder_id === folder.id),
                    ))}

                    {!compact && lists.length === 0 && adding === null && (
                        <p className="sidebar-empty">Aucune liste : clique sur + pour en créer une.</p>
                    )}
                </nav>

                <div className="sidebar-footer">
                    <button className="sidebar-item" title="Paramètres" onClick={this.props.onOpenSettings}>
                        <Settings size={16} />
                        <span className="sidebar-item-name">Paramètres</span>
                    </button>
                    <div className="sidebar-user">
                        <span className="sidebar-user-name" title={user.email}>{user.display_name}</span>
                        <button className="icon-btn" title={`Se déconnecter (${user.display_name})`} onClick={this.props.onLogout}>
                            <LogOut size={14} />
                        </button>
                    </div>
                </div>
            </aside>
        )
    }

    /** Un groupe de listes : son en-tête (cliquable pour replier), puis ses listes s'il est ouvert. */
    private renderFolder(folder: Folder, folderLists: TodoList[]): React.ReactNode {
        const Chevron = folder.collapsed ? ChevronRight : ChevronDown
        const { adding } = this.state
        const addingHere = !this.props.compact && adding?.kind === 'list' && adding.folderId === folder.id

        if (this.state.renamingFolderId === folder.id) {
            return (
                <div key={folder.id} className="sidebar-folder">
                    <div className="sidebar-adding">
                        <InlineInput
                            placeholder="Nom du groupe de listes"
                            initialValue={folder.name}
                            onSubmit={async name => {
                                await this.props.onRenameFolder(folder.id, name)
                                this.setState({ renamingFolderId: null })
                            }}
                            onCancel={() => this.setState({ renamingFolderId: null })}
                        />
                    </div>
                    {!folder.collapsed && folderLists.map(list => this.renderList(list, true))}
                </div>
            )
        }

        return (
            <div key={folder.id} className="sidebar-folder">
                <div className={this.state.folderMenuId === folder.id ? 'sidebar-folder-head menu-open' : 'sidebar-folder-head'}>
                    <button
                        className="sidebar-folder-toggle"
                        title={folder.name}
                        onClick={() => this.props.onToggleFolder(folder)}
                    >
                        <Chevron size={14} />
                        <span className="sidebar-folder-name">{folder.name}</span>
                        {folder.collapsed && (
                            <span className="sidebar-count">
                                {folderLists.length} {folderLists.length > 1 ? 'listes' : 'liste'}
                            </span>
                        )}
                    </button>
                    <button
                        className="icon-btn sidebar-folder-add"
                        title={`Nouvelle liste dans « ${folder.name} »`}
                        onClick={() => {
                            if (folder.collapsed) this.props.onToggleFolder(folder) // On l'ouvre pour voir le champ
                            this.startAdding({ kind: 'list', folderId: folder.id })
                        }}
                    >
                        <Plus size={13} />
                    </button>
                    <div className="menu-anchor sidebar-folder-menu">
                        <button
                            className="icon-btn"
                            title={`Options de « ${folder.name} »`}
                            onClick={() => this.setState({ folderMenuId: folder.id })}
                        >
                            <Ellipsis size={14} />
                        </button>
                        {this.state.folderMenuId === folder.id && (
                            <Menu
                                onClose={() => this.setState({ folderMenuId: null })}
                                items={[
                                    { label: 'Renommer', icon: Pencil, onSelect: () => this.setState({ renamingFolderId: folder.id }) },
                                    { label: 'Supprimer le groupe', icon: Trash2, danger: true, onSelect: () => this.props.onDeleteFolder(folder) },
                                ]}
                            />
                        )}
                    </div>
                </div>

                {!folder.collapsed && folderLists.map(list => this.renderList(list, true))}
                {addingHere && this.renderNewList(folder.id)}
            </div>
        )
    }

    private renderNewList(folderId: string | null): React.ReactNode {
        return (
            <div className={folderId ? 'sidebar-adding indented' : 'sidebar-adding'}>
                <InlineInput
                    placeholder="Nom de la liste"
                    onSubmit={async name => {
                        await this.props.onCreateList(name, folderId)
                        this.onAddingDone()
                    }}
                    onCancel={this.onAddingDone}
                />
            </div>
        )
    }

    /**
     * Une liste : icône, nom, nombre de tâches à faire (nom en info-bulle en mode compact).
     * Au survol, un crayon remplace le nombre : il ouvre « Modifier la liste ».
     */
    private renderList(list: TodoList, indented: boolean): React.ReactNode {
        const classes = ['sidebar-item']
        if (indented) classes.push('indented')
        if (list.id === this.props.selectedListId) classes.push('active')

        return (
            <div key={list.id} className="sidebar-row">
                <button
                    className={classes.join(' ')}
                    title={this.props.compact ? list.name : undefined}
                    onClick={() => this.props.onSelectList(list.id)}
                >
                    <ListIcon icon={list.icon} color={list.color} />
                    <span className="sidebar-item-name">{list.name}</span>
                    {list.open_tasks > 0 && <span className="sidebar-count">{list.open_tasks}</span>}
                </button>
                {!this.props.compact && (
                    <button
                        className="icon-btn sidebar-row-edit"
                        title={`Modifier « ${list.name} »`}
                        data-list-edit
                        onClick={event => this.props.onEditList(list, event.currentTarget.parentElement!.getBoundingClientRect())}
                    >
                        <Pencil size={13} />
                    </button>
                )}
            </div>
        )
    }
}
