plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(project(":extraction"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mime4j.core)
    implementation(libs.mime4j.dom)
    implementation(libs.java.libpst)
    implementation(libs.angus.mail)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    testImplementation(libs.greenmail)
}
