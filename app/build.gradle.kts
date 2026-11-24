plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace   = "com.fieldconsole"
    compileSdk  = 34

    defaultConfig {
        applicationId   = "com.fieldconsole"
        minSdk          = 29
        targetSdk       = 34
        versionCode     = 11
        versionName     = "4.0.0-beta.8.dev1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled   = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable        = true
            applicationIdSuffix = ".debug"
            isMinifyEnabled     = false
            isShrinkResources   = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("src/test/resources")
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("shared"))
    sourceSets.getByName("test").resources.srcDir("schemas")
    sourceSets.getByName("test").resources.srcDir(rootProject.file("shared"))
    sourceSets.getByName("main").resources.srcDir(rootProject.file("python/src/field_intelligence_console/regional_data"))
    sourceSets.getByName("main").resources.srcDir(rootProject.file("shared/schemas"))
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

class RoomSchemaArgProvider(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("room.schemaLocation=${schemaDir.path}")
}

ksp { arg(RoomSchemaArgProvider(file("schemas"))) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Location
    implementation(libs.play.services.location)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)

    // DataStore
    implementation(libs.datastore.prefs)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
