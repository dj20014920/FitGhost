import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-kapt")
}

android {
    namespace = "com.fitghost.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fitghost.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // API Keys from local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        // Prefer local.properties, then Gradle property, then environment variable
        val geminiKey =
                (localProperties.getProperty("GEMINI_VERTEX_API_KEY")
                        ?: (project.findProperty("GEMINI_VERTEX_API_KEY") as String?)
                                ?: System.getenv("GEMINI_VERTEX_API_KEY") ?: "")
        buildConfigField("String", "GEMINI_VERTEX_API_KEY", "\"$geminiKey\"")

        // NanoBanana API Key (accept also 'nanobananaapikey' from local.properties)
        val nanoBananaKey =
                (localProperties.getProperty("NANOBANANA_API_KEY")
                        ?: localProperties.getProperty("nanobananaapikey")
                        ?: (project.findProperty("NANOBANANA_API_KEY") as String?)
                                ?: System.getenv("NANOBANANA_API_KEY") ?: "")
        buildConfigField("String", "NANOBANANA_API_KEY", "\"$nanoBananaKey\"")

        // NanoBanana Base URL (configurable)
        val nanoBananaBaseUrl =
                (localProperties.getProperty("NANOBANANA_BASE_URL")
                        ?: (project.findProperty("NANOBANANA_BASE_URL") as String?)
                                ?: System.getenv("NANOBANANA_BASE_URL")
                                ?: "https://api.nanobanana.ai/")
        buildConfigField("String", "NANOBANANA_BASE_URL", "\"$nanoBananaBaseUrl\"")
        // Optional: NanoBanana try-on endpoint path (e.g., "v1/tryon/compose")
        val nanoBananaTryOnEndpoint = localProperties.getProperty("NANOBANANA_TRYON_ENDPOINT", "")
        buildConfigField("String", "NANOBANANA_TRYON_ENDPOINT", "\"$nanoBananaTryOnEndpoint\"")
        // Optional: request format hint ("json" | "multipart")
        val nanoBananaTryOnFormat = localProperties.getProperty("NANOBANANA_TRYON_FORMAT", "json")
        buildConfigField("String", "NANOBANANA_TRYON_FORMAT", "\"$nanoBananaTryOnFormat\"")
        // Optional: auth header configuration
        val nbAuthHeader = localProperties.getProperty("NANOBANANA_AUTH_HEADER", "Authorization")
        val nbAuthScheme = localProperties.getProperty("NANOBANANA_AUTH_SCHEME", "Bearer")
        buildConfigField("String", "NANOBANANA_AUTH_HEADER", "\"$nbAuthHeader\"")
        buildConfigField("String", "NANOBANANA_AUTH_SCHEME", "\"$nbAuthScheme\"")

        // Optional: send API key via query parameter instead of header
        // Example: NANOBANANA_QUERY_KEY_PARAM=key  → appends ?key=<API_KEY>
        val nbQueryKeyParam = localProperties.getProperty("NANOBANANA_QUERY_KEY_PARAM", "")
        buildConfigField("String", "NANOBANANA_QUERY_KEY_PARAM", "\"$nbQueryKeyParam\"")

        // Cloud Try-On (Gemini) opt-in flag
        val cloudTryOnEnabled =
                (localProperties.getProperty("CLOUD_TRYON_ENABLED")
                                ?: (project.findProperty("CLOUD_TRYON_ENABLED") as String?)
                                        ?: System.getenv("CLOUD_TRYON_ENABLED") ?: "false")
                        .toString()
        buildConfigField("boolean", "CLOUD_TRYON_ENABLED", cloudTryOnEnabled)

        // Debug-only emergency: allow insecure TLS for NanoBanana (hostname mismatch)
        // Default false. Enable ONLY for local debug if server cert is misconfigured.
        val allowInsecureTls = (localProperties.getProperty("ALLOW_INSECURE_TLS")
                ?: (project.findProperty("ALLOW_INSECURE_TLS") as String?)
                ?: System.getenv("ALLOW_INSECURE_TLS") ?: "false").toString()
        buildConfigField("boolean", "ALLOW_INSECURE_TLS", allowInsecureTls)

        // AdMob App ID injected as string resource for all build types
        val admobAppId =
                localProperties.getProperty(
                        "ADMOB_APP_ID",
                        "ca-app-pub-3940256099942544~3347511713" // Google sample App ID (safe
                        // fallback)
                        )
        resValue("string", "admob_app_id", "\"$admobAppId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Safety: never unlimited in release builds
            buildConfigField("boolean", "UNLIMITED_CREDITS", "false")
        }
        debug {
            applicationIdSuffix = ".debug"
            // Dev-only: make in-app credits unlimited in debug builds
            buildConfigField("boolean", "UNLIMITED_CREDITS", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { jvmTarget = "1.8" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("com.google.android.material:material:1.10.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    
    // Google AI Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Ads & UMP
    implementation("com.google.android.gms:play-services-ads:23.3.0")
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")

    // Device
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.browser:browser:1.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
