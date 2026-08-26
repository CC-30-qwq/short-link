#!/usr/bin/env bash
# Celery 队列精准管理
#
# 用法:
#   bash queue_manage.sh status          # 查看所有队列状态/长度
#   bash queue_manage.sh pause low       # 暂停消费 low 队列
#   bash queue_manage.sh resume low      # 恢复消费 low 队列
#   bash queue_manage.sh purge low       # 清空 low 队列

set -euo pipefail

APP="celery_app"
CMD="${1:-status}"
QUEUE="${2:-low}"

case "$CMD" in
    status)
        echo "▶ 队列状态（worker 统计）"
        celery -A "$APP" inspect stats
        echo "▶ 已注册任务"
        celery -A "$APP" inspect registered
        ;;
    pause)
        echo "▶ 暂停消费队列 [$QUEUE]"
        celery -A "$APP" control cancel_consumer "$QUEUE"
        ;;
    resume)
        echo "▶ 恢复消费队列 [$QUEUE]"
        celery -A "$APP" control add_consumer "$QUEUE"
        ;;
    purge)
        echo "▶ 清空队列 [$QUEUE]"
        celery -A "$APP" purge -Q "$QUEUE" -f
        ;;
    *)
        echo "用法: bash queue_manage.sh [status|pause|resume|purge] [queue]"
        exit 1
        ;;
esac
