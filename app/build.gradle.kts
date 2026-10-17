plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "ai.lotus.app"
  compileSdk = 37

  lint {
    // 版本号沿用 YYYYMMDDNN，保证已安装版本可以连续覆盖升级。
    // Android 的 Int versionCode 上限决定了日期方案最终会逼近上限，因此只关闭这一条结构性检查。
    disable += "HighAppVersionCode"
  }

  defaultConfig {
    applicationId = "ai.lotus.app"
    minSdk = 28
    targetSdk = 35
    versionCode = 2026101902
    versionName = "2026.10.19.2"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {
      versionNameSuffix = "-debug"
      isMinifyEnabled = false
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources {
      excludes +=
        setOf(
          "/META-INF/{AL2.0,LGPL2.1}",
          "/META-INF/*.version",
          "DebugProbesKt.bin",
          "kotlin-tooling-metadata.json",
        )
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  // ===== 液态玻璃 =====
  // 折射玻璃层：Kyant0 backdrop（折射 / 色散 / 高光 / 阴影）+ 形状库 shapes。
  implementation(libs.backdrop)
  implementation(libs.kyant.shapes)
  // 模糊底座：haze v2 的三个模块（核心 / 模糊 / 材质预设）。
  implementation(libs.haze.core)
  implementation(libs.haze.blur)
  implementation(libs.haze.blur.materials)
}
