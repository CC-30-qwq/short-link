# -*- coding: utf-8 -*-
"""计费异常 + 边界值参数化测试（10 个用例）。

业务建模的边界值覆盖：
  金额下界(0/负数)、上界(超上限)、类型(浮点/字符串/None)、
  业务边界(余额不足/幂等)、空值(空订单号)
"""

import pytest

from billing import MAX_AMOUNT, deduct, validate_amount


# ===== 1~7：非法金额（7 个参数化）=====
@pytest.mark.parametrize("amount,exc", [
    (0, ValueError),               # 边界下界：0
    (-1, ValueError),              # 边界：负 1
    (-100, ValueError),            # 负大数
    (1.5, TypeError),              # 浮点（计费精度陷阱）
    ("100", TypeError),            # 字符串
    (None, TypeError),             # None
    (MAX_AMOUNT + 1, ValueError),  # 边界上界：超上限
])
def test_invalid_amount(amount, exc):
    with pytest.raises(exc):
        validate_amount(amount)


# ===== 8~9：扣费业务边界（2 个参数化）=====
@pytest.mark.parametrize("balance,amount,processed,expected", [
    (50, 100, set(), (50, False)),      # 余额不足：不超扣
    (100, 50, {"o1"}, (100, False)),    # 幂等：重复订单拒绝
])
def test_deduct_rejected(balance, amount, processed, expected):
    assert deduct(balance, amount, "o1", processed) == expected


# ===== 10：空订单号（1 个）=====
def test_deduct_empty_order_id():
    with pytest.raises(ValueError, match="订单号不能为空"):
        deduct(100, 10, "", set())
