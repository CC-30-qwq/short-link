# -*- coding: utf-8 -*-
"""共享 fixture：session 级数据库连接。

fixture 重点：
  1. scope="session"：整个测试会话只创建一次连接，所有测试复用（快）
  2. yield：yield 之前是 setup（建表），之后是 teardown（关连接）
  3. 用 pytest 内置的 tmp_path_factory 创建临时库文件，测试结束自动清理
"""

import sqlite3

import pytest


@pytest.fixture(scope="session")
def db_connection(tmp_path_factory):
    db_path = tmp_path_factory.mktemp("db") / "test.db"
    conn = sqlite3.connect(db_path)
    conn.execute(
        "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT UNIQUE, age INTEGER)"
    )
    yield conn          # 测试拿到这个连接
    conn.close()        # teardown：会话结束关闭连接
