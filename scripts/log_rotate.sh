#!/usr/bin/env bash
# 日志切割：日志超过阈值就切割压缩，并清理过期日志（防磁盘爆满）
# 用法: bash log_rotate.sh <日志文件> [阈值MB=100] [保留份数=5]

set -u

LOG_FILE="${1:?用法: bash log_rotate.sh <日志文件> [阈值MB] [保留份数]}"
MAX_SIZE_MB="${2:-100}"
KEEP="${3:-5}"

[ -f "$LOG_FILE" ] || { echo "日志文件不存在: $LOG_FILE"; exit 1; }

# 当前大小（MB）
size_mb=$(du -m "$LOG_FILE" 2>/dev/null | awk '{print $1}')
size_mb=${size_mb:-0}

if [ "$size_mb" -lt "$MAX_SIZE_MB" ]; then
    echo "[跳过] $LOG_FILE 当前 ${size_mb}MB < 阈值 ${MAX_SIZE_MB}MB"
    exit 0
fi

# 切割：重命名 + 压缩
timestamp=$(date +%Y%m%d%H%M%S)
rotated="$LOG_FILE.$timestamp"
echo "[切割] ${size_mb}MB 超过阈值，重命名为 $rotated"
mv "$LOG_FILE" "$rotated"
gzip "$rotated"
touch "$LOG_FILE"          # 新建空日志，服务继续写

# 清理：只保留最近 KEEP 份
count=$(ls -1 "$LOG_FILE".*.gz 2>/dev/null | wc -l)
if [ "$count" -gt "$KEEP" ]; then
    ls -1t "$LOG_FILE".*.gz | tail -n +$((KEEP + 1)) | xargs -r rm -f
    echo "[清理] 保留最近 $KEEP 份，删除 $(($count - KEEP)) 份旧日志"
fi

echo "[完成] 当前保留日志:"
ls -1 "$LOG_FILE".*.gz 2>/dev/null
