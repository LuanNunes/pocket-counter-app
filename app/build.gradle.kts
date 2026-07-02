import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

// Upload-key credentials live outside git (see .gitignore). When the file is absent — CI, a fresh
// clone — release builds still compile, they just come out unsigned.
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties: Properties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // Only PocketCounter's custom rules run; detekt's built-in rulesets stay off.
    disableDefaultRuleSets = true
    buildUponDefaultConfig = false
}

dependencies {
    detektPlugins(project(":detekt-rules"))
}

android {
    namespace = "com.resolveprogramming.pocketcounter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.resolveprogramming.pocketcounter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Same OAuth Web client ID across environments.
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"387853581038-tu9fjbge0rjqfraf168n4ovt6iq34jsv.apps.googleusercontent.com\"")
    }

    flavorDimensions += "environment"
    productFlavors {
        // Emulator → host loopback. Requires the backend running locally and the
        // cleartext allow-list in network_security_config.xml (10.0.2.2 / localhost).
        create("local") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        // Shared dev backend over HTTPS — works on physical devices.
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://api-dev.pocket-counter.com/\"")
        }
        // Production backend — the Play Store build is prodRelease.
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://api.pocket-counter.com/\"")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release").takeIf { keystorePropertiesFile.exists() }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // DataStore
    implementation(libs.datastore.preferences)

    // Auth
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Biometric
    implementation(libs.biometric)
    implementation(libs.fragment.ktx)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
