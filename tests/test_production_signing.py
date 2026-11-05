"""Signing must fail closed, never publish a mismatched APK, and clean temporary keys."""
import base64
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    'production_signing', Path(__file__).resolve().parents[1] / 'tools/signing/sign-production.py')
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class ProductionSigningTest(unittest.TestCase):
    def environment(self, tmp):
        return dict(PRODUCTION_KEYSTORE_BASE64=base64.b64encode(b'not-a-real-private-key').decode(),
                    PRODUCTION_STORE_PASSWORD='never-print-this-password',
                    PRODUCTION_KEY_PASSWORD='never-print-this-password',
                    PRODUCTION_KEY_ALIAS='production', PRODUCTION_CERT_SHA256='a' * 64,
                    ANDROID_HOME='/nonexistent-sdk', RUNNER_TEMP=tmp)

    def test_missing_secrets_never_invokes_signer(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(signing.subprocess, 'run') as run:
            with self.assertRaisesRegex(SystemExit, 'Missing GitHub Actions signing secrets'):
                signing.main()
            run.assert_not_called()

    def test_invalid_keystore_cleans_private_temp_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = self.environment(tmp)
            env['PRODUCTION_KEYSTORE_BASE64'] = '!invalid-base64!'
            before = Path.cwd()
            try:
                os.chdir(tmp)
                with patch.dict(os.environ, env, clear=True):
                    with self.assertRaisesRegex(SystemExit, 'not valid single-line base64'):
                        signing.main()
                self.assertEqual([], list(Path(tmp).glob('glass-sign-*')))
                self.assertFalse(Path('signed/Glass-TV-Enhanced.apk').exists())
            finally:
                os.chdir(before)

    def test_certificate_mismatch_removes_apk_and_private_temp_files(self):
        def fake_run(command, **kwargs):
            if '--out' in command:
                Path(command[command.index('--out') + 1]).write_bytes(b'fake-apk')
        with tempfile.TemporaryDirectory() as tmp:
            before = Path.cwd()
            try:
                os.chdir(tmp)
                with patch.dict(os.environ, self.environment(tmp), clear=True), \
                     patch.object(signing.subprocess, 'run', side_effect=fake_run), \
                     patch.object(signing.subprocess, 'check_output', return_value=(
                         'Number of signers: 1\nSigner #1 certificate SHA-256 digest: ' + 'b' * 64)):
                    with self.assertRaisesRegex(SystemExit, 'does not match the pinned fingerprint'):
                        signing.main()
                self.assertFalse(Path('signed/Glass-TV-Enhanced.apk').exists())
                self.assertFalse(Path('signed/SIGNING-CERTIFICATE.txt').exists())
                self.assertEqual([], list(Path(tmp).glob('glass-sign-*')))
            finally:
                os.chdir(before)
