plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")  // Из нового
}

android {
    namespace = "com.example.stagemon"  // Старое имя
    compileSdk = 34  // Из нового (36 пока рано, 34 стабильнее)

    defaultConfig {
        applicationId = "com.example.stagemon"  // Старое
        minSdk = 31
        targetSdk = 34

        // Version code — из старого (автоинкремент)
        versionCode = 2
        versionName = "0.16.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // НАТИВ — ИЗ НОВОГО (твой движок)
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Все архитектуры из нового
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
            }
        }

        // NDK abiFilters — из старого (дубль, можно убрать если externalNativeBuild есть)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    ndkVersion = "29.0.14206865"  // ИЗ НОВОГО! Фиксируем версию

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
        // ИЗ СТАРОГО — Java 17 (Oboe нормально с 17)
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // ИЗ СТАРОГО
        jvmTarget = "17"
    }

    buildFeatures {
        prefab = true  // Общее
        viewBinding = true  // ИЗ НОВОГО! Очень удобно
    }

    // НАТИВ — ИЗ НОВОГО
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // packaging — ИЗ СТАРОГО (для lottie и jniLibs)
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Core — ИЗ СТАРОГО (новее)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // Lottie — ИЗ СТАРОГО (для анимации)
    implementation("com.airbnb.android:lottie:6.7.1")

    // Gson — ИЗ СТАРОГО (для сохранения списков)
    implementation("com.google.code.gson:gson:2.13.2")

    // Oboe — ИЗ НОВОГО (твой аудиодвижок)
    implementation("com.google.oboe:oboe:1.10.0")

    // Media — ИЗ НОВОГО (для работы с аудио)
    implementation("androidx.media:media:1.7.1")
    // WaveformSeekBar
    implementation("com.github.massoudss:waveformSeekBar:5.0.2")
    // Amplituda (нужна для загрузки аудиофайлов)
    implementation("com.github.lincollincol:amplituda:2.3.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    implementation("dev.aige.pub:WheelPicker:1.2.0")


    // Тесты
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}