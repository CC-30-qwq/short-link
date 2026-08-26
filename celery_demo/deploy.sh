#!/usr/bin/env bash
# 一键部署 + 发布/回滚（版本目录 + current 指针原子切换）
#
# 原理（Capistrano 经典模式）：
#   releases/20260101120000/  <- 每个版本一个独立目录
#   current -> 指向当前版本（Linux 用软链接；Windows 无权限时复制兜底）
#   发布 = 切指针到新版本；回滚 = 切回旧版本（秒级、可随时回退）
#
# 用法:
#   bash deploy.sh release    # 发布新版本（一键部署）
#   bash deploy.sh rollback   # 回滚到上一个版本
#   bash deploy.sh list       # 列出所有版本

set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-.deploy}"
RELEASES_DIR="$DEPLOY_ROOT/releases"
CURRENT_LINK="$DEPLOY_ROOT/current"
VERSION_FILE="$DEPLOY_ROOT/current_version"
KEEP_RELEASES="${KEEP_RELEASES:-5}"

mkdir -p "$RELEASES_DIR"

# 切换 current 到目标版本目录（软链接优先，Windows 无权限时复制兜底）
switch_to() {
    local target="$1" version="$2"
    rm -rf "$CURRENT_LINK"
    if ln -sfn "$target" "$CURRENT_LINK" 2>/dev/null; then
        echo "  切换方式: 软链接"
    else
        cp -r "$target" "$CURRENT_LINK"
        echo "  切换方式: 复制（当前系统无符号链接权限）"
    fi
    echo "$version" > "$VERSION_FILE"
}

# 一键部署：拉代码 -> 构建 -> 原子切换 -> 重启 -> 健康检查
release() {
    local version release_dir
    version=$(date +%Y%m%d%H%M%S)
    release_dir="$RELEASES_DIR/$version"

    echo "▶ 1/5 拉取代码到 $release_dir"
    mkdir -p "$release_dir"
    # 真实场景：git clone <repo> "$release_dir"
    [ -d src ] && cp -r src/* "$release_dir/" 2>/dev/null || true
    echo "  版本号: $version"

    echo "▶ 2/5 安装依赖"
    # 真实场景：cd "$release_dir" && pip install -r requirements.txt

    echo "▶ 3/5 原子切换"
    switch_to "$release_dir" "$version"

    echo "▶ 4/5 重启服务"
    # 真实场景：systemctl restart shortlink

    echo "▶ 5/5 健康检查"
    # 真实场景：curl -sf http://localhost:8080/health || rollback

    echo "✔ 发布成功：$version"
    cleanup_old
}

# 回滚：切回上一个版本
rollback() {
    local prev
    prev=$(ls -1t "$RELEASES_DIR" | sed -n '2p')
    if [ -z "$prev" ]; then
        echo "✗ 没有可回滚的版本"; exit 1
    fi
    switch_to "$RELEASES_DIR/$prev" "$prev"
    echo "✔ 已回滚到：$prev"
}

# 只保留最近 KEEP_RELEASES 个版本
cleanup_old() {
    ls -1t "$RELEASES_DIR" | tail -n +$((KEEP_RELEASES + 1)) | while read -r old; do
        rm -rf "$RELEASES_DIR/$old"
        echo "  清理旧版本: $old"
    done
}

list() {
    echo "当前版本: $(cat "$VERSION_FILE" 2>/dev/null || echo 无)"
    echo "所有版本（新→旧）:"
    ls -1t "$RELEASES_DIR" 2>/dev/null || true
}

case "${1:-release}" in
    release)  release ;;
    rollback) rollback ;;
    list)     list ;;
    *) echo "用法: bash deploy.sh [release|rollback|list]"; exit 1 ;;
esac
