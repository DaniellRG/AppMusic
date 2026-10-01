plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "com.example.music"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.music"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- NUEVAS DEPENDENCIAS ---
    // Media3 (ExoPlayer) para reproducción de audio
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-session:1.4.0")
    implementation("androidx.media3:media3-common:1.4.0")

    // Room para base de datos (favoritos, carpetas, metadatos)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Coil para cargar imágenes (portadas de álbum)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Palette: extrae el color dominante de la carátula para que la interfaz siga al álbum
    // (el reproductor de Phoenix saca el tema entero de la portada)
    implementation("androidx.palette:palette-ktx:1.0.0")
    // OkHttp para peticiones online (letras lrclib + portada iTunes). Versión
    // alineada a la que trae Coil 2.7.0 (transitiva) para evitar conflictos.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // DocumentFile para escanear carpetas elegidas vía SAF (Storage Access Framework)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Material icons (Icons.Default, Icons.Outlined) — versión gestionada por el BOM
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Lifecycle ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")
}
