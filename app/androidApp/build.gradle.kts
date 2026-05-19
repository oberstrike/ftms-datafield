plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinPluginCompose)
}

android {
    namespace = "de.ma.ftms.bridge"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.ma.ftms.bridge"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 10000
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(libs.androidxLifecycleViewmodelCompose)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.decompose)
    implementation(libs.decomposeExtensionsCompose)
    implementation(libs.garminConnectIq) {
        artifact {
            type = "aar"
        }
    }
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.sqldelightAndroidDriver)
    implementation(libs.vicoCompose)
    implementation(libs.vicoComposeM3)

    debugImplementation(libs.composeUiTooling)

    testImplementation(kotlin("test-junit"))
}
