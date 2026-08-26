# -*- coding: utf-8 -*-
"""fixture 重点：复用 session 级共享数据库连接。"""

import pytest

from user_store import UserStore


@pytest.fixture(scope="session")
def store(db_connection):
    """复用 conftest.py 里的共享连接，不重复建连。"""
    return UserStore(db_connection)


def test_add_and_get_user(store):
    store.add_user("alice", 30)
    assert store.get_user("alice") == ("alice", 30)


# fixture + parametrize 结合：共享连接 + 数据驱动
@pytest.mark.parametrize("name,age", [
    ("bob", 25),
    ("carol", 40),
    ("dave", 18),
])
def test_add_multiple_users(store, name, age):
    store.add_user(name, age)
    assert store.get_user(name) == (name, age)
