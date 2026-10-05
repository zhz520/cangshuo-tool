"""Inspect release APK/AAB, HTTPS constants, permissions, native ELF and ZIP alignment."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_PERMISSIONS = {'android.permission.INTERNET', 'android.permission.CAMERA',
    'android.permission.ACCESS_NETWORK_STATE', 'com.cangshuo.toolbox.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}

def require(value, reason):
    if not value: raise ValueError(reason)

def inspect_elf(data, name):
    require(data[:4] == b'\x7fELF' and data[5] == 1, 'Unsupported native format')
    if data[4] == 2:
        offset = struct.unpack_from('<Q', data, 32)[0]
        size, count = struct.unpack_from('<HH', data, 54)
        require(size >= 56 and count <= 128, 'Invalid ELF headers')
        for i in range(count):
            at = offset + size * i
            kind, = struct.unpack_from('<I', data, at)
            if kind == 1:
                file_offset, address = struct.unpack_from('<QQ', data, at + 8)
                alignment, = struct.unpack_from('<Q', data, at + 48)
                require(alignment >= 16_384 and file_offset % 16_384 == address % 16_384,
                        '64-bit native library does not support 16 KB pages: ' + name)
    else: require(data[4] == 1, 'Unsupported native ELF class')

def inspect_zip(path, apk=False):
    native = 0
    with zipfile.ZipFile(path) as archive, path.open('rb') as source:
        require(archive.testzip() is None, 'Corrupt archive')
        for entry in archive.infolist():
            if entry.filename.endswith('.so'):
                require(entry.file_size <= 50 * 1024 * 1024, 'Native entry too large')
                inspect_elf(archive.read(entry), entry.filename); native += 1
                if apk and entry.compress_type == zipfile.ZIP_STORED:
                    source.seek(entry.header_offset)
                    header = source.read(30)
                    name_length, extra_length = struct.unpack_from('<HH', header, 26)
                    require((entry.header_offset + 30 + name_length + extra_length) % 16_384 == 0,
                            'Uncompressed native APK entry is not 16 KB aligned')
        require(native > 0, 'Expected native dependencies missing')
        if not apk:
            require('base/manifest/AndroidManifest.xml' in archive.namelist(), 'AAB manifest missing')
            require('BundleConfig.pb' in archive.namelist(), 'AAB configuration missing')
    return native

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--require-signed', action='store_true'); args = parser.parse_args()
    outputs = ROOT / 'android/app/build/outputs'
    apks = list((outputs / 'apk/release').glob('*.apk')); aabs = list((outputs / 'bundle/release').glob('*.aab'))
    require(len(apks) == len(aabs) == 1, 'Expected exactly one release APK and AAB')
    apk, aab = apks[0], aabs[0]
    native = [inspect_zip(apk, True), inspect_zip(aab)]
    build_tools = args.sdk / 'build-tools/36.0.0'
    aapt = build_tools / ('aapt2.exe' if __import__('os').name == 'nt' else 'aapt2')
    result = subprocess.run([str(aapt), 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'],
                            capture_output=True, text=True, check=True)
    manifest = result.stdout
    import re
    permissions = set(re.findall(r'android:name[^\n]*="([^"]+)"', '\n'.join(
        block for block in manifest.split('E: ') if block.startswith(('uses-permission ', 'uses-permission\n')))))
    require(permissions == EXPECTED_PERMISSIONS, 'Unexpected release permission set')
    require('android:allowBackup' in manifest and re.search(r'android:allowBackup[^\n]*(?:0x0|false)', manifest), 'Backup must be disabled')
    require('android:dataExtractionRules' in manifest, 'Backup extraction rules missing')
    require(not re.search(r'android:debuggable[^\n]*(?:0xffffffff|true)', manifest), 'Release must not be debuggable')
    with zipfile.ZipFile(apk) as archive:
        dex = b''.join(archive.read(name) for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name))
        for url in [b'https://tool.zhzgo.cn', b'https://toolapi.zhzgo.cn/api/v1']:
            require(url in dex, 'Production endpoint missing')
        for forbidden in [b'http://10.0.2.2:8081/api/v1', b'http://127.0.0.1:8081/api/v1', b'http://localhost:8088']:
            require(forbidden not in dex, 'Debug endpoint leaked into release')
    resources = subprocess.run([str(aapt), 'dump', 'resources', str(apk)], capture_output=True, text=True, encoding='utf-8', check=True).stdout
    network_path = re.search(r'xml/network_security_config\s+\(\) \(file\) (\S+)', resources)
    require(network_path is not None, 'Network security resource missing')
    network = subprocess.run([str(aapt), 'dump', 'xmltree', str(apk), '--file', network_path.group(1)],
                             capture_output=True, text=True, check=True).stdout
    require('cleartextTrafficPermitted' in network and re.search(r'cleartextTrafficPermitted[^\n]*(?:0x0|false)', network),
            'Release cleartext traffic must be disabled')
    require('E: domain' not in network and 'localhost' not in network, 'Debug network exception leaked')
    signed = False
    if args.require_signed:
        signer = build_tools / ('apksigner.bat' if __import__('os').name == 'nt' else 'apksigner')
        verified = subprocess.run([str(signer), 'verify', '--print-certs', str(apk)], capture_output=True, text=True)
        require(verified.returncode == 0 and 'CN=Android Debug' not in verified.stdout, 'APK release signature verification failed')
        verified_bundle = subprocess.run(['jarsigner', '-verify', str(aab)], capture_output=True, text=True)
        require(verified_bundle.returncode == 0 and 'jar verified' in verified_bundle.stdout, 'AAB signature verification failed')
        signed = True
    files = []
    for p in [apk, aab]:
        with p.open('rb') as source: digest = hashlib.file_digest(source, 'sha256').hexdigest()
        files.append({'name': p.name, 'bytes': p.stat().st_size, 'sha256': digest})
    report = {'signatureVerified': signed, 'nativeEntries': native, 'permissions': sorted(permissions), 'files': files}
    target = outputs / 'release-readiness.json'; target.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Release artifact gate passed: HTTPS, permissions, backup, native ELF and ZIP alignment; signature verified=' + str(signed))

if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('Release artifact gate failed: ' + (str(error) if isinstance(error, ValueError) else type(error).__name__))
        raise SystemExit(1)
