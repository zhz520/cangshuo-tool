"""Rebuild or check the OpenCC 1.1.9 assets from a pinned source tree.

Python 3.10+, standard library only, no network or temporary files. With no
source directory, --check verifies bundled bytes, format and provenance metadata
against independent pins; it cannot rederive dictionaries without the sources.
See docs/CHINESE_CONVERT.md for source URLs, semantics and commands.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
from typing import NamedTuple


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ASSETS = ROOT / 'android/app/src/main/assets'
COMMIT = '556ed22496d650bd0b13b6c163be9814637970ae'
VERSION = '1.1.9'
MAX_LINE_UTF16 = 256  # LocalChineseConversionRepository.MAX_DICTIONARY_LINE
MANIFEST_SHA256 = '6d32ff78951668d89fb63d3d94a80fa1abbfc2071d27cc9b0a715d9a432728d4'
LICENSE_SHA256 = '14a7601eae4ce7606e255ab23287ea2ad2a1c8ee418fcc1e5a5ef094b54948be'
LICENSE_BYTES = 9396
LICENSE_PREAMBLE = (
    'OpenCC 1.1.9 — Copyright 2010-2024 Carbo Kuo and contributors.\n'
    f'https://github.com/BYVoid/OpenCC/tree/{COMMIT}\n'
    'Bundled dictionaries adapted to first-candidate UTF-8 TSV; no runtime native library.\n\n'
).encode('utf-8')

# Raw bytes from the fixed GitHub commit, NOT values trusted from the asset
# manifest or the caller's source-manifest.json. Includes all eight configs,
# build scripts and reviewed runtime sources so source-backed checks audit the
# same source snapshot that established the data/algorithm contract.
SOURCE_FILES = {
    'LICENSE': (9165, 'b534e465949558eec2597b04f5092b5e161236a68dfbfd04d547592ac3964308'),
    'src/Conversion.cpp': (1574, '953f93275df84e078b68d0d1290ea26a439fc11c0359445dce6fd394cfdbeeeb'),
    'src/ConversionChain.cpp': (1065, '4d7321edc0a35b12d7f3a66cfbc480a77c8e399673fc6db9ef2c805f43c9af80'),
    'src/MaxMatchSegmentation.cpp': (1664, 'fb008d70321cde8d3e38ea4040a6b616c7689171f514ade48aed39e8a8a97127'),
    'src/DictGroup.cpp': (3281, 'fb0c74f0e53b7cbd89382bf0fa5e3b38e3b9b83824ece10698d5d3f711b45e04'),
    'src/TextDict.cpp': (2595, 'a935c12e8eadc5f288b1a62dbc7ca1ab754bc8bbfa0181b170c44fe5da719f49'),
    'src/Converter.cpp': (1186, '4cfcb7af71a9b7fdf707a89181685279093c249ddcd975e1d6ecd584ad828d6f'),
    'src/DictEntry.cpp': (1037, '713231bad47a86dec90746a7c9bba3e160d524d77e2753959c719b616e6fc3c6'),
    'data/scripts/merge.py': (693, 'd5cbf8e973a85e1e9fc64b84a04660d8c23159fdbfad3a6a75c4ecfc28b4e892'),
    'data/scripts/reverse.py': (274, '2f1cdfff49f3af85e9f1f88fa92be936b21e83ac3ec72d0ffca214b49630bec9'),
    'data/scripts/common.py': (1962, '9d8b6633a0212c3349922a9f9ec37d6906c498b1304fc44669d179f78e3e2555'),
    'data/CMakeLists.txt': (3832, '410183d4f27cdca58798a0e469d506a63cd18c89c952e4139e89df7cf23301ad'),
    'data/config/s2t.json': (406, '710bb970e406d9c0a3dd33c609248686cf1c5578d064b7b7fc89e70fbb9ea75d'),
    'data/config/t2s.json': (406, 'b818534194f27c2d95f01001edb0a5ec49b9050119892cb30a0504bb202cc07c'),
    'data/config/s2tw.json': (505, '67ce1f5aa3d6b21ebb523084b40e66f80bec27bb2dc6b540d44dac2e27f7194e'),
    'data/config/s2twp.json': (599, '72d187574510542be608c862d5359d29480ee61f137082df8a06d74f7bcfc79c'),
    'data/config/s2hk.json': (507, '3708e756ea01783e9cf6b3cce527580810b3d539b8c7fcc3594dd1517aa05bff'),
    'data/config/tw2s.json': (642, 'fd3e758691a87c9246d081612021c7f9186ce7abe845cd4eff3e7326c300434d'),
    'data/config/tw2sp.json': (728, 'd107837817e7704cec83a9ff7b876c289eb0ae752722c790c9bf967509c9eaa6'),
    'data/config/hk2s.json': (644, 'eb651448d8b536d6131e53e254e8ca2c158c9ced50fd55ee88a76e606dd32efa'),
    'data/dictionary/STCharacters.txt': (34353, 'ed1d268e0ad028511dcf5b0089faed0a980ad332449ec11d481ceefde6879f41'),
    'data/dictionary/STPhrases.txt': (1004897, '17fece21a28f3db2dc32397abd73d21ae73261c3f295fa6a3fe64b5e8d8b554b'),
    'data/dictionary/TSCharacters.txt': (34627, '6b5a0a799bea2bb22c001f635eaa3fc2904310f0c08addbff275477a80ecf09a'),
    'data/dictionary/TSPhrases.txt': (5181, '504169029c43f7f234b8e2ae470720af3657675c5574ff8aa0feb257e1dc5ce2'),
    'data/dictionary/TWVariants.txt': (312, '30e6f8395edbfdd74e293fd8b9c62105d787c849fbb208d2a7832eac696734d7'),
    'data/dictionary/TWVariantsRevPhrases.txt': (1075, 'bef60ceb4e57b6b062351406cb5d4644875574231d64787e03711317b7e773f3'),
    'data/dictionary/HKVariants.txt': (532, 'c3c93c35885902ba2b12a3235a7761b00fb2b027f36aa8314db2f6b6ad51d374'),
    'data/dictionary/HKVariantsRevPhrases.txt': (2709, 'c2da309afa7fdd9061f204664039d33b000a4dca0ecae4e7480dcbf9e20f658e'),
    'data/dictionary/TWPhrasesIT.txt': (7659, '3a4a2ad207f3a9442eb8f399630cf982c2ffc561df9c58b2ee352cfa023915c1'),
    'data/dictionary/TWPhrasesName.txt': (2121, '76e643569a30ea54e7ab6e52621fd4c396e01ee6dc2d15b7d25bf23addf4438a'),
    'data/dictionary/TWPhrasesOther.txt': (623, '6d0365fd180283f3e14b44f63d19d1aca045d60b0e000765902ad889a90d7a33'),
}


class TableSpec(NamedTuple):
    entries: int
    bytes: int
    sha256: str


TABLES = {
    'STCharacters': TableSpec(3980, 33333, '5f030b6ec68074b26b9b8816fbcb7887f7e518a68174327ea6310aecc489e977'),
    'STPhrases': TableSpec(49096, 1004369, '76559d352bd3ac0bb1024cc80ed1b776efa8034b950c1bdf14784c97c07e081d'),
    'TSCharacters': TableSpec(4113, 34378, '479a998eb07c19d9ccbadbb7e737843a3528f096cddd54fcdaf2ca118873784d'),
    'TSPhrases': TableSpec(278, 5141, '5b76ff04a507a472bc5cb5366a405cd3a8976a5598226fdc22ecd6296aa73eff'),
    'TWVariants': TableSpec(39, 312, '30e6f8395edbfdd74e293fd8b9c62105d787c849fbb208d2a7832eac696734d7'),
    'TWVariantsRev': TableSpec(39, 312, 'aed256cd515db700cbd96fae5d2158d6a98c9628ee51e14ac1e1c182f6041c90'),
    'TWVariantsRevPhrases': TableSpec(68, 1054, '2f9145fce55f1f10ff6e401dc34a8e6cbf892d30d3392f52ce385f9a0018d281'),
    'TWPhrases': TableSpec(512, 10185, 'f6d68e00a98fed36a9dd172271df27ca45849654efc937faa676fe4c21ff3da4'),
    'TWPhrasesRev': TableSpec(522, 10280, 'd61e2bb021535748e36901ee723cbabd5fccd22a3d9f97dde2225eaaac5d6399'),
    'HKVariants': TableSpec(63, 504, 'bebc74ecf90d6f250e67e9f18bdea2753ee9e525df9a8dc9791cfe5d730abef7'),
    'HKVariantsRev': TableSpec(70, 560, '1b9d1a6bae833245d547ba7299d53d99cb22ed22477241c6f5d16df1c0182404'),
    'HKVariantsRevPhrases': TableSpec(156, 2688, '3da1d42168bf87a3fb6f4f9280b46ff70a554d4f68e6356cb5e0698300ac8a88'),
}

# A group is ordered, not a global longest-match union. Keep stage and original
# segmentation boundaries; flattening either would change OpenCC's behavior.
ST = ('STPhrases', 'STCharacters')
TS = ('TSPhrases', 'TSCharacters')
TW_REV = ('TWVariantsRevPhrases', 'TWVariantsRev')
PLANS = {
    's2t': ('STPhrases', (ST,)),
    't2s': ('TSPhrases', (TS,)),
    's2tw': ('STPhrases', (ST, ('TWVariants',))),
    'tw2s': ('TSPhrases', (TW_REV, TS)),
    's2twp': ('STPhrases', (ST, ('TWPhrases',), ('TWVariants',))),
    'tw2sp': ('TSPhrases', (('TWPhrasesRev',) + TW_REV, TS)),
    's2hk': ('STPhrases', (ST, ('HKVariants',))),
    'hk2s': ('TSPhrases', (('HKVariantsRevPhrases', 'HKVariantsRev'), TS)),
}

Rows = dict[str, list[str]]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_bounded(path: Path, limit: int) -> bytes:
    # Bound the read itself, including for unexpected oversized/special files.
    with path.open('rb') as stream:
        data = stream.read(limit + 1)
    require(len(data) <= limit, f'{path}: exceeds {limit}-byte budget')
    return data


def utf8_lf(data: bytes, label: str) -> str:
    require(not data.startswith(b'\xef\xbb\xbf'), f'{label}: UTF-8 BOM is not allowed')
    text = data.decode('utf-8', errors='strict')
    require('\r' not in text, f'{label}: expected LF line endings')
    require(text.endswith('\n'), f'{label}: missing final LF')
    return text


def parse_rows(data: bytes, label: str, *, multiple: bool = False) -> Rows:
    text = utf8_lf(data, label)
    rows: Rows = {}
    previous: str | None = None
    # Split only on LF: Unicode separators must not silently become new rows.
    for number, line in enumerate(text[:-1].split('\n'), 1):
        at = f'{label}:{number}'
        require(len(line.encode('utf-16-le')) // 2 <= MAX_LINE_UTF16,
                f'{at}: exceeds {MAX_LINE_UTF16} UTF-16 units')
        require(line.count('\t') == 1, f'{at}: expected exactly one TAB')
        key, value = line.split('\t')
        values = value.split(' ') if multiple else [value]
        require(bool(key) and all(values), f'{at}: empty key/candidate')
        require(not any(c.isspace() for c in key), f'{at}: whitespace in key')
        require(not any(c.isspace() for word in values for c in word),
                f'{at}: whitespace/extra candidate in value')
        require(not any(ord(c) < 32 or ord(c) == 127 for c in key + ''.join(values)),
                f'{at}: control character')
        require(key not in rows, f'{at}: duplicate key')
        require(previous is None or previous < key, f'{at}: keys are not code-point sorted')
        rows[key] = values
        previous = key
    return rows


def reverse_rows(rows: Rows) -> Rows:
    """Match common.reverse_items: all candidates, input order, no deduplication."""
    result: Rows = {}
    for key, values in rows.items():
        for value in values:
            result.setdefault(value, []).append(key)
    return dict(sorted(result.items()))


def encode_first(rows: Rows) -> bytes:
    return ''.join(key + '\t' + rows[key][0] + '\n' for key in sorted(rows)).encode('utf-8')


def expected_manifest() -> dict:
    return {
        'commit': COMMIT,
        'dictionaries': {name: spec._asdict() for name, spec in TABLES.items()},
        'license': 'Apache-2.0',
        'project': 'BYVoid/OpenCC',
        'sourceSha256': {
            Path(name).stem: spec[1] for name, spec in SOURCE_FILES.items()
            if name.startswith('data/dictionary/')
        },
        'version': VERSION,
    }


def manifest_bytes() -> bytes:
    data = (json.dumps(expected_manifest(), ensure_ascii=False, indent=2, sort_keys=True) + '\n').encode('utf-8')
    require(sha256(data) == MANIFEST_SHA256, 'Generator manifest differs from the pinned bytes')
    return data


def config_group(node: dict) -> tuple[str, ...]:
    if node.get('type') == 'group':
        return tuple(name for child in node['dicts'] for name in config_group(child))
    require(node.get('type') == 'ocd2', 'Unexpected upstream dictionary type')
    filename = node['file']
    require(filename in {name + '.ocd2' for name in TABLES}, 'Unexpected upstream dictionary file')
    return (filename[:-5],)


def verify_sources(source: Path) -> dict[str, bytes]:
    files = {}
    for name, (size, digest) in SOURCE_FILES.items():
        data = read_bounded(source / name, size)
        require(len(data) == size and sha256(data) == digest,
                f'{name}: source bytes/SHA-256 differ from OpenCC {COMMIT}')
        # Source code may legitimately have no final LF (DictEntry.cpp does).
        # Hash raw bytes without normalizing them; strict TSV rules apply below.
        data.decode('utf-8', errors='strict')
        files[name] = data
    for name, plan in PLANS.items():
        config = json.loads(files[f'data/config/{name}.json'])
        segmentation = config['segmentation']
        require(segmentation['type'] == 'mmseg', f'{name}: unexpected segmentation')
        segment_dict = config_group(segmentation['dict'])
        stages = tuple(config_group(stage['dict']) for stage in config['conversion_chain'])
        require(segment_dict == (plan[0],) and stages == plan[1], f'{name}: conversion plan differs')
    return files


def build(source: Path) -> dict[str, bytes]:
    files = verify_sources(source)
    raw = {
        Path(name).stem: parse_rows(data, name, multiple=True)
        for name, data in files.items() if name.startswith('data/dictionary/')
    }
    merged: Rows = {}
    # CMake order + common.sort_items: the LAST input wins on duplicate keys.
    for name in ('TWPhrasesIT', 'TWPhrasesName', 'TWPhrasesOther'):
        merged.update(raw[name])
    tables = {name: rows for name, rows in raw.items() if name in TABLES}
    tables['TWPhrases'] = dict(sorted(merged.items()))
    for name in ('TWVariants', 'HKVariants', 'TWPhrases'):
        # Reverse BEFORE discarding non-default forward candidates (e.g. 衹/糭).
        tables[name + 'Rev'] = reverse_rows(tables[name])
    require(tables.keys() == TABLES.keys(), 'Generator dictionary set differs')
    payloads = {f'opencc/{name}.tsv': encode_first(rows) for name, rows in tables.items()}
    payloads['opencc/manifest.json'] = manifest_bytes()
    payloads['licenses/opencc.txt'] = LICENSE_PREAMBLE + files['LICENSE']
    validate_payloads(payloads)
    return payloads


def validate_payloads(payloads: dict[str, bytes]) -> None:
    expected_names = {f'opencc/{name}.tsv' for name in TABLES} | {
        'opencc/manifest.json', 'licenses/opencc.txt',
    }
    require(payloads.keys() == expected_names, 'Asset file set differs')
    for name, spec in TABLES.items():
        path = f'opencc/{name}.tsv'
        data = payloads[path]
        require(len(data) == spec.bytes, f'{path}: byte count differs')
        rows = parse_rows(data, path)
        require(len(rows) == spec.entries, f'{path}: entry count differs')
        require(sha256(data) == spec.sha256, f'{path}: SHA-256 differs')
    manifest = payloads['opencc/manifest.json']
    require(json.loads(manifest) == expected_manifest(), 'manifest.json: source/table metadata differs')
    require(manifest == manifest_bytes(), 'manifest.json: non-canonical bytes')
    license_data = payloads['licenses/opencc.txt']
    utf8_lf(license_data, 'licenses/opencc.txt')
    require(len(license_data) == LICENSE_BYTES and sha256(license_data) == LICENSE_SHA256,
            'licenses/opencc.txt: license/attribution bytes differ')


def check_assets(assets: Path, generated: dict[str, bytes] | None = None) -> None:
    expected_files = {name + '.tsv' for name in TABLES} | {'manifest.json'}
    require({path.name for path in (assets / 'opencc').iterdir()} == expected_files,
            'opencc/: missing or unexpected files')
    payloads = {
        f'opencc/{name}.tsv': read_bounded(assets / 'opencc' / (name + '.tsv'), spec.bytes)
        for name, spec in TABLES.items()
    }
    payloads['opencc/manifest.json'] = read_bounded(assets / 'opencc/manifest.json', len(manifest_bytes()))
    payloads['licenses/opencc.txt'] = read_bounded(assets / 'licenses/opencc.txt', LICENSE_BYTES)
    validate_payloads(payloads)
    if generated is not None:
        for name, data in generated.items():
            require(payloads[name] == data, f'{name}: source rebuild differs from bundled bytes')


def write_assets(assets: Path, payloads: dict[str, bytes]) -> int:
    # All generation and pin validation finish BEFORE any output is created.
    # Resolve every destination first, including existing symlinks/junctions.
    assets = assets.resolve()
    require(os.name != 'nt' or assets.drive.upper() == 'D:', 'Windows output must be on D:')
    targets = {name: (assets / name).resolve() for name in payloads}
    for path in targets.values():
        require(path.is_relative_to(assets), f'{path}: output escapes the assets root')
    if (assets / 'opencc').exists():
        require({path.name for path in (assets / 'opencc').iterdir()} <=
                {Path(name).name for name in payloads if name.startswith('opencc/')},
                'opencc/: unexpected files; refusing to overwrite an unrelated directory')
    written = 0
    for name, path in targets.items():
        data = payloads[name]
        if path.is_file() and path.stat().st_size == len(data) and read_bounded(path, len(data)) == data:
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        written += 1
    return written


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?', type=Path,
                        help='OpenCC source root (LICENSE, data/, src/) from the pinned commit')
    parser.add_argument('--check', action='store_true', help='Read-only validation; never creates output')
    parser.add_argument('--assets-root', type=Path, default=DEFAULT_ASSETS,
                        help='Assets root containing opencc/ and licenses/ (default: bundled assets)')
    args = parser.parse_args(argv)
    if args.source is None and not args.check:
        parser.error('source is required for generation; use --check for an asset-only audit')
    try:
        generated = build(args.source) if args.source is not None else None
        if args.check:
            check_assets(args.assets_root, generated)
            print('CHECK OK: 12 TSVs, 58936 entries, 1103116 TSV bytes; manifest + license pinned.')
        else:
            require(generated is not None, 'No verified source data')
            count = write_assets(args.assets_root, generated)
            print(f'BUILD OK: 14 verified files ({count} written) at {args.assets_root.resolve()}')
        if generated is not None:
            print(f'SOURCE OK: {len(SOURCE_FILES)} pinned files, 8 conversion configs; OpenCC {VERSION} {COMMIT}')
        else:
            print('PROVENANCE: pinned manifest/source hashes verified; source texts not supplied, no rederivation.')
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'OpenCC data check failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
