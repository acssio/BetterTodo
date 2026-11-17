package io.github.acssio.bettertodo.ui

/** Vérification d'un formulaire de mot de passe, avant de l'envoyer (le serveur vérifie aussi). */
object PasswordForm {
    /** Longueur minimale, la même que le serveur (validateNewUser). */
    const val MIN_LENGTH = 8

    /**
     * @return le message d'erreur à afficher, ou null si le formulaire peut être envoyé
     */
    fun check(current: String, new: String, confirm: String): String? = when {
        current.isEmpty() || new.isEmpty() || confirm.isEmpty() -> "Remplis les trois champs"
        new.length < MIN_LENGTH -> "Le mot de passe doit faire au moins $MIN_LENGTH caractères"
        new != confirm -> "Les deux nouveaux mots de passe ne sont pas identiques." // Message du site
        else -> null
    }
}
