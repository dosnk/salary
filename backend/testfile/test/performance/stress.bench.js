/**
 * 扩展性能压测脚本
 *
 * 测试场景：
 *   1. 深页翻页性能（大数据量下翻到深页）
 *   2. 并发结算预览（多用户同时预览，验证数据隔离）
 *   3. 连续请求稳定性（1000次请求内存增长检测）
 *   4. 健康检查与资源监控
 *
 * 用法：
 *   node testfile/test/performance/stress.bench.js
 *   BASE_URL=http://192.168.1.100:3000 node testfile/test/performance/stress.bench.js
 *   CONCURRENT=20 ROUNDS=500 node testfile/test/performance/stress.bench.js
 */

const axios = require('axios');
const { performance } = require('perf_hooks');

// ===================== 配置 =====================
const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';
const CONCURRENT = parseInt(process.env.CONCURRENT || '10');
const ROUNDS = parseInt(process.env.ROUNDS || '100'); // 连续请求轮数
const SLOW_THRESHOLD_MS = 500;
const DEEP_PAGE_SIZES = [20, 50, 100]; // 测试深页翻页的每页数量

let authToken = '';
let constructorToken = '';
let constructorUserId = null;

// 内存采样数据
const memorySamples = [];

// ===================== 工具函数 =====================
const log = {
  info: (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  pass: (msg) => console.log(`[${new Date().toISOString()}] [✓] ${msg}`),
  fail: (msg) => console.error(`[${new Date().toISOString()}] [✗] ${msg}`),
  warn: (msg) => console.warn(`[${new Date().toISOString()}] [!] ${msg}`)
};

/**
 * 采样当前进程内存
 */
const sampleMemory = (label = '') => {
  const mem = process.memoryUsage();
  memorySamples.push({
    label,
    rss: mem.rss / 1024 / 1024,           // 常驻内存 MB
    heapUsed: mem.heapUsed / 1024 / 1024, // 堆已用 MB
    heapTotal: mem.heapTotal / 1024 / 1024,
    external: mem.external / 1024 / 1024,
    timestamp: Date.now()
  });
};

/**
 * 格式化内存数值
 */
const fmtMem = (mb) => `${mb.toFixed(1)}MB`;

/**
 * 登录获取Token
 */
async function login() {
  try {
    // 管理员登录（admin角色的中文名）
    const adminRes = await axios.post(`${BASE_URL}/v1/auth/login`, {
      username: process.env.TEST_USERNAME || '喜临门',
      password: process.env.TEST_PASSWORD || 'admin123'
    });
    if (adminRes.data.code === 200) {
      authToken = adminRes.data.data.token;
    } else {
      log.fail(`管理员登录失败: ${adminRes.data.msg}`);
      return false;
    }

    // 尝试施工员登录（用于并发结算测试）
    try {
      const ctorRes = await axios.post(`${BASE_URL}/v1/auth/login`, {
        username: process.env.CTOR_USERNAME || '杨耀贵',
        password: process.env.CTOR_PASSWORD || 'admin123'
      });
      if (ctorRes.data.code === 200) {
        constructorToken = ctorRes.data.data.token;
        constructorUserId = ctorRes.data.data.user?.id;
      }
    } catch (e) {
      log.warn('施工员登录失败，将跳过并发结算测试');
    }

    log.pass('登录成功');
    return true;
  } catch (e) {
    log.fail(`登录异常: ${e.message}`);
    return false;
  }
}

// ===================== 测试场景 =====================

/**
 * 场景1：深页翻页性能测试
 * 翻到第10、50、100页，验证深页查询性能
 */
async function testDeepPagination() {
  log.info('\n--- 场景1：深页翻页性能 ---\n');

  for (const pageSize of DEEP_PAGE_SIZES) {
    const pages = [1, 10, 50, 100];
    log.info(`  每页 ${pageSize} 条：`);

    for (const page of pages) {
      const start = performance.now();
      try {
        const res = await axios.get(`${BASE_URL}/v1/projects`, {
          params: { page, size: pageSize },
          headers: { Authorization: `Bearer ${authToken}` },
          timeout: 10000
        });
        const elapsed = performance.now() - start;
        const status = elapsed > SLOW_THRESHOLD_MS ? '⚠' : '✓';
        const total = res.data.data?.total || 0;
        const listLen = res.data.data?.list?.length || 0;
        log.info(`    ${status} 第${page}页: ${elapsed.toFixed(1)}ms | 返回${listLen}条/共${total}条`);
      } catch (e) {
        log.fail(`    第${page}页: 失败 - ${e.response?.status || e.message}`);
      }
    }
  }
}

/**
 * 场景2：并发结算预览测试
 * 多个施工员同时预览结算，验证数据隔离和并发安全
 */
async function testConcurrentSettlementPreview() {
  if (!constructorToken) {
    log.warn('\n--- 场景2：并发结算预览（跳过，无施工员Token） ---\n');
    return;
  }

  log.info('\n--- 场景2：并发结算预览 ---\n');
  log.info(`  并发数: ${CONCURRENT}`);

  // 先获取施工员参与的工程列表
  let projectIds = [];
  try {
    const res = await axios.get(`${BASE_URL}/v1/projects`, {
      params: { page: 1, size: 5 },
      headers: { Authorization: `Bearer ${constructorToken}` },
      timeout: 10000
    });
    projectIds = (res.data.data?.list || []).map(p => p.id);
  } catch (e) {
    log.fail(`  获取工程列表失败: ${e.message}`);
    return;
  }

  if (projectIds.length === 0) {
    log.warn('  无可用工程，跳过并发结算预览测试');
    return;
  }

  // 并发预览
  const start = performance.now();
  const promises = Array(CONCURRENT).fill(null).map((_, i) =>
    axios.post(`${BASE_URL}/v1/settlements/calculate`,
      { projectIds: projectIds.slice(0, 3) },
      { headers: { Authorization: `Bearer ${constructorToken}` }, timeout: 30000 }
    ).then(res => ({ status: 'fulfilled', value: res }))
      .catch(err => ({ status: 'rejected', reason: err }))
  );

  const results = await Promise.allSettled(promises);
  const elapsed = performance.now() - start;

  // 注意：Promise.allSettled 返回的是 {status: 'fulfilled', value: {status, value}} 或 {status: 'rejected', reason}
  const succeeded = results.filter(r => r.status === 'fulfilled' && r.value?.status === 'fulfilled').length;
  const failed = results.length - succeeded;

  // 提取金额用于一致性校验
  const amounts = [];
  for (const r of results) {
    if (r.status === 'fulfilled' && r.value?.status === 'fulfilled') {
      const grandTotal = r.value.value?.data?.data?.grand_total;
      if (grandTotal !== undefined) {
        amounts.push(parseFloat(grandTotal));
      }
    }
  }

  const avgPerRequest = elapsed / CONCURRENT;
  const status = avgPerRequest > 2000 ? '⚠' : '✓';

  log.info(`  ${status} 总耗时: ${elapsed.toFixed(1)}ms | 成功: ${succeeded} | 失败: ${failed} | 平均: ${avgPerRequest.toFixed(1)}ms/请求`);

  // 校验并发结果一致性：所有成功请求返回的金额应相同
  if (amounts.length > 1) {
    const min = Math.min(...amounts);
    const max = Math.max(...amounts);
    const diff = max - min;
    if (diff > 0.01) {
      log.fail(`  并发结果不一致: 金额范围 ${min} ~ ${max}, 差异 ${diff}`);
    } else {
      log.pass(`  并发结果一致: 所有请求返回金额 ${amounts[0]}`);
    }
  }
}

/**
 * 场景3：连续请求稳定性测试
 * 连续发起 ROUNDS 次请求，采样内存增长，检测内存泄漏
 */
async function testContinuousStability() {
  log.info('\n--- 场景3：连续请求稳定性 ---\n');
  log.info(`  请求轮数: ${ROUNDS}`);

  sampleMemory('测试前');

  const times = [];
  const errors = [];
  const checkInterval = Math.max(Math.floor(ROUNDS / 5), 1); // 分5次采样

  for (let i = 0; i < ROUNDS; i++) {
    const start = performance.now();
    try {
      // 交替请求不同接口
      if (i % 3 === 0) {
        await axios.get(`${BASE_URL}/v1/projects`, {
          params: { page: 1, size: 10 },
          headers: { Authorization: `Bearer ${authToken}` },
          timeout: 10000
        });
      } else if (i % 3 === 1) {
        await axios.get(`${BASE_URL}/v1/statistics/dashboard`, {
          headers: { Authorization: `Bearer ${authToken}` },
          timeout: 10000
        });
      } else {
        await axios.get(`${BASE_URL}/v1/settlements/history`, {
          params: { page: 1, pageSize: 10 },
          headers: { Authorization: `Bearer ${authToken}` },
          timeout: 10000
        });
      }
      times.push(performance.now() - start);
    } catch (e) {
      errors.push(e.response?.status || e.message);
    }

    // 分段采样内存
    if ((i + 1) % checkInterval === 0) {
      sampleMemory(`第${i + 1}轮`);
    }
  }

  sampleMemory('测试后');

  // 统计
  if (times.length === 0) {
    log.fail(`  全部失败 [${errors.join(', ')}]`);
    return;
  }

  const avg = times.reduce((a, b) => a + b, 0) / times.length;
  const min = Math.min(...times);
  const max = Math.max(...times);
  const sorted = [...times].sort((a, b) => a - b);
  const p95 = sorted[Math.floor(sorted.length * 0.95)];
  const p99 = sorted[Math.floor(sorted.length * 0.99)];
  const slowCount = times.filter(t => t > SLOW_THRESHOLD_MS).length;

  const status = avg > SLOW_THRESHOLD_MS ? '⚠ 慢' : '✓ 正常';
  log.info(`  ${status}`);
  log.info(`    平均: ${avg.toFixed(1)}ms | 最小: ${min.toFixed(1)}ms | 最大: ${max.toFixed(1)}ms`);
  log.info(`    P95: ${p95.toFixed(1)}ms | P99: ${p99.toFixed(1)}ms | 慢请求: ${slowCount}/${times.length}`);
  if (errors.length > 0) {
    log.warn(`    错误: ${errors.length}/${ROUNDS}`);
  }

  // 内存增长分析
  if (memorySamples.length >= 2) {
    const first = memorySamples[0];
    const last = memorySamples[memorySamples.length - 1];
    const heapGrowth = last.heapUsed - first.heapUsed;
    const rssGrowth = last.rss - first.rss;

    log.info(`  内存变化:`);
    log.info(`    堆: ${fmtMem(first.heapUsed)} → ${fmtMem(last.heapUsed)} (增长 ${fmtMem(heapGrowth)})`);
    log.info(`    RSS: ${fmtMem(first.rss)} → ${fmtMem(last.rss)} (增长 ${fmtMem(rssGrowth)})`);

    // 内存增长阈值：每1000次请求增长 > 50MB 视为泄漏
    const growthPer1k = (heapGrowth / ROUNDS) * 1000;
    if (growthPer1k > 50) {
      log.fail(`  ⚠ 疑似内存泄漏: 每1000次请求堆内存增长 ${fmtMem(growthPer1k)}（阈值50MB）`);
    } else {
      log.pass(`  内存稳定: 每1000次请求堆内存增长 ${fmtMem(growthPer1k)}`);
    }
  }

  // GC 后再采样一次（如果可用）
  if (global.gc) {
    global.gc();
    sampleMemory('GC后');
    const last = memorySamples[memorySamples.length - 1];
    const before = memorySamples[memorySamples.length - 2];
    log.info(`  GC后堆内存: ${fmtMem(last.heapUsed)} (释放 ${fmtMem(before.heapUsed - last.heapUsed)})`);
  }
}

/**
 * 场景4：健康检查与资源监控
 * 调用 diagnose 接口检查服务状态
 */
async function testHealthCheck() {
  log.info('\n--- 场景4：健康检查 ---\n');

  // 尝试诊断接口
  const endpoints = [
    { name: '认证检查', path: '/v1/auth/profile', method: 'GET' },
    { name: '字典数据', path: '/v1/dictionary/space-types', method: 'GET' }
  ];

  for (const ep of endpoints) {
    const start = performance.now();
    try {
      await axios({
        method: ep.method,
        url: `${BASE_URL}${ep.path}`,
        headers: { Authorization: `Bearer ${authToken}` },
        timeout: 5000
      });
      const elapsed = performance.now() - start;
      log.pass(`  ${ep.name}: ${elapsed.toFixed(1)}ms`);
    } catch (e) {
      log.fail(`  ${ep.name}: ${e.response?.status || e.message}`);
    }
  }

  // 采样客户端内存作为基线
  sampleMemory('健康检查完成');
}

// ===================== 主流程 =====================

async function main() {
  console.log('========================================');
  console.log('  扩展性能压测');
  console.log('========================================');
  console.log(`  目标: ${BASE_URL}`);
  console.log(`  并发: ${CONCURRENT}`);
  console.log(`  轮数: ${ROUNDS}`);
  console.log(`  慢请求阈值: ${SLOW_THRESHOLD_MS}ms`);
  console.log('========================================\n');

  if (!(await login())) {
    console.error('无法登录，测试终止');
    process.exit(1);
  }

  const startTime = Date.now();

  try {
    await testHealthCheck();
    await testDeepPagination();
    await testConcurrentSettlementPreview();
    await testContinuousStability();

    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log('\n========================================');
    console.log('  压测完成');
    console.log('========================================');
    console.log(`  总耗时: ${elapsed}s`);

    // 内存采样汇总
    if (memorySamples.length > 0) {
      console.log('\n  内存采样历史:');
      memorySamples.forEach(s => {
        console.log(`    ${s.label}: 堆${fmtMem(s.heapUsed)}/RSS${fmtMem(s.rss)}`);
      });
    }
    console.log('');
  } catch (e) {
    console.error('压测异常:', e.message);
    process.exit(1);
  }
}

main().catch(console.error);
