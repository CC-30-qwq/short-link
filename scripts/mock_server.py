#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地 mock 服务，用于演示 HTTP 客户端对各类状态码的处理。

端点：
  /ok             -> 200（成功）
  /not-found      -> 404（4xx 客户端错）
  /forbidden      -> 403（4xx 权限错）
  /server-error   -> 500（5xx 服务端错，每次都失败）
  /flaky          -> 第一次 500，之后 200（模拟瞬时故障，验证重试成功）

启动： python mock_server.py
"""

import http.server
import socketserver

PORT = 8899
flaky_calls = {"n": 0}


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/ok":
            self._respond(200)
        elif self.path == "/not-found":
            self._respond(404)
        elif self.path == "/forbidden":
            self._respond(403)
        elif self.path == "/server-error":
            self._respond(500)
        elif self.path == "/flaky":
            flaky_calls["n"] += 1
            self._respond(500 if flaky_calls["n"] == 1 else 200)
        else:
            self._respond(404)

    def _respond(self, code):
        self.send_response(code)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, *args):
        pass  # 静默，避免刷屏


if __name__ == "__main__":
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", PORT), Handler) as httpd:
        print(f"mock server 启动: http://127.0.0.1:{PORT}")
        httpd.serve_forever()
