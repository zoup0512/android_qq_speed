plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.feiche.qqspeed"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.feiche.qqspeed"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // 仓库自带仅供侧载的调试密钥；缺失时跳过签名配置（assembleRelease 输出未签名 apk）
        if (rootProject.file("debug.keystore").exists()) {
            create("release") {
                storeFile = rootProject.file("debug.keystore")
                storePassword = "qqspeed"
                keyAlias = "qqspeed"
                keyPassword = "qqspeed"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (rootProject.file("debug.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
}
