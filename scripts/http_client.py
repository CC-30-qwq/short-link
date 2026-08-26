#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HTTP 客户端封装：根据状态码和异常决定「重试」还是「报警」。

HTTP 协议肌肉记忆（核心规则）：
  - 2xx：成功
  - 3xx：重定向（requests 默认自动跟随，一般无需处理）
  - 4xx：客户端问题（参数错 / 权限不足 / 资源不存在）→ 重试无效，直接报错
  - 5xx：服务端崩溃/过载 → 可能瞬时，可重试（指数退避），重试仍失败 → 报警
  - 网络异常（超时 / 连接失败）→ 可重试

用法：
  python http_client.py http://127.0.0.1:8899/server-error
  python http_client.py http://127.0.0.1:8899/flaky --retries 3
"""

import argparse
import logging
import sys
import time

import requests

logger = logging.getLogger("http_client")

# 可重试的状态码：429（限流）+ 全部 5xx
RETRYABLE_STATUS = {429} | set(range(500, 600))


def alert(message):
    """报警：真实场景替换成邮件 / 钉钉 / Webhook / 短信。"""
    # TODO: 接入告警系统，例如：
    # requests.post("https://your-webhook", json={"text": message})
    print(f"[ALERT] {message}", file=sys.stderr)


def request_with_retry(url, max_retries=3, timeout=5, backoff=2):
    """
    带重试的 GET 请求。

    返回 (status_code, response)；最终失败返回 (None, None)。
    """
    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.get(url, timeout=timeout)

            if 200 <= resp.status_code < 300:
                logger.info("SUCCESS %s -> %d", url, resp.status_code)
                return resp.status_code, resp

            if resp.status_code in RETRYABLE_STATUS:
                # 5xx / 429：服务端问题，可重试（指数退避 1s, 2s, 4s ...）
                if attempt < max_retries:
                    wait = backoff ** (attempt - 1)
                    logger.warning("5xx=%d attempt=%d, retry after %ds",
                                   resp.status_code, attempt, wait)
                    time.sleep(wait)
                    continue
                alert(f"{url} 重试 {max_retries} 次仍返回 {resp.status_code}")
                return resp.status_code, resp

            # 4xx：客户端问题，重试无效，直接报错不重试
            logger.error("4xx=%d client error (param/auth/resource), no retry",
                         resp.status_code)
            return resp.status_code, resp

        except requests.exceptions.Timeout:
            if attempt < max_retries:
                logger.warning("TIMEOUT attempt=%d, retry", attempt)
                continue
            alert(f"{url} 超时 {max_retries} 次")

        except requests.exceptions.ConnectionError as e:
            if attempt < max_retries:
                logger.warning("CONNECTION ERROR attempt=%d (%s), retry", attempt, e)
                continue
            alert(f"{url} 连接失败 {max_retries} 次: {e}")

    return None, None


def main(argv=None):
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="HTTP 客户端：状态码驱动的重试与报警")
    parser.add_argument("url")
    parser.add_argument("--retries", type=int, default=3)
    args = parser.parse_args(argv)

    code, _ = request_with_retry(args.url, max_retries=args.retries)
    return 0 if code and code < 400 else 1


if __name__ == "__main__":
    sys.exit(main())
