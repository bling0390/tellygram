import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace  = "tv.telegram"
    compileSdk = 35

    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun credential(name: String): String =
        (
            providers.gradleProperty(name).orNull
                ?: System.getenv(name)
                ?: localProps.getProperty(name, "")
            ).trim()

    val tgApiId   = credential("TG_API_ID")
    val tgApiHash = credential("TG_API_HASH")

    val proxyHost = localProps.getProperty("PROXY_HOST", "")
    val proxyPort = localProps.getProperty("PROXY_PORT", "")
    val proxyType = localProps.getProperty("PROXY_TYPE", "socks5")
    val proxyUser = localProps.getProperty("PROXY_USER", "")
    val proxyPass = localProps.getProperty("PROXY_PASS", "")

    val baseVersion = "1.2.1"

    val debugPatchBase = 171
    val baseParts = baseVersion.split(".").mapNotNull { it.toIntOrNull() }
    val baseMajor = baseParts.getOrNull(0) ?: 1
    val baseMinor = baseParts.getOrNull(1) ?: 0
    val basePatch = baseParts.getOrNull(2) ?: 0

    val versionParts = baseVersion.split(".").mapNotNull { it.toIntOrNull() }
    require(versionParts.isNotEmpty()) { "baseVersion must look like 1.2.3" }
    val derivedVersionCode = versionParts[0] * 10000 +
        (versionParts.getOrNull(1) ?: 0) * 100 +
        (versionParts.getOrNull(2) ?: 0)

    val buildNumber = (localProps.getProperty("BUILD_NUMBER", "0").toIntOrNull() ?: 0)

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    androidComponents {
        onVariants { variant ->
            if (variant.buildType == "debug") {
                variant.outputs.forEach { output ->
                    output.versionCode.set(baseMajor * 10000 + baseMinor * 100 + buildNumber)
                    val patch = (buildNumber - debugPatchBase).coerceAtLeast(basePatch + 1)
                    output.versionName.set("$baseMajor.$baseMinor.$patch-debug")
                }
            }
        }
    }

    defaultConfig {
        applicationId = "app.tellygram"
        minSdk        = 21
        targetSdk     = 34
        versionCode   = baseMajor * 10000 + baseMinor * 100 + basePatch
        versionName   = baseVersion

        buildConfigField("int",    "TG_API_ID",   tgApiId)
        buildConfigField("String", "TG_API_HASH", "\"$tgApiHash\"")
        buildConfigField("int",    "BUILD_NUMBER", buildNumber.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    val requestedAbi = providers.gradleProperty("abi").orNull?.trim().orEmpty()
    val singleAbi = requestedAbi.isNotEmpty() && requestedAbi != "universal"
    splits {
        abi {
            isEnable        = requestedAbi != "universal"
            reset()
            if (singleAbi) {
                include(requestedAbi)
                isUniversalApk = false
            } else {
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                isUniversalApk  = true
            }
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                keyAlias      = keystoreProperties["keyAlias"]      as String?
                keyPassword   = keystoreProperties["keyPassword"]   as String?
                storeFile     = (keystoreProperties["storeFile"]    as String?)
                    ?.let { rootProject.file(it) }
                storePassword = keystoreProperties["storePassword"] as String?
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"

            buildConfigField("String", "PROXY_HOST", "\"$proxyHost\"")
            buildConfigField("int",    "PROXY_PORT", if (proxyPort.isNotBlank()) proxyPort else "0")
            buildConfigField("String", "PROXY_TYPE", "\"$proxyType\"")
            buildConfigField("String", "PROXY_USER", "\"$proxyUser\"")
            buildConfigField("String", "PROXY_PASS", "\"$proxyPass\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }

            buildConfigField("String", "PROXY_HOST", "\"\"")
            buildConfigField("int",    "PROXY_PORT", "0")
            buildConfigField("String", "PROXY_TYPE", "\"\"")
            buildConfigField("String", "PROXY_USER", "\"\"")
            buildConfigField("String", "PROXY_PASS", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
        jniLibs {
            useLegacyPackaging = false
        }
    }

    afterEvaluate {
        tasks.findByName("assembleDebug")?.doLast {
            val newBuildNumber = buildNumber + 1
            val propsFile = rootProject.file("local.properties")
            val updatedProps = Properties().apply {
                if (propsFile.exists()) propsFile.inputStream().use { load(it) }
                setProperty("BUILD_NUMBER", newBuildNumber.toString())
            }
            propsFile.outputStream().use {
                updatedProps.store(it, null)
            }
            logger.lifecycle("🔢 Bumped BUILD_NUMBER to $newBuildNumber (next build: versionCode = $newBuildNumber)")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.coil.compose)

    implementation(project(":libtd"))

    implementation(libs.zxing.core)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
