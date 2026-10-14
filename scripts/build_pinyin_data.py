"""Rebuild the bundled, numbered pinyin table from a verified source snapshot.

No network access. Provide pinyin.txt from the commit recorded in docs/PINYIN.md.
The default output is inside this repository; refuse a different source hash.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import unicodedata

SOURCE_SHA256 = '621f8ca9eff8519f47e2b17b564fd318161e13bca07eea8c8e04993cd5d3b52e'
ASSET_SHA256 = '9bc44a77c0b3534e7f9d0014d4449a8a37a7159e680d3a426787a569a1c9e3f9'
TONES = {'\u0304': '1', '\u0301': '2', '\u030c': '3', '\u0300': '4'}
ROOT = Path(__file__).resolve().parents[1]


def numbered(value: str) -> str:
    letters: list[str] = []
    tone = '5'
    for char in unicodedata.normalize('NFD', value):
        if char in TONES:
            tone = TONES[char]
        elif char == '\u0308':
            if not letters or letters[-1] != 'u':
                raise ValueError('Unexpected diaeresis in pinned input')
            letters[-1] = 'v'
        elif char == '\u0302':
            if not letters or letters[-1] != 'e':
                raise ValueError('Unexpected circumflex in pinned input')
            letters[-1] = 'ê'
        else:
            letters.append(char)
    return ''.join(letters) + tone


def build(source: Path) -> bytes:
    raw = source.read_bytes()
    if hashlib.sha256(raw).hexdigest() != SOURCE_SHA256:
        raise ValueError('Source hash differs from the pinned pinyin-data snapshot')
    rows: list[str] = []
    seen: set[int] = set()
    for line in raw.decode('utf-8').splitlines():
        body = line.partition('#')[0].strip()
        if not body:
            continue
        code, raw_readings = body.split(':', 1)
        code = code.removeprefix('U+')
        code_point = int(code, 16)
        if code_point in seen or not 0 <= code_point <= 0x10FFFF:
            raise ValueError('Invalid or duplicate code point')
        seen.add(code_point)
        readings = list(dict.fromkeys(numbered(word) for word in raw_readings.strip().split(',')))
        rows.append(code + '\t' + ','.join(readings))
    if len(rows) != 44435:
        raise ValueError('Unexpected code-point count')
    data = ('\n'.join(rows) + '\n').encode('utf-8')
    if hashlib.sha256(data).hexdigest() != ASSET_SHA256:
        raise ValueError('Generated data differs from the pinned asset hash')
    return data


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--check', action='store_true', help='Verify without writing')
    args = parser.parse_args()
    data = build(args.source)
    target = ROOT / 'android/app/src/main/assets/pinyin/readings.tsv'
    if args.check:
        if target.read_bytes() != data:
            raise ValueError('Bundled asset is out of date')
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print('pinyin-data: 44435 code points,', len(data), 'bytes, SHA-256', ASSET_SHA256)


if __name__ == '__main__':
    main()
