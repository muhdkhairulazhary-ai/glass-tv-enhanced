#!/usr/bin/env python3
"""Sign only with explicitly configured persistent key material; never generate a key."""
import base64
import os
from pathlib import Path
import re
import subprocess
import tempfile


def main():
    names = ('PRODUCTION_KEYSTORE_BASE64', 'PRODUCTION_STORE_PASSWORD',
             'PRODUCTION_KEY_PASSWORD', 'PRODUCTION_KEY_ALIAS', 'PRODUCTION_CERT_SHA256')
    missing = [name for name in names if not os.environ.get(name)]
    if missing:
        raise SystemExit('Missing GitHub Actions signing secrets: ' + ', '.join(missing)
                         + '. Follow docs/STABLE-RELEASE.md. No substitute key will be generated.')
    expected = os.environ['PRODUCTION_CERT_SHA256'].replace(':', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', expected):
        raise SystemExit('PRODUCTION_CERT_SHA256 must contain a SHA-256 certificate fingerprint.')
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '34.0.0'
    source = Path('unsigned/Glass-TV-Enhanced-production-unsigned.apk')
    output = Path('signed')
    output.mkdir(exist_ok=True)
    final = output / 'Glass-TV-Enhanced.apk'
    # Temporary private files are outside the artifact directory, mode 0600, never cached/uploaded.
    with tempfile.TemporaryDirectory(prefix='glass-sign-', dir=os.environ.get('RUNNER_TEMP')) as tmp:
        store = Path(tmp) / 'production.keystore'
        try:
            raw = base64.b64decode(os.environ['PRODUCTION_KEYSTORE_BASE64'], validate=True)
        except ValueError:
            raise SystemExit('The keystore secret is not valid single-line base64.') from None
        if not raw or len(raw) > 30000:
            raise SystemExit('Unexpected keystore size; refusing to sign.')
        fd = os.open(store, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as f:
            f.write(raw)
        aligned = Path(tmp) / 'aligned.apk'
        subprocess.run([str(tools / 'zipalign'), '-f', '4', str(source), str(aligned)], check=True)
        subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(store),
                        '--ks-key-alias', os.environ['PRODUCTION_KEY_ALIAS'],
                        '--ks-pass', 'env:PRODUCTION_STORE_PASSWORD',
                        '--key-pass', 'env:PRODUCTION_KEY_PASSWORD',
                        '--out', str(final), str(aligned)], check=True)
        verified = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose',
                    '--print-certs', '--min-sdk-version', '30', '--max-sdk-version', '30',
                    str(final)], text=True)
        match = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', verified)
        if not match or match[1].lower() != expected or 'Number of signers: 1' not in verified:
            final.unlink(missing_ok=True)
            raise SystemExit('Signing certificate does not match the pinned fingerprint; no APK published.')
        subprocess.run([str(tools / 'zipalign'), '-c', '4', str(final)], check=True)
        (output / 'SIGNING-CERTIFICATE.txt').write_text(verified)
        # Public certificate information only. Passwords and private key bytes are never printed.
        print('Production signature verified against the configured certificate fingerprint.')


if __name__ == '__main__':
    main()
