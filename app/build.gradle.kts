plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.codex.edgeshelf"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.codex.edgeshelf"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "1.1.2"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("androidx.compose") && requested.name != "compiler") {
            useVersion(
                when (requested.name) {
                    "material3" -> "1.1.1"
                    "material", "material-android",
                    "material-icons-core", "material-icons-core-android",
                    "material-ripple", "material-ripple-android" -> "1.5.0"
                    else -> "1.5.1"
                },
            )
        }
        if (requested.group == "androidx.collection" && requested.name == "collection") {
            useVersion("1.1.0")
        }
    }
}

dependencies {
    implementation("androidx.compose.material3:material3:1.1.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.5.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling:1.5.1")

    testImplementation("junit:junit:4.13.2")
}
