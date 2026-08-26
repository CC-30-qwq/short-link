#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""部署 + 队列隔离管理脚本（Python 版）。

版本管理：
  python deploy.py release                       # 发布新版本
  python deploy.py rollback --version 2026010112 # 回滚到指定版本
  python deploy.py list                          # 列出所有版本

队列隔离管理（8 组优先级队列，可单独操作某一个）：
  python deploy.py queue list                    # 列出 8 组队列
  python deploy.py queue monitor high            # 监控指定队列积压
  python deploy.py queue purge low               # 清空指定队列
  python deploy.py queue restart payment         # 单独重启指定队列的 worker

核心思想：队列隔离 —— 核心计费(critical/payment)在最高优先级队列，
低优先级队列(cleanup/low)积压不会影响核心计费，且可单独操作不互相干扰。
"""

import argparse
import datetime
import shutil
import subprocess
import sys
from pathlib import Path

DEPLOY_ROOT = Path(".deploy")
RELEASES_DIR = DEPLOY_ROOT / "releases"
CURRENT = DEPLOY_ROOT / "current"
VERSION_FILE = DEPLOY_ROOT / "current_version"
KEEP_RELEASES = 5

# 8 组队列，优先级从高到低（队列隔离的核心：核心计费独占最高优先级）
QUEUES = {
    "critical":     {"priority": 10, "desc": "核心计费（最高优先，必须单独监控）"},
    "payment":      {"priority": 9,  "desc": "支付/扣款"},
    "high":         {"priority": 8,  "desc": "高优先级业务"},
    "default":      {"priority": 5,  "desc": "默认队列"},
    "upload":       {"priority": 4,  "desc": "素材上传"},
    "notification": {"priority": 3,  "desc": "通知（可延迟）"},
    "cleanup":      {"priority": 2,  "desc": "清理任务"},
    "low":          {"priority": 1,  "desc": "最低优先级（积压不影响核心）"},
}

# 模拟队列积压（真实场景从 celery inspect stats 读取）
MOCK_BACKLOG = {
    "critical": 0, "payment": 0, "high": 2, "default": 15,
    "upload": 42, "notification": 130, "cleanup": 890, "low": 5000,
}


# ==================== 版本管理 ====================

def switch_to(target: Path, version: str) -> str:
    """切换 current 指针到目标版本（软链接优先，Windows 复制兜底）。"""
    if CURRENT.exists() or CURRENT.is_symlink():
        shutil.rmtree(CURRENT)
    try:
        CURRENT.symlink_to(target, target_is_directory=True)
        mode = "软链接"
    except OSError:
        shutil.copytree(target, CURRENT)
        mode = "复制"
    VERSION_FILE.write_text(version)
    return mode


def cmd_release(args):
    version = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    release_dir = RELEASES_DIR / version
    release_dir.mkdir(parents=True, exist_ok=True)

    src = Path("src")
    if src.is_dir():
        for f in src.iterdir():
            dst = release_dir / f.name
            shutil.copytree(f, dst, dirs_exist_ok=True) if f.is_dir() else shutil.copy2(f, dst)

    mode = switch_to(release_dir, version)
    print(f"✔ 发布成功：{version}（切换方式: {mode}）")
    # 清理旧版本
    versions = sorted(RELEASES_DIR.iterdir(), key=lambda p: p.name, reverse=True)
    for old in versions[KEEP_RELEASES:]:
        shutil.rmtree(old)
        print(f"  清理旧版本: {old.name}")
    return 0


def cmd_rollback(args):
    version = args.version
    target = RELEASES_DIR / version
    if not target.exists():
        print(f"✗ 版本 {version} 不存在，可用版本：", file=sys.stderr)
        for v in sorted(RELEASES_DIR.iterdir(), key=lambda p: p.name, reverse=True):
            print(f"  {v.name}", file=sys.stderr)
        return 1
    mode = switch_to(target, version)
    print(f"✔ 已回滚到：{version}（切换方式: {mode}）")
    return 0


def cmd_list(args):
    cur = VERSION_FILE.read_text().strip() if VERSION_FILE.exists() else "无"
    print(f"当前版本: {cur}")
    print("所有版本（新→旧）:")
    for v in sorted(RELEASES_DIR.iterdir(), key=lambda p: p.name, reverse=True):
        print(f"  {v.name}")
    return 0


# ==================== 队列隔离管理 ====================

def cmd_queue_list(args):
    print(f"{'队列':<14}{'优先级':<8}说明")
    for name, info in sorted(QUEUES.items(), key=lambda kv: -kv[1]["priority"]):
        print(f"{name:<14}{info['priority']:<8}{info['desc']}")
    return 0


def cmd_queue_monitor(args):
    q = args.queue
    if q not in QUEUES:
        print(f"✗ 未知队列: {q}", file=sys.stderr)
        return 1
    backlog = MOCK_BACKLOG.get(q, 0)
    info = QUEUES[q]
    status = "⚠️ 积压！" if backlog > 100 else "✅ 正常"
    print(f"队列 [{q}]  优先级={info['priority']}  {info['desc']}")
    print(f"  积压任务数: {backlog}  {status}")
    # 核心计费队列积压 → 立即告警
    if q in ("critical", "payment") and backlog > 0:
        print("  🚨 告警：核心计费队列积压，立即处理（可能影响扣款）！")
    # 低优先级队列积压 → 提醒但不影响核心
    if q in ("cleanup", "low") and backlog > 100:
        print("  💡 提示：低优先级队列积压，不影响核心计费，可稍后清理")
    return 0


def cmd_queue_purge(args):
    q = args.queue
    if q not in QUEUES:
        print(f"✗ 未知队列: {q}", file=sys.stderr)
        return 1
    # 真实场景：celery -A celery_app purge -Q <q> -f
    print(f"▶ 清空队列 [{q}]")
    print(f"  执行: celery -A celery_app purge -Q {q} -f")
    return 0


def cmd_queue_restart(args):
    q = args.queue
    if q not in QUEUES:
        print(f"✗ 未知队列: {q}", file=sys.stderr)
        return 1
    # 真实场景：只重启消费该队列的 worker，不影响其他队列
    print(f"▶ 单独重启队列 [{q}] 的 worker（不影响其他队列）")
    print(f"  执行: celery -A celery_app worker -Q {q} --concurrency=2")
    return 0


def main():
    parser = argparse.ArgumentParser(prog="deploy.py", description="部署 + 队列隔离管理")
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("release", help="发布新版本")
    p_rollback = sub.add_parser("rollback", help="回滚到指定版本")
    p_rollback.add_argument("--version", required=True, help="回滚到的版本号")
    sub.add_parser("list", help="列出所有版本")

    p_queue = sub.add_parser("queue", help="队列隔离管理")
    q_sub = p_queue.add_subparsers(dest="queue_cmd")
    q_sub.add_parser("list", help="列出 8 组队列")
    p_mon = q_sub.add_parser("monitor", help="监控指定队列")
    p_mon.add_argument("queue")
    p_purge = q_sub.add_parser("purge", help="清空指定队列")
    p_purge.add_argument("queue")
    p_restart = q_sub.add_parser("restart", help="单独重启指定队列 worker")
    p_restart.add_argument("queue")

    args = parser.parse_args()
    if args.command is None:
        parser.print_help()
        return 1

    RELEASES_DIR.mkdir(parents=True, exist_ok=True)

    handlers = {
        "release": cmd_release,
        "rollback": cmd_rollback,
        "list": cmd_list,
    }
    queue_handlers = {
        "list": cmd_queue_list,
        "monitor": cmd_queue_monitor,
        "purge": cmd_queue_purge,
        "restart": cmd_queue_restart,
    }

    if args.command == "queue":
        fn = queue_handlers.get(args.queue_cmd)
        if fn is None:
            p_queue.print_help()
            return 1
        return fn(args)
    return handlers[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
