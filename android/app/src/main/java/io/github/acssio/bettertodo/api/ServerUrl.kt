package io.github.acssio.bettertodo.api

/** Adresse du serveur tapée sur l'écran de connexion. */
object ServerUrl {

    /**
     * Met l'adresse sous la forme attendue par ApiClient : "https://todo.exemple.fr".
     *  - espaces et "/" de fin retirés ;
     *  - "https://" ajouté s'il manque ;
     *  - "/api/v1" retiré si l'utilisateur l'a tapé (ApiClient l'ajoute).
     * @return l'adresse normalisée, ou "" si rien n'a été tapé
     */
    fun normalize(input: String): String {
        var url = input.trim().trimEnd('/')
        if (url.isEmpty()) {
            return ""
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url.removeSuffix("/api/v1").removeSuffix("/api").trimEnd('/')
    }
}
