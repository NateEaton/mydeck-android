plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutLibraries)
}

fun networkSecurityConfigRef(allowHttp: Boolean, allowUserCa: Boolean): String = when {
    allowHttp && allowUserCa -> "@xml/network_security_config_insecure"
    allowHttp -> "@xml/network_security_config_http"
    allowUserCa -> "@xml/network_security_config_user_ca"
    else -> "@xml/network_security_config"
}

android {
    namespace = "com.mydeck.app"
    compileSdk = 35

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        multiDexEnabled = true
        applicationId = "com.mydeck.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 14006
        versionName = "0.14.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Single source of truth for the OAuth authorization-code redirect URI.
        // The manifest intent-filter, MainActivity's callback matcher, and the use case's
        // registered redirect_uri all derive from these — change them here only.
        // Every non-production flavor overrides the scheme (see below) so its build can
        // coexist with the production install without an app-chooser on the browser redirect.
        // NOTE: changing the scheme requires a CLEAN build — REDIRECT_URI is a constant-folded
        // BuildConfig expression that incremental compilation may not recompile.
        val oauthCallbackScheme = "mydeck"
        val oauthCallbackHost = "oauth-callback"
        manifestPlaceholders["oauthCallbackScheme"] = oauthCallbackScheme
        manifestPlaceholders["oauthCallbackHost"] = oauthCallbackHost
        buildConfigField("String", "OAUTH_CALLBACK_SCHEME", "\"$oauthCallbackScheme\"")
        buildConfigField("String", "OAUTH_CALLBACK_HOST", "\"$oauthCallbackHost\"")
    }

    signingConfigs {
        create("release") {
            val appKeystoreFile = System.getenv("KEYSTORE")
            if (appKeystoreFile != null && file(appKeystoreFile).exists()) {
                storeFile = file(appKeystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            buildConfigField("boolean", "SHOW_CARD_INDEX_OVERLAY", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            buildConfigField("boolean", "SHOW_CARD_INDEX_OVERLAY", "false")
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("githubSnapshot") {
            dimension = "version"
            applicationIdSuffix = ".snapshot"
            versionName = System.getenv("SNAPSHOT_VERSION_NAME") ?: "${defaultConfig.versionName}-snapshot"
            versionCode = System.getenv("SNAPSHOT_VERSION_CODE")?.toInt() ?: defaultConfig.versionCode
            manifestPlaceholders["appLabel"] = "MyDeck Snapshot"
            manifestPlaceholders["networkSecurityConfig"] = networkSecurityConfigRef(
                allowHttp = false,
                allowUserCa = false
            )
            buildConfigField("boolean", "ALLOW_INSECURE_HTTP", "false")
            buildConfigField("boolean", "ALLOW_USER_CA_CERTIFICATES", "false")
            buildConfigField("boolean", "IS_HTTP_ENABLED_BUILD", "false")
            // Distinct OAuth callback scheme so this build can coexist with production
            // (and other variants) without the browser redirect popping an app-chooser.
            manifestPlaceholders["oauthCallbackScheme"] = "mydeck-snapshot"
            buildConfigField("String", "OAUTH_CALLBACK_SCHEME", "\"mydeck-snapshot\"")
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("githubSnapshotHttp") {
            dimension = "version"
            applicationIdSuffix = ".snapshot.permissive"
            versionName = System.getenv("SNAPSHOT_VERSION_NAME") ?: "${defaultConfig.versionName}-snapshot"
            versionCode = System.getenv("SNAPSHOT_VERSION_CODE")?.toInt() ?: defaultConfig.versionCode
            manifestPlaceholders["appLabel"] = "MyDeck HTTP Snapshot"
            manifestPlaceholders["networkSecurityConfig"] = networkSecurityConfigRef(
                allowHttp = true,
                allowUserCa = true
            )
            buildConfigField("boolean", "ALLOW_INSECURE_HTTP", "true")
            buildConfigField("boolean", "ALLOW_USER_CA_CERTIFICATES", "true")
            buildConfigField("boolean", "IS_HTTP_ENABLED_BUILD", "true")
            // Distinct OAuth callback scheme so this build can coexist with production
            // (and other variants) without the browser redirect popping an app-chooser.
            manifestPlaceholders["oauthCallbackScheme"] = "mydeck-snapshot-permissive"
            buildConfigField("String", "OAUTH_CALLBACK_SCHEME", "\"mydeck-snapshot-permissive\"")
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("githubRelease") {
            dimension = "version"
            versionName = System.getenv("RELEASE_VERSION_NAME") ?: defaultConfig.versionName
            versionCode = System.getenv("RELEASE_VERSION_CODE")?.toInt() ?: defaultConfig.versionCode
            manifestPlaceholders["appLabel"] = "MyDeck"
            manifestPlaceholders["networkSecurityConfig"] = networkSecurityConfigRef(
                allowHttp = false,
                allowUserCa = false
            )
            buildConfigField("boolean", "ALLOW_INSECURE_HTTP", "false")
            buildConfigField("boolean", "ALLOW_USER_CA_CERTIFICATES", "false")
            buildConfigField("boolean", "IS_HTTP_ENABLED_BUILD", "false")
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("githubReleaseHttp") {
            dimension = "version"
            applicationIdSuffix = ".permissive"
            versionName = System.getenv("RELEASE_VERSION_NAME") ?: defaultConfig.versionName
            versionCode = System.getenv("RELEASE_VERSION_CODE")?.toInt() ?: defaultConfig.versionCode
            manifestPlaceholders["appLabel"] = "MyDeck HTTP"
            manifestPlaceholders["networkSecurityConfig"] = networkSecurityConfigRef(
                allowHttp = true,
                allowUserCa = true
            )
            buildConfigField("boolean", "ALLOW_INSECURE_HTTP", "true")
            buildConfigField("boolean", "ALLOW_USER_CA_CERTIFICATES", "true")
            buildConfigField("boolean", "IS_HTTP_ENABLED_BUILD", "true")
            // Distinct OAuth callback scheme so this build can coexist with production
            // (and other variants) without the browser redirect popping an app-chooser.
            manifestPlaceholders["oauthCallbackScheme"] = "mydeck-permissive"
            buildConfigField("String", "OAUTH_CALLBACK_SCHEME", "\"mydeck-permissive\"")
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "-XX:CompileCommand=exclude,androidx/room/driver/SupportSQLiteStatement\$SupportAndroidSQLiteStatement.ensureCursor"
                )
            }
        }
    }

    sourceSets {
        getByName("debug").assets.srcDirs(files("$projectDir/schemas"))
        getByName("githubSnapshot").res.srcDir("src/snapshotShared/res")
        getByName("githubSnapshotHttp").res.srcDir("src/snapshotShared/res")
    }

    lint {
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this
            if (output is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                val httpSuffix = if (variant.productFlavors.any { it.name.endsWith("Http") }) {
                    "-http-enabled"
                } else {
                    ""
                }
                val newName = "MyDeck-${variant.versionName}$httpSuffix.apk"
                output.outputFileName = newName
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.ui.test.junit4.android)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.webkit)

    // hilt
    ksp(libs.dagger.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation)
    implementation(libs.dagger.hilt.android)
    testImplementation(libs.dagger.hilt.android.testing)
    kspTest(libs.androidx.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.timber)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.compose)
    
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlinx.datetime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.okhttp3.logging.interceptor)
    testImplementation(libs.okhttp3.mockserver)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.security.crypto)
    implementation(libs.google.crypto.tink)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.accompanist.permissions)
    
    // Markdown rendering
    implementation(libs.markwon.core) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.ext.strikethrough) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.ext.tables) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.ext.tasklist) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.image) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.syntax.highlight) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
}

tasks.register("assembleDebugAll") {
    group = "verification"
    description = "Assembles all debug flavor variants."
    dependsOn(
        ":app:assembleGithubReleaseDebug",
        ":app:assembleGithubReleaseHttpDebug",
        ":app:assembleGithubSnapshotDebug",
        ":app:assembleGithubSnapshotHttpDebug"
    )
}

tasks.register("testDebugUnitTestAll") {
    group = "verification"
    description = "Runs unit tests for all debug flavor variants."
    dependsOn(
        ":app:testGithubReleaseDebugUnitTest",
        ":app:testGithubReleaseHttpDebugUnitTest",
        ":app:testGithubSnapshotDebugUnitTest",
        ":app:testGithubSnapshotHttpDebugUnitTest"
    )
}

tasks.register("lintDebugAll") {
    group = "verification"
    description = "Runs lint for all debug flavor variants."
    dependsOn(
        ":app:lintGithubReleaseDebug",
        ":app:lintGithubReleaseHttpDebug",
        ":app:lintGithubSnapshotDebug",
        ":app:lintGithubSnapshotHttpDebug"
    )
}

aboutLibraries {
    android {
        registerAndroidTasks = false
    }
    export {
        prettyPrint = true
    }
    collect {
        configPath.set(file("../config"))
    }
}

tasks.whenTaskAdded {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}
