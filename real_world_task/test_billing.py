# -*- coding: utf-8 -*-
"""计费测试：并发扣费 + ACID 最终一致性 + 幂等。"""

import threading

from billing_service import BillingService


def concurrent_deduct(service, account_id, amount, order_prefix, n):
    """n 线程并发扣费，返回每个线程的扣费结果（True/False）。"""
    results = []
    lock = threading.Lock()

    def worker(i):
        ok = service.deduct(account_id, amount, f"{order_prefix}-{i}")
        with lock:
            results.append(ok)

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(n)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return results


def test_concurrent_deduct_never_overdraw():
    """余额 100，200 线程各扣 1，绝不超扣。"""
    svc = BillingService()
    svc.create_account("acc", 100)

    results = concurrent_deduct(svc, "acc", 1, "order", 200)

    assert sum(results) == 100                # 只有 100 次成功
    assert svc.get_balance("acc") == 0        # 余额精确到 0
    assert svc.get_balance("acc") >= 0        # 不为负


def test_concurrent_deduct_final_consistency():
    """ACID 最终一致性：余额 + 已扣金额 = 初始余额（守恒）。"""
    initial = 100_000
    amount = 3
    svc = BillingService()
    svc.create_account("acc", initial)

    results = concurrent_deduct(svc, "acc", amount, "order", 500)

    success = sum(results)
    assert svc.get_balance("acc") + success * amount == initial


def test_deduct_is_idempotent():
    """同一订单重复扣费只成功一次。"""
    svc = BillingService()
    svc.create_account("acc", 1000)

    assert svc.deduct("acc", 10, "same-order") is True    # 第一次成功
    assert svc.deduct("acc", 10, "same-order") is False   # 重复订单拒绝
    assert svc.get_balance("acc") == 990                  # 只扣一次
