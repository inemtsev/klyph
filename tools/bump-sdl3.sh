#!/usr/bin/env bash
# Points the klyph Gradle plugin's SDL3 download at another SDL release: downloads that release's
# macOS disk image and MinGW development package, computes their SHA-256 and rewrites
# DEFAULT_VERSION and PINNED_SHA256 in Sdl3Release.kt to match.
#
#   tools/bump-sdl3.sh <version>             # for example 3.4.20
#   tools/bump-sdl3.sh --dry-run <version>   # prints the new values, changes nothing
#
# Running it with the version already pinned downloads and checks both files and leaves
# Sdl3Release.kt unchanged. Exits non-zero when a download fails, when a file is not a disk image
# or a zip, or when Sdl3Release.kt does not have the shape this script expects. Other files that
# name the old version (the plugin README, its tests, CLAUDE.md, CONTRIBUTING.md) are not touched;
# the script prints how to find them.
set -euo pipefail

usage() {
    echo "Usage: $0 [--dry-run] <version>" >&2
    exit 2
}

dry_run=0
if [[ "${1:-}" == "--dry-run" ]]; then
    dry_run=1
    shift
fi
[[ $# -eq 1 ]] || usage
version="$1"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
    echo "error: '$version' is not a version such as 3.4.18." >&2
    exit 2
}

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_file="$root/klyph-gradle-plugin/src/main/kotlin/com/eventslooped/klyph/gradle/Sdl3Release.kt"
[[ -f "$source_file" ]] || {
    echo "error: $source_file does not exist." >&2
    exit 1
}

dmg="SDL3-$version.dmg"
zip="SDL3-devel-$version-mingw.zip"
base_url="https://github.com/libsdl-org/SDL/releases/download/release-$version"

work="$(mktemp -d "${TMPDIR:-/tmp}/klyph-sdl3-XXXXXX")"
trap 'rm -rf "$work"' EXIT

download() {
    local name="$1"
    echo "Downloading $base_url/$name"
    if ! curl --fail --location --silent --show-error --output "$work/$name" "$base_url/$name"; then
        echo "error: could not download $name; is $version an SDL release?" >&2
        exit 1
    fi
}

download "$dmg"
download "$zip"

# A UDIF disk image ends with a 512-byte trailer that starts with "koly"; a zip starts with the
# local file header "PK\x03\x04".
dmg_magic="$(tail -c 512 "$work/$dmg" | head -c 4)"
[[ "$dmg_magic" == "koly" ]] || {
    echo "error: $dmg is not a disk image (no koly trailer)." >&2
    exit 1
}
zip_magic="$(head -c 4 "$work/$zip")"
[[ "$zip_magic" == $'PK\x03\x04' ]] || {
    echo "error: $zip is not a zip file." >&2
    exit 1
}

sha256_of() {
    local hash
    hash="$(shasum -a 256 "$1")"
    echo "${hash%% *}"
}
dmg_sha="$(sha256_of "$work/$dmg")"
zip_sha="$(sha256_of "$work/$zip")"

# Reads the current values and checks the file's shape: one DEFAULT_VERSION line, and a
# PINNED_SHA256 mapOf( ... ) block whose closing parenthesis sits on a line of its own.
version_pattern='^( *const val DEFAULT_VERSION: String = )"([^"]*)"$'
map_start_pattern='^ *val PINNED_SHA256: Map<String, String> = mapOf\($'
map_end_pattern='^ *\)$'
entry_pattern='^ *"([^"]+)" to "([0-9a-f]+)",$'
old_version=""
version_lines=0
map_starts=0
map_closed=0
old_entries=""
in_map=0
while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" =~ $version_pattern ]]; then
        old_version="${BASH_REMATCH[2]}"
        version_lines=$((version_lines + 1))
    elif [[ "$line" =~ $map_start_pattern ]]; then
        map_starts=$((map_starts + 1))
        in_map=1
    elif ((in_map)) && [[ "$line" =~ $map_end_pattern ]]; then
        in_map=0
        map_closed=1
    elif ((in_map)) && [[ "$line" =~ $entry_pattern ]]; then
        old_entries="$old_entries  ${BASH_REMATCH[1]}  ${BASH_REMATCH[2]}"$'\n'
    fi
done < "$source_file"
if ((version_lines != 1 || map_starts != 1 || !map_closed)); then
    echo "error: $source_file does not have one DEFAULT_VERSION line and one PINNED_SHA256 mapOf( ... ) block." >&2
    exit 1
fi

# Writes the file again with the version replaced and the map body replaced as a whole.
new_file="$work/Sdl3Release.kt"
in_map=0
while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" =~ $version_pattern ]]; then
        printf '%s"%s"\n' "${BASH_REMATCH[1]}" "$version"
    elif [[ "$line" =~ $map_start_pattern ]]; then
        printf '%s\n' "$line"
        indent="${line%%val PINNED_SHA256*}    "
        printf '%s"%s" to "%s",\n' "$indent" "$dmg" "$dmg_sha" "$indent" "$zip" "$zip_sha"
        in_map=1
    elif ((in_map)) && [[ "$line" =~ $map_end_pattern ]]; then
        printf '%s\n' "$line"
        in_map=0
    elif ((!in_map)); then
        printf '%s\n' "$line"
    fi
done < "$source_file" > "$new_file"

echo
echo "Old version: $old_version"
printf '%s' "$old_entries"
echo "New version: $version"
echo "  $dmg  $dmg_sha"
echo "  $zip  $zip_sha"
echo

if [[ "$(<"$source_file")" == "$(<"$new_file")" ]]; then
    echo "Sdl3Release.kt already pins $version with these hashes; nothing to change."
elif ((dry_run)); then
    echo "Dry run: Sdl3Release.kt left unchanged."
else
    cp "$new_file" "$source_file"
    echo "Rewrote ${source_file#"$root"/}."
    if [[ "$old_version" != "$version" ]]; then
        echo "Other files may still name $old_version: git grep -n '$old_version'"
    fi
fi
