plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.salary.core.common"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // 启用 BuildConfig 生成，供 AppLog 工具类判断 DEBUG/RELEASE 构建类型
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.serialization.json)
}
