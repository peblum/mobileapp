#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<EOF
Usage: ${0##*/} [--fdroid] <variant> [<variant> ...]

Variants:
  debug    Existing debug/store build type
  release  Existing release/store build type
  ci       Forgejo installable CI build type (requires --fdroid)
  nightly  Forgejo optimized nightly build type (requires --fdroid)
EOF
}

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

fdroid_build=false
variants=()

while (($# > 0)); do
    case "$1" in
        --fdroid)
            fdroid_build=true
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        --)
            shift
            variants+=("$@")
            break
            ;;
        -*)
            usage >&2
            die "unknown option: $1"
            ;;
        *)
            variants+=("$1")
            ;;
    esac
    shift
done

((${#variants[@]} > 0)) || {
    usage >&2
    die "at least one build variant is required"
}

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/../.." && pwd -P)
gradlew=${GRADLEW:-"$repository_root/gradlew"}

[[ -x "$gradlew" ]] || die "Gradle wrapper is not executable: $gradlew"

gradle_arguments=(--continue --no-daemon)
if "$fdroid_build"; then
    gradle_arguments+=("-PfdroidBuild=true")
fi

declare -A selected_variants=()
for variant in "${variants[@]}"; do
    if [[ -n "${selected_variants[$variant]:-}" ]]; then
        die "duplicate build variant: $variant"
    fi
    selected_variants["$variant"]=true

    case "$variant" in
        debug)
            gradle_arguments+=(":androidApp:assembleDebug")
            ;;
        release)
            gradle_arguments+=(":androidApp:assembleRelease")
            ;;
        ci)
            "$fdroid_build" || die "the ci variant requires --fdroid"
            gradle_arguments+=(":androidApp:assembleCi")
            ;;
        nightly)
            "$fdroid_build" || die "the nightly variant requires --fdroid"
            gradle_arguments+=(":androidApp:assembleNightly")
            ;;
        *)
            die "unsupported build variant: $variant"
            ;;
    esac
done

gradle_arguments+=(
    "testDebugUnitTest"
    "testAndroidHostTest"
    "jvmTest"
    "lint"
)

cd -- "$repository_root"
exec "$gradlew" "${gradle_arguments[@]}"
