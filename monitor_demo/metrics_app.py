#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""监控埋点服务：覆盖三类监控指标。

① 队列积压（Celery）：
   celery_queue_backlog{queue}  各队列积压数
   celery_task_failed_total     任务失败数
② CPU 负载（系统资源）：
   system_cpu_usage_percent     CPU 使用率
   system_memory_usage_percent  内存使用率
③ 业务指标：
   shortlink_redirect_total{status}  跳转数 → 成功率
   shortlink_cache_hit/miss_total    缓存命中率
   shortlink_rate_limit_total        限流触发
   shortlink_generate_total          短链生成数
"""

import random
import time

import psutil
from prometheus_client import Counter, Gauge, Histogram, start_http_server

# ===== ① 队列积压 =====
queue_backlog = Gauge("celery_queue_backlog", "队列积压任务数", ["queue"])
task_failed_total = Counter("celery_task_failed_total", "任务失败数", ["queue"])

# ===== ② CPU 负载 =====
cpu_usage = Gauge("system_cpu_usage_percent", "CPU 使用率")
memory_usage = Gauge("system_memory_usage_percent", "内存使用率")

# ===== ③ 业务指标 =====
redirect_total = Counter("shortlink_redirect_total", "短链跳转总数", ["status"])
cache_hit = Counter("shortlink_cache_hit_total", "缓存命中数")
cache_miss = Counter("shortlink_cache_miss_total", "缓存未命中数")
rate_limit = Counter("shortlink_rate_limit_total", "限流触发次数")
generate_total = Counter("shortlink_generate_total", "短链生成数")
request_duration = Histogram(
    "shortlink_request_duration_seconds", "请求耗时",
    buckets=[0.01, 0.05, 0.1, 0.5, 1.0, 5.0],
)


def collect_system():
    """采集系统资源（CPU/内存）。"""
    cpu_usage.set(psutil.cpu_percent(interval=0.1))
    memory_usage.set(psutil.virtual_memory().percent)


def simulate_business():
    """模拟业务请求。"""
    request_duration.observe(random.uniform(0.01, 0.5))
    redirect_total.labels(status="success" if random.random() < 0.99 else "failed").inc()
    if random.random() < 0.8:
        cache_hit.inc()
    else:
        cache_miss.inc()
    if random.random() < 0.01:
        rate_limit.inc()
    generate_total.inc(random.randint(0, 3))


def update_queue():
    """更新队列积压（critical 正常，low 积压，体现隔离）。"""
    queue_backlog.labels(queue="critical").set(0)
    queue_backlog.labels(queue="payment").set(random.randint(0, 5))
    queue_backlog.labels(queue="low").set(random.randint(3000, 6000))
    task_failed_total.labels(queue="low").inc(random.randint(0, 2))


if __name__ == "__main__":
    start_http_server(8000)
    print("监控服务启动: http://localhost:8000/metrics")
    while True:
        for _ in range(10):
            simulate_business()
        update_queue()
        collect_system()
        time.sleep(1)
