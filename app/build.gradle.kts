plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Número de versión: en GitHub Actions sale del número de ejecución
val numero = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.miplata.kotlin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.miplata.kotlin"
        minSdk = 26
        targetSdk = 35
        versionCode = numero
        versionName = "10.0.$numero"
    }

    // Siempre la misma clave: así cada APK nuevo se instala ENCIMA del anterior, sin desinstalar.
    // La clave NO está en el repo: GitHub Actions la arma desde los secretos MIPLATA_KEYSTORE y MIPLATA_CLAVE.
    val rutaClave = System.getenv("MIPLATA_KEYSTORE")
    val contrasena = System.getenv("MIPLATA_CLAVE")
    signingConfigs {
        create("fija") {
            if (rutaClave != null && contrasena != null) {
                storeFile = file(rutaClave)
                storePassword = contrasena
                keyAlias = "miplata"
                keyPassword = contrasena
            } else {
                // Sin secretos: clave de prueba de la máquina (sirve para compilar, no para actualizar encima)
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fija")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fija")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
}
