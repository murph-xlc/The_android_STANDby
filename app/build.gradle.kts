plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.stillstand.clock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stillstand.clock"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }
}
