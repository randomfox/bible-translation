plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.randomfox.bible"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.randomfox.bible"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

// The bundled database is generated from the project's bible.sqlite.
val bibleDb = tasks.register<Exec>("bibleDb") {
    val root = rootProject.projectDir.parentFile
    inputs.file(File(root, "bible.sqlite"))
    inputs.file(File(root, "scripts/app_db.py"))
    outputs.file(file("src/main/assets/bible.db"))
    workingDir = root
    commandLine("python3", "-I", "scripts/app_db.py")
}
tasks.named("preBuild") { dependsOn(bibleDb) }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
