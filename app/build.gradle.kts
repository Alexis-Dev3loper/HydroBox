import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun hydroboxSetting(gradleName: String, environmentName: String, defaultValue: String): String =
    providers.gradleProperty(gradleName).orNull
        ?: localProperties.getProperty(gradleName)
        ?: System.getenv(environmentName)
        ?: defaultValue

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val hydroboxApiBaseUrl = hydroboxSetting(
    "hydrobox.apiBaseUrl",
    "HYDROBOX_MOBILE_API_BASE_URL",
    "https://api.example.invalid/api/v1"
)
val hydroboxMqttEnabled = hydroboxSetting(
    "hydrobox.mqttEnabled",
    "HYDROBOX_MOBILE_MQTT_ENABLED",
    "false"
).equals("true", ignoreCase = true)
val hydroboxMqttHost = hydroboxSetting(
    "hydrobox.mqttHost",
    "HYDROBOX_MOBILE_MQTT_HOST",
    "mqtt.example.invalid"
)
val hydroboxMqttPort = hydroboxSetting(
    "hydrobox.mqttPort",
    "HYDROBOX_MOBILE_MQTT_PORT",
    "1883"
).toIntOrNull()?.takeIf { it in 1..65535 } ?: 1883
val hydroboxMqttUsername = hydroboxSetting(
    "hydrobox.mqttUsername",
    "HYDROBOX_MOBILE_MQTT_USERNAME",
    ""
)
val hydroboxMqttPassword = hydroboxSetting(
    "hydrobox.mqttPassword",
    "HYDROBOX_MOBILE_MQTT_PASSWORD",
    ""
)

android {
    namespace = "com.hydrobox.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hydrobox.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "HYDROBOX_API_BASE_URL", hydroboxApiBaseUrl.asBuildConfigString())
        buildConfigField("boolean", "HYDROBOX_MQTT_ENABLED", hydroboxMqttEnabled.toString())
        buildConfigField("String", "HYDROBOX_MQTT_HOST", hydroboxMqttHost.asBuildConfigString())
        buildConfigField("int", "HYDROBOX_MQTT_PORT", hydroboxMqttPort.toString())
        buildConfigField("String", "HYDROBOX_MQTT_USERNAME", hydroboxMqttUsername.asBuildConfigString())
        buildConfigField("String", "HYDROBOX_MQTT_PASSWORD", hydroboxMqttPassword.asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Direct MQTT is legacy. A release cannot enable it or embed its credentials.
            buildConfigField("boolean", "HYDROBOX_MQTT_ENABLED", "false")
            buildConfigField("String", "HYDROBOX_MQTT_USERNAME", "\"\"")
            buildConfigField("String", "HYDROBOX_MQTT_PASSWORD", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            // ✅ sin “/” inicial y usando setOf(...)
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                // a veces también aparece con HiveMQ/Netty:
                "META-INF/io.netty.versions.properties"
            )
            // (opcional) en vez de excluir, podrías hacer pickFirst; no uses ambas para el mismo archivo
            // pickFirsts += setOf("META-INF/INDEX.LIST")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)   // ✅ deja solo UNA línea
    implementation(libs.material)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // HiveMQ MQTT v1.3.10 desde el version catalog
    implementation(libs.hivemq.mqtt.client)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

