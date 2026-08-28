#!/usr/bin/env bash

set -euo pipefail

usage() {
    printf 'Usage: %s <store|forgejo>\n' "${0##*/}"
}

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

(($# == 1)) || {
    usage >&2
    die "exactly one build target is required"
}

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)

case "$1" in
    store)
        variants=(debug release)
        ;;
    forgejo)
        variants=(ci nightly)
        ;;
    *)
        usage >&2
        die "unsupported build target: $1"
        ;;
esac

"$script_dir/prepare-fdroid-source.sh"
exec "$script_dir/run-host-matrix.sh" --fdroid "${variants[@]}"
