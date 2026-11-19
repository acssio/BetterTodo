import * as React from 'react'
import {
    Check, ChevronDown, ChevronRight, Ellipsis, FolderPlus, ListPlus, LogOut, Pencil, Plus, Settings, ShieldUser, Trash2,
} from 'lucide-react'
import type { DueCounts, Folder, SmartList, TodoList, User } from '../api/types'
import { SMART_SCHEDULED, SMART_TODAY, smartPaneId, type SmartKind } from '../utils/smartLists'
import { InlineInput } from './InlineInput'
import { ListIcon } from './ListIcon'
import { Menu } from './Menu'
import { SMART_INFO } from './smartInfo'
import './MobileHome.css'

interface MobileHomeProps {
    user: User
    folders: Folder[]
    lists: TodoList[]
    dueCounts: DueCounts | null
    smartLists: SmartList[] // Listes intelligentes perso (seules les visibles sont affichées)
    onSelectList: (listId: string) => void // Liste ou liste intelligente : ouvre l'écran de la liste
    onToggleFolder: (folder: Folder) => void
    onCreateList: (name: string, folderId: string | null) => Promise<void>
    onCreateFolder: (name: string) => Promise<void>
    onRenameFolder: (folderId: string, name: string) => Promise<void>
    onDeleteFolder: (folder: Folder) => void
    onOpenSettings: () => void
    onOpenAdmin: () => void
    onLogout: () => void
    onQuickTask: () => void // Bouton flottant « Quick task »
}

/** Ce qu'on est en train d'ajouter (même principe que la barre latérale). */
type Adding =
    | { kind: 'list', folderId: string | null }
    | { kind: 'folder' }
    | null

class MobileHomeState {
    adding: Adding = null
    newMenuOpen: boolean = false          // Menu du « + » de l'en-tête
    folderMenuId: string | null = null    // Groupe de listes dont le menu « … » est ouvert
    renamingFolderId: string | null = null
}

/**
 * Écran d'accueil du mode mobile : il remplace la barre latérale.
 * « Aujourd'hui » et « Planifié » en tuiles, puis les listes rangées par groupe de listes,
 * et en bas Paramètres / Console admin / déconnexion. Toucher une liste ouvre son écran.
 */
export class MobileHome extends React.Component<MobileHomeProps, MobileHomeState> {
    constructor(props: MobileHomeProps) {
        super(props)
        this.state = new MobileHomeState()
    }

    onAddingDone = (): void => {
        this.setState({ adding: null })
    }

    render(): React.ReactNode {
        const { folders, lists, user } = this.props
        const { adding } = this.state
        const ungrouped = lists.filter(list => list.folder_id === null)

        return (
            <div className="MobileHome">
                <header className="mhome-head">
                    <span className="mhome-logo"><Check size={16} strokeWidth={3} /></span>
                    <span className="mhome-brand">BetterTodo</span>
                    <div className="menu-anchor">
                        <button className="mhome-btn" aria-label="Nouvelle liste ou groupe de listes" onClick={() => this.setState({ newMenuOpen: true })}>
                            <Plus size={22} />
                        </button>
                        {this.state.newMenuOpen && (
                            <Menu
                                onClose={() => this.setState({ newMenuOpen: false })}
                                items={[
                                    { label: 'Nouvelle liste', icon: ListPlus, onSelect: () => this.setState({ adding: { kind: 'list', folderId: null } }) },
                                    { label: 'Nouveau groupe de listes', icon: FolderPlus, onSelect: () => this.setState({ adding: { kind: 'folder' } }) },
                                ]}
                            />
                        )}
                    </div>
                </header>

                <div className="mhome-main">
                    <div className="mhome-scroll">
                        <nav className="mhome-smart">
                            {this.props.user.show_today && this.renderSmart(SMART_TODAY)}
                            {this.props.user.show_scheduled && this.renderSmart(SMART_SCHEDULED)}
                            {this.props.smartLists.filter(smart => smart.visible).map(smart => this.renderCustomSmart(smart))}
                        </nav>

                        <div className="mhome-label">Listes</div>

                        <nav className="mhome-lists">
                            {adding?.kind === 'folder' && (
                                <div className="mhome-adding">
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

                            {ungrouped.map(list => this.renderList(list))}
                            {adding?.kind === 'list' && adding.folderId === null && this.renderNewList(null)}

                            {folders.map(folder => this.renderFolder(folder, lists.filter(list => list.folder_id === folder.id)))}

                            {lists.length === 0 && adding === null && (
                                <p className="mhome-empty">Aucune liste : touche + en haut pour en créer une.</p>
                            )}
                        </nav>
                    </div>

                    {/* Bouton flottant : centré en bas, reste en place pendant que les listes défilent */}
                    {lists.length > 0 && (
                        <button className="mhome-quick" onClick={this.props.onQuickTask}>
                            <Plus size={20} strokeWidth={2.5} />Quick task
                        </button>
                    )}
                </div>

                {/* Pied compact : nom, puis console admin, paramètres et déconnexion en icônes seules */}
                <footer className="mhome-foot">
                    <div className="mhome-user">
                        <span className="mhome-user-name">{user.display_name}</span>
                        {user.is_admin && (
                            <button className="mhome-btn" aria-label="Console admin" title="Console admin" onClick={this.props.onOpenAdmin}>
                                <ShieldUser size={18} />
                            </button>
                        )}
                        <button className="mhome-btn" aria-label="Paramètres" title="Paramètres" onClick={this.props.onOpenSettings}>
                            <Settings size={18} />
                        </button>
                        <button className="mhome-btn" aria-label={`Se déconnecter (${user.display_name})`} onClick={this.props.onLogout}>
                            <LogOut size={18} />
                        </button>
                    </div>
                </footer>
            </div>
        )
    }

    /** Tuile « Aujourd'hui » ou « Planifié » avec son compteur. */
    private renderSmart(kind: SmartKind): React.ReactNode {
        const info = SMART_INFO[kind]
        const count = (kind === SMART_TODAY ? this.props.dueCounts?.today : this.props.dueCounts?.scheduled) ?? 0
        return this.renderTile(kind, info.title, info.color, <info.icon size={18} color="#FFFFFF" />, count)
    }

    /** Tuile d'une liste intelligente perso (par tags ou par urgence). */
    private renderCustomSmart(smart: SmartList): React.ReactNode {
        const icon = <ListIcon icon={smart.icon} color="#FFFFFF" size={18} />
        return this.renderTile(smartPaneId(smart.id), smart.name, smart.color, icon, smart.open_tasks)
    }

    private renderTile(paneId: string, title: string, color: string, icon: React.ReactNode, count: number): React.ReactNode {
        return (
            <button key={paneId} className="mhome-tile" onClick={() => this.props.onSelectList(paneId)}>
                <span className="mhome-tile-top">
                    <span className="mhome-tile-icon" style={{ background: color }}>{icon}</span>
                    <span className="mhome-tile-count">{count}</span>
                </span>
                <span className="mhome-tile-name">{title}</span>
            </button>
        )
    }

    /** Un groupe de listes : en-tête repliable avec son menu « … », puis ses listes. */
    private renderFolder(folder: Folder, folderLists: TodoList[]): React.ReactNode {
        const Chevron = folder.collapsed ? ChevronRight : ChevronDown
        const { adding } = this.state
        const addingHere = adding?.kind === 'list' && adding.folderId === folder.id

        return (
            <div key={folder.id} className="mhome-folder">
                {this.state.renamingFolderId === folder.id ? (
                    <div className="mhome-adding">
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
                ) : (
                    <div className="mhome-folder-head">
                        <button className="mhome-folder-toggle" onClick={() => this.props.onToggleFolder(folder)}>
                            <Chevron size={16} />
                            <span className="mhome-folder-name">{folder.name}</span>
                            {folder.collapsed && (
                                <span className="mhome-count">
                                    {folderLists.length} {folderLists.length > 1 ? 'listes' : 'liste'}
                                </span>
                            )}
                        </button>
                        <div className="menu-anchor">
                            <button className="mhome-btn" aria-label={`Options de « ${folder.name} »`} onClick={() => this.setState({ folderMenuId: folder.id })}>
                                <Ellipsis size={18} />
                            </button>
                            {this.state.folderMenuId === folder.id && (
                                <Menu
                                    onClose={() => this.setState({ folderMenuId: null })}
                                    items={[
                                        {
                                            label: 'Nouvelle liste ici', icon: ListPlus,
                                            onSelect: () => {
                                                if (folder.collapsed) this.props.onToggleFolder(folder) // On l'ouvre pour voir le champ
                                                this.setState({ adding: { kind: 'list', folderId: folder.id } })
                                            },
                                        },
                                        { label: 'Renommer', icon: Pencil, onSelect: () => this.setState({ renamingFolderId: folder.id }) },
                                        { label: 'Supprimer le groupe', icon: Trash2, danger: true, onSelect: () => this.props.onDeleteFolder(folder) },
                                    ]}
                                />
                            )}
                        </div>
                    </div>
                )}

                {!folder.collapsed && folderLists.map(list => this.renderList(list))}
                {addingHere && this.renderNewList(folder.id)}
            </div>
        )
    }

    private renderNewList(folderId: string | null): React.ReactNode {
        return (
            <div className="mhome-adding">
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

    /** Une liste : icône sur fond teinté, nom, tâches à faire, chevron. */
    private renderList(list: TodoList): React.ReactNode {
        return (
            <button key={list.id} className="mhome-row" onClick={() => this.props.onSelectList(list.id)}>
                <ListIcon icon={list.icon} color={list.color} size={17} boxed />
                <span className="mhome-row-name">{list.name}</span>
                {list.open_tasks > 0 && <span className="mhome-count">{list.open_tasks}</span>}
                <ChevronRight size={16} className="mhome-chevron" />
            </button>
        )
    }
}
