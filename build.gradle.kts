plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace   = "sn.innolink.kyvshield.lite"
    compileSdk  = 34

    defaultConfig {
        minSdk     = 21
        targetSdk  = 34

        // Library version — matches JS SDK version for easy correlation
        buildConfigField("String", "SDK_VERSION", "\"0.0.5\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // AppCompat — required for Theme.AppCompat.NoActionBar and AppCompatActivity
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Activity KTX — ActivityResultContract, OnBackPressedDispatcher, etc.
    implementation("androidx.activity:activity-ktx:1.9.3")

    // Modern WebView features (Safe Browsing, WebViewCompat, etc.)
    implementation("androidx.webkit:webkit:1.12.1")

    // Kotlin coroutines (used for suspendCancellableCoroutine in permission helpers)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Core KTX
    implementation("androidx.core:core-ktx:1.13.1")

    // ── Test ──────────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// ── Maven publishing (optional — for local/private Maven repos) ───────────────

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId    = "sn.innolink"
            artifactId = "kyvshield-lite"
            version    = "0.0.5"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("KyvShield Lite Android SDK")
                description.set("WebView-based KYC SDK for Android — API-compatible with Flutter Lite SDK")
                url.set("https://kyvshield.sn")

                licenses {
                    license {
                        name.set("Proprietary")
                        url.set("https://kyvshield.sn/license")
                    }
                }

                developers {
                    developer {
                        id.set("innolink")
                        name.set("Innolink")
                        email.set("dev@innolink.sn")
                    }
                }
            }
        }
    }
}
