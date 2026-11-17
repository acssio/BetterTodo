package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.ui.PasswordForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordFormTest {
    @Test
    fun champVide() {
        assertEquals("Remplis les trois champs", PasswordForm.check("", "nouveau-mdp", "nouveau-mdp"))
    }

    @Test
    fun tropCourt() {
        assertEquals("Le mot de passe doit faire au moins 8 caractères", PasswordForm.check("ancien", "court", "court"))
    }

    @Test
    fun confirmationDifferente() {
        assertEquals("Les deux nouveaux mots de passe ne sont pas identiques.", PasswordForm.check("ancien", "nouveau-mdp", "nouveau-mdP"))
    }

    @Test
    fun valide() {
        assertNull(PasswordForm.check("ancien", "nouveau-mdp", "nouveau-mdp"))
    }
}
