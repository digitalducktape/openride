plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.digitalducktape.openride"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.digitalducktape.openride"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // T3 / #3: toggle between MockBikeDataSource and the real (unverified-on-hardware)
        // Gen 2 sensor binding in AppContainer. Defaults to false (Mock) — flip only when
        // building for the actual bike tablet, see PelotonBikeDataSource's TODOs.
        buildConfigField("boolean", "USE_REAL_BIKE_SENSOR", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // T3/#3: the real-sensor build. Identical to debug but flips USE_REAL_BIKE_SENSOR on
        // so AppContainer wires PelotonBikeDataSource (the live Gen 2 affernet binding) instead
        // of MockBikeDataSource. Installs alongside the mock build via a .real applicationId
        // suffix so both can coexist on the tablet. Build/install the real-sensor APK with:
        //     ./gradlew :app:installDebugReal
        create("debugReal") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".real"
            versionNameSuffix = "-real"
            buildConfigField("boolean", "USE_REAL_BIKE_SENSOR", "true")
            matchingFallbacks += "debug"
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
        // T3/#3: the real Gen 2 sensor binding talks to Peloton's affernet system service
        // through a reconstructed AIDL interface (app/src/main/aidl/com/onepeloton/...),
        // so the AIDL toolchain generates the Stub/proxy with the correct transaction codes
        // and Parcel marshalling instead of hand-rolled Parcel.transact() guesses.
        aidl = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))

    // Instrumented (on-device) tests. Used by SensorBindingInstrumentedTest to verify the
    // real Gen 2 affernet bind/decode pipeline on the physical bike tablet (T3/#3).
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation("androidx.test:runner:1.6.1")
}
