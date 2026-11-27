plugins {
    alias(libs.plugins.android.application)
}
android {
    namespace = "com.myanmar.cherryocrbenchmark"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.myanmar.cherryocrbenchmark"
        minSdk = 26
        targetSdk = 32
        versionCode = 1
        versionName = "0.1-stage-a"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation(libs.junit)
}
