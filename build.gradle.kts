import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// One place that sets up every module the same way (the spec's "convention plugins").
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt.yml"))
    }

    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            (this as? org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions)?.jvmTarget?.set(JvmTarget.JVM_17)
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinProjectExtension> { jvmToolchain(17) }
    }

    fun BaseExtension.shared() {
        compileSdkVersion(36)
        defaultConfig {
            minSdk = 29
            targetSdk = 36
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions.unitTests.isIncludeAndroidResources = true
        // Robolectric runs tests on SDK 35 (SDK 36 needs Java 21).
        sourceSets.getByName("test").resources.srcDir(rootProject.file("config/robolectric"))
    }
    plugins.withId("com.android.library") { extensions.configure<BaseExtension> { shared() } }
    plugins.withId("com.android.application") { extensions.configure<BaseExtension> { shared() } }
}
