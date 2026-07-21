#!/usr/bin/env node
/**
 * 性能测试一键运行脚本（Node.js 版本，跨平台兼容）
 *
 * 用法:
 *   node testfile/test/performance/run-all.js [BASE_URL] [选项]
 *
 * 选项:
 *   --skip-stress    跳过扩展压测（只跑基础性能测试）
 *   --stress-only    只跑扩展压测
 *   --with-gc        启用GC（需要 node --expose-gc run-all.js）
 *
 * 环境变量:
 *   BASE_URL         目标服务器地址（默认 http://localhost:3000）
 *   CONCURRENT       并发请求数（默认 10）
 *   ROUNDS           连续请求轮数（默认 100）
 */

const { execSync } = require('child_process');
const path = require('path');

// ===================== 参数解析 =====================
const args = process.argv.slice(2);
const SKIP_STRESS = args.includes('--skip-stress');
const STRESS_ONLY = args.includes('--stress-only');
const WITH_GC = args.includes('--with-gc');

// 从参数中提取 BASE_URL（第一个非 -- 开头的参数）
const baseUrlArg = args.find(a => !a.startsWith('--'));
const BASE_URL = baseUrlArg || process.env.BASE_URL || 'http://localhost:3000';

// Node 可执行文件路径（用于调用子脚本）
const NODE_BIN = process.argv[0];
const NODE_ARGS = WITH_GC ? ['--expose-gc'] : [];

// ===================== 工具函数 =====================
const log = {
  info: (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  pass: (msg) => console.log(`[${new Date().toISOString()}] [✓] ${msg}`),
  fail: (msg) => console.error(`[${new Date().toISOString()}] [✗] ${msg}`)
};

/**
 * 运行子脚本（同步执行，继承 stdio）
 * @param {string} scriptPath - 脚本相对路径
 * @param {string} label - 显示标签
 * @returns {boolean} 是否成功
 */
const runScript = (scriptPath, label) => {
  const fullPath = path.resolve(__dirname, scriptPath);
  log.info(`--- ${label} ---`);
  try {
    const result = execSync(`"${NODE_BIN}" ${NODE_ARGS.join(' ')} "${fullPath}"`, {
      stdio: 'inherit',
      env: { ...process.env, BASE_URL },
      cwd: process.cwd()
    });
    return true;
  } catch (error) {
    log.fail(`${label} 执行失败: ${error.message}`);
    return false;
  }
};

// ===================== 主流程 =====================
const main = async () => {
  console.log('========================================');
  console.log('  三人行吊顶管理系统 - 性能测试套件');
  console.log('  目标: ' + BASE_URL);
  console.log('  GC: ' + (WITH_GC ? '启用' : '未启用'));
  console.log('========================================\n');

  // 检查服务可用性（用 http 模块，避免依赖 axios）
  await new Promise((resolve) => {
    const http = require('http');
    const url = new URL(`${BASE_URL}/v1/auth/login`);
    const req = http.request({
      hostname: url.hostname,
      port: url.port,
      path: url.pathname,
      method: 'POST',
      timeout: 3000,
      headers: { 'Content-Type': 'application/json' }
    }, (res) => {
      // 任何 HTTP 响应都说明服务可用
      res.resume(); // 丢弃响应体
      if (res.statusCode) {
        log.pass(`服务可用 (状态码 ${res.statusCode})`);
      } else {
        log.fail('服务不可用');
        process.exit(1);
      }
      resolve();
    });
    req.on('error', (e) => {
      log.fail(`服务不可用: ${e.message}`);
      log.fail(`请检查后端服务是否在 ${BASE_URL} 启动`);
      process.exit(1);
    });
    req.on('timeout', () => {
      log.fail('服务不可用: 连接超时');
      process.exit(1);
    });
    req.write(JSON.stringify({}));
    req.end();
  });
  console.log('');

  const startTime = Date.now();
  const results = [];

  // 1. 基础性能测试
  if (!STRESS_ONLY) {
    results.push({ name: '统计查询性能', success: runScript('statistics.bench.js', '1. 统计查询性能') });
    console.log('');
    results.push({ name: '结算操作性能', success: runScript('settlement.bench.js', '2. 结算操作性能') });
  }

  // 2. 扩展压测
  if (!SKIP_STRESS) {
    console.log('');
    results.push({ name: '扩展压测', success: runScript('stress.bench.js', '3. 扩展压测（深页翻页+并发+稳定性）') });
  }

  // 汇总
  const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
  console.log('\n========================================');
  console.log('  全部性能测试完成');
  console.log('========================================');
  console.log(`  总耗时: ${elapsed}s`);
  console.log(`  成功: ${results.filter(r => r.success).length}/${results.length}`);
  results.forEach(r => {
    console.log(`  ${r.success ? '✓' : '✗'} ${r.name}`);
  });
  console.log('');
};

main().catch(err => {
  log.fail(`执行异常: ${err.message}`);
  process.exit(1);
});
