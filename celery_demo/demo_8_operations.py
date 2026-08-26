# -*- coding: utf-8 -*-
"""演示 Celery 的「8 组队列操控」（eager 模式，本地无 broker 也能跑）。

生产环境去掉 task_always_eager，改用 redis broker + 独立 worker 进程。
"""

from celery_app import app
from tasks import cleanup, process_payment, process_upload

# 本地演示：同步执行，不依赖 broker/worker
app.conf.task_always_eager = True
app.conf.task_eager_propagates = False


def section(title):
    print("\n" + "=" * 60)
    print(title)
    print("=" * 60)


def demo():
    # ① 投递：delay / apply_async
    section("① 投递 enqueue：delay() = apply_async() 简写")
    r1 = process_upload.delay("file-001")
    print(f"  任务ID: {r1.id}")
    print(f"  结果: {r1.result}")

    # ② 路由：任务按配置进不同队列
    section("② 路由 route：task_routes 决定任务进哪个队列")
    for name in ("tasks.process_payment", "tasks.process_upload", "tasks.cleanup"):
        q = app.conf.task_routes.get(name, {}).get("queue", "default")
        print(f"  {name:<22} -> 队列 [{q}]")

    # ③ 消费：worker -Q
    section("③ 消费 consume：worker 指定消费哪些队列（见 deploy.sh）")
    print("  celery -A celery_app worker -Q high,default,low --concurrency=4")

    # ④ 重试：计费失败自动重试
    section("④ 重试 retry：计费任务失败自动重试（指数退避）")
    r2 = process_payment.delay("order-ok")
    print(f"  成功订单结果: {r2.result}")
    try:
        process_payment.delay("fail")
    except Exception as e:
        print(f"  失败订单: 重试 3 次后仍失败 -> {type(e).__name__}")

    # ⑤ 优先级：三队列分级
    section("⑤ 优先级 priority：high/default/low 三级队列")
    for q in app.conf.task_queues:
        print(f"  队列 {q.name:<8} routing_key={q.routing_key}")

    # ⑥ 调度：Beat 定时任务
    section("⑥ 调度 schedule：Celery Beat 定时任务")
    for name, cfg in app.conf.beat_schedule.items():
        print(f"  {name}: task={cfg['task']}, 每 {cfg['schedule']}s")

    # ⑦ 撤销：revoke
    section("⑦ 撤销 revoke：取消已投递未执行的任务")
    print("  app.control.revoke(task_id, terminate=True)")

    # ⑧ 检查：inspect / purge
    section("⑧ 检查 inspect：查看 worker 状态 / 清空队列")
    print("  app.control.inspect().active()    # 活跃任务")
    print("  app.control.inspect().scheduled() # 已调度任务")
    print("  app.control.purge()               # 清空队列")


if __name__ == "__main__":
    demo()
