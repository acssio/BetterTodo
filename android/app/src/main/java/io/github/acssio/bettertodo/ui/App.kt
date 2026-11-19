package io.github.acssio.bettertodo.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.ServerUrl
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.data.MODE_LOCAL
import io.github.acssio.bettertodo.data.SessionStore
import io.github.acssio.bettertodo.local.LocalApi
import io.github.acssio.bettertodo.local.LocalDatabase
import io.github.acssio.bettertodo.sync.SyncEngine
import kotlinx.coroutines.launch

/**
 * L'écran affiché : la machine d'état de l'app.
 *
 *   Loading ──(premier lancement)──────→ Welcome ──(Commencer)──────────→ Home sans serveur
 *      │                                     └──(J'ai un serveur)──→ Login
 *      │ ──(mode sans serveur)─────────→ Home sans serveur
 *      │ ──(mode serveur, jeton gardé)──→ Home avec serveur (tout de suite, même hors ligne)
 *      └──(mode serveur, pas de jeton)──→ Login
 *   Login ──(connexion)──────────────────→ Home avec serveur (ou d'abord MustChangePassword)
 *        ──(Utiliser sans serveur)───────→ Home sans serveur
 *   MustChangePassword ──(nouveau mot de passe)──→ Home avec serveur
 *   Home sans serveur ──(Paramètres › Se connecter à un serveur)──→ Login
 *   Home avec serveur ──(déconnexion, ou session expirée pendant une synchro)──→ Login
 *
 * Depuis la 8-5, les écrans lisent et écrivent TOUJOURS la base du téléphone (LocalApi).
 * Avec un serveur, SyncEngine les synchronise en plus : l'app marche donc aussi hors ligne.
 */
sealed class Screen {
    /** Au lancement : on lit le mode et le jeton enregistrés. */
    data object Loading : Screen()

    /** Tout premier lancement : sans serveur, ou se connecter à un serveur. */
    data object Welcome : Screen()

    /** @param message affiché au-dessus du formulaire (ex. « Session expirée »), ou null */
    data class Login(val serverUrl: String, val message: String? = null) : Screen()

    /** Connecté, mais le mot de passe provisoire doit d'abord être changé. */
    data class MustChangePassword(val api: ApiClient, val user: User, val token: String) : Screen()

    /**
     * Les listes (toujours dans la base du téléphone).
     * @param server le serveur relié (synchronisation, mot de passe), ou null sans serveur
     * @param sessionKey change à chaque connexion : un nouveau HomeViewModel est créé
     */
    data class Home(val user: User, val server: ApiClient?, val sessionKey: String) : Screen()
}

/**
 * État de toute l'app : l'écran affiché, la connexion et la synchronisation (comme App.tsx dans la page web).
 * Un ViewModel survit à la rotation de l'écran : rien n'est rechargé quand on tourne le téléphone.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private val db = LocalDatabase.get(application)

    /** Les données, toujours dans la base du téléphone. */
    val local = LocalApi(db)

    /** La synchronisation avec le serveur (inactive sans serveur). */
    val sync = SyncEngine(db, local, viewModelScope).also { engine ->
        engine.onExpired = { onSessionExpired() }
    }

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set

    // État du formulaire de connexion (les champs eux-mêmes sont dans LoginScreen)
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    // État du formulaire « Choisis ton mot de passe » (écran obligatoire)
    var passwordBusy by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set

    init {
        start()
    }

    /** Au lancement : le mode enregistré. Avec un serveur, les listes s'ouvrent sans attendre le réseau. */
    private fun start() {
        viewModelScope.launch {
            val saved = store.load()
            when {
                saved.mode == "" -> screen = Screen.Welcome
                saved.mode == MODE_LOCAL -> openLocal()
                saved.token == null || saved.serverUrl.isEmpty() -> screen = Screen.Login(saved.serverUrl)
                else -> {
                    val server = ApiClient(saved.serverUrl, saved.token)
                    openServer(server, saved.token)
                    checkPasswordToChange(server, saved.token)
                }
            }
        }
    }

    /**
     * Mot de passe provisoire pas encore changé (l'app a été fermée avant) : on le demande,
     * comme ChangePasswordPage sur le site. En arrière-plan : hors ligne, les listes restent ouvertes.
     */
    private suspend fun checkPasswordToChange(server: ApiClient, token: String) {
        try {
            val user = server.me()
            if (user.mustChangePassword) screen = Screen.MustChangePassword(server, user, token)
        } catch (e: ApiError) {
            // Hors ligne, ou session expirée (la synchronisation s'en occupe)
        }
    }

    /** Bouton « Se connecter ». */
    fun login(serverInput: String, email: String, password: String) {
        val serverUrl = ServerUrl.normalize(serverInput)
        if (serverUrl.isEmpty() || email.isBlank() || password.isEmpty()) {
            loginError = "Remplis les trois champs"
            return
        }

        loginBusy = true
        loginError = null
        viewModelScope.launch {
            val api = ApiClient(serverUrl)
            try {
                val session = api.login(email.trim(), password)
                store.save(serverUrl, session.token)
                sync.useAccount(session.user.id) // Un autre compte qu'avant : on repart de zéro
                val connected = ApiClient(serverUrl, session.token)
                if (session.user.mustChangePassword) {
                    screen = Screen.MustChangePassword(connected, session.user, session.token)
                } else {
                    openServer(connected, session.token)
                }
            } catch (e: ApiError) {
                loginError = loginErrorMessage(api, e)
            }
            loginBusy = false
        }
    }

    /** Les listes du téléphone, synchronisées avec ce serveur. */
    private suspend fun openServer(server: ApiClient, token: String) {
        sync.start(server)
        screen = Screen.Home(local.me(), server, sessionKey = token)
    }

    /** « Commencer » (premier lancement) ou « Utiliser l'app sans serveur » (connexion). */
    fun useLocal() {
        viewModelScope.launch {
            sync.stop()
            store.useLocal()
            openLocal()
        }
    }

    /** Les listes du téléphone, sans serveur. */
    private suspend fun openLocal() {
        // Clé neuve à chaque ouverture : sinon on retrouvait l'accueil d'avant la connexion (page Paramètres ouverte)
        screen = Screen.Home(local.me(), server = null, sessionKey = "$MODE_LOCAL-${System.nanoTime()}")
    }

    /** « J'ai un serveur » (premier lancement) ou Paramètres › Se connecter à un serveur (sans serveur). */
    fun showLogin() {
        loginError = null
        viewModelScope.launch {
            screen = Screen.Login(store.load().serverUrl)
        }
    }

    /** « Enregistrer » sur l'écran du mot de passe obligatoire. */
    fun changeInitialPassword(current: String, new: String, confirm: String) {
        val screenNow = screen as? Screen.MustChangePassword ?: return
        PasswordForm.check(current, new, confirm)?.let {
            passwordError = it
            return
        }
        passwordBusy = true
        passwordError = null
        viewModelScope.launch {
            try {
                screenNow.api.changePassword(current, new) // Les autres sessions sont fermées, pas celle-ci
                openServer(screenNow.api, screenNow.token)
            } catch (e: ApiError) {
                if (e.status == 401) onSessionExpired() else passwordError = e.message
            }
            passwordBusy = false
        }
    }

    /** Le compte a changé (Paramètres, ou reçu à la synchronisation). */
    fun updateUser(user: User) {
        val home = screen as? Screen.Home ?: return
        screen = home.copy(user = user)
    }

    /** Après une synchronisation : le compte a pu changer sur le serveur (nom, langue…). */
    fun reloadUser() {
        viewModelScope.launch { updateUser(local.me()) }
    }

    /** Sur un refus (401), explique si le serveur n'a encore aucun compte. */
    private suspend fun loginErrorMessage(api: ApiClient, error: ApiError): String {
        if (error.status == 401) {
            try {
                if (api.needsSetup()) {
                    return "Aucun compte sur ce serveur : crée d'abord le compte administrateur depuis le site"
                }
            } catch (e: ApiError) {
                // On garde le message de la connexion
            }
        }
        return error.message ?: "Erreur"
    }

    /**
     * Bouton de déconnexion : on envoie d'abord ce qui attend encore (si le serveur répond),
     * puis on ferme la session. Les listes restent sur le téléphone.
     */
    fun logout() {
        val server = when (val current = screen) {
            is Screen.Home -> current.server ?: return // Sans serveur : pas de déconnexion
            is Screen.MustChangePassword -> current.api
            else -> return
        }
        viewModelScope.launch {
            sync.syncNow()
            try {
                server.logout()
            } catch (e: ApiError) {
                // Serveur injoignable ou session déjà fermée : on se déconnecte quand même ici
            }
            sync.stop()
            store.clearToken()
            screen = Screen.Login(store.load().serverUrl)
        }
    }

    /**
     * 401 pendant une synchronisation : jeton révoqué ou expiré. On garde la file d'attente :
     * en se reconnectant au même compte, elle partira.
     */
    private fun onSessionExpired() {
        viewModelScope.launch {
            store.clearToken()
            screen = Screen.Login(store.load().serverUrl, "Session expirée : reconnecte-toi")
        }
    }
}

/** Racine de l'interface : affiche l'écran correspondant à l'état. */
@Composable
fun App(app: AppViewModel = viewModel()) {
    // Une synchronisation a changé la base : le compte a pu changer aussi
    LaunchedEffect(app.sync.version) {
        if (app.sync.version > 0) app.reloadUser()
    }

    when (val screen = app.screen) {
        is Screen.Loading -> LoadingScreen()
        is Screen.Welcome -> WelcomeScreen(onStartLocal = app::useLocal, onConnect = app::showLogin)
        is Screen.Login -> LoginScreen(
            serverUrl = screen.serverUrl,
            message = screen.message,
            busy = app.loginBusy,
            error = app.loginError,
            onLogin = app::login,
            onUseLocal = app::useLocal,
        )
        is Screen.MustChangePassword -> ChangePasswordScreen(
            user = screen.user,
            busy = app.passwordBusy,
            error = app.passwordError,
            onSubmit = app::changeInitialPassword,
            onLogout = app::logout,
        )
        is Screen.Home -> HomeScreen(
            api = app.local,
            server = screen.server,
            sync = if (screen.server != null) app.sync else null,
            user = screen.user,
            sessionKey = screen.sessionKey,
            onLogout = app::logout,
            onConnectServer = app::showLogin,
            onUserChanged = app::updateUser,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
