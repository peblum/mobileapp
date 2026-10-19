#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<EOF
Usage: ${0##*/} --variant <debug|release|ci|nightly> --output-dir <directory>
       [--metadata <output-metadata.json>] [--apk-name <file.apk>]

Paths are resolved relative to the repository root. The output directory must
be absent or empty. --apk-name is valid only when the metadata lists one APK.
EOF
}

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

require_value() {
    local option=$1
    local value=${2:-}

    [[ -n "$value" ]] || die "$option requires a value"
}

variant=
output_dir=
metadata_path=
apk_name=

while (($# > 0)); do
    case "$1" in
        --variant)
            require_value "$1" "${2:-}"
            variant=$2
            shift 2
            ;;
        --output-dir)
            require_value "$1" "${2:-}"
            output_dir=$2
            shift 2
            ;;
        --metadata)
            require_value "$1" "${2:-}"
            metadata_path=$2
            shift 2
            ;;
        --apk-name)
            require_value "$1" "${2:-}"
            apk_name=$2
            shift 2
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
done

[[ -n "$variant" ]] || die "--variant is required"
[[ -n "$output_dir" ]] || die "--output-dir is required"

case "$variant" in
    debug|release|ci|nightly)
        ;;
    *)
        die "unsupported build variant: $variant"
        ;;
esac

command -v jq >/dev/null || die "jq is required"
command -v sha256sum >/dev/null || die "sha256sum is required"

if [[ -n "$apk_name" ]] &&
    ! jq -en --arg file_name "$apk_name" \
        '$file_name | test("^[A-Za-z0-9][A-Za-z0-9._+-]*[.]apk$")' >/dev/null; then
    die "--apk-name must be a safe APK base name"
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/../.." && pwd -P)

if [[ -z "$metadata_path" ]]; then
    metadata_path="androidApp/build/outputs/apk/$variant/output-metadata.json"
fi
if [[ "$metadata_path" != /* ]]; then
    metadata_path="$repository_root/$metadata_path"
fi
if [[ "$output_dir" != /* ]]; then
    output_dir="$repository_root/$output_dir"
fi

[[ -f "$metadata_path" ]] || die "APK output metadata not found: $metadata_path"

if ! jq -e --arg variant "$variant" '
    type == "object"
    and .artifactType.type == "APK"
    and .variantName == $variant
    and (.applicationId | type == "string" and length > 0)
    and (.elements | type == "array" and length > 0)
    and all(
        .elements[];
        (.outputFile | type == "string"
            and test("^[^/\\\\[:cntrl:]]+[.]apk$")
            and . != "."
            and . != "..")
        and (.versionCode |
            (type == "number" and . > 0 and . == floor))
        and (.versionName | type == "string")
    )
    and ([.elements[].outputFile] | length == (unique | length))
' "$metadata_path" >/dev/null; then
    die "invalid or mismatched APK output metadata: $metadata_path"
fi

if [[ -e "$output_dir" && ! -d "$output_dir" ]]; then
    die "output path exists and is not a directory: $output_dir"
fi
if [[ -d "$output_dir" ]] &&
    [[ -n "$(find "$output_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    die "output directory is not empty: $output_dir"
fi

metadata_dir=$(cd -- "$(dirname -- "$metadata_path")" && pwd -P)
element_count=$(jq -er '.elements | length' "$metadata_path")
if [[ -n "$apk_name" && "$element_count" -ne 1 ]]; then
    die "--apk-name requires metadata containing exactly one APK"
fi

output_files=()
source_apks=()
for ((index = 0; index < element_count; index += 1)); do
    output_file=$(jq -er --argjson index "$index" \
        '.elements[$index].outputFile' "$metadata_path")
    source_apk="$metadata_dir/$output_file"

    [[ -f "$source_apk" ]] || die "APK listed in metadata is missing: $source_apk"
    [[ ! -L "$source_apk" ]] || die "APK listed in metadata must not be a symlink: $source_apk"

    output_files+=("$output_file")
    source_apks+=("$source_apk")
done

mkdir -p -- "$output_dir"
temporary_dir=$(mktemp -d)
artifacts_jsonl="$temporary_dir/artifacts.jsonl"
build_inputs_jsonl="$temporary_dir/build-inputs.jsonl"
checksums_file="$output_dir/SHA256SUMS"

cleanup() {
    rm -rf -- "$temporary_dir"
}
trap cleanup EXIT

: >"$artifacts_jsonl"
: >"$build_inputs_jsonl"
: >"$checksums_file"

for ((index = 0; index < element_count; index += 1)); do
    output_file=${output_files[$index]}
    source_apk=${source_apks[$index]}
    collected_file=$output_file
    if [[ -n "$apk_name" ]]; then
        collected_file=$apk_name
    fi
    destination_apk="$output_dir/$collected_file"

    install -m 0644 -- "$source_apk" "$destination_apk"

    sha256=$(sha256sum -- "$destination_apk" | awk '{print $1}')
    size_bytes=$(wc -c <"$destination_apk")
    size_bytes=${size_bytes//[[:space:]]/}
    element_json=$(jq -c --argjson index "$index" \
        '.elements[$index]' "$metadata_path")

    printf '%s  %s\n' "$sha256" "$collected_file" >>"$checksums_file"
    jq -cn \
        --arg fileName "$collected_file" \
        --arg sourceFileName "$output_file" \
        --arg sha256 "$sha256" \
        --argjson sizeBytes "$size_bytes" \
        --argjson androidElement "$element_json" \
        '{
            fileName: $fileName,
            sourceFileName: $sourceFileName,
            sha256: $sha256,
            sizeBytes: $sizeBytes,
            androidElement: $androidElement
        }' >>"$artifacts_jsonl"
done

install -m 0644 -- "$metadata_path" "$output_dir/output-metadata.json"

built_revision=$(git -C "$repository_root" rev-parse --verify 'HEAD^{commit}') ||
    die "unable to resolve the built Git revision"
trigger_revision=${FORGEJO_SHA:-}
generated_at=${CI_GENERATED_AT:-$(date -u '+%Y-%m-%dT%H:%M:%SZ')}
metadata_sha256=$(sha256sum -- "$metadata_path" | awk '{print $1}')
application_id=$(jq -er '.applicationId' "$metadata_path")

build_input_paths=(
    "gradlew"
    "gradle/wrapper/gradle-wrapper.properties"
    "docs/fdroid-scanner.patch"
    "flake.lock"
)
for relative_path in "${build_input_paths[@]}"; do
    input_path="$repository_root/$relative_path"
    if [[ -f "$input_path" ]]; then
        input_sha256=$(sha256sum -- "$input_path" | awk '{print $1}')
        jq -cn \
            --arg path "$relative_path" \
            --arg sha256 "$input_sha256" \
            '{path: $path, sha256: $sha256}' >>"$build_inputs_jsonl"
    fi
done

jq -n \
    --slurpfile artifacts "$artifacts_jsonl" \
    --slurpfile buildInputs "$build_inputs_jsonl" \
    --arg generatedAt "$generated_at" \
    --arg repository "${FORGEJO_REPOSITORY:-}" \
    --arg builtRevision "$built_revision" \
    --arg triggerRevision "$trigger_revision" \
    --arg ref "${FORGEJO_REF:-}" \
    --arg eventName "${FORGEJO_EVENT_NAME:-}" \
    --arg runId "${FORGEJO_RUN_ID:-}" \
    --arg runNumber "${FORGEJO_RUN_NUMBER:-}" \
    --arg runAttempt "${FORGEJO_RUN_ATTEMPT:-}" \
    --arg serverUrl "${FORGEJO_SERVER_URL:-}" \
    --arg variant "$variant" \
    --arg applicationId "$application_id" \
    --arg outputMetadataSha256 "$metadata_sha256" \
    '{
        schemaVersion: 1,
        generatedAt: $generatedAt,
        source: {
            repository: $repository,
            builtRevision: $builtRevision,
            triggerRevision: $triggerRevision,
            ref: $ref,
            eventName: $eventName,
            runId: $runId,
            runNumber: $runNumber,
            runAttempt: $runAttempt,
            serverUrl: $serverUrl
        },
        android: {
            variant: $variant,
            applicationId: $applicationId,
            outputMetadataSha256: $outputMetadataSha256
        },
        buildInputs: $buildInputs,
        artifacts: $artifacts
    }' >"$output_dir/build-manifest.json"

printf 'Collected %d APK(s) for %s in %s\n' \
    "$element_count" "$variant" "$output_dir"
