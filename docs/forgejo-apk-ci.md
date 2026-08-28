# Forgejo APK builds

Forgejo Actions builds the public F-Droid dependency graph for pull requests
targeting `master` and for pushes to `master`. Pull requests receive temporary
workflow artifacts. Successful `master` builds are signed with a dedicated
nightly key and published as immutable
[Generic Packages](https://forgejo.org/docs/latest/user/packages/generic/).

The Forgejo APKs deliberately use application IDs that differ from the store
app:

| Build | Application ID | Distribution |
| --- | --- | --- |
| Pull request | `coredevices.coreapp.ci` | Forgejo workflow artifact |
| `master` | `coredevices.coreapp.nightly` | `pebble-nightly` Generic Package |
| F-Droid/store | `coredevices.coreapp` | Store repository |

This separation lets test builds install alongside an official app and
prevents a CI signing key from impersonating a store build. Do not run two
companion apps against the same watch at once: Bluetooth ownership, Android
services, and shared deep links are not isolated by the package name.

CI and nightly builds disable the legacy PebbleKit Classic provider because
Classic clients hard-code the production provider authority. They also omit
PebbleKit 2's legacy custom-permission declarations so Android can install the
independently signed packages side by side. Clients that depend specifically
on these legacy declarations are unsupported in test builds.

Test-channel version names are deterministic:
`0.0.0-<ci|nightly>.<version-code>+<short-sha>`.

Pull-request APKs use the disposable build runner's debug key. If that key
changes between runs, uninstall the previous CI build before installing its
replacement. Nightly APKs use the stable, dedicated key configured below.

## Workflows

`.forgejo/workflows/apk-pr.yml` runs for pull requests targeting `master`. It
uses no repository secrets and publishes an installable CI APK plus an
unsigned, optimized nightly APK for inspection. Artifacts expire after 14
days.

`.forgejo/workflows/apk-master.yml` runs for pushes to `master` and manual
dispatches on `master`. Its build job uses the same secret-free build and test
path as pull requests. A separate publishing job downloads the unsigned
nightly APK without checking out or executing repository code, aligns it,
signs it, verifies it, and publishes:

- `Pebble-master-<version-code>-<short-sha>.apk`
- `SHA256SUMS`
- `build-manifest.json`
- `unsigned-output-metadata.json`

Package versions have the form `master-<version-code>-<short-sha>`. They are
immutable: rerunning a workflow accepts an existing file only when its
checksum is identical. Each upload is fetched back and checked before the job
succeeds.

Adding `.forgejo/workflows` makes Forgejo prefer that directory instead of
falling back to `.github/workflows`. The Forgejo workflows intentionally cover
the free Android APK path; GitHub remains responsible for the normal Android,
instrumented Android, and iOS jobs in `.github/workflows/build.yml`.

## Runner requirements

Runner labels route jobs; they are not an authorization boundary. A
same-repository pull request can change its workflow and select any known
label. Consequently, neither runner may contain repository signing material or
other ambient credentials.

- `[native, android-untrusted]` must select a disposable container, LXC
  container, or VM. It executes public pull-request code and must not expose a
  host home directory, Docker socket, SSH agent, signing material, or a
  writable cache later consumed by trusted jobs.
- `[native, android-publish]` selects a separate disposable image provisioned
  with APK inspection tools. It has no signing key or package token; external
  OIDC policy authorizes protected-`master` operations.

The first label selects the Forgejo Runner execution backend; both labels must
match the chosen runner. Do not configure `native` as an unisolated host label
for public pull requests.

Use Forgejo Runner 7 or newer. The build runner must be x86_64 Linux and needs
Nix with flakes enabled plus Node.js 20 or newer for the pinned checkout and
artifact actions. The repository's development shell currently exports only
`x86_64-linux`; it supplies JDK 17, Android SDK platform 37, build-tools
36.0.0, and the validation tools.

The publishing runner does not evaluate the repository flake or check out
repository code. It does parse an APK and JSON created by an untrusted build,
so keep its operating system and Android tools pinned, patched, isolated, and
rebuilt from a clean image for each job. The remote signer must independently
validate every request rather than trusting the runner's inspection.

The publishing runner needs Node.js 20 or newer for the pinned artifact
actions, JDK 17, Android build-tools 36.0.0 with compatible command-line tools,
Bash 4.4 or newer, and standard GNU coreutils and findutils. Provision these
commands independently and keep them on `PATH`:

```text
aapt2
apkanalyzer
apksigner
awk
curl
java
jq
sha256sum
xmllint
zipalign
```

## Signing configuration

Do not store a keystore, signing password, or signing API token as a Forgejo
repository secret. Same-repository workflow changes can request ordinary
secrets before review. Instead, provision an HTTPS signing service or HSM
gateway as an
[OIDC relying party](https://forgejo.org/docs/latest/user/actions/security-openid-connect/)
and create these non-secret repository variables:

| Variable | Value |
| --- | --- |
| `FORGEJO_APK_SIGNER_URL` | Signing-service base URL |
| `FORGEJO_APK_SIGNER_AUDIENCE` | Exact OIDC audience accepted by the signer |
| `FORGEJO_APK_CERT_SHA256` | Dedicated nightly certificate's SHA-256 digest |

The workflow sends a 16-KiB-aligned unsigned APK as the request body to
`POST <FORGEJO_APK_SIGNER_URL>/v1/sign/android-apk`, with content type
`application/vnd.android.package-archive`, Bearer OIDC authorization, and an
`X-Pebble-Unsigned-SHA256` consistency header. The successful response body
must be the signed APK.

The signer must verify the JWT signature and algorithm from Forgejo's OIDC
discovery/JWKS, then validate:

- exact issuer `<FORGEJO_SERVER_URL>/api/actions`, audience, and time claims;
- immutable repository and owner IDs plus the expected repository name;
- exact full `workflow_ref`
  `<owner>/<repo>/.forgejo/workflows/apk-master.yml@refs/heads/master`;
- `ref=refs/heads/master`, `ref_type=branch`, `ref_protected="true"`, and
  `event_name` equal to `push` or `workflow_dispatch`;
- the deployment's pinned `sub` claim policy.

It must compute the body checksum itself, limit request size, reject signed or
malformed input, and independently require application ID
`coredevices.coreapp.nightly`, minimum SDK 26, target SDK 36, 16-KiB
alignment, exactly one APK Signature Scheme v2 signer, and version name
`0.0.0-nightly.<version-code>+<JWT-sha-prefix>`. It must issue only the
configured nightly certificate and should make each JWT or token hash
single-use until expiry. Signing must be byte-stable for the same APK and
certificate, either deterministically or by caching on the body checksum, so a
workflow rerun can match an immutable package. Never reuse a Play, F-Droid, or
other production key.

The workflow independently checks the APK before requesting a signature and
checks the returned metadata, alignment, signature, and configured certificate
before publishing. Update both workflow and signer policy when Android version
settings change.

## Package authentication

Package publication requires Forgejo 16 or newer and a
[local Authorized Integration](https://forgejo.org/docs/latest/user/authorized-integrations/);
there is no long-lived-token fallback. The Forgejo server URL must use HTTPS.

1. Create a **Forgejo Actions (Local)** Authorized Integration.
2. Restrict it to this repository.
3. Set the workflow filename to `apk-master.yml`.
4. Set the Git reference to `refs/heads/master`.
5. Allow only the `push` and `workflow_dispatch` events.
6. Grant only `write:package`.
7. Save its non-secret audience as the repository variable
   `FORGEJO_PACKAGE_AUDIENCE`.

The workflow requests a short-lived JWT and uses Bearer authentication for
package uploads.

Generic Packages belong to the repository owner rather than directly to a
repository. After the first upload, link `pebble-nightly` to this repository
once in Forgejo if it should appear in the repository's **Packages** tab.

## Package retention

Every successful `master` commit creates an immutable package version. Workflow
artifact retention does not remove Generic Packages. Configure an
operator-owned package cleanup policy that keeps the desired age or count of
nightlies while preserving any referenced versions. The publishing integration
intentionally has no package-delete permission.

## Repository protection

Protect `master` before enabling signing and package OIDC policy:

- Require review and the pull-request APK check.
- Disallow force pushes and deletion.
- Require owner review for `.forgejo/workflows/`, `flake.nix`,
  `scripts/ci/`, and Android signing or versioning changes.
- Permit the package Authorized Integration only for the protected workflow
  and reference.

GitHub-style workflow `permissions` declarations are not a Forgejo security
boundary. Isolation, branch protection, event restrictions, scoped
integrations, and separation of build and publishing jobs provide that
boundary.

## Local validation

The scanner-preparation script modifies and deletes tracked files by design.
Run the full CI build only in a disposable clone or Git worktree:

```bash
nix develop --command scripts/ci/build-fdroid-apks.sh forgejo
```

Workflow and shell validation can run in the normal checkout:

```bash
nix develop --command actionlint .forgejo/workflows/*.yml
nix develop --command shellcheck scripts/ci/*.sh
nix develop --command forgejo-runner validate --directory .
```

After a build, verify the installable CI APK and confirm the nightly input is
unsigned before handing it to the trusted signing job:

```bash
apksigner verify --verbose dist/ci/Pebble-ci-*.apk
! apksigner verify dist/nightly/Pebble-nightly-*-unsigned.apk
```
