import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    // id("kotlin-kapt") // Migrate away from kapt to KSP where possible
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.fitghost.app"
    compileSdk = 35

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

        // Optional: Model download base URL override (R2 public domain or CDN)
        val modelBaseUrl = localProperties.getProperty("MODEL_BASE_URL", "")
        buildConfigField("String", "MODEL_BASE_URL", "\"$modelBaseUrl\"")

        // Cloudflare Workers Proxy base (필수) - 기본값은 emoZleep production 워커
        val proxyBaseUrl =
                localProperties.getProperty(
                        "PROXY_BASE_URL",
                        "https://fitghost-proxy.vinny4920-081.workers.dev"
                )
        buildConfigField("String", "PROXY_BASE_URL", "\"$proxyBaseUrl\"")

        // Cloud Try-On (Gemini) opt-in flag
        val cloudTryOnEnabled =
                (localProperties.getProperty("CLOUD_TRYON_ENABLED")
                                ?: (project.findProperty("CLOUD_TRYON_ENABLED") as String?)
                                        ?: System.getenv("CLOUD_TRYON_ENABLED") ?: "false")
                        .toString()
        buildConfigField("boolean", "CLOUD_TRYON_ENABLED", cloudTryOnEnabled)

        // Max total images for try-on (model + clothes). Can be overridden in local.properties
        val maxTryOnTotalImages =
            (localProperties.getProperty("MAX_TRYON_TOTAL_IMAGES")
                ?: (project.findProperty("MAX_TRYON_TOTAL_IMAGES") as String?)
                ?: System.getenv("MAX_TRYON_TOTAL_IMAGES") ?: "4")
        buildConfigField("int", "MAX_TRYON_TOTAL_IMAGES", maxTryOnTotalImages)

        // Max side pixels for each uploaded image (preserve aspect ratio). Default 1024.
        val tryOnMaxSidePx =
            (localProperties.getProperty("TRYON_MAX_SIDE_PX")
                ?: (project.findProperty("TRYON_MAX_SIDE_PX") as String?)
                ?: System.getenv("TRYON_MAX_SIDE_PX") ?: "1024")
        buildConfigField("int", "TRYON_MAX_SIDE_PX", tryOnMaxSidePx)

        // Debug-only emergency: allow insecure TLS for NanoBanana (hostname mismatch)
        // Default false. Enable ONLY for local debug if server cert is misconfigured.
        val allowInsecureTls = (localProperties.getProperty("ALLOW_INSECURE_TLS")
                ?: (project.findProperty("ALLOW_INSECURE_TLS") as String?)
                ?: System.getenv("ALLOW_INSECURE_TLS") ?: "false").toString()
        buildConfigField("boolean", "ALLOW_INSECURE_TLS", allowInsecureTls)

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // NDK + CMake (임베드 서버)
    if (project.findProperty("enableEmbeddedLlama") == "true") {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    defaultConfig {
        // 임베드 우선 arm64-v8a만 포함 (릴리즈 기준)
        // 필요 시 -PabiFiltersOverride=arm64-v8a 또는 x86_64 등으로 단일 ABI 빌드 가능
        val abiOverride = (project.findProperty("abiFiltersOverride") as String?)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
        ndk {
            // 에뮬레이터(x86_64)와 물리 ARM64 디바이스 모두 지원 (기본)
            abiFilters.clear()
            abiFilters += (abiOverride ?: listOf("arm64-v8a", "x86_64"))
        }
        // 빌드 시 플래그로 온디바이스 엔진 사용 여부를 명확히 주입
        val embeddedEnabled = (project.findProperty("enableEmbeddedLlama") as String?) == "true"
        buildConfigField("boolean", "ENABLE_EMBEDDED_LLAMA", embeddedEnabled.toString())

        if (embeddedEnabled) {
            externalNativeBuild {
                cmake {
                    // 필요시 오프라인/저속 네트워크 환경에서도 안전 빌드
                    val enableVulkan = (project.findProperty("enableVulkan") as String?) == "true"
                    val ggmlVulkanArg = "-DGGML_VULKAN=" + if (enableVulkan) "ON" else "OFF"
                    arguments += listOf(
                        ggmlVulkanArg,
                        "-DLLAMA_BUILD_TOOLS=OFF",
                        "-DLLAMA_BUILD_EXAMPLES=OFF",
                        "-DLLAMA_CURL=OFF",
                        "-DLLAMA_OPENSSL=OFF"
                    )
                }
            }
        }
    }

    

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    // CMake / NDK 빌드 타임아웃 증가(대규모 서브프로젝트 Fetch 시 빌드 취소 방지)
    // 주의: 일부 Gradle/AGP 조합에서 ExternalNativeBuildTask.timeout API와 java.time이 미노출일 수 있어 주석 처리
    // 필요 시 gradle.properties로 제어하거나 CI에서 --max-workers 등으로 조정 권장
    // tasks.withType<com.android.build.gradle.tasks.ExternalNativeBuildTask>().configureEach {
    //     timeout.set(java.time.Duration.ofMinutes(30))
    // }
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
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    
    // Google AI Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")
    
    // On-device AI via local llama.cpp server (OpenAI 호환)
    // (Android 바인딩 제거)

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

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
