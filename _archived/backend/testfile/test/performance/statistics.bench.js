/**
 * 统计查询性能测试
 *
 * 测试场景:
 * - 月度统计查询响应时间
 * - 大量工程数据下的统计性能
 * - 结算历史查询性能
 * - 并发请求压力测试
 *
 * 运行: node tests/performance/statistics.bench.js
 */

const axios = require('axios');
const { performance } = require('perf_hooks');

// 配置
const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';
const CONCURRENT_USERS = parseInt(process.env.CONCURRENT_USERS || '10');
const REQUESTS_PER_USER = parseInt(process.env.REQUESTS_PER_USER || '5');
const SLOW_THRESHOLD_MS = 500; // 慢查询阈值

let authToken = '';

/**
 * 登录获取Token
 */
async function login() {
  try {
    const res = await axios.post(`${BASE_URL}/v1/auth/login`, {
      username: process.env.TEST_USERNAME || '喜临门',
      password: process.env.TEST_PASSWORD || 'admin123'
    });
    if (res.data.code === 200) {
      authToken = res.data.data.accessToken;
      console.log('✓ 登录成功');
      return true;
    }
    console.error('✗ 登录失败:', res.data.msg);
    return false;
  } catch (e) {
    console.error('✗ 登录异常:', e.message);
    return false;
  }
}

/**
 * 测试单个接口响应时间
 */
async function benchmarkEndpoint(name, method, path, data = null) {
  const config = {
    headers: { Authorization: `Bearer ${authToken}` },
    timeout: 10000
  };

  const times = [];
  const errors = [];

  for (let i = 0; i < REQUESTS_PER_USER; i++) {
    const start = performance.now();
    try {
      if (method === 'GET') {
        await axios.get(`${BASE_URL}${path}`, config);
      } else {
        await axios.post(`${BASE_URL}${path}`, data, config);
      }
      const elapsed = performance.now() - start;
      times.push(elapsed);
    } catch (e) {
      errors.push(e.response?.status || e.message);
    }
  }

  if (times.length === 0) {
    console.log(`  ${name}: 全部失败 [${errors.join(', ')}]`);
    return null;
  }

  const avg = times.reduce((a, b) => a + b, 0) / times.length;
  const min = Math.min(...times);
  const max = Math.max(...times);
  const p95 = times.sort((a, b) => a - b)[Math.floor(times.length * 0.95)];
  const slowCount = times.filter(t => t > SLOW_THRESHOLD_MS).length;

  const status = avg > SLOW_THRESHOLD_MS ? '⚠ 慢' : '✓ 正常';

  console.log(`  ${name}: ${status}`);
  console.log(`    平均: ${avg.toFixed(1)}ms | 最小: ${min.toFixed(1)}ms | 最大: ${max.toFixed(1)}ms | P95: ${p95.toFixed(1)}ms`);
  if (slowCount > 0) {
    console.log(`    慢查询: ${slowCount}/${times.length} (> ${SLOW_THRESHOLD_MS}ms)`);
  }
  if (errors.length > 0) {
    console.log(`    错误: ${errors.length}/${REQUESTS_PER_USER}`);
  }

  return { name, avg, min, max, p95, slowCount, errors: errors.length };
}

/**
 * 并发压力测试
 */
async function concurrentTest(name, requestFn, concurrency) {
  console.log(`\n  并发测试: ${name} (${concurrency}并发)`);

  const start = performance.now();
  const promises = Array(concurrency).fill(null).map(() => requestFn());
  const results = await Promise.allSettled(promises);
  const elapsed = performance.now() - start;

  const succeeded = results.filter(r => r.status === 'fulfilled').length;
  const failed = results.filter(r => r.status === 'rejected').length;
  const avgPerRequest = elapsed / concurrency;

  console.log(`    总耗时: ${elapsed.toFixed(1)}ms | 成功: ${succeeded} | 失败: ${failed} | 平均: ${avgPerRequest.toFixed(1)}ms/请求`);

  return { name, elapsed, succeeded, failed, avgPerRequest };
}

/**
 * 主测试流程
 */
async function main() {
  console.log('========================================');
  console.log('  统计查询性能测试');
  console.log('========================================');
  console.log(`  目标: ${BASE_URL}`);
  console.log(`  并发: ${CONCURRENT_USERS}`);
  console.log(`  每用户请求: ${REQUESTS_PER_USER}`);
  console.log(`  慢查询阈值: ${SLOW_THRESHOLD_MS}ms`);
  console.log('========================================\n');

  // 登录
  if (!(await login())) {
    console.error('无法登录，测试终止');
    process.exit(1);
  }

  // 1. 单接口性能测试
  console.log('\n--- 单接口性能 ---\n');

  const results = [];

  results.push(await benchmarkEndpoint('月度统计', 'GET', '/v1/statistics/monthly'));
  results.push(await benchmarkEndpoint('工程列表', 'GET', '/v1/projects'));
  results.push(await benchmarkEndpoint('结算历史', 'GET', '/v1/settlements/history'));
  results.push(await benchmarkEndpoint('字典数据', 'GET', '/v1/dictionary/space-types'));
  results.push(await benchmarkEndpoint('用户信息', 'GET', '/v1/users/profile'));

  // 2. 并发压力测试
  console.log('\n--- 并发压力测试 ---\n');

  await concurrentTest('月度统计', async () => {
    return axios.get(`${BASE_URL}/v1/statistics/monthly`, {
      headers: { Authorization: `Bearer ${authToken}` },
      timeout: 10000
    });
  }, CONCURRENT_USERS);

  await concurrentTest('工程列表', async () => {
    return axios.get(`${BASE_URL}/v1/projects`, {
      headers: { Authorization: `Bearer ${authToken}` },
      timeout: 10000
    });
  }, CONCURRENT_USERS);

  // 3. 汇总
  console.log('\n========================================');
  console.log('  测试汇总');
  console.log('========================================\n');

  const validResults = results.filter(r => r !== null);
  const slowEndpoints = validResults.filter(r => r.avg > SLOW_THRESHOLD_MS);

  if (slowEndpoints.length === 0) {
    console.log('  ✓ 所有接口响应时间正常');
  } else {
    console.log('  ⚠ 慢接口:');
    slowEndpoints.forEach(r => {
      console.log(`    - ${r.name}: ${r.avg.toFixed(1)}ms`);
    });
  }

  console.log(`\n  测试完成: ${validResults.length}个接口, ${slowEndpoints.length}个慢接口\n`);
}

main().catch(console.error);
