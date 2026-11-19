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
import io.github.acssio.bettertodo.api.TodoApi
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.data.MODE_LOCAL
import io.github.acssio.bettertodo.data.SessionStore
import io.github.acssio.bettertodo.local.LocalApi
import io.github.acssio.bettertodo.local.LocalDatabase
import kotlinx.coroutines.launch

/**
 * L'écran affiché : la machine d'état de l'app.
 *
 *   Loading ──(premier lancement)────────→ Welcome ──(Commencer)──────────→ Home sans serveur
 *      │                                       └──(J'ai un serveur)──→ Login
 *      │ ──(mode sans serveur)───────────→ Home sans serveur
 *      │ ──(mode serveur, jeton valide)──→ Home (ou MustChangePassword)
 *      │ ──(mode serveur, pas de jeton / 401)──→ Login ──(connexion)──→ Home (ou MustChangePassword)
 *      └──(serveur injoignable)─────────→ Unreachable ──(réessayer)──→ Loading
 *   Login ──(Utiliser sans serveur)──────→ Home sans serveur
 *   Home sans serveur ──(Paramètres › Se connecter à un serveur)──→ Login
 *   MustChangePassword ──(nouveau mot de passe enregistré)──→ Home
 *   Home ──(déconnexion / 401)────────→ Login
 *
 * Sans serveur (8-3), les données sont dans la base du téléphone (LocalApi) ;
 * avec un serveur, pour l'instant, elles viennent directement de lui (ApiClient). La synchronisation
 * des deux arrive à la 8-5.
 *
 * MustChangePassword : mot de passe donné par l'admin (must_change_password), à changer avant
 * d'ouvrir les listes, comme ChangePasswordPage sur le site.
 */
sealed class Screen {
    /** Au lancement : on vérifie le jeton enregistré. */
    data object Loading : Screen()

    /** Tout premier lancement : sans serveur, ou se connecter à un serveur. */
    data object Welcome : Screen()

    /** @param message affiché au-dessus du formulaire (ex. « Session expirée »), ou null */
    data class Login(val serverUrl: String, val message: String? = null) : Screen()

    /** Jeton enregistré, mais le serveur ne répond pas : on ne déconnecte pas pour autant. */
    data class Unreachable(val message: String) : Screen()

    /** Connecté, mais le mot de passe provisoire doit d'abord être changé. */
    data class MustChangePassword(val api: ApiClient, val user: User, val token: String) : Screen()

    /**
     * Les listes.
     * @param api les données : LocalApi (sans serveur) ou ApiClient (serveur, avec le jeton)
     * @param server le serveur, pour ce qui n'existe qu'avec lui (mot de passe) ; null sans serveur
     * @param sessionKey change à chaque connexion : un nouveau HomeViewModel est créé
     */
    data class Home(val api: TodoApi, val user: User, val server: ApiClient?, val sessionKey: String) : Screen()
}

/**
 * État de toute l'app : l'écran affiché et la connexion (comme App.tsx dans la page web).
 * Un ViewModel survit à la rotation de l'écran : rien n'est rechargé quand on tourne le téléphone.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private val local = LocalApi(LocalDatabase.get(application)) // Les données du mode sans serveur

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

    /** Au lancement (et sur « Réessayer ») : reprend la session enregistrée si elle est encore valide. */
    fun start() {
        screen = Screen.Loading
        viewModelScope.launch {
            val saved = store.load()
            if (saved.mode == "") {
                screen = Screen.Welcome
                return@launch
            }
            if (saved.mode == MODE_LOCAL) {
                openLocal()
                return@launch
            }
            if (saved.token == null || saved.serverUrl.isEmpty()) {
                screen = Screen.Login(saved.serverUrl)
                return@launch
            }

            val api = ApiClient(saved.serverUrl, saved.token)
            try {
                screen = connected(api, api.me(), saved.token)
            } catch (e: ApiError) {
                if (e.status == 401) {
                    store.clearToken()
                    screen = Screen.Login(saved.serverUrl, "Session expirée : reconnecte-toi")
                } else {
                    screen = Screen.Unreachable(e.message ?: "Erreur")
                }
            }
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
                screen = connected(ApiClient(serverUrl, session.token), session.user, session.token)
            } catch (e: ApiError) {
                loginError = loginErrorMessage(api, e)
            }
            loginBusy = false
        }
    }

    /** Après connexion : l'accueil, ou d'abord l'écran du mot de passe provisoire. */
    private fun connected(api: ApiClient, user: User, token: String): Screen {
        return if (user.mustChangePassword) Screen.MustChangePassword(api, user, token) else Screen.Home(api, user, api, token)
    }

    /** « Commencer » (premier lancement) ou « Utiliser l'app sans serveur » (connexion). */
    fun useLocal() {
        viewModelScope.launch {
            store.useLocal()
            openLocal()
        }
    }

    /** Les listes du téléphone. */
    private suspend fun openLocal() {
        // Clé neuve à chaque ouverture : sinon on retrouvait l'accueil d'avant la connexion (page Paramètres ouverte)
        screen = Screen.Home(local, local.me(), server = null, sessionKey = "$MODE_LOCAL-${System.nanoTime()}")
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
                val user = screenNow.api.changePassword(current, new) // Les autres sessions sont fermées, pas celle-ci
                screen = Screen.Home(screenNow.api, user, screenNow.api, screenNow.token)
            } catch (e: ApiError) {
                if (e.status == 401) onSessionExpired() else passwordError = e.message
            }
            passwordBusy = false
        }
    }

    /** Le compte a changé (Paramètres) : nom affiché, langue, vue par défaut. */
    fun updateUser(user: User) {
        val home = screen as? Screen.Home ?: return
        screen = home.copy(user = user)
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

    /** Bouton de déconnexion de l'accueil. */
    fun logout() {
        val api = when (val current = screen) {
            is Screen.Home -> current.server ?: return // Sans serveur : pas de déconnexion
            is Screen.MustChangePassword -> current.api
            else -> return
        }
        viewModelScope.launch {
            try {
                api.logout()
            } catch (e: ApiError) {
                // Serveur injoignable ou session déjà fermée : on se déconnecte quand même ici
            }
            store.clearToken()
            screen = Screen.Login(store.load().serverUrl)
        }
    }

    /** Écran « serveur injoignable » : oublier le jeton et revenir à la connexion. */
    fun forgetSession() {
        dropToken(null)
    }

    /** Une requête de l'accueil a reçu 401 : jeton révoqué ou expiré. */
    fun onSessionExpired() {
        dropToken("Session expirée : reconnecte-toi")
    }

    /** Oublie le jeton et affiche la connexion, avec l'adresse du serveur pré-remplie. */
    private fun dropToken(message: String?) {
        viewModelScope.launch {
            store.clearToken()
            screen = Screen.Login(store.load().serverUrl, message)
        }
    }
}

/** Racine de l'interface : affiche l'écran correspondant à l'état. */
@Composable
fun App(app: AppViewModel = viewModel()) {
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
        is Screen.Unreachable -> UnreachableScreen(
            message = screen.message,
            onRetry = app::start,
            onLogout = app::forgetSession,
        )
        is Screen.MustChangePassword -> ChangePasswordScreen(
            user = screen.user,
            busy = app.passwordBusy,
            error = app.passwordError,
            onSubmit = app::changeInitialPassword,
            onLogout = app::logout,
        )
        is Screen.Home -> HomeScreen(
            api = screen.api,
            server = screen.server,
            user = screen.user,
            sessionKey = screen.sessionKey,
            onLogout = app::logout,
            onConnectServer = app::showLogin,
            onSessionExpired = app::onSessionExpired,
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

@Composable
private fun UnreachableScreen(message: String, onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text("Réessayer") }
        TextButton(onClick = onLogout) { Text("Se déconnecter") }
    }
}
