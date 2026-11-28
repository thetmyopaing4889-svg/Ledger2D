plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.myanmar.cherrydirectcapture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.myanmar.cherrydirectcapture"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "10.0-text-review"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { testImplementation(libs.junit) }
