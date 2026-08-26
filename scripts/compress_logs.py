#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
扫描指定目录下超过阈值大小的日志文件，自动 gzip 压缩并清理原文件。

用途：日志膨胀时批量压缩归档，释放磁盘。

用法示例：
  python compress_logs.py /var/log                          # 压缩 /var/log 下 >1G 的 .log 文件
  python compress_logs.py /var/log -s 500M -r               # 阈值 500M，递归子目录
  python compress_logs.py /var/log -e .log -e .out --dry-run  # 只看不执行
"""

import argparse
import gzip
import shutil
import sys
from pathlib import Path

DEFAULT_EXTENSIONS = (".log",)
DEFAULT_THRESHOLD = 1 << 30  # 1 GiB


def parse_size(text):
    """把 '1G' / '500M' / '10K' 解析成字节数。"""
    text = text.strip().upper()
    units = {"B": 1, "K": 1 << 10, "M": 1 << 20, "G": 1 << 30, "T": 1 << 40}
    if text[-1] in units:
        number, unit = text[:-1], text[-1]
    else:
        number, unit = text, "B"
    try:
        return int(float(number) * units[unit])
    except ValueError:
        raise argparse.ArgumentTypeError(f"无法解析大小: {text!r}（示例: 1G, 500M）")


def find_large_logs(root, min_size, extensions, recursive):
    """找出 root 下超过 min_size 的日志文件（生成器，避免一次加载全部）。"""
    root = Path(root)
    if not root.is_dir():
        raise NotADirectoryError(f"目录不存在或不是目录: {root}")

    pattern = "**/*" if recursive else "*"
    for path in root.glob(pattern):
        if not path.is_file():
            continue
        if path.suffix.lower() not in extensions:
            continue
        if path.stat().st_size >= min_size:
            yield path


def compress_and_remove(src):
    """把 src 压缩成 src.gz，校验通过后才删除原文件（失败时原文件保留）。"""
    gz = src.with_name(src.name + ".gz")
    # 流式压缩，1MB 分块，避免大文件整体读入内存
    with src.open("rb") as fin, gzip.open(gz, "wb") as fout:
        shutil.copyfileobj(fin, fout, length=1 << 20)
    # 压缩产物可读校验，防止“压缩损坏却已删原文件”
    with gzip.open(gz, "rb") as f:
        f.read(1)
    src.unlink()
    return gz


def main(argv=None):
    parser = argparse.ArgumentParser(description="扫描并压缩超过阈值的大日志文件")
    parser.add_argument("directory", help="要扫描的目录")
    parser.add_argument("-s", "--size", type=parse_size, default=DEFAULT_THRESHOLD,
                        help="大小阈值，如 1G(默认)、500M、10K")
    parser.add_argument("-e", "--ext", action="append", default=None,
                        help="日志扩展名，可多次指定，如 -e .log -e .out（默认 .log）")
    parser.add_argument("-r", "--recursive", action="store_true", help="递归扫描子目录")
    parser.add_argument("--dry-run", action="store_true", help="只列出将处理的文件，不真正压缩")
    args = parser.parse_args(argv)

    extensions = args.ext or list(DEFAULT_EXTENSIONS)
    extensions = [e if e.startswith(".") else "." + e for e in extensions]

    files = list(find_large_logs(args.directory, args.size, extensions, args.recursive))
    if not files:
        print(f"没有找到超过 {args.size >> 20}M 的日志文件")
        return 0

    print(f"找到 {len(files)} 个超过阈值的日志文件：")
    ok = failed = 0
    total_saved = 0
    for f in files:
        size_before = f.stat().st_size
        print(f"  - {f}  ({size_before / (1 << 20):.1f}M)")
        if args.dry_run:
            continue
        try:
            gz = compress_and_remove(f)
            total_saved += size_before - gz.stat().st_size
            ok += 1
            print(f"    -> 已压缩 {gz.name}")
        except Exception as e:  # 单个文件失败不影响其他文件
            failed += 1
            print(f"    -> 失败: {e}", file=sys.stderr)

    if args.dry_run:
        print("\n[dry-run] 未做任何修改，去掉 --dry-run 即真正执行。")
    else:
        print(f"\n完成：成功 {ok} 个，失败 {failed} 个，释放空间约 {total_saved / (1 << 20):.1f}M")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
