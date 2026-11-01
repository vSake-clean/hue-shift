import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is configured through environment variables so that no secrets
// ever end up in the repository (see README -> "Building from source").
val releaseKeystore: String? = System.getenv("HUESHIFT_KEYSTORE_PATH")

android {
    namespace = "io.github.vsakeclean.hueshift"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.vsakeclean.hueshift"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("HUESHIFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HUESHIFT_KEY_ALIAS")
                keyPassword = System.getenv("HUESHIFT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Without a release keystore the APK is signed with the debug key, so it stays installable.
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

    androidResources {
        generateLocaleConfig = true
        // Only ship the languages the app is translated into (libraries bring dozens more).
        localeFilters += listOf("en", "pl")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Lets plain JVM tests run code that logs with android.util.Log.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    dependenciesInfo {
        // Keeps the APK free of the Google-encrypted dependency blob (friendlier for F-Droid).
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    // Screenshot rendering is opt-in: ./gradlew testDebugUnitTest -Phueshift.screenshots
    systemProperty("hueshift.screenshots", project.hasProperty("hueshift.screenshots").toString())
    systemProperty("hueshift.screenshotDir", rootProject.file("docs/screenshots").absolutePath)
    // Optional mirror for the Android framework jars Robolectric downloads: -Probolectric.repo=https://...
    (project.findProperty("robolectric.repo") as String?)?.let {
        systemProperty("robolectric.dependency.repo.url", it)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
