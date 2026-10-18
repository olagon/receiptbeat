plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// Rules parser accuracy on testdata/receipts. Fails when total accuracy drops under 90%.
tasks.register<JavaExec>("extractionEval") {
    group = "verification"
    description = "Runs the rules parser on testdata/receipts and prints accuracy per field."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.kealohalabs.receiptbeat.extraction.eval.EvalKt")
    args(rootProject.file("testdata/receipts").absolutePath)
}
