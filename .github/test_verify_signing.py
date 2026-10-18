"""Offline certificate checks; no real private keys are used by these tests."""

import hashlib
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

import verify_signing


class VerifySigningTest(unittest.TestCase):
    def test_private_signing_properties_are_not_generated_as_app_constants(self):
        root = Path(__file__).resolve().parent.parent
        settings = (root / "settings.gradle.kts").read_text()
        app_build = (root / "app/build.gradle.kts").read_text()
        self.assertNotIn('"keystore.properties"', settings)
        self.assertNotIn("property.keystore.", app_build)

    def test_only_fixed_release_certificate_is_accepted(self):
        expected = verify_signing.EXPECTED_CERTIFICATE_SHA256
        verify_signing.require_pinned_certificate([expected])
        for fingerprints in (
            [], ["0" * 64], [expected, "0" * 64], [expected, expected],
            ["3223c578b313b1b04224c510dc6707a9c437fff5de0512e40101f41b1ea8a485"],
            ["b0e269188c314c58af2ef1c75358ecb8551850099a62470e8ea1763ef7aa719f"],
            ["b240b0ff2aed9c0eea75e6e3fcd5491da8bc00ca06abcc99d53097d16b3bedee"],
        ):
            with self.subTest(fingerprints=fingerprints), self.assertRaises(ValueError):
                verify_signing.require_pinned_certificate(fingerprints)

    def test_keystore_checks_certificate_without_password_in_command_arguments(self):
        certificate = b"test public certificate, not a private key"
        expected = hashlib.sha256(certificate).hexdigest()
        with patch.dict("os.environ", {"RELEASE_KEY_ALIAS": "test-alias", "RELEASE_STORE_PASSWORD": "test-password"}), \
                patch.object(verify_signing, "EXPECTED_CERTIFICATE_SHA256", expected), \
                patch("verify_signing.subprocess.run", return_value=subprocess.CompletedProcess([], 0, certificate)) as run:
            verify_signing.verify_keystore("original.jks")
            command = run.call_args.args[0]
            self.assertIn("-storepass:env", command)
            self.assertIn("RELEASE_STORE_PASSWORD", command)
            self.assertNotIn("test-password", command)

    def test_every_apk_must_have_the_fixed_verified_certificate(self):
        expected = verify_signing.EXPECTED_CERTIFICATE_SHA256
        output = f"Signer #1 certificate SHA-256 digest: {expected}\n"
        with patch("verify_signing.subprocess.run", return_value=subprocess.CompletedProcess([], 0, output)) as run:
            verify_signing.verify_apks("apksigner", ["one.apk", "two.apk"])
            self.assertEqual(run.call_count, 2)
            self.assertTrue(run.call_args.kwargs["check"])
            self.assertEqual(run.call_args.args[0][:3], ["apksigner", "verify", "--print-certs"])

    def test_missing_or_mismatched_apk_certificate_is_rejected(self):
        for output in ("", "Signer #1 certificate SHA-256 digest: " + "0" * 64 + "\n"):
            with self.subTest(output=output), \
                    patch("verify_signing.subprocess.run", return_value=subprocess.CompletedProcess([], 0, output)), \
                    self.assertRaises(ValueError):
                verify_signing.verify_apks("apksigner", ["wrong.apk"])
        with self.assertRaises(ValueError):
            verify_signing.verify_apks("apksigner", [])

    def test_invalid_apk_signature_is_not_ignored(self):
        with patch("verify_signing.subprocess.run", side_effect=subprocess.CalledProcessError(1, "apksigner")), \
                self.assertRaises(subprocess.CalledProcessError):
            verify_signing.verify_apks("apksigner", ["invalid.apk"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
