#!/usr/bin/env bash
# 进程守护：监控进程，挂了自动拉起（守护进程的简易实现）
# 用法: bash process_guard.sh <进程匹配串> [检查间隔秒=5]
# 例:   bash process_guard.sh "celery -A celery_app" 5

set -u

PROCESS_PATTERN="${1:?用法: bash process_guard.sh <进程匹配串> [间隔秒]}"
INTERVAL="${2:-5}"

echo "[守护] 开始监控进程: $PROCESS_PATTERN (每 ${INTERVAL}s 检查一次)"

restart_count=0
while true; do
    if ! pgrep -f "$PROCESS_PATTERN" > /dev/null 2>&1; then
        restart_count=$((restart_count + 1))
        echo "$(date '+%Y-%m-%d %H:%M:%S') [拉起] 进程挂了，第 $restart_count 次拉起: $PROCESS_PATTERN"
        # 真实场景：用 systemctl restart 或 nohup 拉起
        # 这里用 bash -c 演示拉起动作
        (nohup bash -c "$PROCESS_PATTERN" > /dev/null 2>&1 &)
    fi
    sleep "$INTERVAL"
done
