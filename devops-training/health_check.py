#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""健康检查：检测 CPU 和内存使用率，超阈值告警。

用法:
  python health_check.py              # 默认阈值 80%
  python health_check.py --cpu 90     # 自定义 CPU 阈值
  python health_check.py --mem 85     # 自定义内存阈值

退出码: 0=健康, 1=有告警（可接进 CI / 告警系统）
"""

import argparse
import sys

import psutil


def check_cpu(threshold):
    """检测 CPU 使用率，返回 (使用率, 是否告警)。"""
    cpu = psutil.cpu_percent(interval=1)   # interval=1 采样 1 秒得到真实值
    return cpu, cpu > threshold


def check_memory(threshold):
    """检测内存使用率，返回 (使用率, 是否告警)。"""
    mem = psutil.virtual_memory().percent
    return mem, mem > threshold


def main():
    parser = argparse.ArgumentParser(description="健康检查：CPU 和内存使用率")
    parser.add_argument("--cpu", type=float, default=80, help="CPU 告警阈值 %%（默认 80）")
    parser.add_argument("--mem", type=float, default=80, help="内存告警阈值 %%（默认 80）")
    args = parser.parse_args()

    cpu, cpu_alert = check_cpu(args.cpu)
    mem, mem_alert = check_memory(args.mem)

    print(f"CPU 使用率:  {cpu:.1f}%  {'[告警]' if cpu_alert else '[正常]'}")
    print(f"内存使用率:  {mem:.1f}%  {'[告警]' if mem_alert else '[正常]'}")

    # 有告警则退出码 1，方便脚本化接进告警系统
    return 1 if (cpu_alert or mem_alert) else 0


if __name__ == "__main__":
    sys.exit(main())
