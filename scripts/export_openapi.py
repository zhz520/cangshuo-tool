#!/usr/bin/env python3
"""Export the real Springdoc contract without credentials or running infrastructure."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline', action='store_true', help='Use only the existing Maven dependency cache')
    parser.add_argument('--check', action='store_true', help='Fail if docs/openapi.json differs from the actual controllers/DTOs')
    parser.add_argument('--from-build', action='store_true', help='With --check, compare the export from an immediately preceding successful Maven test/package')
    args = parser.parse_args()
    if args.from_build and not args.check:
        parser.error('--from-build requires --check')
    root = Path(__file__).resolve().parents[1]
    server = root / 'server'
    env = os.environ.copy()
    if os.name == 'nt' and root.drive.upper() != 'D:':
        parser.error('The Windows project/output directory must be on D: under the storage policy')
    temporary = root / 'temp' / 'openapi'
    env.update(TEMP=str(temporary), TMP=str(temporary), TMPDIR=str(temporary))
    env['MAVEN_USER_HOME'] = str(server / '.maven-cache')
    if os.name == 'nt' and not env.get('JAVA_HOME'):
        jdk = Path('D:/Android/Android Studio/jbr')
        if (jdk / 'bin/java.exe').exists():
            env['JAVA_HOME'] = str(jdk)
    if os.name == 'nt':
        command = [str(server / 'mvnw.cmd')]
    else:
        command = ['sh', str(server / 'mvnw')]
    if args.offline:
        command.append('-o')
    command += ['-B', '-ntp', '-Dmaven.repo.local=' + str(server / '.maven-cache'), '-Dtest=OpenApiExportTest', 'test']
    if not args.from_build:
        temporary.mkdir(parents=True, exist_ok=True)
        result = subprocess.run(command, cwd=server, env=env, check=False)
        if result.returncode:
            return result.returncode
    generated = server / 'target/openapi.json'
    if not generated.exists():
        parser.error('No generated contract; run Maven test/package or omit --from-build')
    destination = root / 'docs/openapi.json'
    contract = json.loads(generated.read_text(encoding='utf-8'))
    if args.check:
        if not destination.exists() or json.loads(destination.read_text(encoding='utf-8')) != contract:
            print('OpenAPI snapshot is stale; run python scripts/export_openapi.py', file=sys.stderr)
            return 1
        print('OpenAPI snapshot matches the implemented routes and DTOs.')
    else:
        destination.write_bytes(generated.read_bytes())
        print('Updated ' + str(destination))
    return 0


if __name__ == '__main__':
    sys.exit(main())
