package io.github.acssio.bettertodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ce qu'une notification demande d'ouvrir (8-7). MainActivity le pose en recevant l'Intent,
 * l'accueil l'exécute puis le remet à false.
 */
object AppLinks {
    var openToday by mutableStateOf(false)
}
