#!/bin/bash
# 性能测试一键运行脚本
#
# 用法: bash tests/performance/run-all.sh [BASE_URL]

set -e

BASE_URL=${1:-"http://localhost:3000"}
export BASE_URL

echo "========================================"
echo "  三人行吊顶管理系统 - 性能测试套件"
echo "  目标: $BASE_URL"
echo "========================================"
echo ""

# 检查服务是否可用
echo "检查服务状态..."
if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/v1/auth/login" | grep -q "200\|400\|401\|405"; then
    echo "✓ 服务可用"
else
    echo "✗ 服务不可用，请先启动后端服务"
    exit 1
fi

echo ""
echo "--- 1. 统计查询性能 ---"
node "$(dirname "$0")/statistics.bench.js"

echo ""
echo "--- 2. 结算操作性能 ---"
node "$(dirname "$0")/settlement.bench.js"

echo ""
echo "========================================"
echo "  全部性能测试完成"
echo "========================================"
