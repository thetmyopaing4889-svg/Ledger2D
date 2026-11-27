plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.myanmar.cherrydirectcapture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.myanmar.cherrydirectcapture"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0-shortcut-test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { testImplementation(libs.junit) }
