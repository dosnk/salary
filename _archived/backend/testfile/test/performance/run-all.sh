#!/bin/bash
# 性能测试一键运行脚本
#
# 用法: bash testfile/test/performance/run-all.sh [BASE_URL] [选项]
# 选项:
#   --skip-stress    跳过扩展压测（只跑基础性能测试）
#   --stress-only    只跑扩展压测
#   --with-gc        启用GC（需要 node --expose-gc）

set -e

BASE_URL=${1:-"http://localhost:3000"}
export BASE_URL

# 解析选项
SKIP_STRESS=false
STRESS_ONLY=false
WITH_GC=false
NODE_BIN="node"

for arg in "$@"; do
  case $arg in
    --skip-stress) SKIP_STRESS=true ;;
    --stress-only) STRESS_ONLY=true ;;
    --with-gc) WITH_GC=true; NODE_BIN="node --expose-gc" ;;
  esac
done

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

if [ "$STRESS_ONLY" = false ]; then
  echo "--- 1. 统计查询性能 ---"
  $NODE_BIN "$(dirname "$0")/statistics.bench.js"

  echo ""
  echo "--- 2. 结算操作性能 ---"
  $NODE_BIN "$(dirname "$0")/settlement.bench.js"
fi

if [ "$SKIP_STRESS" = false ]; then
  echo ""
  echo "--- 3. 扩展压测（深页翻页+并发+稳定性） ---"
  $NODE_BIN "$(dirname "$0")/stress.bench.js"
fi

echo ""
echo "========================================"
echo "  全部性能测试完成"
echo "========================================"
