#!/usr/bin/env bash
# 端口探测：探测 host:port 是否开放（用 bash 内建 /dev/tcp，无需 nc）
# 用法: bash port_probe.sh <host> <port> [超时秒=3]

set -u

HOST="${1:?用法: bash port_probe.sh <host> <port> [超时秒]}"
PORT="${2:?用法: bash port_probe.sh <host> <port> [超时秒]}"
TIMEOUT="${3:-3}"

# 用 /dev/tcp 探测（bash 内建，不依赖 nc）
if timeout "$TIMEOUT" bash -c "echo > /dev/tcp/$HOST/$PORT" 2>/dev/null; then
    echo "[开放] $HOST:$PORT ✅"
    exit 0
else
    echo "[关闭] $HOST:$PORT ❌ (或超时 ${TIMEOUT}s)"
    exit 1
fi
