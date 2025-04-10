plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

android {
    namespace = "com.remedio.weassist"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.remedio.weassist"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true  // ✅ Enable View Binding
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.generativeai)
    implementation(libs.common)
    implementation(libs.firebase.functions.ktx)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.protolite.well.known.types)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)

    // Navigation Component dependencies
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.3")
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Material Design CardView
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.cardview:cardview:1.0.0")

    implementation("com.squareup.picasso:picasso:2.8")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation ("com.google.firebase:firebase-database:20.0.3")
    implementation ("com.squareup.picasso:picasso:2.8")
    implementation ("androidx.recyclerview:recyclerview:1.2.1")
    implementation("com.google.firebase:firebase-messaging-ktx:23.3.1")

    implementation("com.cloudinary:cloudinary-android:2.0.0")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Location services
    implementation ("com.google.android.gms:play-services-location:21.0.1")

    // Kotlin coroutines
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")


}