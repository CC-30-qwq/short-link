#!/usr/bin/env bash
# Docker-Compose 发布 + 回滚脚本
# 用法:
#   bash release.sh release v1     # 发布 v1
#   bash release.sh release v2     # 发布 v2
#   bash release.sh rollback       # 回滚到上一个版本
#   DRY_RUN=1 bash release.sh ...  # 无 Docker 环境演示（只打印命令）

set -euo pipefail

VERSION_FILE=".current_version"
HISTORY_FILE=".version_history"

# 执行 docker 命令（DRY_RUN=1 时只打印不执行）
docker_cmd() {
    if [ "${DRY_RUN:-0}" = "1" ]; then
        echo "  [dry-run] $*"
    else
        "$@"
    fi
}

# 发布新版本
release() {
    local version="${1:?用法: release.sh release <版本号>}"

    echo "▶ 1/3 构建镜像 shortlink:$version"
    docker_cmd docker build -t "shortlink:$version" .

    echo "▶ 2/3 切换版本并启动容器"
    APP_VERSION="$version" docker_cmd docker compose up -d

    echo "▶ 3/3 记录版本"
    echo "$version" > "$VERSION_FILE"
    echo "$version" >> "$HISTORY_FILE"
    echo "✔ 发布成功: $version"
}

# 回滚到上一个版本
rollback() {
    local current prev
    current=$(cat "$VERSION_FILE" 2>/dev/null || echo "无")
    prev=$(tail -2 "$HISTORY_FILE" 2>/dev/null | head -1)

    # 没有历史，或上一个版本 = 当前版本，说明无可回滚
    if [ -z "$prev" ] || [ "$prev" = "$current" ]; then
        echo "✗ 没有可回滚的版本"
        exit 1
    fi

    echo "▶ 回滚 $current -> $prev"
    APP_VERSION="$prev" docker_cmd docker compose up -d
    echo "$prev" > "$VERSION_FILE"
    echo "✔ 已回滚到: $prev"
}

case "${1:-release}" in
    release)
        release "${2:?缺少版本号，用法: release.sh release <版本号>}"
        ;;
    rollback)
        rollback
        ;;
    *)
        echo "用法: bash release.sh release <版本号> | bash release.sh rollback"
        exit 1
        ;;
esac
