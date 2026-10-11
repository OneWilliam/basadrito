plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

application {
    mainClass.set("org.basadrito.tools.ProbeKt")
}
dependencies {
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.clientCore)
    implementation(libs.dotenv.kotlin)
}
tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}
