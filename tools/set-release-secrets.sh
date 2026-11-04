#!/usr/bin/env bash
# Copies the Maven Central and signing credentials from ~/.gradle/gradle.properties into the
# GitHub repository secrets that .github/workflows/release.yml reads. Values are piped straight
# from the file into `gh secret set` and never printed.
#
#   tools/set-release-secrets.sh            # repository of the current checkout
#   tools/set-release-secrets.sh owner/repo
#
# Needs `gh` logged in with access to the repository. Re-running replaces the secrets.
set -euo pipefail

properties="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"
repo="${1:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}"

[[ -r "$properties" ]] || { echo "Cannot read $properties." >&2; exit 1; }
gh auth status > /dev/null 2>&1 || { echo "gh is not logged in; run 'gh auth login' first." >&2; exit 1; }

# Prints the value of a Gradle property, with the "\n" escapes that gradle.properties uses for
# line breaks turned into real newlines, which is what Gradle expects from an environment variable.
property() {
    local line
    line="$(grep -E "^[[:space:]]*$1[[:space:]]*[=:]" "$properties" | head -n 1)" || true
    [[ -n "$line" ]] || { echo "Property $1 is missing from $properties." >&2; return 1; }
    printf '%b' "${line#*[=:]}" | sed -e 's/^[[:space:]]*//'
}

set_secret() {
    local property="$1" secret="$2"
    property "$property" | gh secret set "$secret" --repo "$repo"
    echo "Set $secret from $property."
}

set_secret mavenCentralUsername       MAVEN_CENTRAL_USERNAME
set_secret mavenCentralPassword       MAVEN_CENTRAL_PASSWORD
set_secret signingInMemoryKey         SIGNING_IN_MEMORY_KEY
set_secret signingInMemoryKeyPassword SIGNING_IN_MEMORY_KEY_PASSWORD

echo
echo "Secrets now in $repo:"
gh secret list --repo "$repo"
