# ============================================================
# Python 测试环境 Dockerfile（容器化跑测试）
# 用法: docker build -t shortlink-tests . && docker run --rm shortlink-tests
# ============================================================

FROM python:3.12-slim
WORKDIR /app

# 先复制依赖清单 → 利用层缓存（依赖没变不重装）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 再复制代码和测试
COPY . .

# 容器启动即跑测试（CI 里直接 docker run 就能执行全套测试）
CMD ["pytest", "-v"]
