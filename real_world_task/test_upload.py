# -*- coding: utf-8 -*-
"""素材上传测试：分片正确性 + 断网重传 + 重试耗尽。"""

import pytest

from upload_service import FlakyNetwork, Uploader


@pytest.mark.parametrize("size,chunk_size", [
    (0, 1024),      # 空文件
    (1, 1024),      # 1 字节
    (1024, 1024),   # 恰好一个分片
    (1025, 1024),   # 不整除，2 个分片
    (10000, 3),     # 大量小分片
])
def test_upload_reassembles_correctly(size, chunk_size):
    data = bytes(i % 256 for i in range(size))
    net = FlakyNetwork(fail_rate=0.0)  # 网络正常
    uploader = Uploader(net, chunk_size=chunk_size)
    assert uploader.upload(data) == data


def test_upload_retries_on_network_failure():
    data = b"x" * 10000
    net = FlakyNetwork(fail_rate=0.5, seed=42)  # 高失败率，强制断网
    uploader = Uploader(net, chunk_size=1000, max_retries=10)

    result = uploader.upload(data)

    assert result == data          # 重传后数据完整
    assert net.send_count > 10     # 10 个分片，但发送次数 > 10，说明发生了重传


def test_upload_gives_up_after_max_retries():
    net = FlakyNetwork(fail_rate=1.0)  # 100% 失败
    uploader = Uploader(net, chunk_size=100, max_retries=3)

    with pytest.raises(ConnectionError):
        uploader.upload(b"x" * 300)  # 3 个分片，每个重试 3 次都失败
