#!/bin/sh
# Sheaf Android release verification.
#
# Verifies that an APK was cosign-signed by the project's CI workflow via
# Sigstore keyless OIDC, with the signature recorded in the public Rekor
# transparency log. Optionally prints the APK signing certificate so you
# can compare it against the project's published key fingerprint.
#
# Two modes:
#   verify-release.sh <apk-path>
#       Verify a local APK. Expects <apk>.sig and <apk>.pem alongside it.
#
#   verify-release.sh --tag [<tag>]
#       Download the artefacts from a GitHub release at <tag> (default 'dev')
#       and verify them in a temp dir. Cleans up after itself.
#
# Exit codes:
#   0  - everything verified
#   1  - at least one APK failed verification
#   64 - bad usage
#   69 - required tool missing

set -eu

REPO="sheaf-project/android"
WORKFLOW_RE="https://github.com/sheaf-project/android/.github/workflows/.+"
ISSUER="https://token.actions.githubusercontent.com"

usage() {
    cat <<'EOF' >&2
usage:
  verify-release.sh <apk-path>
  verify-release.sh --tag [<tag>]   (default tag: dev)

Requires: cosign, gh (only with --tag), apksigner (optional, recommended).
EOF
    exit 64
}

require() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "error: required tool not found: $1" >&2
        echo "  install: $2" >&2
        exit 69
    fi
}

# Verify one APK against its sibling .sig and .pem. Returns 0 on success.
verify_apk() {
    apk="$1"
    sig="$apk.sig"
    pem="$apk.pem"
    base=$(basename "$apk")

    if [ ! -f "$sig" ] || [ ! -f "$pem" ]; then
        echo "FAIL [$base]: missing $(basename "$sig") or $(basename "$pem")" >&2
        return 1
    fi

    echo "==> $base"
    echo "  cosign verify-blob"
    if ! cosign verify-blob \
            --signature "$sig" \
            --certificate "$pem" \
            --certificate-identity-regexp "$WORKFLOW_RE" \
            --certificate-oidc-issuer "$ISSUER" \
            "$apk" >/dev/null 2>&1; then
        echo "  FAIL: cosign verify-blob rejected the signature" >&2
        # Re-run noisily so the user sees the real error.
        cosign verify-blob \
            --signature "$sig" \
            --certificate "$pem" \
            --certificate-identity-regexp "$WORKFLOW_RE" \
            --certificate-oidc-issuer "$ISSUER" \
            "$apk" >&2 || true
        return 1
    fi
    echo "    OK"

    # Show the workflow identity baked into the cert (the SAN URI). cosign
    # writes the .pem base64-encoded, so decode once for openssl.
    if command -v openssl >/dev/null 2>&1; then
        san=$(base64 -d < "$pem" 2>/dev/null \
            | openssl x509 -noout -ext subjectAltName 2>/dev/null \
            | sed -n 's/^[[:space:]]*URI://p')
        if [ -n "$san" ]; then
            echo "  signed by: $san"
        fi
    fi

    # APK signing cert (Android's own signature, separate from cosign).
    if command -v apksigner >/dev/null 2>&1; then
        echo "  apksigner certificate fingerprint"
        apksigner verify --print-certs "$apk" 2>/dev/null \
            | awk '/Signer #1 certificate (SHA-256|DN|SHA-1|MD5)/ {sub(/^Signer #1 /, "    "); print}'
    else
        echo "  (apksigner not installed; skipping APK signing cert check)"
    fi

    return 0
}

# ---- arg parsing ----
[ $# -ge 1 ] || usage

case "$1" in
    -h|--help) usage ;;
    --tag)
        MODE="tag"
        TAG="${2:-dev}"
        ;;
    *)
        MODE="file"
        APK_PATH="$1"
        ;;
esac

require cosign "https://docs.sigstore.dev/cosign/system_config/installation/"

OVERALL=0

if [ "$MODE" = "tag" ]; then
    require gh "https://cli.github.com/"

    workdir=$(mktemp -d)
    trap 'rm -rf "$workdir"' EXIT INT TERM

    echo "Downloading release '$TAG' from $REPO into $workdir"
    gh release download "$TAG" \
        --repo "$REPO" \
        --dir "$workdir" \
        --pattern '*.apk' \
        --pattern '*.apk.sig' \
        --pattern '*.apk.pem' \
        --clobber
    echo

    found=0
    for apk in "$workdir"/*.apk; do
        [ -f "$apk" ] || continue
        found=1
        verify_apk "$apk" || OVERALL=1
        echo
    done

    if [ "$found" -eq 0 ]; then
        echo "error: no APKs found in release '$TAG'" >&2
        exit 1
    fi
else
    [ -f "$APK_PATH" ] || { echo "error: $APK_PATH does not exist" >&2; exit 64; }
    verify_apk "$APK_PATH" || OVERALL=1
fi

echo
if [ "$OVERALL" -eq 0 ]; then
    echo "PASS: all APKs verified."
else
    echo "FAIL: at least one APK did not verify." >&2
fi
exit "$OVERALL"
