plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.kealohalabs.receiptbeat"

    defaultConfig {
        applicationId = "org.kealohalabs.receiptbeat"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += listOf("META-INF/*.md", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":mail"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.datastore.preferences)

    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.turbine)
    testImplementation(libs.work.testing)
}
