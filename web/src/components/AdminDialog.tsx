import * as React from 'react'
import { KeyRound, Plus, ShieldUser, Trash2, X } from 'lucide-react'
import { api, ApiError } from '../api/ApiClient'
import type { User } from '../api/types'
import { DismissListener } from '../utils/popover'
import { confirmDialog } from './confirm'
import { InlineInput } from './InlineInput'
import './SettingsDialog.css' // Même cadre que les Paramètres (.SettingsDialog, .settings-…)
import './AdminDialog.css'

interface AdminDialogProps {
    user: User // L'admin connecté
    onClose: () => void
}

class AdminDialogState {
    users: User[] = []
    loaded: boolean = false
    email: string = ''           // Formulaire « Nouveau compte »
    displayName: string = ''
    password: string = ''
    creating: boolean = false    // Bouton désactivé pendant l'appel à l'API
    resettingId: string | null = null // Compte dont on saisit le nouveau mot de passe
    error: string = ''
}

/**
 * Console admin : la liste des comptes, en créer, en supprimer, réinitialiser un mot de passe.
 * Visible seulement pour l'admin (le premier compte créé).
 *
 * Comme SettingsDialog, c'est un écran à part qui charge et modifie ses propres données.
 * Rien à prévenir côté Workspace : les autres comptes n'apparaissent pas dans ses listes.
 */
export class AdminDialog extends React.Component<AdminDialogProps, AdminDialogState> {
    private dialogRef = React.createRef<HTMLDivElement>()
    private dismiss = new DismissListener(this.dialogRef, () => this.props.onClose())

    constructor(props: AdminDialogProps) {
        super(props)
        this.state = new AdminDialogState()
    }

    async componentDidMount(): Promise<void> {
        this.dismiss.start()
        try {
            this.setState({ users: await api.getUsers(), loaded: true })
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

    onCreateSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault() // Empêche le navigateur de recharger la page

        const { email, displayName, password } = this.state
        this.setState({ creating: true, error: '' })
        try {
            const created = await api.createUser({ email, password, display_name: displayName })
            this.setState(state => ({
                users: [...state.users, created],
                email: '', displayName: '', password: '', creating: false,
            }))
        } catch (error) {
            this.showError(error)
            this.setState({ creating: false })
        }
    }

    private async resetPassword(user: User, password: string): Promise<void> {
        try {
            await api.resetPassword(user.id, password)
            this.setState({ resettingId: null, error: '' })
            await confirmDialog.inform(`Mot de passe de ${user.display_name} changé. Ses sessions ouvertes sont fermées.`)
        } catch (error) {
            this.showError(error) // Le champ reste ouvert pour corriger
        }
    }

    private async deleteUser(user: User): Promise<void> {
        const message = `Supprimer le compte de ${user.display_name} (${user.email}) ? `
            + 'Toutes ses listes et tâches seront supprimées, sans retour possible.'
        if (!(await confirmDialog.ask(message, { confirmLabel: 'Supprimer', danger: true }))) return

        try {
            await api.deleteUser(user.id)
            this.setState(state => ({ users: state.users.filter(u => u.id !== user.id) }))
        } catch (error) {
            this.showError(error)
        }
    }

    render(): React.ReactNode {
        return (
            <div className="SettingsDialog-backdrop">
                <div className="SettingsDialog AdminDialog" ref={this.dialogRef} role="dialog" aria-label="Console admin">
                    <header className="settings-head">
                        <h2>Console admin</h2>
                        <button className="icon-btn" onClick={this.props.onClose} aria-label="Fermer"><X size={16} /></button>
                    </header>
                    <div className="settings-body">
                        {this.state.error && (
                            <div className="settings-error">
                                {this.state.error}
                                <button onClick={() => this.setState({ error: '' })}>OK</button>
                            </div>
                        )}
                        {this.renderUsers()}
                        {this.renderNewUser()}
                    </div>
                    <footer className="settings-foot">
                        <button className="btn-ghost" onClick={this.props.onClose}>Fermer</button>
                    </footer>
                </div>
            </div>
        )
    }

    private renderUsers(): React.ReactNode {
        return (
            <section>
                <h3>Comptes</h3>
                <p className="settings-desc">Chaque compte a ses propres listes, tags et niveaux d'urgence.</p>
                {!this.state.loaded && <span className="settings-desc">Chargement…</span>}
                <div className="admin-users">
                    {this.state.users.map(user => this.renderUser(user))}
                </div>
            </section>
        )
    }

    private renderUser(user: User): React.ReactNode {
        const isMe = user.id === this.props.user.id

        if (this.state.resettingId === user.id) {
            return (
                <div key={user.id} className="admin-user">
                    <KeyRound size={16} className="admin-user-icon" />
                    <InlineInput
                        className="admin-reset-input"
                        placeholder={`Nouveau mot de passe de ${user.display_name} (8 caractères min.)`}
                        onSubmit={password => this.resetPassword(user, password)}
                        onCancel={() => this.setState({ resettingId: null })}
                    />
                </div>
            )
        }

        return (
            <div key={user.id} className="admin-user">
                {user.is_admin && <span title="Administrateur"><ShieldUser size={16} className="admin-user-icon" /></span>}
                <div className="admin-user-text">
                    <span className="admin-user-name">{user.display_name}{isMe && ' (toi)'}</span>
                    <span className="admin-user-email">{user.email}</span>
                </div>
                <button
                    className="icon-btn"
                    title={`Changer le mot de passe de ${user.display_name}`}
                    onClick={() => this.setState({ resettingId: user.id, error: '' })}
                >
                    <KeyRound size={15} />
                </button>
                <button
                    className="icon-btn"
                    title={isMe ? 'Tu ne peux pas supprimer ton propre compte' : `Supprimer ${user.display_name}`}
                    disabled={isMe}
                    onClick={() => void this.deleteUser(user)}
                >
                    <Trash2 size={15} />
                </button>
            </div>
        )
    }

    private renderNewUser(): React.ReactNode {
        return (
            <section>
                <h3>Nouveau compte</h3>
                <p className="settings-desc">Donne ensuite l'email et le mot de passe à la personne.</p>
                <form className="admin-form" onSubmit={this.onCreateSubmit}>
                    <label className="settings-field">
                        <span>Nom affiché</span>
                        <input
                            value={this.state.displayName}
                            onChange={event => this.setState({ displayName: event.target.value })}
                            required
                        />
                    </label>
                    <label className="settings-field">
                        <span>Email</span>
                        <input
                            type="email"
                            value={this.state.email}
                            onChange={event => this.setState({ email: event.target.value })}
                            autoComplete="off"
                            required
                        />
                    </label>
                    <label className="settings-field">
                        <span>Mot de passe</span>
                        <input
                            type="text"
                            value={this.state.password}
                            onChange={event => this.setState({ password: event.target.value })}
                            autoComplete="new-password"
                            minLength={8}
                            required
                        />
                    </label>
                    <button type="submit" className="btn-primary admin-create" disabled={this.state.creating}>
                        <Plus size={14} />Créer le compte
                    </button>
                </form>
            </section>
        )
    }
}
