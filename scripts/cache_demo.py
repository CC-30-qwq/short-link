#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""缓存三大问题演示：穿透 / 击穿 / 雪崩 及解决方案。

观察指标：数据库查询次数（db.hits）—— hits 越少说明防护越好。
"""

import random
import threading
import time


class FakeDB:
    """模拟数据库：只有 hot_key 存在，其余都不存在。"""

    def __init__(self):
        self.data = {"hot_key": "热点数据"}
        self.hits = 0

    def get(self, key):
        self.hits += 1
        time.sleep(0.01)  # 模拟查库耗时
        return self.data.get(key)


class Cache:
    """带三大问题防护的缓存（enable_guard 开关防护）。"""

    def __init__(self, db, enable_guard=True):
        self.db = db
        self.store = {}              # key -> (value, expire_at)
        self.enable_guard = enable_guard
        self.lock = threading.Lock()

    def get(self, key):
        # 命中缓存直接返回
        if key in self.store:
            value, expire = self.store[key]
            if time.time() < expire:
                return value

        if not self.enable_guard:
            # 无防护：直接查库（不缓存空值、不加锁、无抖动）
            return self.db.get(key)

        # 有防护：互斥锁（防击穿），只有一个线程去查库重建
        with self.lock:
            # double-check：可能别的线程已经重建好了
            if key in self.store:
                value, expire = self.store[key]
                if time.time() < expire:
                    return value
            value = self.db.get(key)
            if value is None:
                # 防穿透：空值也缓存（短 TTL）
                self.store[key] = (None, time.time() + 5)
            else:
                # 防雪崩：过期时间加随机抖动
                self.store[key] = (value, time.time() + 5 + random.random())
            return value


def demo_penetration():
    """穿透：连续查 10 次不存在的 key，对比 DB 查询次数。"""
    print("=" * 50)
    print("① 穿透：查不存在的 key（10 次）")
    print("=" * 50)
    for guard in (False, True):
        db = FakeDB()
        cache = Cache(db, enable_guard=guard)
        for _ in range(10):
            cache.get("not_exist_key")
        label = "有防护(空值缓存)" if guard else "无防护"
        print(f"  {label:<14} DB 查询次数 = {db.hits}")


def demo_breakdown():
    """击穿：热点 key 过期后，20 线程并发查，对比 DB 查询次数。"""
    print("\n" + "=" * 50)
    print("② 击穿：热点 key 过期后，20 线程并发查")
    print("=" * 50)
    for guard in (False, True):
        db = FakeDB()
        cache = Cache(db, enable_guard=guard)
        # 先缓存一次，再让缓存过期
        cache.store["hot_key"] = ("热点数据", time.time() - 1)  # 已过期

        def worker():
            cache.get("hot_key")

        threads = [threading.Thread(target=worker) for _ in range(20)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        label = "有防护(互斥锁)" if guard else "无防护"
        print(f"  {label:<14} DB 查询次数 = {db.hits}")


if __name__ == "__main__":
    demo_penetration()
    demo_breakdown()
    print("\n" + "=" * 50)
    print("③ 雪崩：解决方案是「过期时间加随机抖动」")
    print("=" * 50)
    print("  无防护: 所有 key 的 TTL 都是 300s → 同时过期 → 集中打库")
    print("  有防护: TTL = 300s × (1 ± 10%随机) → 错开过期 → 平滑")
