#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟 CPU 飙高，检测「连续超标」后触发告警，POST 到 webhook。

生产环境：Prometheus 按 alert.rules.yml 的 for: 5m 评估，
这里用「连续 3 次采样超标」模拟（演示用，几秒内触发）。
"""

import json
import random
import time
import urllib.request

CPU_THRESHOLD = 80           # CPU 告警阈值
CONSECUTIVE = 3              # 连续 3 次采样超标（演示；生产是 for: 5m）
WEBHOOK_URL = "http://127.0.0.1:9000/webhook"


def send_alert(severity, title, content):
    """发送告警到 webhook（真实场景换成钉钉/企业微信机器人地址）。"""
    payload = json.dumps({
        "severity": severity,
        "title": title,
        "content": content,
    }).encode("utf-8")
    req = urllib.request.Request(
        WEBHOOK_URL, data=payload, headers={"Content-Type": "application/json"}
    )
    urllib.request.urlopen(req, timeout=3)
    print(f"  📤 已发送 [{severity}] 告警: {title}")


def main():
    print("模拟 CPU 飙高，检测连续超标后触发告警...\n")
    cpu_history = []
    cpu = 30.0
    while True:
        cpu = min(95.0, cpu + random.uniform(8, 15))   # CPU 逐步飙高
        cpu_history.append(cpu)
        print(f"  CPU 使用率: {cpu:.1f}%")

        # 告警条件：连续 CONSECUTIVE 次采样都 > 阈值
        if len(cpu_history) >= CONSECUTIVE and all(c > CPU_THRESHOLD for c in cpu_history[-CONSECUTIVE:]):
            send_alert(
                "P1", "CPU 飙高",
                f"CPU 连续 {CONSECUTIVE} 次采样超过 {CPU_THRESHOLD}%，当前 {cpu:.1f}%",
            )
            break
        time.sleep(0.5)


if __name__ == "__main__":
    main()
