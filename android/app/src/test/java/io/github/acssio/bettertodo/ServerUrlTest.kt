package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.ServerUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {
    @Test
    fun ajouteHttpsSiAbsent() {
        assertEquals("https://todo.exemple.fr", ServerUrl.normalize("todo.exemple.fr"))
    }

    @Test
    fun garderHttpPourLeDockerLocal() {
        assertEquals("http://10.0.2.2:8000", ServerUrl.normalize("http://10.0.2.2:8000/"))
    }

    @Test
    fun retireEspacesSlashEtApi() {
        assertEquals("https://todo.exemple.fr", ServerUrl.normalize("  https://todo.exemple.fr/api/v1/ "))
        assertEquals("https://todo.exemple.fr", ServerUrl.normalize("https://todo.exemple.fr/api"))
    }

    @Test
    fun videResteVide() {
        assertEquals("", ServerUrl.normalize("   "))
    }
}
