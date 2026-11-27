plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinPluginCompose)
}

android {
    namespace = "de.ma.ftms.relay"
    compileSdk {
        version = release(libs.versions.androidCompileSdk.get().toInt()) {
            minorApiLevel = libs.versions.androidCompileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        applicationId = "de.ma.ftms.relay"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 10100
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }
}

dependencies {
    implementation(project(":app:shared"))

    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.kotlinxCoroutinesAndroid)

    debugImplementation(libs.composeUiTooling)

    testImplementation(kotlin("test-junit"))
}
