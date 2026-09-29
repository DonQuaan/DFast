#!/usr/bin/env bash
set -u
root="${1:-.}"
found=0
report() {
    if [ -n "$1" ]; then
        printf '%s\n' "$1"
        found=1
    fi
}
report "$(grep -rnE '(^|[^:"])//|/\*' --include='*.java' "$root/src" 2>/dev/null)"
report "$(grep -nE '(^|[^:"])//|/\*' "$root/build.gradle" "$root/settings.gradle" 2>/dev/null)"
report "$(grep -nE '(^|[[:space:]])[#!]' "$root/gradle.properties" 2>/dev/null)"
report "$(grep -nE '(^|[[:space:]])#' "$root/.gitignore" "$root/.gitattributes" "$root"/.github/workflows/*.yml 2>/dev/null)"
exit "$found"
