#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核心指标暴露：队列积压数(queue_size) + 失败率(failure_rate)。

失败率的两种暴露方式（关键知识点）：
  方式A（推荐）：暴露 Counter（total / failed），用 PromQL rate() 算失败率
       rate(task_failed[5m]) / rate(task_total[5m])
       好处：能看趋势、能做时间窗口、不丢失历史信息
  方式B（不推荐）：直接暴露 failure_rate Gauge
       坏处：瞬时值丢失历史，无法用 rate() 做时间聚合
"""

import random
import time

from prometheus_client import Counter, Gauge, start_http_server

QUEUES = ["critical", "payment", "upload", "low"]

# ① 队列积压数（Gauge：可升可降的瞬时值）
queue_size = Gauge("celery_queue_size", "队列积压任务数", ["queue"])

# ② 失败率基础：两个 Counter（总数 + 失败数），PromQL 里算失败率
task_total = Counter("celery_task_total", "任务处理总数", ["queue"])
task_failed = Counter("celery_task_failed_total", "任务失败总数", ["queue"])


def update():
    for q in QUEUES:
        # 队列积压：low 积压严重，critical 正常（队列隔离）
        backlog = random.randint(3000, 6000) if q == "low" else random.randint(0, 10)
        queue_size.labels(queue=q).set(backlog)

        # 任务处理：模拟总数和失败数（失败率约 0~5%）
        total = random.randint(100, 500)
        failed = random.randint(0, int(total * 0.05))
        task_total.labels(queue=q).inc(total)
        task_failed.labels(queue=q).inc(failed)


if __name__ == "__main__":
    start_http_server(8000)
    print("核心指标服务启动: http://localhost:8000/metrics")
    print("  暴露指标:")
    print("    celery_queue_size{queue}         队列积压数")
    print("    celery_task_total{queue}         任务总数")
    print("    celery_task_failed_total{queue}  任务失败数（配合 total 算失败率）")
    while True:
        update()
        time.sleep(5)
