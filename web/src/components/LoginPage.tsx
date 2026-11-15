import * as React from 'react'
import { Check } from 'lucide-react'
import { api, ApiError } from '../api/ApiClient'
import type { User } from '../api/types'
import './LoginPage.css'

interface LoginPageProps {
    onLoggedIn: (user: User) => void
    initialError: string
}

type LoginMode = 'login' | 'register'

class LoginPageState {
    mode: LoginMode = 'login'
    email: string = ''
    password: string = ''
    displayName: string = ''
    error: string = ''
    submitting: boolean = false // Bouton désactivé pendant l'appel à l'API
}

/** Écran de connexion, qui bascule en inscription avec le lien du bas. */
export class LoginPage extends React.Component<LoginPageProps, LoginPageState> {
    constructor(props: LoginPageProps) {
        super(props)
        this.state = { ...new LoginPageState(), error: props.initialError }
    }

    onSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault() // Empêche le navigateur de recharger la page

        this.setState({ submitting: true, error: '' })
        try {
            const session = this.state.mode === 'login'
                ? await api.login(this.state.email, this.state.password)
                : await api.register(this.state.email, this.state.password, this.state.displayName)
            this.props.onLoggedIn(session.user)
        } catch (error) {
            const message = error instanceof ApiError ? error.message : 'Impossible de joindre le serveur.'
            this.setState({ error: message, submitting: false })
        }
    }

    onToggleMode = (): void => {
        this.setState({ mode: this.state.mode === 'login' ? 'register' : 'login', error: '' })
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
        const isRegister = this.state.mode === 'register'

        return (
            <div className="LoginPage">
                <form className="login-card" onSubmit={this.onSubmit}>
                    <div className="login-brand">
                        <span className="login-logo"><Check size={18} color="#FFFFFF" strokeWidth={3} /></span>
                        BetterTodo
                    </div>

                    <h1 className="login-title">{isRegister ? 'Créer un compte' : 'Connexion'}</h1>

                    {isRegister && (
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
                            autoComplete={isRegister ? 'new-password' : 'current-password'}
                            minLength={isRegister ? 8 : undefined}
                            required
                        />
                    </label>

                    {this.state.error && <div className="login-error">{this.state.error}</div>}

                    <button type="submit" className="btn-primary login-submit" disabled={this.state.submitting}>
                        {isRegister ? 'Créer le compte' : 'Se connecter'}
                    </button>

                    <button type="button" className="login-switch" onClick={this.onToggleMode}>
                        {isRegister ? "J'ai déjà un compte" : 'Pas encore de compte ? En créer un'}
                    </button>
                </form>
            </div>
        )
    }
}
