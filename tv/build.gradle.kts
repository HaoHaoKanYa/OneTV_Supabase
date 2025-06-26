import com.android.build.gradle.internal.dsl.BaseAppModuleExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.jetpack.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    @Suppress("UNCHECKED_CAST")
    apply(extra["appConfig"] as BaseAppModuleExtension.() -> Unit)

    namespace = "top.cywin.onetv.tv"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "top.cywin.onetv.tv"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "2.0.0"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
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
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 为调试版本也使用相同的签名配置，确保测试时的签名一致性
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        // 使用等号进行赋值
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

//    splits {
//        abi {
//            isEnable = true
//            isUniversalApk = false
//            reset()
//            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
//        }
//    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.compose.material.icons.extended)

    // 播放器
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    implementation(libs.okhttp)

    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:util"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    coreLibraryDesugaring(libs.desugar.jdk)
    
    // Compose相关
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    
    // Supabase依赖
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
    
    // Supabase集成模块
    implementation("io.github.jan-tennert.supabase:apollo-graphql:${libs.versions.supabase.get()}")
    implementation("io.github.jan-tennert.supabase:compose-auth:${libs.versions.supabase.get()}")
    implementation("io.github.jan-tennert.supabase:compose-auth-ui:${libs.versions.supabase.get()}")
    implementation("io.github.jan-tennert.supabase:coil-integration:${libs.versions.supabase.get()}")
    implementation("io.github.jan-tennert.supabase:auth-kt:${libs.versions.supabase.get()}")
    
    // Ktor客户端
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.json)
    implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-logging:${libs.versions.ktor.get()}")

// // 二维码
      implementation(libs.qrose)
     implementation(libs.coil.compose)
    // AndroidAsync HTTP服务器
    implementation("com.koushikdutta.async:androidasync:${libs.versions.androidasync.get()}")
    
    // RTSP播放器支持
    implementation("com.github.alexeyvasilyev:rtsp-client-android:1.2.0")
    implementation("com.github.pedroSG94:rtmp-rtsp-stream-client-java:2.1.9")
    
    // ViewModel相关
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${libs.versions.lifecycleRuntimeKtx.get()}")

    // 图片加载
    implementation("io.coil-kt:coil-compose:2.5.0")
}