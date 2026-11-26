import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.myanmar.ledger2d"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myanmar.ledger2d"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }
    testOptions {
        // Run real Room repository tests on Robolectric without a physical device.
        unitTests.isIncludeAndroidResources = true
        // Robolectric downloads large Android test jars outside Gradle dependency resolution.
        // Shared GitHub CI egress receives HTTP 429 from repo1.maven.org.
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url",
                "https://maven-central.storage-download.googleapis.com/maven2/")
            it.systemProperty("robolectric.dependency.repo.id","googleMavenCentralMirror")
        }
    }
    buildTypes {
        debug {
            // Preview only: install alongside the existing production Ledger2D app.
            // Never ask testers to uninstall their ledger to compare Rules layouts.
            // Full Cherry LIVE with SET integration in an isolated debug install.
            // Do not overwrite production, rulespreview, 15s or 3s probe APKs.
            applicationIdSuffix = ".setintegrationtest"
            versionNameSuffix = "-set-integration-preview"
        }
        release {
            // Keep the distributable release APK complete. R8/resource shrinking
            // reduced the CI artifact from about 17 MiB to about 1.5 MiB.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Tolerant HTML5 parser; mirrors the Colab HTMLParser's visible-text extraction.
    implementation("org.jsoup:jsoup:1.17.2")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}


/** Offline color variants from the unchanged Quick Entry glass orb.
 * Preserve green leaf, brown stem, highlights and the original circular silhouette.
 * No image processing or extra allocations on the Android device. */
@CacheableTask
abstract class GenerateCherryRuleOrbs : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceImage: RegularFileProperty
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val src=checkNotNull(ImageIO.read(sourceImage.get().asFile))
        val dir=outputDir.get().asFile.resolve("drawable-nodpi").apply{mkdirs()}
        for(variant in listOf("ruby","rose","pearl")) {
            val out=BufferedImage(src.width,src.height,BufferedImage.TYPE_INT_RGB)
            for(y in 0 until src.height) for(x in 0 until src.width) {
                val rgb=src.getRGB(x,y)
                val r=(rgb shr 16) and 255
                val g=(rgb shr 8) and 255
                val b=rgb and 255
                // Red/pink glass and fruit, but not the green leaf or brown stem.
                val mask=if(r>g && r>=b*0.9 && g<=b+16)
                    ((r-g)/24.0).coerceIn(0.0,1.0) else 0.0
                val light=(0.299*r+0.587*g+0.114*b)
                fun mix(v:Int,t:Double)= (v*(1-mask)+t.coerceIn(0.0,255.0)*mask)
                    .roundToInt().coerceIn(0,255)
                val channels=when(variant) {
                    "rose"->listOf(mix(r,120+light*0.56),mix(g,58+light*0.75),mix(b,88+light*0.66))
                    "pearl"->listOf(mix(r,108+light*0.61),mix(g,106+light*0.61),mix(b,112+light*0.60))
                    else->listOf(r,g,b)
                }
                out.setRGB(x,y,(channels[0] shl 16) or (channels[1] shl 8) or channels[2])
            }
            check(ImageIO.write(out,"png",dir.resolve("cherry_rule_"+variant+".png")))
        }
    }
}

androidComponents {
    onVariants { variant ->
        val generate=tasks.register<GenerateCherryRuleOrbs>(
            "generate"+variant.name.replaceFirstChar{it.uppercase()}+"CherryRuleOrbs") {
            sourceImage.set(layout.projectDirectory.file("src/main/res/drawable/btn_cherry_orb.jpg"))
            outputDir.set(layout.buildDirectory.dir("generated/cherryRuleOrbs/"+variant.name))
        }
        variant.sources.res?.addGeneratedSourceDirectory(generate,GenerateCherryRuleOrbs::outputDir)
    }
}
