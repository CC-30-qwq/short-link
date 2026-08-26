# -*- coding: utf-8 -*-
"""模拟 Celery Worker 消费 Redis List 的核心机制（无真实 Redis）。

复现的关键概念：
  LPUSH           生产者把任务推进队列（Redis List 左进）
  BRPOP           Worker 阻塞弹出（队列空就阻塞等待）
  inflight        已取走但未确认的任务（防止丢任务）
  ACK             处理完成，确认移除
  visibility_timeout  可见性超时：取走后超时未确认 → 重新入队
"""

import time
from collections import deque


class RedisLikeQueue:
    def __init__(self, visibility_timeout=2):
        self.queue = deque()               # 待处理队列
        self.inflight = {}                 # 已取走未确认：{task: deadline}
        self.visibility_timeout = visibility_timeout

    def lpush(self, task):
        """生产者：任务入队（对应 Redis LPUSH）。"""
        self.queue.appendleft(task)
        print(f"  [生产者] LPUSH 入队: {task}")

    def brpop(self):
        """Worker：阻塞出队（对应 Redis BRPOP，队列空则阻塞轮询）。"""
        while not self.queue:
            time.sleep(0.2)                # 队列空，阻塞等待
        task = self.queue.pop()
        self.inflight[task] = time.time() + self.visibility_timeout
        print(f"  [Worker] BRPOP 取出: {task}（进入 inflight）")
        return task

    def ack(self, task):
        """Worker：处理完成，确认（ACK）后任务才真正从系统移除。"""
        self.inflight.pop(task, None)
        print(f"  [Worker] ACK 确认: {task}")

    def redeliver_timeout(self):
        """超时重投：取走后超时未 ACK 的任务，重新入队（防 Worker 崩溃丢任务）。"""
        now = time.time()
        for task, deadline in list(self.inflight.items()):
            if now > deadline:
                self.inflight.pop(task)
                self.queue.appendleft(task)
                print(f"  [重投] 任务 {task} 超时未确认，重新入队")


if __name__ == "__main__":
    q = RedisLikeQueue(visibility_timeout=1)

    print("=== 正常流程：入队 -> 消费 -> ACK ===")
    q.lpush("task-A")
    q.lpush("task-B")
    t1 = q.brpop()
    q.ack(t1)
    t2 = q.brpop()
    q.ack(t2)

    print("\n=== 崩溃场景：取走但未 ACK，超时重投 ===")
    q.lpush("task-C")
    t3 = q.brpop()
    print(f"  （Worker 崩溃，task-C 未 ACK）")
    time.sleep(1.2)                       # 等待超过可见性超时
    q.redeliver_timeout()                 # 重新入队
    t4 = q.brpop()                        # 新 Worker 重新消费
    q.ack(t4)
