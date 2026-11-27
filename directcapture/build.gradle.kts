plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.myanmar.cherrydirectcapture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.myanmar.cherrydirectcapture"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "5.0-isolated-overlay"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { testImplementation(libs.junit) }
