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
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.data.SessionStore
import kotlinx.coroutines.launch

/**
 * L'écran affiché : la machine d'état de l'app.
 *
 *   Loading ──(jeton valide)──────────→ Home
 *      │ ──(pas de jeton / 401)───────→ Login ──(connexion)──→ Home
 *      └──(serveur injoignable)───────→ Unreachable ──(réessayer)──→ Loading
 *   Home ──(déconnexion / 401)────────→ Login
 */
sealed class Screen {
    /** Au lancement : on vérifie le jeton enregistré. */
    data object Loading : Screen()

    /** @param message affiché au-dessus du formulaire (ex. « Session expirée »), ou null */
    data class Login(val serverUrl: String, val message: String? = null) : Screen()

    /** Jeton enregistré, mais le serveur ne répond pas : on ne déconnecte pas pour autant. */
    data class Unreachable(val message: String) : Screen()

    /** Connecté. api envoie le jeton à chaque requête. */
    data class Home(val api: ApiClient, val user: User, val token: String) : Screen()
}

/**
 * État de toute l'app : l'écran affiché et la connexion (comme App.tsx dans la page web).
 * Un ViewModel survit à la rotation de l'écran : rien n'est rechargé quand on tourne le téléphone.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set

    // État du formulaire de connexion (les champs eux-mêmes sont dans LoginScreen)
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    init {
        start()
    }

    /** Au lancement (et sur « Réessayer ») : reprend la session enregistrée si elle est encore valide. */
    fun start() {
        screen = Screen.Loading
        viewModelScope.launch {
            val saved = store.load()
            if (saved.token == null || saved.serverUrl.isEmpty()) {
                screen = Screen.Login(saved.serverUrl)
                return@launch
            }

            val api = ApiClient(saved.serverUrl, saved.token)
            try {
                screen = Screen.Home(api, api.me(), saved.token)
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
                screen = Screen.Home(ApiClient(serverUrl, session.token), session.user, session.token)
            } catch (e: ApiError) {
                loginError = loginErrorMessage(api, e)
            }
            loginBusy = false
        }
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
        val current = screen as? Screen.Home ?: return
        viewModelScope.launch {
            try {
                current.api.logout()
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
        is Screen.Login -> LoginScreen(
            serverUrl = screen.serverUrl,
            message = screen.message,
            busy = app.loginBusy,
            error = app.loginError,
            onLogin = app::login,
        )
        is Screen.Unreachable -> UnreachableScreen(
            message = screen.message,
            onRetry = app::start,
            onLogout = app::forgetSession,
        )
        is Screen.Home -> HomeScreen(
            api = screen.api,
            user = screen.user,
            sessionKey = screen.token,
            onLogout = app::logout,
            onSessionExpired = app::onSessionExpired,
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
