plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    // Renders the guide pages in docs/ to HTML (Guide.kt); the Markdown parser Dokka itself uses.
    implementation(libs.markdown.jvm)
    // The klyph Gradle plugin, from the build buildSrc/settings.gradle.kts includes: klyph.native
    // applies its library mode, which owns the target rules and the SDL3 lookup.
    implementation("com.eventslooped.klyph:klyph-gradle-plugin")
}
