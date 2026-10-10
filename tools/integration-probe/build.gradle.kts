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
}
