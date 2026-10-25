import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Релизный ключ RuStore — вне репозитория: keystore.properties (storeFile, storePassword, keyAlias, keyPassword).
// Без файла release собирается неподписанным.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use(::load)
    }
}

android {
    namespace = "com.workorder.app"
    compileSdk = 34

    defaultConfig {
        // Пакет для RuStore: короткий и постоянный — после публикации его не сменить.
        applicationId = "ru.workorder"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "4.2"
        buildConfigField("boolean", "ENABLE_WEAR_SYNC", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Сборка разработчика ставится рядом с выпуском из RuStore и не трогает его данные.
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "ENABLE_WEAR_SYNC", "false")
        }
        // Выпуск для RuStore и GitHub: пакет ru.workorder, релизный ключ (не отладочный).
        // Ключ нужно беречь — обновление с другим ключом поверх не встанет.
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // Instrumentation is isolated from the real user database by default.
    testBuildType = "qa"
    testOptions {
        unitTests {
            // Robolectric рисует экраны в модульных тестах (ScreenshotTourTest).
            isIncludeAndroidResources = true
            all { test ->
                // Снимки экранов для проверки вёрстки: .\gradlew.bat testDebugUnitTest -Pscreenshots --tests "*ScreenshotTourTest*"
                // В обычный прогон не входят: Robolectric-экраны медленные и оставляют фоновые подписки на базу.
                if (!project.hasProperty("screenshots")) test.exclude("**/ui/ScreenshotTourTest*", "**/ui/DialogScreenshotTest*", "**/ui/StoreAssetsTest*")
                test.systemProperty("workorder.screenshots", if (project.hasProperty("screenshots")) "1" else "")
                test.systemProperty("workorder.screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.path)
                test.systemProperty("workorder.storeDir", layout.buildDirectory.dir("store").get().asFile.path)
            }
        }
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
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
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.gson)
    implementation(libs.itext7.core)
    implementation(libs.opencsv)
    implementation(libs.play.services.wearable.china)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
