import * as React from 'react'
import { Columns3, GripVertical, Plus, Rows2, Trash2, X } from 'lucide-react'
import { api, ApiError } from '../api/ApiClient'
import type { ListView, Tag, UrgencyLevel, User, UserChanges } from '../api/types'
import { computeDropIndex } from '../utils/dragDrop'
import { moveItem } from '../utils/moveItem'
import { DismissListener } from '../utils/popover'
import { InlineInput } from './InlineInput'
import { LIST_COLORS } from './listIcons'
import './TaskPopover.css'     // .pop-tag, .pop-tag-add
import './ListEditPopover.css' // .list-color
import './Menu.css'            // .menu-anchor, .menu-backdrop
import './TaskDropZone.css'    // .drop-indicator
import './SettingsDialog.css'

interface SettingsDialogProps {
    user: User
    urgencyLevels: UrgencyLevel[]
    onUserChanged: (user: User) => void                    // Nom ou vue par défaut modifiés
    onUrgencyLevelsChanged: (levels: UrgencyLevel[]) => void
    onClose: () => void
}

class SettingsDialogState {
    displayName: string = ''
    levels: UrgencyLevel[] = []
    tags: Tag[] = []
    tagsLoaded: boolean = false
    colorPickerFor: string | null = null  // Niveau dont la palette est ouverte
    addingLevel: boolean = false
    addingTag: boolean = false
    renamingTagId: string | null = null
    dragLevelId: string | null = null     // Niveau en train d'être glissé
    dropIndex: number | null = null
    error: string = ''
}

/**
 * Écran Paramètres (écran 6 de la maquette) : compte, niveaux d'urgence, tags, vue par défaut.
 *
 * Exception à la règle « seul Workspace parle à l'API » : c'est un écran à part,
 * qui charge et modifie ses propres données (les tags). Il prévient Workspace de
 * ce qui le concerne (onUserChanged, onUrgencyLevelsChanged), et Workspace recharge
 * la liste affichée à la fermeture (un tag renommé y apparaît peut-être).
 */
export class SettingsDialog extends React.Component<SettingsDialogProps, SettingsDialogState> {
    private dialogRef = React.createRef<HTMLDivElement>()
    private dismiss = new DismissListener(this.dialogRef, () => this.props.onClose())

    constructor(props: SettingsDialogProps) {
        super(props)
        this.state = { ...new SettingsDialogState(), displayName: props.user.display_name, levels: props.urgencyLevels }
    }

    async componentDidMount(): Promise<void> {
        this.dismiss.start()
        try {
            this.setState({ tags: await api.getTags(), tagsLoaded: true })
        } catch (error) {
            this.showError(error)
        }
    }

    componentWillUnmount(): void {
        this.dismiss.stop()
    }

    private showError(error: unknown): void {
        this.setState({ error: error instanceof ApiError ? error.message : 'Impossible de joindre le serveur.' })
    }

    // =========================================================================
    // Compte
    // =========================================================================

    private async updateUser(changes: UserChanges): Promise<void> {
        try {
            this.props.onUserChanged(await api.updateMe(changes))
        } catch (error) {
            this.showError(error)
        }
    }

    onDisplayNameBlur = (): void => {
        const name = this.state.displayName.trim()
        if (name === '') {
            this.setState({ displayName: this.props.user.display_name }) // Nom vide refusé
        } else if (name !== this.props.user.display_name) {
            void this.updateUser({ display_name: name })
        }
    }

    onDefaultViewClick = (view: ListView): void => {
        if (view !== this.props.user.default_view) {
            void this.updateUser({ default_view: view })
        }
    }

    // =========================================================================
    // Niveaux d'urgence
    // =========================================================================

    /** Change la liste locale ET prévient Workspace (les cartes utilisent les niveaux). */
    private setLevels(levels: UrgencyLevel[]): void {
        this.setState({ levels })
        this.props.onUrgencyLevelsChanged(levels)
    }

    private async updateLevel(level: UrgencyLevel, changes: Partial<UrgencyLevel>): Promise<void> {
        this.setLevels(this.state.levels.map(l => (l.id === level.id ? { ...l, ...changes } : l)))
        try {
            await api.updateUrgencyLevel(level.id, changes)
        } catch (error) {
            this.showError(error)
        }
    }

    private async createLevel(name: string): Promise<void> {
        // Couleur : la première de la palette qui n'est pas encore prise
        const used = this.state.levels.map(l => l.color.toUpperCase())
        const color = LIST_COLORS.find(c => !used.includes(c)) ?? LIST_COLORS[LIST_COLORS.length - 1]
        try {
            const level = await api.createUrgencyLevel(name, color)
            this.setLevels([...this.state.levels, level])
        } catch (error) {
            this.showError(error)
        }
    }

    private async deleteLevel(level: UrgencyLevel): Promise<void> {
        if (!window.confirm(`Supprimer le niveau « ${level.name} » ? Les tâches de ce niveau passeront sans urgence.`)) return

        this.setLevels(this.state.levels.filter(l => l.id !== level.id))
        try {
            await api.deleteUrgencyLevel(level.id)
        } catch (error) {
            this.showError(error)
        }
    }

    // --- Réordonner par glisser-déposer (même principe que les tâches) ----------

    onLevelDragStart = (event: React.DragEvent<HTMLDivElement>, level: UrgencyLevel): void => {
        event.dataTransfer.setData('text/plain', level.id)
        event.dataTransfer.effectAllowed = 'move'
        this.setState({ dragLevelId: level.id })
    }

    onLevelsDragOver = (event: React.DragEvent<HTMLDivElement>): void => {
        const dragId = this.state.dragLevelId
        if (dragId === null) return
        event.preventDefault()
        const index = computeDropIndex(event.currentTarget, event.clientY, dragId, 'data-level-id')
        if (index !== this.state.dropIndex) this.setState({ dropIndex: index })
    }

    onLevelsDrop = (event: React.DragEvent<HTMLDivElement>): void => {
        event.preventDefault()
        const { dragLevelId, dropIndex } = this.state
        this.setState({ dragLevelId: null, dropIndex: null })
        if (dragLevelId === null || dropIndex === null) return

        const currentIndex = this.state.levels.findIndex(l => l.id === dragLevelId)
        if (currentIndex === dropIndex) return // Reposé à sa place

        // Écran tout de suite (rangs renumérotés comme le fera le serveur), puis l'API
        const reordered = moveItem(this.state.levels, dragLevelId, dropIndex).map((l, rank) => ({ ...l, rank }))
        this.setLevels(reordered)
        api.updateUrgencyLevel(dragLevelId, { rank: dropIndex }).catch(error => this.showError(error))
    }

    onLevelDragEnd = (): void => {
        this.setState({ dragLevelId: null, dropIndex: null })
    }

    // =========================================================================
    // Tags
    // =========================================================================

    private async createTag(name: string): Promise<void> {
        try {
            const tag = await api.createTag(name)
            this.setState(state => ({ tags: [...state.tags, tag].sort((a, b) => a.name.localeCompare(b.name)) }))
        } catch (error) {
            this.showError(error)
        }
    }

    private async renameTag(tag: Tag, name: string): Promise<void> {
        try {
            const renamed = await api.renameTag(tag.id, name)
            this.setState(state => ({ tags: state.tags.map(t => (t.id === tag.id ? renamed : t)), renamingTagId: null }))
        } catch (error) {
            this.showError(error) // Ex. 409 : un tag de ce nom existe déjà
        }
    }

    private async deleteTag(tag: Tag): Promise<void> {
        const usage = tag.task_count > 0 ? ` Il sera enlevé de ${tag.task_count} tâche${tag.task_count > 1 ? 's' : ''}.` : ''
        if (!window.confirm(`Supprimer le tag « ${tag.name} » ?${usage}`)) return

        this.setState(state => ({ tags: state.tags.filter(t => t.id !== tag.id) }))
        try {
            await api.deleteTag(tag.id)
        } catch (error) {
            this.showError(error)
        }
    }

    // =========================================================================
    // Rendu
    // =========================================================================

    render(): React.ReactNode {
        return (
            <div className="SettingsDialog-backdrop">
                <div className="SettingsDialog" ref={this.dialogRef} role="dialog" aria-label="Paramètres">
                    <header className="settings-head">
                        <h2>Paramètres</h2>
                        <button className="icon-btn" onClick={this.props.onClose} aria-label="Fermer"><X size={16} /></button>
                    </header>

                    <div className="settings-body">
                        {this.state.error && (
                            <div className="settings-error">
                                {this.state.error}
                                <button onClick={() => this.setState({ error: '' })}>OK</button>
                            </div>
                        )}
                        {this.renderAccount()}
                        {this.renderLevels()}
                        {this.renderTags()}
                        {this.renderDefaultView()}
                    </div>

                    <footer className="settings-foot">
                        <button className="btn-ghost" onClick={this.props.onClose}>Fermer</button>
                    </footer>
                </div>
            </div>
        )
    }

    private renderAccount(): React.ReactNode {
        return (
            <section>
                <h3>Compte</h3>
                <p className="settings-desc">{this.props.user.email}</p>
                <label className="settings-field">
                    <span>Nom affiché</span>
                    <input
                        value={this.state.displayName}
                        onChange={event => this.setState({ displayName: event.target.value })}
                        onBlur={this.onDisplayNameBlur}
                        onKeyDown={event => { if (event.key === 'Enter') event.currentTarget.blur() }}
                    />
                </label>
            </section>
        )
    }

    private renderLevels(): React.ReactNode {
        const { levels, dragLevelId, dropIndex } = this.state

        // Comme pour les tâches : la ligne de dépôt est placée en sautant le niveau glissé
        const rows: React.ReactNode[] = []
        let visibleIndex = 0
        for (const level of levels) {
            if (level.id !== dragLevelId) {
                if (dropIndex === visibleIndex) rows.push(<div key="drop" className="drop-indicator" />)
                visibleIndex++
            }
            rows.push(this.renderLevel(level))
        }
        if (dropIndex === visibleIndex) rows.push(<div key="drop" className="drop-indicator" />)

        return (
            <section>
                <h3>Niveaux d'urgence</h3>
                <p className="settings-desc">Du moins urgent au plus urgent. Glisser pour réordonner.</p>
                <div className="settings-levels" onDragOver={this.onLevelsDragOver} onDrop={this.onLevelsDrop}>
                    {rows}
                </div>
                {this.state.addingLevel ? (
                    <InlineInput
                        placeholder="Nom du niveau"
                        onSubmit={async name => {
                            await this.createLevel(name)
                            this.setState({ addingLevel: false })
                        }}
                        onCancel={() => this.setState({ addingLevel: false })}
                    />
                ) : (
                    <button className="settings-add" onClick={() => this.setState({ addingLevel: true })}>
                        <Plus size={14} />Ajouter un niveau
                    </button>
                )}
            </section>
        )
    }

    private renderLevel(level: UrgencyLevel): React.ReactNode {
        return (
            <div
                key={level.id}
                className={level.id === this.state.dragLevelId ? 'settings-level dragging' : 'settings-level'}
                data-level-id={level.id}
                draggable
                onDragStart={event => this.onLevelDragStart(event, level)}
                onDragEnd={this.onLevelDragEnd}
            >
                <GripVertical size={16} className="settings-grip" />

                <div className="menu-anchor">
                    <button
                        className="settings-swatch"
                        style={{ background: level.color }}
                        aria-label={`Couleur de ${level.name}`}
                        onClick={() => this.setState({ colorPickerFor: level.id })}
                    />
                    {this.state.colorPickerFor === level.id && (
                        <>
                            <div className="menu-backdrop" onClick={() => this.setState({ colorPickerFor: null })} />
                            <div className="settings-palette">
                                {LIST_COLORS.map(color => (
                                    <button
                                        key={color}
                                        className="list-color"
                                        style={{ background: color }}
                                        aria-label={`Couleur ${color}`}
                                        onClick={() => {
                                            this.setState({ colorPickerFor: null })
                                            void this.updateLevel(level, { color })
                                        }}
                                    />
                                ))}
                            </div>
                        </>
                    )}
                </div>

                <input
                    className="settings-level-name"
                    defaultValue={level.name}
                    aria-label="Nom du niveau"
                    onBlur={event => {
                        const name = event.currentTarget.value.trim()
                        if (name === '') event.currentTarget.value = level.name
                        else if (name !== level.name) void this.updateLevel(level, { name })
                    }}
                    onKeyDown={event => { if (event.key === 'Enter') event.currentTarget.blur() }}
                />

                <button className="icon-btn" title={`Supprimer « ${level.name} »`} onClick={() => void this.deleteLevel(level)}>
                    <Trash2 size={15} />
                </button>
            </div>
        )
    }

    private renderTags(): React.ReactNode {
        const { tags, tagsLoaded } = this.state

        return (
            <section>
                <h3>Tags</h3>
                <p className="settings-desc">Partagés par toutes les listes. Cliquer sur un tag pour le renommer.</p>
                <div className="settings-tags">
                    {!tagsLoaded && <span className="settings-desc">Chargement…</span>}
                    {tags.map(tag => (this.state.renamingTagId === tag.id ? (
                        <span key={tag.id} className="settings-tag-input">
                            <InlineInput
                                placeholder="Nom du tag"
                                initialValue={tag.name}
                                onSubmit={name => this.renameTag(tag, name.replace(/^#/, ''))}
                                onCancel={() => this.setState({ renamingTagId: null })}
                            />
                        </span>
                    ) : (
                        <span key={tag.id} className="pop-tag" title={`${tag.task_count} tâche(s)`}>
                            <button className="settings-tag-name" onClick={() => this.setState({ renamingTagId: tag.id })}>
                                #{tag.name}
                            </button>
                            <button onClick={() => void this.deleteTag(tag)} aria-label={`Supprimer ${tag.name}`}>
                                <X size={11} />
                            </button>
                        </span>
                    )))}

                    {this.state.addingTag ? (
                        <span className="settings-tag-input">
                            <InlineInput
                                placeholder="Nouveau tag"
                                keepOpen
                                onSubmit={name => this.createTag(name.replace(/^#/, ''))}
                                onCancel={() => this.setState({ addingTag: false })}
                            />
                        </span>
                    ) : (
                        <button className="pop-tag-add" onClick={() => this.setState({ addingTag: true })}>
                            <Plus size={11} />Nouveau tag
                        </button>
                    )}
                </div>
            </section>
        )
    }

    private renderDefaultView(): React.ReactNode {
        const view = this.props.user.default_view
        return (
            <section>
                <h3>Affichage par défaut</h3>
                <p className="settings-desc">Vue utilisée quand on crée une nouvelle liste.</p>
                <div className="header-views settings-views">
                    <button className={view === 'columns' ? 'on' : ''} onClick={() => this.onDefaultViewClick('columns')}>
                        <Columns3 size={14} />Colonnes
                    </button>
                    <button className={view === 'sections' ? 'on' : ''} onClick={() => this.onDefaultViewClick('sections')}>
                        <Rows2 size={14} />Sections
                    </button>
                </div>
            </section>
        )
    }
}
