# ============================================================
# Celery Worker Dockerfile（任务队列容器化）
# ============================================================

FROM python:3.12-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 启动 worker，消费三个优先级队列
CMD ["celery", "-A", "celery_app", "worker", "-Q", "high,default,low", "--loglevel=info"]
