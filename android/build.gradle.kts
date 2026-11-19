// Fichier de build commun à tous les modules (ici un seul : app).
// On déclare les plugins sans les appliquer ; app/build.gradle.kts les applique.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
