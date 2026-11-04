import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.4.20"
    // Gradle's Action parameters become lambdas with a receiver, as in build scripts.
    kotlin("plugin.sam.with.receiver") version "2.4.20"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "com.eventslooped.klyph"
// -Pklyph.version=<v> overrides the version, as in the engine build's root build.gradle.kts.
version = providers.gradleProperty("klyph.version").getOrElse("1.0.0")

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
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
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
    }
}

// Unit tests run in `test`. TestKit tests, which apply the plugin to a throwaway project, run in
// `functionalTest` against the plugin classpath that java-gradle-plugin writes for them.
val functionalTest: SourceSet = sourceSets.create("functionalTest")
gradlePlugin.testSourceSets(functionalTest)

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    // java-gradle-plugin puts the Gradle API on the compile classpath only.
    testImplementation(gradleApi())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    "functionalTestImplementation"(kotlin("test-junit5"))
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter:5.13.4")
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
