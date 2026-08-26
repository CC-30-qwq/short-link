#!/usr/bin/env bash
# 运行 pytest 并正确传播退出码（非0则失败）
# 用法: bash run_tests.sh [pytest 参数]
#
# 关键：pytest 的退出码语义
#   0 = 全部通过    1 = 有失败    5 = 没有收集到测试
#   只要非 0，就应该让流水线失败（exit 1）

python -m pytest -v "$@"
EXIT_CODE=$?

if [ "$EXIT_CODE" -ne 0 ]; then
    echo "FAIL: pytest exit code = $EXIT_CODE (non-zero), tests failed"
    exit 1
fi

echo "PASS: all tests passed"
exit 0
