import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import java.io.FileInputStream
import java.util.Properties
import java.io.File

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// 加载签名配置
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    println("已加载签名配置文件: key.properties")
}

allprojects {
    // 移除jitpack.io仓库配置
    
    val appConfig: BaseAppModuleExtension.() -> Unit = {
        signingConfigs {
            // 创建发布签名配置
            create("release") {
                // 1. 首先检查环境变量（CI/CD工作流使用）
                val envKeystore = System.getenv("KEYSTORE")
                val envStorePassword = System.getenv("KEYSTORE_PASSWORD")
                val envKeyAlias = System.getenv("KEY_ALIAS")
                val envKeyPassword = System.getenv("KEY_PASSWORD")
                
                if (!envKeystore.isNullOrEmpty()) {
                    // CI/CD环境使用环境变量中的密钥库
                    println("使用环境变量中的签名配置")
                    // 检查项目目录下是否有keystore文件
                    val projectKeystoreFile = File(project.projectDir, envKeystore)
                    if (projectKeystoreFile.exists()) {
                        // 优先使用项目目录下的keystore
                        storeFile = projectKeystoreFile
                    } else {
                        // 如果项目目录下没有，使用根目录的keystore
                        storeFile = file(envKeystore)
                    }
                    storePassword = envStorePassword
                    keyAlias = envKeyAlias
                    keyPassword = envKeyPassword
                } else {
                    // 2. 本地开发环境使用key.properties中的配置
                    val keyPropStoreFile = keystoreProperties.getProperty("storeFile")
                    
                    if (keyPropStoreFile != null) {
                        // 处理路径中的空格问题
                        val normalizedPath = keyPropStoreFile.trim()
                        val keystoreFile = rootProject.file(normalizedPath)
                        
                        if (keystoreFile.exists()) {
                            println("使用key.properties中的签名配置: ${keystoreFile.absolutePath}")
                            storeFile = keystoreFile
                            storePassword = keystoreProperties.getProperty("storePassword")
                            keyAlias = keystoreProperties.getProperty("keyAlias")
                            keyPassword = keystoreProperties.getProperty("keyPassword")
                        } else {
                            println("警告: 在 ${keystoreFile.absolutePath} 找不到密钥库文件")
                            // 如果找不到密钥库文件，则使用调试密钥
                            val debugKeystore = "${System.getProperty("user.home")}/.android/debug.keystore"
                            if (File(debugKeystore).exists()) {
                                println("使用调试密钥: $debugKeystore")
                                storeFile = File(debugKeystore)
                                storePassword = "android"
                                keyAlias = "androiddebugkey"
                                keyPassword = "android"
                            } else {
                                println("警告: 找不到调试密钥，构建可能会失败")
                            }
                        }
                    } else {
                        // 3. 如果没有配置，使用调试密钥
                        println("未找到签名配置，使用调试密钥")
                        val debugKeystore = "${System.getProperty("user.home")}/.android/debug.keystore"
                        if (File(debugKeystore).exists()) {
                            println("使用调试密钥: $debugKeystore")
                            storeFile = File(debugKeystore)
                            storePassword = "android"
                            keyAlias = "androiddebugkey"
                            keyPassword = "android"
                        } else {
                            println("警告: 找不到调试密钥，构建可能会失败")
                        }
                    }
                }
            }
        }

        applicationVariants.all {
            outputs.all {
                val ver = defaultConfig.versionName
                val minSdk =
                    project.extensions.getByType(BaseAppModuleExtension::class.java).defaultConfig.minSdk
                val abi = filters.find { it.filterType == "ABI" }?.identifier ?: "all"
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                    "onetv-android-${project.name}-$ver-${abi}-sdk$minSdk.apk"
            }
        }
    }

    extra["appConfig"] = appConfig
}