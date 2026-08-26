# -*- coding: utf-8 -*-
"""Celery 应用配置：队列定义 + 路由 + 定时调度。

「8 组队列操控」在代码层的体现：
  ① 队列定义（task_queues）
  ② 路由（task_routes）
  ③ 消费（worker -Q 见 deploy.sh）
  ④ 重试（tasks.py 里的 autoretry）
  ⑤ 优先级（high/default/low 三队列）
  ⑥ 调度（beat_schedule）
  ⑦ 撤销（app.control.revoke）
  ⑧ 检查（app.control.inspect）
"""

from celery import Celery
from kombu import Queue

# 生产环境换成 Redis：broker='redis://localhost:6379/0'
# 这里用 memory 便于本地无 Redis 演示：
#   broker 用 memory://（队列）
#   backend 用 cache+memory://（结果存储）—— 注意 memory:// 不能当 backend
app = Celery('shortlink', broker='memory://', backend='cache+memory://')

# ① 定义三个优先级队列
app.conf.task_queues = (
    Queue('high', routing_key='high'),       # 高优先级：计费
    Queue('default', routing_key='default'), # 默认：素材上传
    Queue('low', routing_key='low'),         # 低优先级：清理
)

# ② 路由：不同任务进不同队列
app.conf.task_routes = {
    'tasks.process_payment': {'queue': 'high'},
    'tasks.process_upload': {'queue': 'default'},
    'tasks.cleanup': {'queue': 'low'},
}

# ⑥ 定时调度（Celery Beat）
app.conf.beat_schedule = {
    'cleanup-every-minute': {
        'task': 'tasks.cleanup',
        'schedule': 60.0,
    },
}

app.conf.timezone = 'Asia/Shanghai'
app.conf.task_serializer = 'json'
app.conf.result_serializer = 'json'
