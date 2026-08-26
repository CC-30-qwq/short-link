# -*- coding: utf-8 -*-
"""计费业务逻辑（纯函数，业务建模 + 边界测试）。"""

MAX_AMOUNT = 10 ** 12  # 单笔金额上限（分）


def validate_amount(amount):
    """校验金额：必须是正整数(分)，非法抛异常。"""
    if not isinstance(amount, int):
        raise TypeError(f"金额必须是整数(分): {amount!r}")
    if amount <= 0:
        raise ValueError(f"金额必须 > 0: {amount}")
    if amount > MAX_AMOUNT:
        raise ValueError(f"金额超出上限 {MAX_AMOUNT}: {amount}")
    return amount


def deduct(balance, amount, order_id, processed_orders):
    """扣费：幂等 + 不超扣。返回 (新余额, 是否成功)。"""
    if not order_id:
        raise ValueError("订单号不能为空")
    amount = validate_amount(amount)
    if order_id in processed_orders:
        return balance, False   # 幂等：重复订单
    if balance < amount:
        return balance, False   # 余额不足
    return balance - amount, True
