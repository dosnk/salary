import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// ========== 版本号管理 ==========
// 从 version.properties 读取版本号，构建后自动递增 BUILD_NUMBER
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        load(versionPropsFile.inputStream())
    } else {
        // 文件不存在时使用默认值
        setProperty("MAJOR", "1")
        setProperty("MINOR", "0")
        setProperty("PATCH", "0")
        setProperty("BUILD_NUMBER", "1")
    }
}

val majorVersion = (versionProps.getProperty("MAJOR") ?: "1").toInt()
val minorVersion = (versionProps.getProperty("MINOR") ?: "0").toInt()
val patchVersion = (versionProps.getProperty("PATCH") ?: "0").toInt()
val buildNumber = (versionProps.getProperty("BUILD_NUMBER") ?: "1").toInt()

// 语义化版本名：MAJOR.MINOR.PATCH
val appVersionName = "$majorVersion.$minorVersion.$patchVersion"
// 版本代码：MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + BUILD_NUMBER
// 确保每次构建 versionCode 递增，且同一版本内构建号越大值越大
val appVersionCode = majorVersion * 1000000 + minorVersion * 10000 + patchVersion * 100 + buildNumber

android {
    namespace = "com.salary.manager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salary.manager"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "${appVersionName}-${buildNumber}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:3000\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BASE_URL", "\"https://api.salary.com\"")
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
    }

    // ========== 自定义APK输出文件名 ==========
    // 格式：salary-{版本名}-{构建类型}.apk
    // 例：salary-1.0.0-1-debug.apk, salary-1.0.0-1-release.apk
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val buildTypeLabel = variant.buildType.name
            outputImpl.outputFileName = "salary-${appVersionName}-${buildNumber}-${buildTypeLabel}.apk"
        }
    }
}

// ========== 构建完成后自动递增 BUILD_NUMBER ==========
// 仅在 release 构建或显式执行 assembleDebug/assembleRelease 时递增
tasks.whenTaskAdded {
    if (name in listOf("assembleDebug", "assembleRelease", "bundleRelease")) {
        val incrementTaskName = "incrementBuildNumber"
        // 注册递增任务（确保只注册一次）
        if (tasks.findByName(incrementTaskName) == null) {
            tasks.register(incrementTaskName) {
                doLast {
                    val newBuildNumber = buildNumber + 1
                    versionProps.setProperty("BUILD_NUMBER", newBuildNumber.toString())
                    versionProps.store(versionPropsFile.outputStream(), null)
                    println("========================================")
                    println("构建号已递增: $buildNumber -> $newBuildNumber")
                    println("下次构建版本: salary-${appVersionName}-${newBuildNumber}")
                    println("========================================")
                }
            }
        }
        // 在打包任务完成后执行递增
        finalizedBy(tasks.named(incrementTaskName))
    }
}

dependencies {
    // 项目模块
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(project(":core:ui"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:statistics"))
    implementation(project(":feature:ai"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:messages"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
}
