import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)       // Compile les fonctions @Composable
    alias(libs.plugins.kotlin.serialization) // Génère le code JSON des classes @Serializable
    alias(libs.plugins.ksp)                  // Génère le code de Room (base locale)
}

android {
    namespace = "io.github.acssio.bettertodo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.acssio.bettertodo" // Identifiant définitif de l'app : ne plus le changer
        minSdk = 26                                    // Android 8 : java.time disponible
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // Tests de app/src/androidTest
    }

    // Signature de la version release (voir docs/etapes/08-8-android-release.md).
    // La clé et ses mots de passe sont dans android/keystore.properties, JAMAIS dans Git (.gitignore).
    // Autre fichier possible : ./gradlew assembleRelease -PkeystoreProperties=/chemin/vers/fichier
    val keystoreFile = rootProject.file(providers.gradleProperty("keystoreProperties").getOrElse("keystore.properties"))
    val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile.parentFile.resolve(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Sans keystore.properties, l'APK release n'est pas signé (Android refuse de l'installer)
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lucide)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx) // withTransaction
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling) // Aperçus @Preview dans Android Studio

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)  // Tests sur l'émulateur (base locale : vrai SQLite)
    androidTestImplementation(libs.androidx.test.runner)
}
