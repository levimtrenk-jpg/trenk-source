plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// הגרסה מגיעה מ-GitHub Actions (מספר הריצה). בבנייה מקומית: 1 / 1.0.0-dev
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val ciVersionName = System.getenv("VERSION_NAME") ?: "1.0.0-dev"

// החתימה מגיעה ממשתני סביבה שה-workflow ממלא מ-GitHub Secrets. ה-keystore לא נמצא בריפו.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

// ספירה אנונימית (config/stats.json בשורש הריפו): topic של ntfy וכתובת GoatCounter
val stats = groovy.json.JsonSlurper().parse(rootProject.file("../config/stats.json")) as Map<*, *>
// כתובת יצירת קשר ו-DSN של Sentry (config/site.json)
val site = groovy.json.JsonSlurper().parse(rootProject.file("../config/site.json")) as Map<*, *>

android {
    namespace = "com.trenk.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trenk.downloader"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField("String", "NTFY_TOPIC", "\"${stats["ntfy_topic"]}\"")
        buildConfigField("String", "GOATCOUNTER", "\"${stats["goatcounter"]}\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"${site["contact_email"]}\"")
        buildConfigField("String", "SENTRY_DSN", "\"${site["sentry_dsn"] ?: ""}\"")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // APK נפרד לכל ABI; בונים רק arm64-v8a (כל טלפון מודרני) כדי שהקובץ יהיה קטן
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    // youtubedl-android מריץ את python/ffmpeg/quickjs מתוך תיקיית הספריות המקומיות,
    // ולכן הן חייבות להיות מחולצות מה-APK (extractNativeLibs)
    packaging {
        jniLibs {
            useLegacyPackaging = true
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // youtubedl-android: yt-dlp + python + quickjs, ו-ffmpeg למיזוג/המרה.
    // 0.18.1 ב-JitPack נכשל בבנייה; הספרייה מתפרסמת עכשיו ב-Maven Central.
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    // שפת האפליקציה (AppCompatDelegate.setApplicationLocales) – עובד גם מתחת לאנדרואיד 13
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // דוחות קריסה (בלי NDK, בלי מידע אישי – ראו Telemetry.kt)
    implementation("io.sentry:sentry-android-core:8.59.0")
}
