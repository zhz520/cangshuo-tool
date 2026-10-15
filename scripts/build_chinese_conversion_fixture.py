"""Build a differential corpus using the upstream OpenCC 1.1.9 command line.

Usage: python scripts/build_chinese_conversion_fixture.py /path/to/opencc /path/to/configs
The executable and configs must come from the official OpenCC 1.1.9 release.
Only development uses this program; tests and the app need no external executable.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
MODES = ("s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s")
SEPARATOR = "\x01"
EXAMPLES = (
    "", " \t\r\n", "头发发展，皇后在后台喝干杯酒。", "理发、发型、发现、发展、出发。",
    "鼠标、软件、内存和打印机，硬盘与互联网连接。", "里面有群峰，吃面看着海。",
    "皇后後來在後臺聽音樂，頭髮乾淨。", "滑鼠、軟體、記憶體、印表機和硬碟。",
    "臺灣、裏面、羣峯、衞生、牀、麪、鐘錶。", "乾坤乾隆干净干涉，干一杯酒。",
    "重慶銀行、音樂與快樂。", "一边工作一边想着什么，重庆长江。",
    "網路、資料庫、欄位、預設值、伺服器。", "中文 English 123，标点！？  \t保持\r\n换行\n末尾。",
    "😀𠮷𰻞汉字🙂 A\u0301 ＡＢＣ １２３", "發髮後后乾幹干臺台裡裏里麵面",
    "著作着火，覆蓋覆盖，滑鼠标与软体操。", "二〇二六年十月十五日，中文转换测试。",
    "源代码、文件夹、计算机与程序设计。", "吃面条，鸡蛋面；钟表、钟声、手表。",
    "轉換不一定可逆，請保留原文。", "春风十里，不如相遇；山河远阔，人间烟火。",
    "HTTP https://example.com/a?q=中文&lang=zh#繁體", "繁簡轉換\n簡繁互轉\r\n軟體 滑鼠\t内存",
)


def build(executable: Path, configs: Path) -> bytes:
    version = subprocess.run([str(executable), "--version"], check=True, capture_output=True).stdout
    if b"Version: 1.1.9" not in version:
        raise ValueError("The differential oracle must be OpenCC 1.1.9")
    assets = ROOT / "android/app/src/main/assets/opencc"
    manifest = json.loads((assets / "manifest.json").read_text(encoding="utf-8"))
    cases = list(EXAMPLES)
    for name, metadata in sorted(manifest["dictionaries"].items()):
        data = (assets / (name + ".tsv")).read_bytes()
        if hashlib.sha256(data).hexdigest() != metadata["sha256"]:
            raise ValueError("Bundled dictionary checksum mismatch: " + name)
        keys = [line.split("\t", 1)[0] for line in data.decode("utf-8").splitlines()]
        # Evenly sample each shipped dictionary, including its first and last rows.
        for index in sorted({i * (len(keys) - 1) // 7 for i in range(8)}):
            cases.append(keys[index] + "，" + keys[(index + 1) % len(keys)])
    cases = list(dict.fromkeys(cases))
    if any(SEPARATOR in value for value in cases):
        raise ValueError("Oracle separator must not occur in an input")
    # Windows CLI text-mode streams rewrite LF as CRLF. Convert the actual line
    # contents and reassemble their original delimiters, so transport rewriting
    # is not mistaken for a dictionary/engine rule. Dictionary keys have no CR/LF.
    fragments = list(dict.fromkeys(part for value in cases for part in re.split(r"\r\n|\r|\n", value)))
    lines = [
        "# OpenCC 1.1.9 / BYVoid/OpenCC@556ed22496d650bd0b13b6c163be9814637970ae",
        "# Generated with the official CLI; mode<TAB>base64(UTF-8 input)<TAB>base64(UTF-8 output)",
    ]
    task = ROOT / "temp/chinese-convert"
    task.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="oracle-", dir=task) as directory:
        source = Path(directory) / "input.txt"
        destination = Path(directory) / "output.txt"
        source.write_bytes(SEPARATOR.join(fragments).encode("utf-8"))
        for mode in MODES:
            subprocess.run([str(executable), "-c", str(configs / (mode + ".json")),
                            "-i", str(source), "-o", str(destination)], check=True, capture_output=True)
            outputs = destination.read_bytes().decode("utf-8").split(SEPARATOR)
            if len(outputs) != len(fragments):
                raise ValueError("Oracle changed the separator or case count: " + mode)
            converted = dict(zip(fragments, outputs))
            for value in cases:
                parts = re.split(r"(\r\n|\r|\n)", value)
                output = "".join(part if index % 2 else converted[part] for index, part in enumerate(parts))
                encoded = [base64.b64encode(item.encode("utf-8")).decode("ascii") for item in (value, output)]
                lines.append(mode.upper() + "\t" + "\t".join(encoded))
    return ("\n".join(lines) + "\n").encode("utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("executable", type=Path)
    parser.add_argument("configs", type=Path)
    parser.add_argument("--check", action="store_true", help="Compare without changing the fixture")
    args = parser.parse_args()
    expected = build(args.executable.resolve(), args.configs.resolve())
    target = ROOT / "android/app/src/test/resources/chineseconvert/opencc-1.1.9.tsv"
    if args.check:
        if target.read_bytes() != expected:
            raise SystemExit("Differential fixture differs from the OpenCC 1.1.9 oracle")
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(expected)
    print("OpenCC differential corpus:", len(expected.splitlines()) - 2, "cases, SHA-256",
          hashlib.sha256(expected).hexdigest())


if __name__ == "__main__":
    main()
