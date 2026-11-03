// Maven coordinates of the published modules: com.eventslooped.klyph:<module>:<version>.
// -Pklyph.version=<v> overrides the version, for local experiments with mavenLocal.
val klyphVersion: String = providers.gradleProperty("klyph.version").getOrElse("0.1.0")

allprojects {
    group = "com.eventslooped.klyph"
    version = klyphVersion
}
