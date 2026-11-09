import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// Optional build-time Trakt Client ID. Client IDs are public app identifiers,
// not secrets, but keeping it out of source means each build/person can supply
// their own. Precedence: -PtraktClientId > TRAKT_CLIENT_ID env > local.properties.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

val traktClientId = (
    providers.gradleProperty("traktClientId").orNull
        ?: providers.environmentVariable("TRAKT_CLIENT_ID").orNull
        ?: localProperties.getProperty("trakt.clientId")
        ?: ""
).trim()

val escapedTraktClientId = traktClientId
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

// Optional build-time Sentry DSN. Sentry stays disabled when none is supplied.
// Precedence: -PsentryDsn > SENTRY_DSN env > local.properties.
val sentryDsn = (
    providers.gradleProperty("sentryDsn").orNull
        ?: providers.environmentVariable("SENTRY_DSN").orNull
        ?: localProperties.getProperty("sentry.dsn")
        ?: ""
).trim()

val escapedSentryDsn = sentryDsn
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

// Optional build-time TMDB API key (v3 "API Key"). TMDB supplies the "where to
// watch" streaming providers; without a key the app falls back to services the
// user pins by hand. Precedence: -PtmdbApiKey > TMDB_API_KEY env > local.properties.
val tmdbApiKey = (
    providers.gradleProperty("tmdbApiKey").orNull
        ?: providers.environmentVariable("TMDB_API_KEY").orNull
        ?: localProperties.getProperty("tmdb.apiKey")
        ?: ""
).trim()

val escapedTmdbApiKey = tmdbApiKey
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull

fun versionCodeFor(versionName: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(versionName)
        ?: throw GradleException(
            "appVersionName \"$versionName\" must start with major.minor.patch",
        )
    val (major, minor, patch) = match.destructured
    return major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
}

val appVersionName = (
    providers.gradleProperty("appVersionName").orNull
        ?: providers.environmentVariable("APP_VERSION_NAME").orNull
        ?: "0.1.0"
).trim().removePrefix("v")

val appVersionCode = (
    providers.gradleProperty("appVersionCode").orNull
        ?: providers.environmentVariable("APP_VERSION_CODE").orNull
)
    ?.toIntOrNull()
    ?: versionCodeFor(appVersionName)

android {
    namespace = "ca.gibert.movietracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "ca.gibert.movietracker"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Empty when not supplied at build time; the app then asks for it at
        // first launch. It can always be changed in Settings.
        buildConfigField(
            "String",
            "DEFAULT_TRAKT_CLIENT_ID",
            "\"$escapedTraktClientId\"",
        )

        // Empty when not supplied at build time; streaming providers then come
        // only from services pinned in the app.
        buildConfigField(
            "String",
            "TMDB_API_KEY",
            "\"$escapedTmdbApiKey\"",
        )

        // Empty when not supplied at build time, which disables Sentry.
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"$escapedSentryDsn\"",
        )
    }

    signingConfigs {
        // Release signing is optional. CI supplies these environment variables
        // from repository secrets; without them the release APK is unsigned.
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.coil.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sentry.android.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
