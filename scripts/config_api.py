#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""线上配置管理工具（FastAPI CRUD）。

补位价值：运维/测开能自己写 Web 工具管理线上配置，而不是手改配置文件。

用法：
  python config_api.py                     # 启动 http://localhost:8001
  浏览器打开 http://localhost:8001/docs    # FastAPI 自动生成的交互式页面
"""

from typing import Dict

import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="线上配置管理", version="1.0.0")

# 内存存储（真实场景换成 Redis/数据库）
_configs: Dict[str, dict] = {}


class ConfigItem(BaseModel):
    key: str = Field(..., description="配置键")
    value: str = Field(..., description="配置值")
    desc: str = Field("", description="描述")


class ConfigUpdate(BaseModel):
    value: str = Field(..., description="新配置值")
    desc: str = Field("", description="描述")


# ============ 增：Create ============
@app.post("/configs", status_code=201)
def create_config(item: ConfigItem):
    if item.key in _configs:
        raise HTTPException(status_code=409, detail=f"配置 {item.key} 已存在")
    _configs[item.key] = {"value": item.value, "desc": item.desc}
    return {"key": item.key, **_configs[item.key]}


# ============ 查：Read（列表 + 单个）============
@app.get("/configs")
def list_configs():
    return dict(_configs)


@app.get("/configs/{key}")
def get_config(key: str):
    if key not in _configs:
        raise HTTPException(status_code=404, detail=f"配置 {key} 不存在")
    return {"key": key, **_configs[key]}


# ============ 改：Update ============
@app.put("/configs/{key}")
def update_config(key: str, update: ConfigUpdate):
    if key not in _configs:
        raise HTTPException(status_code=404, detail=f"配置 {key} 不存在")
    _configs[key] = {"value": update.value, "desc": update.desc}
    return {"key": key, **_configs[key]}


# ============ 删：Delete ============
@app.delete("/configs/{key}")
def delete_config(key: str):
    if key not in _configs:
        raise HTTPException(status_code=404, detail=f"配置 {key} 不存在")
    del _configs[key]
    return {"deleted": key}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)
