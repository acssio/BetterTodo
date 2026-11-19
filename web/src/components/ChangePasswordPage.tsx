import * as React from 'react'
import { Check } from 'lucide-react'
import { api, ApiError } from '../api/ApiClient'
import type { User } from '../api/types'
import './LoginPage.css' // Même carte que l'écran de connexion
import './ChangePasswordPage.css'

interface ChangePasswordPageProps {
    user: User
    onPasswordChanged: (user: User) => void
    onLogout: () => void
}

class ChangePasswordPageState {
    currentPassword: string = ''
    newPassword: string = ''
    confirmPassword: string = ''
    error: string = ''
    submitting: boolean = false // Bouton désactivé pendant l'appel à l'API
}

/**
 * Écran obligatoire à la première connexion d'un compte créé par l'admin (ou après
 * un changement de mot de passe par l'admin) : la personne choisit son propre mot de passe.
 */
export class ChangePasswordPage extends React.Component<ChangePasswordPageProps, ChangePasswordPageState> {
    constructor(props: ChangePasswordPageProps) {
        super(props)
        this.state = new ChangePasswordPageState()
    }

    onSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault() // Empêche le navigateur de recharger la page

        const { currentPassword, newPassword, confirmPassword } = this.state
        if (newPassword !== confirmPassword) {
            this.setState({ error: 'Les deux nouveaux mots de passe ne sont pas identiques.' })
            return
        }

        this.setState({ submitting: true, error: '' })
        try {
            this.props.onPasswordChanged(await api.changePassword(currentPassword, newPassword))
        } catch (error) {
            const message = error instanceof ApiError ? error.message : 'Impossible de joindre le serveur.'
            this.setState({ error: message, submitting: false })
        }
    }

    render(): React.ReactNode {
        return (
            <div className="LoginPage">
                <form className="login-card" onSubmit={this.onSubmit}>
                    <div className="login-brand">
                        <span className="login-logo"><Check size={18} strokeWidth={3} /></span>
                        BetterTodo
                    </div>

                    <h1 className="login-title">Choisis ton mot de passe</h1>
                    <p className="login-hint">
                        Bienvenue {this.props.user.display_name} ! Ton mot de passe a été donné par l'administrateur :
                        remplace-le par un mot de passe personnel.
                    </p>

                    <label className="login-field">
                        <span>Mot de passe provisoire</span>
                        <input
                            type="password"
                            value={this.state.currentPassword}
                            onChange={event => this.setState({ currentPassword: event.target.value })}
                            autoComplete="current-password"
                            required
                            autoFocus
                        />
                    </label>

                    <label className="login-field">
                        <span>Nouveau mot de passe</span>
                        <input
                            type="password"
                            value={this.state.newPassword}
                            onChange={event => this.setState({ newPassword: event.target.value })}
                            autoComplete="new-password"
                            minLength={8}
                            required
                        />
                    </label>

                    <label className="login-field">
                        <span>Confirmer le nouveau mot de passe</span>
                        <input
                            type="password"
                            value={this.state.confirmPassword}
                            onChange={event => this.setState({ confirmPassword: event.target.value })}
                            autoComplete="new-password"
                            minLength={8}
                            required
                        />
                    </label>

                    {this.state.error && <div className="login-error">{this.state.error}</div>}

                    <button type="submit" className="btn-primary login-submit" disabled={this.state.submitting}>
                        Enregistrer et continuer
                    </button>

                    <button type="button" className="change-password-logout" onClick={this.props.onLogout}>
                        Se déconnecter
                    </button>
                </form>
            </div>
        )
    }
}
