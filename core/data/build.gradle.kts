import org.jetbrains.kotlin.konan.properties.Properties as KonanProperties
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 加载local.properties文件
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

// 获取环境变量或从local.properties中获取
fun getEnvOrProperty(key: String): String {
    // 首先尝试从系统环境变量获取
    val envValue = System.getenv(key)
    if (!envValue.isNullOrEmpty()) {
        println("从环境变量获取 $key: ${envValue.take(10)}...")
        return envValue
    }

    // 然后尝试从local.properties获取
    val propValue = localProperties.getProperty(key)
    if (!propValue.isNullOrEmpty()) {
        println("从local.properties获取 $key: ${propValue.take(10)}...")
        return propValue
    }

    println("警告: 未找到 $key 的值")
    return ""
}

android {
    namespace = "top.cywin.onetv.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // 使用改进的方法获取环境变量
        buildConfigField("String", "BOOTSTRAP_URL", "\"${getEnvOrProperty("BOOTSTRAP_URL")}\"")
        buildConfigField("String", "BOOTSTRAP_KEY", "\"${getEnvOrProperty("BOOTSTRAP_KEY")}\"")
    }

    buildTypes {
        debug {
            // 使用改进的方法获取环境变量
            buildConfigField("String", "BOOTSTRAP_URL", "\"${getEnvOrProperty("BOOTSTRAP_URL")}\"")
            buildConfigField("String", "BOOTSTRAP_KEY", "\"${getEnvOrProperty("BOOTSTRAP_KEY")}\"")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // 使用改进的方法获取环境变量
            buildConfigField("String", "BOOTSTRAP_URL", "\"${getEnvOrProperty("BOOTSTRAP_URL")}\"")
            buildConfigField("String", "BOOTSTRAP_KEY", "\"${getEnvOrProperty("BOOTSTRAP_KEY")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // 启用BuildConfig生成
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("commons-codec:commons-codec:1.15") // 使用最新稳定版本

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Supabase依赖
    implementation(platform(libs.supabase.bom))

    // Supabase核心模块
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

    // Koin依赖注入
    implementation("io.insert-koin:koin-core:3.5.3")
    implementation("io.insert-koin:koin-android:3.5.3")

    implementation(libs.qrose)
}