plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "com.flowa.data"; compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":core:domain")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"); implementation("com.squareup.okhttp3:okhttp:4.12.0") }
