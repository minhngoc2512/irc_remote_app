plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The IR code database is produced by ../pipeline/build_db.py and packaged as an asset.
val irdbDir = rootProject.file("../pipeline/out")
val checkIrdb by tasks.registering {
    doLast {
        check(File(irdbDir, "irdb.sqlite").exists()) {
            "Missing $irdbDir/irdb.sqlite — run pipeline/fetch_sources.sh and pipeline/build_db.py first"
        }
    }
}
tasks.named("preBuild") { dependsOn(checkIrdb) }

android {
    namespace = "com.minhngoc.irremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.minhngoc.irremote"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    sourceSets["main"].assets.srcDir(irdbDir)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
