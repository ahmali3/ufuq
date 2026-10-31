plugins {
    id("com.android.application")
}

// Each CI run gets a higher version code (Google Play requires every upload to increase it).
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Play upload key: supplied by CI from GitHub secrets, never stored in the repo.
val uploadStore = System.getenv("UFUQ_UPLOAD_STORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.ahmali.ufuq"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ahmali.ufuq"
        // Android 6.0+. java.time on 6.0–7.1 comes from core library desugaring.
        minSdk = 23
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadStore != null) {
            create("upload") {
                storeFile = uploadStore
                storePassword = System.getenv("UFUQ_UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("UFUQ_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UFUQ_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // The Play upload key when CI has it; otherwise the debug key, so test builds
            // still install (those can't be uploaded to Play).
            signingConfig = signingConfigs.getByName(if (uploadStore != null) "upload" else "debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Calling an API that doesn't exist on older Android versions must fail the build.
        error += listOf("NewApi", "InlinedApi")
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Build-time only: back-ports java.time to Android 6.0–7.1. Only used classes ship.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
