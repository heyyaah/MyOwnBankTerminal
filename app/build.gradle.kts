import com.android.build.api.dsl.Packaging

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.myownbank.terminal"
    compileSdk {
        version = release(36)

        buildFeatures {
            viewBinding = true
            fun Packaging.() {
                jniLibs {
                    useLegacyPackaging = true
                }
            }

            defaultConfig {
                applicationId = "com.myownbank.terminal"
                minSdk = 26
                targetSdk = 36
                versionCode = 3
                versionName = "1.2"

                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            signingConfigs {
                create("release") {
                    storeFile = file("../myownbank.keystore")
                    storePassword = "android123"
                    keyAlias = "myownbank"
                    keyPassword = "android123"
                }
            }

            buildTypes {
                release {
                    isMinifyEnabled = false
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )
                    signingConfig = signingConfigs.getByName("release")
                }
            }

            lint {
                checkReleaseBuilds = false
                abortOnError = false
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
            //noinspection WrongGradleMethod
            kotlinOptions {
                jvmTarget = "11"
            }
        }

        //noinspection WrongGradleMethod
        dependencies {
            implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
            implementation("com.google.firebase:firebase-analytics")
            implementation("com.google.firebase:firebase-database-ktx:20.3.0")
            implementation("com.google.mlkit:face-detection:16.1.5")
            implementation("androidx.camera:camera-core:1.3.0")
            implementation("androidx.camera:camera-camera2:1.3.0")
            implementation("androidx.camera:camera-lifecycle:1.3.0")
            implementation("androidx.camera:camera-view:1.3.0")
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
            implementation(libs.androidx.activity)
            implementation(libs.androidx.constraintlayout)
            testImplementation(libs.junit)
            androidTestImplementation(libs.androidx.junit)
            androidTestImplementation(libs.androidx.espresso.core)
        }
    }}