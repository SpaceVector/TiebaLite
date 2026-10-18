"""Require the dedicated, fixed certificate for future Release APKs."""

import hashlib
import os
import re
import subprocess
import sys


EXPECTED_CERTIFICATE_SHA256 = "56b77f9d4f8ea1ac77f7761721a173e3bd15300127fbc64429ab595f221c5183"


def require_pinned_certificate(fingerprints):
    if fingerprints != [EXPECTED_CERTIFICATE_SHA256]:
        raise ValueError(
            "Signing certificate does not match the fixed Release key. "
            f"Expected SHA-256 {EXPECTED_CERTIFICATE_SHA256}; "
            f"got {', '.join(fingerprints) or 'no certificate'}. "
            "Restore the configured release keystore; do not generate a replacement key."
        )


def verify_keystore(path):
    certificate = subprocess.run(
        [
            "keytool", "-exportcert", "-keystore", path,
            "-alias", os.environ["RELEASE_KEY_ALIAS"],
            "-storepass:env", "RELEASE_STORE_PASSWORD",
        ],
        check=True, capture_output=True,
    ).stdout
    require_pinned_certificate([hashlib.sha256(certificate).hexdigest()])


def verify_apks(apksigner, paths):
    if not paths:
        raise ValueError("No APKs were provided for certificate verification.")
    for path in paths:
        result = subprocess.run(
            [apksigner, "verify", "--print-certs", path],
            check=True, capture_output=True, text=True,
        )
        fingerprints = re.findall(
            r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
            result.stdout, re.MULTILINE,
        )
        require_pinned_certificate([value.lower() for value in fingerprints])
        print(f"Verified fixed Release signing certificate: {path}")


if __name__ == "__main__":
    try:
        if len(sys.argv) == 3 and sys.argv[1] == "keystore":
            verify_keystore(sys.argv[2])
        elif len(sys.argv) >= 4 and sys.argv[1] == "apk":
            verify_apks(sys.argv[2], sys.argv[3:])
        else:
            raise ValueError("Usage: verify_signing.py keystore PATH | apk APKSIGNER APK...")
        print(f"Signing certificate SHA-256: {EXPECTED_CERTIFICATE_SHA256}")
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        # Do not print subprocess output: signing tools receive secret configuration.
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
