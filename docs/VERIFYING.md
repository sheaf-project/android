# Verifying your Sheaf Android build

Every APK published from this repo is signed twice over:

1. **APK signing** by the project's release keystore. This is the standard Android signature; the OS uses it to enforce that updates come from the same source as the original install.
2. **Cosign keyless OIDC** by the project's CI workflow. This is a Sigstore signature tying the APK file to the exact GitHub Actions run that built it, recorded in the public [Rekor](https://github.com/sigstore/rekor) transparency log.

The first protects you against drive-by update tampering once the app is installed. The second is what lets you confirm, before installing, that the APK you're about to run was built from public source by Sheaf's CI rather than handed to you by some intermediary.

For the broader threat model and how this relates to Sheaf's backend / web verifiability story, see the main repo's [`docs/VERIFYING.md`](https://github.com/sheaf-project/sheaf/blob/master/docs/VERIFYING.md).

## What you can check

- That the `.apk` you downloaded was signed by `https://github.com/sheaf-project/android/.github/workflows/dev-release.yml` running on a specific commit.
- That the signature is recorded in the public Sigstore transparency log.
- That the APK signature on disk matches the project's published release key fingerprint.

## What this doesn't claim

- **Reproducibility from source.** Byte-for-byte rebuild from a tag is a future goal, not a guarantee today. Android Gradle Plugin output has a few sources of non-determinism (timestamps, ZIP entry order, baseline profile generation) that need to be sanded down before we can stand behind a strict reproducibility claim. Tracked alongside the F-Droid effort.
- **SBOM attestation.** The main repo publishes a Sigstore-attested SPDX SBOM for every released image. The Android equivalent (`syft` over the APK + `cosign attest-blob`) is planned but not yet wired up; this page will describe how to verify it once it ships.
- **Runtime attestation.** Once the APK is installed, the OS only knows the APK signature; it can't tell you whether the running code matches what was on disk minutes ago. This is true of every Android app and isn't something verification at the release level can address.

## How to verify (manual)

You'll need [cosign](https://docs.sigstore.dev/cosign/system_config/installation/) and Java's `apksigner` (ships with the Android SDK build-tools).

### 1. Cosign signature on the APK

Download `app-release.apk`, `app-release.apk.sig`, and `app-release.apk.pem` from the release page, then:

```sh
cosign verify-blob \
  --signature app-release.apk.sig \
  --certificate app-release.apk.pem \
  --certificate-identity-regexp "https://github.com/sheaf-project/android/.github/workflows/.+" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  app-release.apk
```

Expected output: `Verified OK`. Anything else (mismatch, missing log entry, wrong identity) is a failure.

The same pattern applies to `wear-release.apk`.

> **Inspecting the certificate by hand:** cosign emits the `.pem` file base64-encoded (the `LS0tLS1CRUdJTi...` you'll see if you `head` it), not as raw PEM. `cosign verify-blob` decodes this transparently, so the verification command above just works. If you want to look at the cert's identity claims yourself with `openssl x509`, decode first:
>
> ```sh
> base64 -d app-release.apk.pem | openssl x509 -noout -ext subjectAltName -issuer
> ```
>
> The SAN URI is the workflow identity that signed the blob (e.g. `https://github.com/sheaf-project/android/.github/workflows/dev-release.yml@refs/heads/master`). The issuer should be `O=sigstore.dev, CN=sigstore-intermediate`.

### 2. APK signing certificate

```sh
apksigner verify --print-certs app-release.apk
```

Compare the printed `SHA-256` digest of `Signer #1 certificate` against the published release-key fingerprint:

```
4a:9b:b3:0c:d2:c3:bd:74:7b:98:17:b1:66:c2:1d:94:b2:3a:46:c0:45:44:fc:8c:32:74:ea:49:d7:a4:34:e6
```

(Same fingerprint as the [README](../README.md#verifying-a-build).) Android pins this on first install, so every future update has to be signed with the same key. If a build prints a different fingerprint, do not install it.

## How to verify (script)

`scripts/verify-release.sh` bundles the cosign and (optional) `apksigner` checks. Two modes:

```sh
# Verify a local APK file. Expects <apk>.sig and <apk>.pem alongside it.
./scripts/verify-release.sh path/to/app-release.apk

# Download the artefacts from a GitHub release and verify them in a temp dir.
./scripts/verify-release.sh --tag dev          # latest dev build
./scripts/verify-release.sh --tag v0.1.0       # a tagged release (when those exist)
```

Requires `cosign`. The `--tag` mode also needs `gh` (GitHub CLI). `apksigner` is used opportunistically to print the APK signing fingerprint, and the script runs without it but skips that step.
