plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.myanmar.cherrydirectcapture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.myanmar.cherrydirectcapture"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "18.0-smart-permission-test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    testImplementation(libs.junit)
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
}
