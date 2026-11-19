import * as React from 'react'
import { Check, Trash2, X } from 'lucide-react'
import type { SmartList, SmartListChanges, Tag, UrgencyLevel } from '../api/types'
import { canSaveSmartList } from '../utils/smartLists'
import { confirmDialog } from './confirm'
import { ListIcon } from './ListIcon'
import { LIST_COLORS } from './listIcons'
import { ListLookPicker } from './ListLookPicker'
import { UrgencyPill } from './TaskCard'
import './TaskPopover.css'     // .pop-field, .pop-label, .pop-input
import './SettingsDialog.css'  // .SettingsDialog, .settings-head / body / foot, .settings-level
import './SmartListDialog.css'

interface SmartListDialogProps {
    list: SmartList | null   // null = nouvelle liste
    tags: Tag[]              // Tags du compte, à choisir
    urgencyLevels: UrgencyLevel[]
    onSave: (changes: SmartListChanges) => Promise<void> // Rejetée en cas d'erreur (message affiché ici)
    onDelete: () => Promise<void>
    onClose: () => void
}

type Kind = 'tags' | 'urgency'

class SmartListDialogState {
    name: string = ''
    color: string = LIST_COLORS[5]      // Violet, comme la maquette
    icon: string = 'tag'
    kind: Kind = 'tags'
    tagMatch: 'any' | 'all' = 'any'
    tagIds: string[] = []
    urgencyIds: string[] = []
    lookOpen: boolean = false           // Couleur et icône affichées (clic sur l'icône)
    busy: boolean = false               // Enregistrement en cours
    error: string = ''
}

/**
 * Fenêtre « Nouvelle liste intelligente » / « Modifier la liste intelligente » (maquette v3, écrans 5 et 6) :
 * nom, couleur et icône, puis les tâches à afficher, par tags (au moins un / tous) ou par urgence.
 *
 * Ne parle pas à l'API : SettingsDialog enregistre (onSave, onDelete).
 * S'affiche par-dessus les Paramètres : data-keep-popovers évite qu'un clic ici ne les ferme,
 * et Échap ne ferme que cette fenêtre (preventDefault, voir DismissListener).
 */
export class SmartListDialog extends React.Component<SmartListDialogProps, SmartListDialogState> {
    constructor(props: SmartListDialogProps) {
        super(props)
        const list = props.list
        this.state = list
            ? {
                ...new SmartListDialogState(),
                name: list.name, color: list.color, icon: list.icon, kind: list.kind, tagMatch: list.tag_match,
                tagIds: list.tag_ids, urgencyIds: list.urgency_ids,
            }
            : new SmartListDialogState()
    }

    // --- Événements -------------------------------------------------------------

    onKeyDown = (event: React.KeyboardEvent<HTMLFormElement>): void => {
        if (event.key === 'Escape') {
            event.preventDefault() // Les Paramètres, derrière, restent ouverts
            this.props.onClose()
        }
    }

    onToggleTag = (id: string): void => {
        this.setState(state => ({
            tagIds: state.tagIds.includes(id) ? state.tagIds.filter(t => t !== id) : [...state.tagIds, id],
        }))
    }

    onToggleLevel = (id: string): void => {
        this.setState(state => ({
            urgencyIds: state.urgencyIds.includes(id) ? state.urgencyIds.filter(u => u !== id) : [...state.urgencyIds, id],
        }))
    }

    onSubmit = async (event: React.FormEvent): Promise<void> => {
        event.preventDefault()
        const { name, color, icon, kind, tagMatch, tagIds, urgencyIds } = this.state
        if (!canSaveSmartList(name, kind, tagIds, urgencyIds) || this.state.busy) return

        // On n'envoie que les ids du type choisi (une liste vide serait refusée par l'API)
        const changes: SmartListChanges = { name: name.trim(), color, icon, kind, tag_match: tagMatch }
        if (kind === 'tags') changes.tag_ids = tagIds
        else changes.urgency_ids = urgencyIds

        this.setState({ busy: true, error: '' })
        try {
            await this.props.onSave(changes) // La fenêtre est fermée par SettingsDialog si tout va bien
        } catch (error) {
            this.setState({ busy: false, error: error instanceof Error ? error.message : String(error) })
        }
    }

    onDeleteClick = async (): Promise<void> => {
        const list = this.props.list
        if (!list) return
        if (await confirmDialog.ask(`Supprimer la liste intelligente « ${list.name} » ? Les tâches ne sont pas supprimées.`)) {
            await this.props.onDelete()
        }
    }

    // --- Rendu ------------------------------------------------------------------

    render(): React.ReactNode {
        const { list } = this.props
        const { name, color, icon, kind, tagIds, urgencyIds, busy } = this.state
        const canSave = canSaveSmartList(name, kind, tagIds, urgencyIds)

        return (
            <div className="SettingsDialog-backdrop SmartListDialog-backdrop" data-keep-popovers
                onMouseDown={event => { if (event.target === event.currentTarget) this.props.onClose() }}>
                <form
                    className="SettingsDialog SmartListDialog"
                    role="dialog"
                    aria-label={list ? 'Modifier la liste intelligente' : 'Nouvelle liste intelligente'}
                    onKeyDown={this.onKeyDown}
                    onSubmit={this.onSubmit}
                >
                    <header className="settings-head">
                        <h2>{list ? 'Modifier la liste intelligente' : 'Nouvelle liste intelligente'}</h2>
                        <button type="button" className="icon-btn" onClick={this.props.onClose} aria-label="Fermer"><X size={16} /></button>
                    </header>

                    <div className="settings-body smart-body">
                        {this.state.error && <div className="settings-error">{this.state.error}</div>}

                        <div className="smart-name-row">
                            <button
                                type="button"
                                className="smart-look-btn"
                                title="Couleur et icône"
                                onClick={() => this.setState(state => ({ lookOpen: !state.lookOpen }))}
                            >
                                <ListIcon icon={icon} color={color} size={18} boxed />
                            </button>
                            <label className="pop-field">
                                <span className="pop-label">Nom</span>
                                <input
                                    className="pop-input"
                                    value={name}
                                    autoFocus
                                    placeholder="Ex. Travail, Urgent…"
                                    onChange={event => this.setState({ name: event.target.value })}
                                />
                            </label>
                        </div>
                        {this.state.lookOpen ? (
                            <ListLookPicker
                                color={color}
                                icon={icon}
                                onColor={c => this.setState({ color: c })}
                                onIcon={i => this.setState({ icon: i })}
                            />
                        ) : (
                            <p className="settings-desc smart-hint">Couleur et icône : clic sur l'icône.</p>
                        )}

                        <div className="pop-field">
                            <span className="pop-label">Afficher les tâches</span>
                            <div className="header-views settings-views">
                                <button type="button" className={kind === 'tags' ? 'on' : ''} onClick={() => this.setState({ kind: 'tags' })}>
                                    Par tags
                                </button>
                                <button type="button" className={kind === 'urgency' ? 'on' : ''} onClick={() => this.setState({ kind: 'urgency' })}>
                                    Par urgence
                                </button>
                            </div>
                        </div>

                        {kind === 'tags' ? this.renderTags() : this.renderLevels()}
                    </div>

                    <footer className="settings-foot smart-foot">
                        {list && (
                            <button type="button" className="pop-delete smart-delete" onClick={this.onDeleteClick}>
                                <Trash2 size={14} />Supprimer
                            </button>
                        )}
                        <span className="settings-desc smart-count">
                            {list ? `${list.open_tasks} ${list.open_tasks > 1 ? 'tâches à faire' : 'tâche à faire'}` : ''}
                        </span>
                        <button type="button" className="btn-ghost" onClick={this.props.onClose}>Annuler</button>
                        <button type="submit" className="btn-primary" disabled={!canSave || busy}>
                            {list ? 'Enregistrer' : 'Créer la liste'}
                        </button>
                    </footer>
                </form>
            </div>
        )
    }

    private renderTags(): React.ReactNode {
        const { tagIds, tagMatch } = this.state
        if (this.props.tags.length === 0) {
            return <p className="settings-desc">Aucun tag pour l'instant : ajoute des tags à tes tâches d'abord.</p>
        }
        return (
            <>
                <div className="pop-field">
                    <span className="pop-label">Tags</span>
                    <div className="smart-chips">
                        {this.props.tags.map(tag => (
                            <button
                                key={tag.id}
                                type="button"
                                className={tagIds.includes(tag.id) ? 'smart-chip on' : 'smart-chip'}
                                aria-pressed={tagIds.includes(tag.id)}
                                onClick={() => this.onToggleTag(tag.id)}
                            >
                                #{tag.name}
                            </button>
                        ))}
                    </div>
                </div>
                <div className="pop-field">
                    <span className="pop-label">Une tâche doit avoir</span>
                    <div className="header-views settings-views">
                        <button type="button" className={tagMatch === 'any' ? 'on' : ''} onClick={() => this.setState({ tagMatch: 'any' })}>
                            Au moins un de ces tags
                        </button>
                        <button type="button" className={tagMatch === 'all' ? 'on' : ''} onClick={() => this.setState({ tagMatch: 'all' })}>
                            Tous ces tags
                        </button>
                    </div>
                </div>
            </>
        )
    }

    private renderLevels(): React.ReactNode {
        // Du plus urgent au moins urgent (rank 0 = le moins urgent)
        const levels = [...this.props.urgencyLevels].sort((a, b) => b.rank - a.rank)
        return (
            <div className="pop-field">
                <span className="pop-label">Niveaux affichés</span>
                <div className="smart-levels">
                    {levels.map(level => {
                        const on = this.state.urgencyIds.includes(level.id)
                        return (
                            <button
                                key={level.id}
                                type="button"
                                className="settings-level smart-level"
                                aria-pressed={on}
                                onClick={() => this.onToggleLevel(level.id)}
                            >
                                <span className={on ? 'smart-check on' : 'smart-check'}>{on && <Check size={12} strokeWidth={3} />}</span>
                                <UrgencyPill urgency={level} />
                            </button>
                        )
                    })}
                </div>
                <p className="settings-desc">Une section par niveau, du plus urgent au moins urgent, puis par échéance.</p>
            </div>
        )
    }
}
