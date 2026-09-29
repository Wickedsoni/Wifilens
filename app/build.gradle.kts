import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("wifilens.android.hilt")
}

// Release signing comes from <repo root>/keystore.properties (git-ignored — never commit it or the
// .jks). Expected keys: storeFile (path relative to the repo root), storePassword, keyAlias, keyPassword.
// Without that file a release build falls back to the debug key so it can still be installed locally;
// that build is NOT uploadable to Play.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.wickedcoder.wifilens"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    sourceSets {
        // Room schema JSONs (committed in :core:database) so MigrationTestHelper can open old versions.
        getByName("androidTest").assets.directories.add("$rootDir/core/database/schemas")
    }

    defaultConfig {
        applicationId = "com.wickedcoder.wifilens"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true // R8 code shrinking/obfuscation — this alone does NOT shrink resources
            }
            isShrinkResources = true // unused resources (drawables, strings, etc.) stripped from the APK/AAB
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// Test APKs get Guava (which contains ListenableFuture) from Espresso and the accessibility checks; the app's
// standalone listenablefuture:1.0 (via core-splashscreen) would clash with it, so drop it from test classpaths only.
configurations.matching { it.name.endsWith("AndroidTestRuntimeClasspath") }.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

gradle.taskGraph.whenReady {
    if (!hasReleaseKeystore &&
        allTasks.any { it.path.startsWith(":app:") && it.name.contains("Release") && it.name.startsWith("bundle") }
    ) {
        logger.warn(
            "WARNING: keystore.properties not found — :app:bundleRelease is signed with the DEBUG key and cannot be uploaded to Play.",
        )
    }
}

dependencies {
    constraints {
        // core-splashscreen pulls concurrent-futures 1.1.0 while Espresso 3.7 needs 1.2.0, and AGP pins the
        // androidTest classpath to the app's versions; lift the app to the (compatible) newer one.
        implementation(libs.androidx.concurrent.futures)
        // Hilt brings fragment 1.5.1 transitively; the accessibility test framework needs 1.5.4+ and AGP's
        // consistent resolution pins tests to the app's version. A current fragment is better for the app anyway.
        implementation(libs.androidx.fragment)
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(project(":core:common"))
    implementation(project(":feature:analyze:presentation"))
    implementation(project(":feature:map:presentation"))
    implementation(project(":feature:map:data"))
    implementation(project(":feature:diagnose:presentation"))
    implementation(project(":feature:diagnose:data"))
    implementation(project(":feature:more:presentation"))
    implementation(project(":feature:more:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:history"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.process)
    implementation(project(":core:wifi"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(project(":core:model")) // GridPlan/CellType for MapCanvasGestureTest
    androidTestImplementation(project(":feature:map:domain")) // MapRepository for the repository/end-to-end tests
    androidTestImplementation(libs.androidx.room.testing) // MigrationTestHelper
    androidTestImplementation(libs.androidx.room.runtime) // in-memory DB for repository integration tests
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
