#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Celery 队列长度 Exporter。

Exporter 模式：从数据源（Celery/Redis）采集数据，转成 Prometheus 指标。
真实场景用 redis.llen(queue) 读队列长度，这里用模拟数据演示。
"""

import random
import time

from prometheus_client import Gauge, start_http_server

# 队列长度指标（Gauge：可升可降）
queue_length = Gauge(
    "celery_queue_length",
    "Celery 队列长度（积压任务数）",
    ["queue"],
)

QUEUES = ["critical", "high", "default", "low"]


def collect():
    """采集队列长度。

    真实场景（从 Redis 读）：
        import redis
        r = redis.Redis(host="localhost", port=6379)
        for q in QUEUES:
            queue_length.labels(queue=q).set(r.llen(q))
    """
    for q in QUEUES:
        # 模拟：low 队列积压严重，critical 正常（体现队列隔离）
        length = random.randint(3000, 6000) if q == "low" else random.randint(0, 50)
        queue_length.labels(queue=q).set(length)


if __name__ == "__main__":
    start_http_server(9100)   # Exporter 默认监听 9100
    print("Celery Exporter 启动: http://localhost:9100/metrics")
    while True:
        collect()
        time.sleep(5)
