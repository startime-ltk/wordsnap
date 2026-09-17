# -*- coding: utf-8 -*-
"""下载开源四级词表并清洗成 单词,释义 的 CSV（UTF-8 无 BOM）。

用法：
    python fetch_cet4.py [输出csv路径]

数据源（按顺序尝试）：
    1. jsDelivr CDN（国内可达性较好）
    2. GitHub raw
来源仓库：KyleBing/english-vocabulary（四级乱序，约 7500 词，格式：单词<TAB>释义）
"""
import io
import os
import re
import sys
import urllib.parse
import urllib.request

SOURCES = [
    "https://cdn.jsdelivr.net/gh/KyleBing/english-vocabulary@master/"
    + urllib.parse.quote("3 四级-乱序.txt"),
    "https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/"
    + urllib.parse.quote("3 四级-乱序.txt"),
]

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}


def download() -> str:
    last_err = None
    for url in SOURCES:
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            text = data.decode("utf-8-sig", errors="replace")
            print("downloaded from:", url)
            return text
        except Exception as e:  # noqa: BLE001
            last_err = e
            print("failed:", url, "->", e)
    raise SystemExit("all sources failed: %s" % last_err)


WORD_RE = re.compile(r"^[A-Za-z][A-Za-z'\- ]*$")


def clean(text: str):
    seen = {}
    bad = 0
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        # 支持 TAB / 逗号 / 多空格分隔，取第一个分隔符
        if "\t" in line:
            w, _, m = line.partition("\t")
        elif "," in line:
            w, _, m = line.partition(",")
        else:
            parts = line.split(None, 1)
            if len(parts) < 2:
                bad += 1
                continue
            w, m = parts[0], parts[1]
        w = w.strip().lower()
        m = m.strip()
        # 去掉常见脏字符
        w = w.strip(".,;:!?\"'()[]{}")
        if not w or not m:
            bad += 1
            continue
        if not WORD_RE.match(w):
            bad += 1
            continue
        if w not in seen:
            seen[w] = m
    return seen, bad


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "cet4_full.csv"
    text = download()
    words, bad = clean(text)
    with io.open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("# 大学英语四级词库 · 来源 KyleBing/english-vocabulary（MIT）\n")
        f.write("# 格式：单词,释义\n")
        for w in sorted(words):
            f.write("%s,%s\n" % (w, words[w]))
    print("entries=%d skipped=%d -> %s" % (len(words), bad, os.path.abspath(out)))


if __name__ == "__main__":
    main()
