plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.room.common)
    testImplementation(libs.junit)
}
