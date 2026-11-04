plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.37.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")
    // Renders the guide pages in docs/ to HTML (Guide.kt); the Markdown parser Dokka itself uses.
    implementation("org.jetbrains:markdown-jvm:0.7.3")
}
