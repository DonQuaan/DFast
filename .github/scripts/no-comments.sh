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
report "$(grep -nE '(^|[[:space:]])#' "$root/.gitignore" "$root/.gitattributes" "$root"/.github/workflows/*.y*ml 2>/dev/null)"
report "$(grep -HnE '(^|[[:space:]])#' "$root"/.github/scripts/*.py "$root"/.github/scripts/*.sh "$root"/tools/*.py "$root"/tools/*.sh 2>/dev/null | grep -v ':1:#!')"
exit "$found"
