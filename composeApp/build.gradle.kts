import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCocoapods)
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

    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        version = "1.0"
        summary = "EatPlease shared module"
        homepage = "https://github.com/vikrama/eat-please-app"
        ios.deploymentTarget = "15.3"
        podfile = project.file("../iosApp/Podfile")

        framework {
            baseName = "ComposeApp"
            isStatic = true
        }

        pod("TensorFlowLiteC") {
            version = libs.versions.tensorflowLiteC.get()
            moduleName = "TensorFlowLiteC"
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
            implementation(libs.androidx.camera.view)
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

// The MoViNet-A0-Stream int8 TFLite model (~3 MB) is checked into the repo at
// composeApp/src/commonMain/composeResources/files/movinet_a0_stream.tflite and
// packaged as a Compose resource (read at runtime via Res.readBytes). This task
// fails the build fast if the model is missing or truncated — e.g. an incomplete
// clone or a Git LFS pointer that wasn't fetched — so we never ship without it.
val movinetModelPath = "composeApp/src/commonMain/composeResources/files/movinet_a0_stream.tflite"
val movinetModelFile = layout.projectDirectory
    .file("src/commonMain/composeResources/files/movinet_a0_stream.tflite")
    .asFile

val checkMoViNetModel by tasks.registering {
    val model = movinetModelFile
    val displayPath = movinetModelPath
    doLast {
        require(model.exists()) {
            "MoViNet model missing at $displayPath. It is checked into the repo; " +
                "ensure the clone is complete (and `git lfs pull` if LFS is enabled)."
        }
        require(model.length() > 1_000_000) {
            "MoViNet model at $displayPath looks truncated (${model.length()} bytes)."
        }
    }
}

listOf(
    "assembleDebug",
    "assembleRelease",
    "podspec",
    "syncFramework",
).forEach { taskName ->
    tasks.matching { it.name.equals(taskName, ignoreCase = true) }.configureEach {
        dependsOn(checkMoViNetModel)
    }
}


dependencies {
    debugImplementation(compose.uiTooling)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
