# -*- coding: utf-8 -*-
"""Celery 任务定义：计费 + 素材上传 + 清理（含重试）。"""

from celery_app import app


@app.task(bind=True, autoretry_for=(Exception,), retry_backoff=True, max_retries=3)
def process_payment(self, order_id):
    """计费任务：失败自动重试（指数退避 1s/2s/4s）。"""
    print(f"[计费] 处理订单 {order_id}（第 {self.request.retries + 1} 次尝试）")
    if order_id == "fail":
        raise ValueError("计费失败，触发自动重试")
    return f"订单 {order_id} 处理完成"


@app.task
def process_upload(file_id):
    """素材上传任务。"""
    print(f"[上传] 处理文件 {file_id}")
    return f"文件 {file_id} 处理完成"


@app.task
def cleanup():
    """清理任务（定时调度）。"""
    print("[清理] 清理过期数据")
    return "清理完成"
