#!/usr/bin/env bash
# Builds the shooter the way a game outside this repository would: a fresh project with only the
# klyph plugin line, the klyph { } block and the engine dependency, resolved from published
# artifacts. Runs the autopilot, builds the distribution zip and checks what the zip holds.
#
#   tools/consumer-test.sh local [version]     # mavenLocal() first, then Maven Central
#   tools/consumer-test.sh central [version]   # Maven Central and the Gradle Plugin Portal only
#
# The version defaults to the one in the root build.gradle.kts. For `local`, publish first:
#   ./gradlew publishToMavenLocal -Pklyph.targets=all
#   ./gradlew -p klyph-gradle-plugin publishToMavenLocal
#
# Environment:
#   SDL3_PREFIX=<folder>   passed to the build as sdl3.prefix (CI on Linux and Windows sets it).
#   KLYPH_CONSUMER_RUN=0   links the debug executable instead of running it, for machines with no
#                          display or GPU, such as CI runners.
#
# The project goes to ${TMPDIR:-/tmp}/klyph-consumer-<pid>/; it is deleted when every check
# passes and kept for inspection when one fails. Exits non-zero on any failure.
set -euo pipefail

usage() {
    echo "Usage: $0 local|central [version]" >&2
    exit 2
}

[[ $# -ge 1 && $# -le 2 ]] || usage
mode="$1"
case "$mode" in
    local | central) ;;
    *) usage ;;
esac

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
version="${2:-$(sed -n 's/.*gradleProperty("klyph.version").getOrElse("\([^"]*\)").*/\1/p' "$repo/build.gradle.kts")}"
[[ -n "$version" ]] || { echo "Cannot read the klyph version from $repo/build.gradle.kts; pass it as the second argument." >&2; exit 1; }

case "$(uname -s)" in
    Darwin) target=macosArm64; executable=shooter.kexe; library='(libSDL3\.0\.dylib|SDL3\.framework/Versions/A/SDL3)' ;;
    Linux)
        if [[ "$(uname -m)" == aarch64 ]]; then target=linuxArm64; else target=linuxX64; fi
        executable=shooter.kexe; library='libSDL3\.so\.0' ;;
    MINGW* | MSYS* | CYGWIN*) target=mingwX64; executable=shooter.exe; library='SDL3\.dll' ;;
    *) echo "Unsupported host $(uname -s)." >&2; exit 1 ;;
esac
# Task-name spelling of the target (macosArm64 -> MacosArm64); macOS ships bash 3.2, without ${x^}.
Target="$(printf '%s' "${target:0:1}" | tr '[:lower:]' '[:upper:]')${target:1}"

dir="${TMPDIR:-/tmp}"
dir="${dir%/}/klyph-consumer-$$"
rm -rf "$dir"
mkdir -p "$dir/gradle"

fail() {
    echo "FAILED: $1" >&2
    echo "The project is kept in $dir." >&2
    exit 1
}

# Runs ./gradlew in the project with its output in <dir>/<log>.log; shows the end of the log and
# fails when the build does. The output goes to a file, never a pipe: the Gradle daemon would
# inherit the pipe and keep it open.
gradle() {
    local log="$dir/$1.log"
    shift
    echo "> ./gradlew $*"
    if ! (cd "$dir" && ./gradlew "$@" > "$log" 2>&1); then
        tail -n 40 "$log" >&2
        fail "./gradlew $* (log: $log)"
    fi
}

echo "Consumer test: klyph $version from $mode, host $target, in $dir"

cp -R "$repo/shooter/src" "$dir/src"
cp -R "$repo/shooter/assets" "$dir/assets"
cp "$repo/gradlew" "$repo/gradlew.bat" "$dir/"
cp -R "$repo/gradle/wrapper" "$dir/gradle/wrapper"

if [[ "$mode" == local ]]; then local_repository="        mavenLocal()
"; else local_repository=""; fi

cat > "$dir/settings.gradle.kts" <<EOF
pluginManagement {
    repositories {
${local_repository}        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
${local_repository}        mavenCentral()
    }
}

rootProject.name = "shooter"
EOF

cat > "$dir/build.gradle.kts" <<EOF
plugins { id("com.eventslooped.klyph") version "$version" }
klyph { entryPoint = "klyph.shooter.main" }
kotlin { sourceSets.nativeMain.dependencies { implementation("com.eventslooped.klyph:klyph:$version") } }
EOF

{
    echo "org.gradle.configuration-cache=true"
    if [[ -n "${SDL3_PREFIX:-}" ]]; then echo "sdl3.prefix=$SDL3_PREFIX"; fi
} > "$dir/gradle.properties"

verified=()

if [[ "${KLYPH_CONSUMER_RUN:-1}" == 0 ]]; then
    gradle link "linkDebugExecutable$Target"
    [[ -f "$dir/build/bin/$target/debugExecutable/$executable" ]] || fail "linkDebugExecutable$Target produced no $executable."
    [[ -f "$dir/build/bin/$target/debugExecutable/assets/ship.png" ]] || fail "The debug link did not copy assets/ next to $executable."
    verified+=("the debug executable links and gets assets/ next to it (not run: KLYPH_CONSUMER_RUN=0)")
else
    gradle run run -Pklyph.autopilot=1 -Pklyph.seconds=5
    summary="$(grep -E '^Shooter ran ' "$dir/run.log" || true)"
    [[ -n "$summary" ]] || fail "The run printed no 'Shooter ran' summary (log: $dir/run.log)."
    verified+=("run with the autopilot for 5 seconds: $summary")
fi

gradle distribute distribute
# The consumer build sets no project version, so the zip and its folder are shooter-<target>.
folder="shooter-$target"
zip="$dir/build/distributions/$folder.zip"
[[ -f "$zip" ]] || fail "distribute produced no $zip."
folder_pattern="${folder//./\\.}"

if command -v unzip > /dev/null; then
    listing="$(unzip -l "$zip")"
else
    listing="$("${JAVA_HOME:+$JAVA_HOME/bin/}jar" tf "$zip")"
fi
grep -Eq "(^|[[:space:]])$folder_pattern/$executable\$" <<< "$listing" || fail "$zip has no $folder/$executable."
grep -Eq "(^|[[:space:]])$folder_pattern/assets/ship\.png\$" <<< "$listing" || fail "$zip has no $folder/assets/ship.png."
grep -Eq "(^|[[:space:]])$folder_pattern/$library\$" <<< "$listing" || fail "$zip has no SDL3 library in $folder/."
verified+=("$(basename "$zip") holds $folder/$executable, $folder/assets/ship.png and the SDL3 library ($(grep -Eo "$folder_pattern/$library\$" <<< "$listing" | head -n 1 | sed "s|^$folder/||"))")

echo "Consumer test passed for klyph $version from $mode on $target:"
for line in "${verified[@]}"; do echo "  - $line"; done
rm -rf "$dir"
