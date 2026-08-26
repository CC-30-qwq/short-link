# -*- coding: utf-8 -*-
"""被测纯函数：URL 规范化（复用短链项目的逻辑，用于 parametrize 数据驱动）。"""


def normalize_url(url):
    """规范化 URL：补协议前缀 + 基础校验。"""
    if url is None or not url.strip():
        raise ValueError("URL不能为空")
    url = url.strip()
    if not url.startswith(("http://", "https://")):
        url = "http://" + url
    if len(url) > 2048 or "." not in url:
        raise ValueError("URL格式不合法")
    return url
