plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
//    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {

    aaptOptions {
        noCompress("tflite") // Prevents compression of .tflite files
    }
    namespace = "project.prem.smartglasses"
    compileSdk = 35

    defaultConfig {
        applicationId = "project.prem.smartglasses"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Make the key available at runtime


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
    buildFeatures {
        compose = true
        mlModelBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1") // Example
    implementation("com.google.android.material:material:1.11.0")

    // Compose dependencies
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // CameraX
    implementation ("androidx.camera:camera-core:1.3.1")
    implementation ("androidx.camera:camera-lifecycle:1.3.1")
    implementation ("androidx.camera:camera-view:1.3.1")
    implementation("androidx.camera:camera-video:1.3.1")

    implementation ("androidx.camera:camera-camera2:1.3.1")
    implementation ("androidx.camera:camera-mlkit-vision:1.4.2")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // ML Kit Face Detection
    implementation ("com.google.mlkit:face-detection:16.1.5")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Location services
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // DocumentFile and ExitInterface
    implementation ("androidx.documentfile:documentfile:1.0.1")
    implementation ("androidx.exifinterface:exifinterface:1.3.6")

    // OpenStreetMap libraries
    implementation ("org.osmdroid:osmdroid-android:6.1.18")
    implementation ("org.osmdroid:osmdroid-wms:6.1.18")
    implementation ("org.osmdroid:osmdroid-mapsforge:6.1.18")
    implementation ("org.osmdroid:osmdroid-geopackage:6.1.18"){
        exclude(group = "com.j256.ormlite", module = "ormlite-core")    }
    // OSMBonusPack for routing
    implementation ("com.github.MKergall:osmbonuspack:6.9.0")


    // Nominatim for geocoding
    implementation ("com.android.volley:volley:1.2.1")



    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.google.maps.android:maps-compose:4.3.0")
//    implementation("com.google.android.libraries.places:places:3.3.0")
    implementation("com.google.maps:google-maps-services:2.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")



    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}