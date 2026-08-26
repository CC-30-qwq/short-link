# -*- coding: utf-8 -*-
"""素材上传：大文件分片 + 断网重传 + 合并。

核心逻辑：
  1. 分片：把大文件切成 chunk_size 大小的分片
  2. 断网重传：每个分片发送失败自动重试，直到成功或超过最大次数
  3. 合并：所有分片按顺序拼接，还原完整文件
"""

import random


def split_chunks(data: bytes, chunk_size: int) -> list:
    """把数据切成 chunk_size 大小的分片。"""
    return [data[i:i + chunk_size] for i in range(0, len(data), chunk_size)]


class FlakyNetwork:
    """模拟不稳定网络：以 fail_rate 概率抛 ConnectionError（断网）。"""

    def __init__(self, fail_rate: float = 0.3, seed: int = None):
        self.fail_rate = fail_rate
        self.rng = random.Random(seed)
        self.send_count = 0  # 实际发送次数（含重传）

    def send(self, chunk_id: int, chunk: bytes):
        self.send_count += 1
        if self.rng.random() < self.fail_rate:
            raise ConnectionError(f"断网：分片 {chunk_id} 发送失败")


class Uploader:
    """分片上传 + 断网重传 + 合并。"""

    def __init__(self, network, chunk_size: int = 1024, max_retries: int = 3):
        self.network = network
        self.chunk_size = chunk_size
        self.max_retries = max_retries
        self.received = {}  # chunk_id -> chunk

    def upload(self, data: bytes) -> bytes:
        chunks = split_chunks(data, self.chunk_size)
        for cid, chunk in enumerate(chunks):
            self._upload_chunk_with_retry(cid, chunk)
        # 按顺序合并，还原完整文件
        return b"".join(self.received[i] for i in range(len(chunks)))

    def _upload_chunk_with_retry(self, cid, chunk):
        for attempt in range(1, self.max_retries + 1):
            try:
                self.network.send(cid, chunk)
                self.received[cid] = chunk
                return
            except ConnectionError:
                if attempt == self.max_retries:
                    raise  # 重试耗尽，放弃
                # 否则继续重传
