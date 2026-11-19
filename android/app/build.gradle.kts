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

    buildTypes {
        release {
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
