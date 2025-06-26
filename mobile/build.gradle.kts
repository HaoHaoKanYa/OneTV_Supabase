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

    namespace = "top.cywin.onetv.mobile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "top.cywin.onetv.mobile"
        minSdk = 24
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
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
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)

    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.compose.material.icons.extended)

    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:util"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

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
    
    // Ktor客户端
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.json)
    implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-logging:${libs.versions.ktor.get()}")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}