#!/usr/bin/env python3
"""One-time, owner-run setup in a private Codespace/terminal. Never prints secret values."""
import argparse
import base64
import getpass
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

REQUIRED = ('PRODUCTION_KEYSTORE_BASE64', 'PRODUCTION_STORE_PASSWORD',
            'PRODUCTION_KEY_PASSWORD', 'PRODUCTION_KEY_ALIAS', 'PRODUCTION_CERT_SHA256')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True, help='owner/repository')
    parser.add_argument('--restore-backup', type=Path,
                        help='Retry a partial setup using the SAME encrypted backup, never a new key')
    args = parser.parse_args()
    for tool in ('gh', 'keytool', 'openssl'):
        if not shutil.which(tool):
            raise SystemExit('Required tool missing: ' + tool)
    # Validate Secrets access before creating private material. The response is a public encryption key.
    subprocess.run(['gh', 'api', 'repos/' + args.repo + '/actions/secrets/public-key'],
                   stdout=subprocess.DEVNULL, check=True)
    existing = json.loads(subprocess.check_output(
        ['gh', 'secret', 'list', '--repo', args.repo, '--json', 'name'], text=True))
    present = {item['name'] for item in existing}.intersection(REQUIRED)
    if present and not args.restore_backup:
        raise SystemExit('Production secrets already exist; refusing to rotate/overwrite them. '
                         'For a partial setup retry only with --restore-backup and the SAME backup.')
    alias = 'glass-tv-production'
    store_pass = getpass.getpass('Keystore password (save in your password manager; minimum 20 characters): ')
    if len(store_pass) < 20:
        raise SystemExit('Use at least 20 characters.')
    if store_pass != getpass.getpass('Confirm keystore password: '):
        raise SystemExit('Passwords do not match.')
    backup_pass = getpass.getpass('Encrypted backup password (save separately; minimum 20 characters): ')
    if len(backup_pass) < 20:
        raise SystemExit('Use at least 20 characters.')
    env = os.environ.copy()
    env.update(GLASS_STORE_PASS=store_pass, GLASS_BACKUP_PASS=backup_pass)
    backup_dir = Path('signing-backup')
    backup_dir.mkdir(exist_ok=True, mode=0o700)
    backup = backup_dir / 'glass-tv-production-signing-backup.enc'
    if not args.restore_backup and backup.exists():
        raise SystemExit('Backup already exists; refusing to generate a replacement key.')
    with tempfile.TemporaryDirectory(prefix='glass-signing-setup-') as tmp:
        store = Path(tmp) / 'production.p12'
        if args.restore_backup:
            subprocess.run(['openssl', 'enc', '-d', '-aes-256-cbc', '-pbkdf2', '-iter', '600000',
                            '-in', str(args.restore_backup), '-out', str(store),
                            '-pass', 'env:GLASS_BACKUP_PASS'], env=env, check=True)
        else:
            subprocess.run(['keytool', '-genkeypair', '-storetype', 'PKCS12', '-keystore', str(store),
                            '-alias', alias, '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                            '-dname', 'CN=Glass TV Enhanced', '-storepass:env', 'GLASS_STORE_PASS',
                            '-keypass:env', 'GLASS_STORE_PASS'], env=env, check=True)
        os.chmod(store, 0o600)
        cert = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(store),
                       '-alias', alias, '-storepass:env', 'GLASS_STORE_PASS'], env=env)
        fingerprint = hashlib.sha256(cert).hexdigest()
        if not args.restore_backup:
            subprocess.run(['openssl', 'enc', '-aes-256-cbc', '-pbkdf2', '-iter', '600000', '-salt',
                            '-in', str(store), '-out', str(backup), '-pass', 'env:GLASS_BACKUP_PASS'],
                           env=env, check=True)
            os.chmod(backup, 0o600)
        values = dict(zip(REQUIRED, (base64.b64encode(store.read_bytes()).decode(), store_pass,
                          store_pass, alias, fingerprint)))
        # No key/password text appears in command arguments, stdout, Git, caches or artifacts.
        for name, value in values.items():
            subprocess.run(['gh', 'secret', 'set', name, '--repo', args.repo],
                           input=value.encode(), stdout=subprocess.DEVNULL, check=True)
        (backup_dir / 'PUBLIC-CERTIFICATE.txt').write_text(
            'Repository: ' + args.repo + '\nAlias: ' + alias + '\nSHA-256: ' + fingerprint + '\n')
        print('All five production signing secrets configured. Public certificate SHA-256: ' + fingerprint)
        print('Download the encrypted backup and PUBLIC-CERTIFICATE.txt from signing-backup; '
              'keep both passwords in your password manager. Do not commit these files.')


if __name__ == '__main__':
    main()
