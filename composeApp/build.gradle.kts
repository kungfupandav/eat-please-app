import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.lifecycle.service)
            implementation(libs.tensorflow.lite)
        }
    }
}

android {
    namespace = "com.eatplease.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.eatplease.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Signed with the debug key so CI/tester builds are installable;
            // replace with a real signing config before shipping to stores.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    packageOfResClass = "com.eatplease.app.generated.resources"
}

// The MoViNet-A0-Stream int8 TFLite model (~5 MB) is fetched at build time
// rather than committed. Sources are tried in order; the file is cached in
// the (gitignored) composeResources/files directory.
val movinetModelFile = layout.projectDirectory.file(
    "src/commonMain/composeResources/files/movinet_a0_stream.tflite",
)

val downloadMoViNetModel by tasks.registering {
    outputs.file(movinetModelFile)
    onlyIf { !movinetModelFile.asFile.exists() }
    doLast {
        val urls = listOf(
            "https://storage.googleapis.com/tfhub-lite-models/google/lite-model/movinet/a0/stream/kinetics-600/classification/tflite/int8/1.tflite",
            "https://tfhub.dev/google/lite-model/movinet/a0/stream/kinetics-600/classification/tflite/int8/1?lite-format=tflite",
        )
        val target = movinetModelFile.asFile
        target.parentFile.mkdirs()
        var lastFailure: Exception? = null
        for (url in urls) {
            try {
                java.net.URI(url).toURL().openStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                require(target.length() > 1_000_000) { "Downloaded model from $url is suspiciously small" }
                logger.lifecycle("Downloaded MoViNet model from $url (${target.length()} bytes)")
                return@doLast
            } catch (e: Exception) {
                lastFailure = e
                target.delete()
                logger.warn("Could not download MoViNet model from $url: ${e.message}")
            }
        }
        throw GradleException("Failed to download the MoViNet model from any source", lastFailure)
    }
}

tasks.configureEach {
    if (name.contains("omposeResources")) {
        dependsOn(downloadMoViNetModel)
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
