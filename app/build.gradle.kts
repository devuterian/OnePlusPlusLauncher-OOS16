import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

fun prop(name: String) = providers.gradleProperty(name).get()
fun intProp(name: String) = prop(name).toInt()
fun stringProp(name: String) = prop(name).trim('"')

android {
    namespace = prop("project.app.packageName")
    compileSdk = intProp("project.android.compileSdk")

    defaultConfig {
        applicationId = prop("project.app.packageName")
        minSdk = intProp("project.android.minSdk")
        targetSdk = intProp("project.android.targetSdk")
        versionName = stringProp("project.app.versionName")
        versionCode = intProp("project.app.versionCode")

        buildConfigField("String", "SUPPORTED_LAUNCHER_VERSION", "\"16.6.5\"")
    }

    signingConfigs {
        val env = System.getenv()
        val dotEnv = Properties().apply {
            rootProject.file(".env").takeIf { it.exists() }?.inputStream()?.use(::load)
        }

        fun readSecret(name: String): String? = env[name] ?: dotEnv.getProperty(name)?.trim('"', '\'')

        val ksPath = readSecret("SIGNING_KEY_STORE_PATH")
        val ksAlias = readSecret("SIGNING_KEY_ALIAS")
        val ksStorePassword = readSecret("SIGNING_KEY_STORE_PASSWORD")
        val ksKeyPassword = readSecret("SIGNING_KEY_PASSWORD")

        create("release") {
            if (!ksPath.isNullOrEmpty() && !ksAlias.isNullOrEmpty() && !ksStorePassword.isNullOrEmpty() && !ksKeyPassword.isNullOrEmpty()) {
                storeFile = file(ksPath)
                storePassword = ksStorePassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            } else {
                logger.lifecycle("Release signing skipped: SIGNING_KEY_* values are incomplete.")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions"
        )
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.highcapable.yukihookapi:api:1.2.1")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.2.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
}
