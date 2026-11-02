import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Automatic versioning: every commit increases versionCode and the patch number.
// Bump the major/minor version here for bigger releases.
val baseVersion = "1.1"
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)
val appVersionName = "$baseVersion.$commitCount"

base {
    archivesName.set("WakeUp-$appVersionName")
}

tasks.register("printVersionName") {
    doLast { println(appVersionName) }
}

val releaseKeystore: File? = System.getenv("SIGNING_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

// In CI a release must never be signed with the debug key: abort instead of falling back.
val ciWithoutReleaseKey = System.getenv("CI") != null && releaseKeystore == null
tasks.matching { it.name == "assembleRelease" }.configureEach {
    doFirst {
        if (ciWithoutReleaseKey) {
            throw GradleException("Release build in CI without SIGNING_KEYSTORE_FILE: refusing to sign with the debug key.")
        }
    }
}

android {
    namespace = "de.vanhake.wakeup"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.vanhake.wakeup"
        minSdk = 26
        targetSdk = 35
        versionCode = commitCount
        versionName = appVersionName
    }

    // Release signing key comes from environment variables (set from GitHub secrets in CI).
    // Without them the release APK falls back to the debug key so local builds keep working.
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Offline sound classifier (YAMNet model in assets/) for the sleep analysis.
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
