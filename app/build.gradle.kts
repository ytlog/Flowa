plugins { id("com.android.application"); kotlin("android") }

val releaseStoreFile = providers.environmentVariable("MOBBY_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("MOBBY_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("MOBBY_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("MOBBY_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = releaseSigningValues.any { !it.isNullOrBlank() }

android {
    namespace = "com.flowa.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.flowa.app"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("flowa.versionCode").get().toInt()
        versionName = providers.gradleProperty("flowa.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("mobbyRelease") {
                storeFile = releaseStoreFile?.takeIf { it.isNotBlank() }?.let { file(it) }
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        configureEach {
            if (providers.gradleProperty("isolatedUiTest").orNull == "true") {
                applicationIdSuffix = ".uitest"
            }
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("mobbyRelease")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

val validateReleaseCredentials = tasks.register("validateReleaseCredentials") {
    doLast {
        check(!hasReleaseSigning || releaseSigningValues.all { !it.isNullOrBlank() }) {
            "Release signing requires all four MOBBY_RELEASE_* environment variables. See docs/RELEASING.md."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseCredentials)
}

dependencies {
    implementation(project(":core:domain")); implementation(project(":core:data"))
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
