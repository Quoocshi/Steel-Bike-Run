import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

val localProps = Properties().also { props ->
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) props.load(localFile.inputStream())
}

android {
    namespace = "com.example.steelbikerunmobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.steelbikerunmobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = localProps.getProperty("MAPS_API_KEY", "")
        buildConfigField("String", "MAPTILER_API_KEY", "\"rARsKNebTp47YXCYPRSn\"")
        buildConfigField("String", "GOONG_MAP_KEY", "\"${localProps.getProperty("GOONG_MAP_KEY", "")}\"")
        buildConfigField("String", "GOONG_API_KEY", "\"${localProps.getProperty("GOONG_API_KEY", "")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            val baseUrl = localProps.getProperty("LOCAL_BASE_URL", "https://steel-bike-run-e6eccka4facfaag3.malaysiawest-01.azurewebsites.net/")
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
            buildConfigField("String", "WS_URL", "\"${baseUrl.toWebSocketUrl()}ws\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://steel-bike-run-e6eccka4facfaag3.malaysiawest-01.azurewebsites.net/\"")
            buildConfigField("String", "WS_URL", "\"wss://steel-bike-run-e6eccka4facfaag3.malaysiawest-01.azurewebsites.net/ws\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

fun String.toWebSocketUrl(): String {
    val normalized = if (endsWith("/")) this else "$this/"
    return when {
        normalized.startsWith("https://") -> normalized.replaceFirst("https://", "wss://")
        normalized.startsWith("http://") -> normalized.replaceFirst("http://", "ws://")
        else -> normalized
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.maplibre.native)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.face.detection)

    // H3 for Hexagon Maps
    implementation("com.uber:h3:4.1.1")

    kapt(libs.hilt.android.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}