# -*- coding: utf-8 -*-
"""计费：并发扣费 + 幂等 + 不超扣。

ACID 保障：
  - 原子性/一致性：用条件 UPDATE「WHERE balance >= ?」保证余额不为负
  - 隔离性：SQLite 单写 + 线程锁串行化写操作
  - 持久性：commit 后数据落盘
  - 幂等：orders 表唯一约束，同一订单只扣一次

金额单位：分（int），不用浮点。
"""

import sqlite3
import threading


class BillingService:
    def __init__(self, db_path=":memory:"):
        # check_same_thread=False 允许多线程复用同一连接（配合 lock 保证安全）
        self.conn = sqlite3.connect(db_path, check_same_thread=False)
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS accounts "
            "(id TEXT PRIMARY KEY, balance INTEGER)"
        )
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS orders (order_id TEXT PRIMARY KEY)"
        )
        self.conn.commit()
        self._lock = threading.Lock()

    def create_account(self, account_id: str, balance: int):
        self.conn.execute(
            "INSERT OR IGNORE INTO accounts (id, balance) VALUES (?, ?)",
            (account_id, balance),
        )
        self.conn.commit()

    def get_balance(self, account_id: str):
        row = self.conn.execute(
            "SELECT balance FROM accounts WHERE id = ?", (account_id,)
        ).fetchone()
        return row[0] if row else None

    def deduct(self, account_id: str, amount: int, order_id: str) -> bool:
        """扣费。返回 True=成功，False=余额不足或订单重复。"""
        if amount <= 0:
            raise ValueError("金额必须 > 0")
        with self._lock:
            # 1. 幂等：订单号唯一约束，重复订单插入失败
            try:
                self.conn.execute(
                    "INSERT INTO orders (order_id) VALUES (?)", (order_id,)
                )
                self.conn.commit()
            except sqlite3.IntegrityError:
                return False

            # 2. 条件 UPDATE：余额不足时 rowcount=0，天然不超扣（原子）
            cur = self.conn.execute(
                "UPDATE accounts SET balance = balance - ? "
                "WHERE id = ? AND balance >= ?",
                (amount, account_id, amount),
            )
            self.conn.commit()
            if cur.rowcount == 0:
                # 没扣成，回滚幂等标记，允许余额充足后重试
                self.conn.execute(
                    "DELETE FROM orders WHERE order_id = ?", (order_id,)
                )
                self.conn.commit()
                return False
            return True
