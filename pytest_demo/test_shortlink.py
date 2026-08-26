# -*- coding: utf-8 -*-
"""parametrize 重点：数据驱动测试。

一个测试函数配多组数据跑多遍，专治「复制粘贴测试函数」的坏味道。
"""

import pytest

from shortlink import normalize_url


@pytest.mark.parametrize("raw,expected", [
    ("example.com", "http://example.com"),             # 补协议
    ("https://example.com/a", "https://example.com/a"), # 已有协议不动
    ("  example.com  ", "http://example.com"),          # 去首尾空格
    ("a.b", "http://a.b"),                             # 最小合法
])
def test_normalize_ok(raw, expected):
    assert normalize_url(raw) == expected


@pytest.mark.parametrize("raw", [
    "",
    "   ",
    "not-a-url",     # 无点号
    "x" * 2049,      # 超长
])
def test_normalize_invalid(raw):
    with pytest.raises(ValueError):
        normalize_url(raw)
