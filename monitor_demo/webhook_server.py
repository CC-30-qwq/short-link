#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""本地 webhook 接收端点（模拟钉钉/企业微信机器人服务器）。

真实场景：把告警 POST 到钉钉/企业微信的机器人 webhook 地址，
这里用本地 HTTP 服务接收，展示「告警消息真实送达」。
"""

import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length))
        print(f"📱 [webhook 收到告警] severity={body['severity']}")
        print(f"   标题: {body['title']}")
        print(f"   内容: {body['content']}")
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args):
        pass  # 静默


if __name__ == "__main__":
    print("webhook 接收端点启动: http://127.0.0.1:9000/webhook")
    HTTPServer(("127.0.0.1", 9000), Handler).serve_forever()
