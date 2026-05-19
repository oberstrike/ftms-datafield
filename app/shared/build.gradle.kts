plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    android {
        namespace = "de.ma.ftms.core"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
        withHostTestBuilder {}
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinxCoroutinesCore)
                implementation(libs.sqldelightCoroutinesExtensions)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinxCoroutinesTest)
                implementation(libs.sqldelightSqliteDriver)
            }
        }
    }
}

sqldelight {
    databases {
        create("FtmsBridgeDatabase") {
            packageName.set("de.ma.ftms.core.storage.db")
        }
    }
}
