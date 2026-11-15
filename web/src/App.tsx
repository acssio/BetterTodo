import * as React from 'react'
import { api, ApiError } from './api/ApiClient'
import type { User } from './api/types'
import { ChangePasswordPage } from './components/ChangePasswordPage'
import { ConfirmHost } from './components/ConfirmDialog'
import { LoginPage } from './components/LoginPage'
import { Workspace } from './components/Workspace'

/**
 * Machine d'état de l'application :
 *
 *   loading ──(session valide)─────────────────► loggedIn
 *      │                                          ▲  │
 *      ├──(aucun compte)──► setup ────────────────┘  │ (compte admin créé)
 *      │                                          │  │
 *      └──(pas de session)──► loggedOut ──────────┘  │ (connexion)
 *                                 ▲                  │
 *                                 └──────────────────┘ (déconnexion ou session expirée)
 *
 * Entre la session et loggedIn : si user.must_change_password (mot de passe donné
 * par l'admin), on passe d'abord par mustChangePassword, qui mène à loggedIn une fois
 * le nouveau mot de passe choisi (ou à loggedOut avec « Se déconnecter »).
 */
type AppStatus = 'loading' | 'setup' | 'loggedOut' | 'mustChangePassword' | 'loggedIn'

class AppState {
    status: AppStatus = 'loading'
    user: User | null = null
    error: string = '' // Erreur au démarrage (serveur injoignable…)
}

export class App extends React.Component<object, AppState> {
    constructor(props: object) {
        super(props)
        this.state = new AppState()
    }

    async componentDidMount(): Promise<void> {
        // Le cookie de session est-il encore valide ?
        try {
            this.onLoggedIn(await api.me())
        } catch (error) {
            if (error instanceof ApiError && error.status === 401) {
                await this.showLoginOrSetup()
            } else {
                this.setState({ status: 'loggedOut', error: 'Impossible de joindre le serveur.' })
            }
        }
    }

    /** Pas de session : premier lancement (aucun compte) → setup, sinon → connexion. */
    private async showLoginOrSetup(): Promise<void> {
        try {
            this.setState({ status: (await api.needsSetup()) ? 'setup' : 'loggedOut' })
        } catch {
            this.setState({ status: 'loggedOut', error: 'Impossible de joindre le serveur.' })
        }
    }

    onLoggedIn = (user: User): void => {
        const status = user.must_change_password ? 'mustChangePassword' : 'loggedIn'
        this.setState({ status, user, error: '' })
    }

    /** Nouveau mot de passe choisi sur l'écran obligatoire : on ouvre les listes. */
    onPasswordChanged = (user: User): void => {
        this.setState({ status: 'loggedIn', user })
    }

    onLogout = async (): Promise<void> => {
        try {
            await api.logout()
        } finally {
            this.setState({ status: 'loggedOut', user: null })
        }
    }

    /** Nom affiché ou vue par défaut changés dans les Paramètres. */
    onUserChanged = (user: User): void => {
        this.setState({ user })
    }

    onSessionExpired = (): void => {
        this.setState({ status: 'loggedOut', user: null, error: 'Ta session a expiré, reconnecte-toi.' })
    }

    render(): React.ReactNode {
        switch (this.state.status) {
            case 'loading':
                return null // Très court : le temps d'un appel à /me

            case 'setup':
                return <LoginPage setup={true} onLoggedIn={this.onLoggedIn} initialError={this.state.error} />

            case 'loggedOut':
                return <LoginPage setup={false} onLoggedIn={this.onLoggedIn} initialError={this.state.error} />

            case 'mustChangePassword':
                return (
                    <ChangePasswordPage
                        user={this.state.user!}
                        onPasswordChanged={this.onPasswordChanged}
                        onLogout={this.onLogout}
                    />
                )

            case 'loggedIn':
                return (
                    <>
                        <Workspace
                            user={this.state.user!}
                            onLogout={this.onLogout}
                            onUserChanged={this.onUserChanged}
                            onSessionExpired={this.onSessionExpired}
                        />
                        <ConfirmHost />
                    </>
                )
        }
    }
}
