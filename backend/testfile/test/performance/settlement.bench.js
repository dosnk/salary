/**
 * 结算操作性能测试
 *
 * 测试场景:
 * - 结算预览计算耗时
 * - 批量结算性能
 * - 结算历史分页查询
 *
 * 运行: node tests/performance/settlement.bench.js
 */

const axios = require('axios');
const { performance } = require('perf_hooks');

const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';
const SLOW_THRESHOLD_MS = 1000; // 结算计算阈值1秒

let authToken = '';

/**
 * 登录获取Token
 */
async function login() {
  try {
    const res = await axios.post(`${BASE_URL}/v1/auth/login`, {
      username: process.env.TEST_USERNAME || 'admin',
      password: process.env.TEST_PASSWORD || 'Admin123456'
    });
    if (res.data.code === 200) {
      authToken = res.data.data.token;
      return true;
    }
    return false;
  } catch (e) {
    console.error('登录失败:', e.message);
    return false;
  }
}

async function main() {
  console.log('========================================');
  console.log('  结算操作性能测试');
  console.log('========================================\n');

  if (!(await login())) {
    console.error('无法登录');
    process.exit(1);
  }

  const config = {
    headers: { Authorization: `Bearer ${authToken}` },
    timeout: 30000
  };

  // 1. 结算预览计算
  console.log('--- 结算预览计算 ---\n');

  const calcTimes = [];
  for (let i = 0; i < 5; i++) {
    const start = performance.now();
    try {
      await axios.post(`${BASE_URL}/v1/settlements/calculate`, {
        projectIds: [1, 2, 3]
      }, config);
      calcTimes.push(performance.now() - start);
    } catch (e) {
      console.log(`  第${i + 1}次: 失败 - ${e.response?.status || e.message}`);
    }
  }

  if (calcTimes.length > 0) {
    const avg = calcTimes.reduce((a, b) => a + b, 0) / calcTimes.length;
    const max = Math.max(...calcTimes);
    const status = avg > SLOW_THRESHOLD_MS ? '⚠ 慢' : '✓ 正常';
    console.log(`  ${status} | 平均: ${avg.toFixed(1)}ms | 最大: ${max.toFixed(1)}ms`);
  }

  // 2. 结算历史分页
  console.log('\n--- 结算历史分页查询 ---\n');

  const pageSizes = [10, 20, 50];
  for (const size of pageSizes) {
    const start = performance.now();
    try {
      await axios.get(`${BASE_URL}/v1/settlements/history?page=1&pageSize=${size}`, config);
      const elapsed = performance.now() - start;
      const status = elapsed > 500 ? '⚠' : '✓';
      console.log(`  ${status} pageSize=${size}: ${elapsed.toFixed(1)}ms`);
    } catch (e) {
      console.log(`  ✗ pageSize=${size}: 失败`);
    }
  }

  // 3. 预支记录查询
  console.log('\n--- 预支记录查询 ---\n');

  const advStart = performance.now();
  try {
    await axios.get(`${BASE_URL}/v1/advances`, config);
    const elapsed = performance.now() - advStart;
    console.log(`  ${elapsed > 500 ? '⚠' : '✓'} ${elapsed.toFixed(1)}ms`);
  } catch (e) {
    console.log('  ✗ 查询失败');
  }

  console.log('\n========================================');
  console.log('  测试完成');
  console.log('========================================\n');
}

main().catch(console.error);
