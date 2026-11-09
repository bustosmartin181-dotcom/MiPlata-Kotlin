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
    val hayClaveFija = !rutaClave.isNullOrEmpty() && !contrasena.isNullOrEmpty()
    signingConfigs {
        if (hayClaveFija) create("fija") {
            storeFile = file(rutaClave!!)
            storePassword = contrasena
            keyAlias = "miplata"
            keyPassword = contrasena
        }
    }

    buildTypes {
        // Sin clave fija se usa la clave de prueba (sirve para probar; puede pedir desinstalar al actualizar)
        val firma = if (hayClaveFija) signingConfigs.getByName("fija") else signingConfigs.getByName("debug")
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = firma
        }
        getByName("debug") {
            signingConfig = firma
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
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
}
