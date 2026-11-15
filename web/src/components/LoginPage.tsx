import * as React from 'react'
import { Check } from 'lucide-react'
import { api, ApiError } from '../api/ApiClient'
import type { User } from '../api/types'
import './LoginPage.css'

interface LoginPageProps {
    setup: boolean // true = premier lancement : on crée le compte administrateur
    onLoggedIn: (user: User) => void
    initialError: string
}

class LoginPageState {
    email: string = ''
    password: string = ''
    displayName: string = ''
    error: string = ''
    submitting: boolean = false // Bouton désactivé pendant l'appel à l'API
}

/**
 * Écran de connexion. Au premier lancement (aucun compte), il sert à créer le compte
 * administrateur, comme sur Portainer. Les comptes suivants sont créés dans la console admin.
 */
export class LoginPage extends React.Component<LoginPageProps, LoginPageState> {
    constructor(props: LoginPageProps) {
        super(props)
        this.state = { ...new LoginPageState(), error: props.initialError }
    }

    onSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault() // Empêche le navigateur de recharger la page

        this.setState({ submitting: true, error: '' })
        try {
            const { email, password, displayName } = this.state
            const session = this.props.setup
                ? await api.setup({ email, password, display_name: displayName })
                : await api.login(email, password)
            this.props.onLoggedIn(session.user)
        } catch (error) {
            const message = error instanceof ApiError ? error.message : 'Impossible de joindre le serveur.'
            this.setState({ error: message, submitting: false })
        }
    }

    onEmailChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ email: event.target.value })
    }

    onPasswordChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ password: event.target.value })
    }

    onDisplayNameChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ displayName: event.target.value })
    }

    render(): React.ReactNode {
        const isSetup = this.props.setup

        return (
            <div className="LoginPage">
                <form className="login-card" onSubmit={this.onSubmit}>
                    <div className="login-brand">
                        <span className="login-logo"><Check size={18} color="#FFFFFF" strokeWidth={3} /></span>
                        BetterTodo
                    </div>

                    <h1 className="login-title">{isSetup ? 'Créer le compte administrateur' : 'Connexion'}</h1>
                    {isSetup && (
                        <p className="login-hint">
                            Premier lancement : ce compte pourra ensuite créer et supprimer les autres comptes.
                        </p>
                    )}

                    {isSetup && (
                        <label className="login-field">
                            <span>Nom affiché</span>
                            <input
                                type="text"
                                value={this.state.displayName}
                                onChange={this.onDisplayNameChanged}
                                autoComplete="name"
                                required
                            />
                        </label>
                    )}

                    <label className="login-field">
                        <span>Email</span>
                        <input
                            type="email"
                            value={this.state.email}
                            onChange={this.onEmailChanged}
                            autoComplete="email"
                            required
                            autoFocus
                        />
                    </label>

                    <label className="login-field">
                        <span>Mot de passe</span>
                        <input
                            type="password"
                            value={this.state.password}
                            onChange={this.onPasswordChanged}
                            autoComplete={isSetup ? 'new-password' : 'current-password'}
                            minLength={isSetup ? 8 : undefined}
                            required
                        />
                    </label>

                    {this.state.error && <div className="login-error">{this.state.error}</div>}

                    <button type="submit" className="btn-primary login-submit" disabled={this.state.submitting}>
                        {isSetup ? 'Créer le compte' : 'Se connecter'}
                    </button>
                </form>
            </div>
        )
    }
}
