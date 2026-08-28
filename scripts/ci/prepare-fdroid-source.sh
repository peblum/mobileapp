#!/usr/bin/env bash

set -euo pipefail

usage() {
    printf 'Usage: %s [--check]\n' "${0##*/}"
}

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

check_only=false

while (($# > 0)); do
    case "$1" in
        --check)
            check_only=true
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            usage >&2
            die "unknown argument: $1"
            ;;
    esac
    shift
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/../.." && pwd -P)
patch_path="$repository_root/docs/fdroid-scanner.patch"

[[ -f "$patch_path" ]] || die "scanner patch not found: $patch_path"
git -C "$repository_root" rev-parse --show-toplevel >/dev/null

scanner_deletions=(
    "cactus-native/src/main/jniLibs/arm64-v8a/libcactus_engine.so"
    "cactus/src/commonMain/resources/ios/lib/ios-arm64-simulator/libcactus_engine.a"
    "cactus/src/commonMain/resources/ios/lib/ios-arm64-simulator/libcurl.a"
    "cactus/src/commonMain/resources/ios/lib/ios-arm64/libcactus_engine.a"
    "cactus/src/commonMain/resources/ios/lib/ios-arm64/libcurl.a"
    "models/needle-pebble-ft-cq4.zip"
    "composeApp/src/androidMain/assets/models/needle-pebble-ft-cq4.zip"
)

cd -- "$repository_root"

patch_state=
if git apply --check --whitespace=error-all "$patch_path" >/dev/null 2>&1; then
    patch_state=unapplied
elif git apply --reverse --check --whitespace=error-all "$patch_path" >/dev/null 2>&1; then
    patch_state=applied
else
    printf 'error: F-Droid scanner patch is neither applicable nor already applied\n' >&2
    git apply --check --whitespace=error-all "$patch_path"
    exit 1
fi

if [[ "$patch_state" == "unapplied" ]]; then
    missing_deletion=false
    for relative_path in "${scanner_deletions[@]}"; do
        if [[ ! -e "$relative_path" && ! -L "$relative_path" ]]; then
            printf 'error: scanner deletion target is missing: %s\n' "$relative_path" >&2
            missing_deletion=true
        fi
    done
    "$missing_deletion" && die "F-Droid scanner deletion list is out of date"
fi

if "$check_only"; then
    if [[ "$patch_state" == "applied" ]]; then
        remaining_deletion=false
        for relative_path in "${scanner_deletions[@]}"; do
            if [[ -e "$relative_path" || -L "$relative_path" ]]; then
                printf 'error: scanner deletion target is still present: %s\n' \
                    "$relative_path" >&2
                remaining_deletion=true
            fi
        done
        "$remaining_deletion" &&
            die "F-Droid scanner preparation is incomplete"
    fi
    printf 'F-Droid scanner preparation is valid (%s patch).\n' "$patch_state"
    exit 0
fi

if [[ "$patch_state" == "unapplied" ]]; then
    git apply --whitespace=error-all "$patch_path"
fi

removed_count=0
for relative_path in "${scanner_deletions[@]}"; do
    if [[ -e "$relative_path" || -L "$relative_path" ]]; then
        rm -- "$relative_path"
        ((removed_count += 1))
    fi
done

printf 'F-Droid scanner preparation complete (%s patch, %d files removed).\n' \
    "$patch_state" "$removed_count"
