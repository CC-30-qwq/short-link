# -*- coding: utf-8 -*-
"""被测数据库类：用户存储（通过注入连接，配合 fixture 共享连接）。"""


class UserStore:
    def __init__(self, conn):
        self.conn = conn

    def add_user(self, name, age):
        self.conn.execute("INSERT INTO users (name, age) VALUES (?, ?)", (name, age))
        self.conn.commit()

    def get_user(self, name):
        row = self.conn.execute(
            "SELECT name, age FROM users WHERE name = ?", (name,)
        ).fetchone()
        return row
