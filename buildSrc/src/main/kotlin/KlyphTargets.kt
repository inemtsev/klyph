import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * The Kotlin/Native targets a build configures.
 *
 * `-Pklyph.targets` takes `host` (the default, the host's own target), `all` (every target in
 * [all], only as the whole value) or a comma-separated list of target names, where `host` may also
 * appear. `-Pklyph.target=<name>` is the older spelling of a one-element list; setting both is an
 * error. Tests and `run` tasks exist only for [host], since no other target's binaries can execute
 * on this machine. The klyph Gradle plugin's `KlyphTargets.resolve` follows the same rules with
 * the same error texts; change both together.
 */
object KlyphTargets {
    /** Every target klyph supports, in the order a build configures them. */
    val all: List<String> = listOf("macosArm64", "linuxX64", "linuxArm64", "mingwX64")

    /** The target whose binaries run on this machine. */
    val host: String = run {
        val os = System.getProperty("os.name")
        val arch = System.getProperty("os.arch")
        when {
            os == "Mac OS X" && arch == "aarch64" -> "macosArm64"
            os == "Linux" && arch == "aarch64" -> "linuxArm64"
            os == "Linux" -> "linuxX64"
            os.startsWith("Windows") -> "mingwX64"
            else -> throw GradleException("Unsupported host: $os $arch.")
        }
    }

    /** Returns the targets selected by `klyph.targets` or `klyph.target`, in the order of [all]. */
    fun selected(project: Project): List<String> = resolve(
        targetsProperty = project.providers.gradleProperty("klyph.targets").orNull,
        targetProperty = project.providers.gradleProperty("klyph.target").orNull,
        host = host,
    )

    /** Returns the targets named by the two property values, in the order of [all] with repeats dropped. */
    fun resolve(targetsProperty: String?, targetProperty: String?, host: String): List<String> {
        val list = targetsProperty?.trim()
        val single = targetProperty?.trim()
        if (list != null && single != null) {
            throw GradleException("Set klyph.targets or klyph.target, not both: klyph.targets=$list, klyph.target=$single.")
        }
        val value = list ?: single ?: "host"
        val names = when (value) {
            "all" -> all
            else -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { if (it == "host") host else it }
        }
        val unknown = names.filter { it !in all }
        if (unknown.isNotEmpty()) {
            throw GradleException(
                "Unknown klyph target ${unknown.joinToString()}. Use host, all, or a comma-separated list of ${all.joinToString()}.",
            )
        }
        if (names.isEmpty()) throw GradleException("No klyph target is selected. Use host, all, or a comma-separated list of ${all.joinToString()}.")
        return all.filter { it in names }
    }
}
