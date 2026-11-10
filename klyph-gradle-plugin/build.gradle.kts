import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    // Gradle's Action parameters become lambdas with a receiver, as in build scripts.
    alias(libs.plugins.kotlin.sam.with.receiver)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.detekt)
}

group = "com.eventslooped.klyph"
// -Pklyph.version=<v> overrides the version, as in the engine build's root build.gradle.kts.
version = providers.gradleProperty("klyph.version").getOrElse("1.1.0")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // The plugin applies the Kotlin Multiplatform plugin of this version, so a game build does
    // not declare Kotlin itself.
    implementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    website = "https://github.com/inemtsev/klyph"
    vcsUrl = "https://github.com/inemtsev/klyph"
    plugins {
        register("klyph") {
            id = "com.eventslooped.klyph"
            implementationClass = "com.eventslooped.klyph.gradle.KlyphPlugin"
            displayName = "klyph"
            description = "Builds, runs and packages Kotlin/Native games made with the klyph engine."
            tags = listOf("kotlin", "kotlin-native", "gamedev", "sdl3")
        }
        register("klyphLibrary") {
            id = "com.eventslooped.klyph.library"
            implementationClass = "com.eventslooped.klyph.gradle.KlyphLibraryPlugin"
            displayName = "klyph library"
            description = "Builds Kotlin/Native libraries for klyph games: the klyph targets and SDL3 for their tests, without executables."
            tags = listOf("kotlin", "kotlin-native", "gamedev", "sdl3")
        }
    }
}

// Unit tests run in `test`. TestKit tests, which apply the plugin to a throwaway project, run in
// `functionalTest` against the plugin classpath that java-gradle-plugin writes for them.
val functionalTest: SourceSet = sourceSets.create("functionalTest")
gradlePlugin.testSourceSets(functionalTest)

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter)
    // java-gradle-plugin puts the Gradle API on the compile classpath only.
    testImplementation(gradleApi())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    "functionalTestImplementation"(kotlin("test-junit5"))
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the TestKit tests, which apply the plugin to throwaway projects."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(functionalTestTask)
}

// The engine build's detekt configuration and layout, as klyph.lint in buildSrc sets them for the
// engine modules: one baseline next to this file and `check` running the `detekt` task over src/.
// This is a JVM build, so that task also gets the compiled classes and classpaths of all three
// source sets, which turns on type resolution and the rules that need it.
detekt {
    config.setFrom(layout.settingsDirectory.file("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    baseline = layout.projectDirectory.file("detekt-baseline.xml")
    basePath = layout.settingsDirectory.dir("..")
    source.setFrom(layout.projectDirectory.dir("src"))
    parallel = true
}

// detekt's analysis does not load the sam-with-receiver compiler plugin (passing it with -Xplugin
// changes nothing), so inside Action lambdas such as tasks.register { } the receiver's members do
// not resolve and the rules see them as unknown; the run still succeeds.
val analysisClasspath: FileCollection = files(sourceSets.map { it.compileClasspath + it.output })
tasks.named<Detekt>("detekt") {
    classpath.setFrom(analysisClasspath)
    jvmTarget = "17"
}
tasks.named<DetektCreateBaselineTask>("detektBaseline") {
    classpath.setFrom(analysisClasspath)
    jvmTarget = "17"
}

mavenPublishing {
    publishToMavenCentral()
    // Central rejects unsigned uploads; gating on the key keeps publishToMavenLocal working on
    // machines and CI jobs that have no signing key.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates("com.eventslooped.klyph", "klyph-gradle-plugin", version.toString())
    pom {
        name = "klyph-gradle-plugin"
        description = "Gradle plugin for klyph games: Kotlin/Native targets, SDL3 lookup and download, run tasks, assets and distribution zips."
        url = "https://github.com/inemtsev/klyph"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "inemtsev"
                name = "Ilya Nemtsev"
            }
        }
        scm {
            url = "https://github.com/inemtsev/klyph"
            connection = "scm:git:https://github.com/inemtsev/klyph.git"
            developerConnection = "scm:git:ssh://git@github.com/inemtsev/klyph.git"
        }
    }
}
