import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val provisionalApplicationId = "com.rasven.android.dev"
val rasvenApplicationId = providers.gradleProperty("rasven.applicationId")
    .orElse(provisionalApplicationId)
val englishSetupPreview = providers.gradleProperty("rasven.englishSetupPreview")
    .map(String::toBoolean).orElse(false)
// Keep signing secrets outside the source tree while allowing Android Studio's
// ordinary assembleRelease action to find them without a shell environment.
val localSigningFile = file("C:/Rasven Key/release-signing.properties")
val localSigning = Properties().apply {
    if (localSigningFile.isFile) {
        localSigningFile.inputStream().use { load(it) }
    }
}
val releaseKeystorePath = providers.environmentVariable("RASVEN_RELEASE_KEYSTORE_PATH")
    .orElse(localSigning.getProperty("storeFile") ?: "")
val releaseStorePassword = providers.environmentVariable("RASVEN_RELEASE_STORE_PASSWORD")
    .orElse(localSigning.getProperty("storePassword") ?: "")
val releaseKeyPassword = providers.environmentVariable("RASVEN_RELEASE_KEY_PASSWORD")
    .orElse(localSigning.getProperty("keyPassword") ?: "")
// English previews remain isolated QA fixtures; product English resources are now first-class.
if (englishSetupPreview.get() && rasvenApplicationId.get() != "com.rasven.android.qa") {
    throw GradleException("English setup preview is restricted to -Prasven.applicationId=com.rasven.android.qa")
}

android {
    namespace = "com.rasven.android"
    compileSdk = 37

    signingConfigs {
        create("rasvenRelease") {
            releaseKeystorePath.orNull?.let { storeFile = file(it) }
            storePassword = releaseStorePassword.orNull
            keyAlias = "darnio-rasven-release"
            keyPassword = releaseKeyPassword.orNull
            storeType = "pkcs12"
        }
    }

    defaultConfig {
        applicationId = rasvenApplicationId.get()
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-callfix4"
        }
        release {
            signingConfig = signingConfigs.getByName("rasvenRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    if (englishSetupPreview.get()) {
        sourceSets.getByName("androidTest").kotlin.directories += "src/englishSetupTest/java"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val verifyReleaseIdentity by tasks.registering {
    group = "verification"
    description = "Fails release builds while the Stage 2 provisional application id is still active."
    doLast {
        val id = rasvenApplicationId.get()
        if (id == provisionalApplicationId) {
            throw GradleException(
                "Release applicationId is unresolved. Set -Prasven.applicationId=<approved production id> before release builds.",
            )
        }
    }
}

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Requires the external Rasven release key and passwords before building for publication."
    doLast {
        val keyPath = releaseKeystorePath.orNull
        if (keyPath.isNullOrBlank() ||
            releaseStorePassword.orNull.isNullOrBlank() ||
            releaseKeyPassword.orNull.isNullOrBlank() ||
            !file(keyPath).isFile
        ) {
            throw GradleException(
                "Release signing is not configured. Provide the RASVEN_RELEASE_* environment " +
                    "variables or C:/Rasven Key/release-signing.properties.",
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseIdentity)
    dependsOn(verifyReleaseSigning)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.telecom)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation.core)

    testImplementation(libs.junit)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(composeBom)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
