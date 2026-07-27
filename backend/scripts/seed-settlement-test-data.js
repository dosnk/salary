/**
 * 结算功能测试数据生成脚本
 *
 * 用途：生成金额精确的测试数据，用于验证统计页面结算功能是否正确
 *       所有工程总额 = 1,432,698.50 元，3 人平均分配，每人应得 477,566.1666... 元
 *
 * 数据规格：
 *   - 12 个月（2025-01 ~ 2025-12），每月 7~16 个工程，共约 140 个
 *   - 工程总额精确 1,432,698.50 元（用整数分分配，杜绝浮点误差）
 *   - 3 个施工人员，全部平均分配（salary_distribution = 'average'）
 *   - 每人应得 = 1,432,698.50 / 3 = 477,566.1666... 元
 *   - 所有工程状态为 completed（可结算），每个工程含 1 个子项目
 *   - 工程名以 "[结算测试]" 前缀标识，方便清理
 *
 * 用法：
 *   node scripts/seed-settlement-test-data.js            # 生成数据
 *   node scripts/seed-settlement-test-data.js --dry-run  # 仅预览分配结果，不写库
 *   node scripts/seed-settlement-test-data.js --clean    # 清理之前的测试数据
 *   node scripts/seed-settlement-test-data.js --yes      # 跳过 3 秒确认
 *
 * 依赖：需先运行 init-db.js，确保字典数据和施工员用户已就绪
 */

const { Pool } = require('pg');
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

// ===================== 常量配置 =====================

// 工程总额（元）—— 精确值，3 人平均后每人 477,566.1666... 元
const TOTAL_AMOUNT = 1432698.50;

// 转为整数分（避免浮点运算误差）
const TOTAL_CENTS = Math.round(TOTAL_AMOUNT * 100); // 143,269,850 分

// 测试数据标识前缀（清理时按此前缀匹配）
const TEST_PREFIX = '[结算测试]';

// 月份配置：2025-01 ~ 2025-12，每月工程数量（7~16 之间）
// 这里用固定数量保证可复现，也可改为随机
const MONTH_CONFIG = [
  { year: 2025, month: 1,  count: 9  },
  { year: 2025, month: 2,  count: 12 },
  { year: 2025, month: 3,  count: 10 },
  { year: 2025, month: 4,  count: 14 },
  { year: 2025, month: 5,  count: 11 },
  { year: 2025, month: 6,  count: 15 },
  { year: 2025, month: 7,  count: 12 },
  { year: 2025, month: 8,  count: 13 },
  { year: 2025, month: 9,  count: 8  },
  { year: 2025, month: 10, count: 13 },
  { year: 2025, month: 11, count: 11 },
  { year: 2025, month: 12, count: 12 },
];

// ===================== 日志工具 =====================

const log = {
  info:    (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  success: (msg) => console.log(`[${new Date().toISOString()}] [✓] ${msg}`),
  warn:    (msg) => console.warn(`[${new Date().toISOString()}] [!] ${msg}`),
  error:   (msg) => console.error(`[${new Date().toISOString()}] [✗] ${msg}`),
};

// ===================== 工具函数 =====================

/** 随机整数 [min, max] */
const randomInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;

/** 随机选择数组元素 */
const randomChoice = (arr) => arr[Math.floor(Math.random() * arr.length)];

/**
 * 生成某月内的随机日期（1~28 日，避免月末边界问题）
 * @param {number} year
 * @param {number} month  1~12
 * @returns {Date}
 */
const randomDateInMonth = (year, month) => {
  const day = randomInt(1, 28);
  return new Date(year, month - 1, day, randomInt(8, 18), randomInt(0, 59), 0);
};

/**
 * 按随机权重将总额（分）分配到 N 份，保证总和精确
 * 算法：生成 N 个随机权重 → 按比例分配分 → 余额修正到最后一份
 *
 * @param {number} totalCents - 总额（分）
 * @param {number} count - 份数
 * @returns {number[]} 每份金额（分），数组长度 = count，总和 = totalCents
 */
const distributeAmount = (totalCents, count) => {
  // 1. 生成随机权重（10~110，避免极端值）
  const weights = [];
  for (let i = 0; i < count; i++) {
    weights.push(Math.random() * 100 + 10);
  }
  const totalWeight = weights.reduce((a, b) => a + b, 0);

  // 2. 按权重比例分配（取整）
  const centsArr = [];
  let assigned = 0;
  for (let i = 0; i < count - 1; i++) {
    const cents = Math.floor(weights[i] / totalWeight * totalCents);
    centsArr.push(cents);
    assigned += cents;
  }

  // 3. 最后一份拿剩余，保证总和精确
  centsArr.push(totalCents - assigned);

  return centsArr;
};

// ===================== 数据库连接 =====================

const pool = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'salary',
  password: process.env.DB_PASSWORD || 'postgres',
  port: parseInt(process.env.DB_PORT, 10) || 5432,
  max: 10,
});

// ===================== 运行时数据（从数据库读取） =====================

let SPACE_TYPE_ID = null;       // 空间类型 ID（取第一个）
let CONSTRUCTION_PLAN_ID = null; // 施工方案 ID（取第一个）
let CONSTRUCTOR_USERS = [];     // 施工员用户（取前 3 个 constructor）
let DOC_USER_ID = null;         // 资料员用户 ID（用作 created_by）

// ===================== 核心逻辑 =====================

/**
 * 加载字典数据：空间类型、施工方案、施工员用户
 */
const loadDictionaryData = async () => {
  // 空间类型（取第一个）
  const stResult = await pool.query('SELECT id FROM space_types ORDER BY id LIMIT 1');
  if (stResult.rows.length === 0) {
    throw new Error('空间类型为空，请先运行 init-db.js');
  }
  SPACE_TYPE_ID = stResult.rows[0].id;

  // 施工方案（取第一个）
  const cpResult = await pool.query('SELECT id FROM construction_plans ORDER BY id LIMIT 1');
  if (cpResult.rows.length === 0) {
    throw new Error('施工方案为空，请先运行 init-db.js');
  }
  CONSTRUCTION_PLAN_ID = cpResult.rows[0].id;

  // 施工员用户（取前 3 个 constructor 角色）
  const userResult = await pool.query(
    "SELECT id, username, nickname FROM users WHERE role = 'constructor' ORDER BY id LIMIT 3"
  );
  if (userResult.rows.length < 3) {
    throw new Error(`施工员不足 3 人（当前 ${userResult.rows.length} 人），需要至少 3 个 constructor 角色用户`);
  }
  CONSTRUCTOR_USERS = userResult.rows;

  // 资料员用户（用作 created_by），没有则用第一个施工员
  const docResult = await pool.query("SELECT id FROM users WHERE role = 'documenter' LIMIT 1");
  DOC_USER_ID = docResult.rows[0]?.id || CONSTRUCTOR_USERS[0].id;

  log.info(`字典数据加载完成：`);
  log.info(`  空间类型 ID: ${SPACE_TYPE_ID}`);
  log.info(`  施工方案 ID: ${CONSTRUCTION_PLAN_ID}`);
  log.info(`  施工员（${CONSTRUCTOR_USERS.length} 人）: ${CONSTRUCTOR_USERS.map(u => `${u.nickname}(${u.username})`).join(', ')}`);
  log.info(`  创建人 ID: ${DOC_USER_ID}`);
};

/**
 * 生成所有工程的金额分配方案
 * @returns {Array} 工程列表 [{ date, name, amountCents, amountYuan }]
 */
const generateProjectPlan = () => {
  // 计算总工程数
  const totalProjects = MONTH_CONFIG.reduce((sum, m) => sum + m.count, 0);
  log.info(`\n工程计划：${totalProjects} 个工程，总额 ${TOTAL_AMOUNT.toFixed(2)} 元`);

  // 按随机权重分配总额（分）
  const allAmountsCents = distributeAmount(TOTAL_CENTS, totalProjects);

  // 按月份组装工程计划
  const projects = [];
  let centsIndex = 0;
  let serialNo = 1; // 全局序号

  for (const monthCfg of MONTH_CONFIG) {
    for (let i = 0; i < monthCfg.count; i++) {
      const amountCents = allAmountsCents[centsIndex++];
      const amountYuan = amountCents / 100;

      projects.push({
        date: randomDateInMonth(monthCfg.year, monthCfg.month),
        name: `${TEST_PREFIX}${monthCfg.year}${String(monthCfg.month).padStart(2, '0')}-${String(serialNo).padStart(3, '0')}`,
        amountCents,
        amountYuan,
        year: monthCfg.year,
        month: monthCfg.month,
      });
      serialNo++;
    }
  }

  // 验证总额
  const sumCents = projects.reduce((sum, p) => sum + p.amountCents, 0);
  const sumYuan = sumCents / 100;
  log.info(`分配验证：总和 = ${sumYuan.toFixed(2)} 元（应为 ${TOTAL_AMOUNT.toFixed(2)} 元）`);
  if (sumCents !== TOTAL_CENTS) {
    throw new Error(`金额分配错误：总和 ${sumCents} 分 ≠ 目标 ${TOTAL_CENTS} 分`);
  }

  return projects;
};

/**
 * 打印分配方案预览（按月汇总）
 */
const printPlanPreview = (projects) => {
  log.info('\n===== 分配方案预览（按月汇总）=====');

  // 按月分组统计
  const monthGroups = {};
  for (const p of projects) {
    const key = `${p.year}-${String(p.month).padStart(2, '0')}`;
    if (!monthGroups[key]) {
      monthGroups[key] = { count: 0, totalCents: 0 };
    }
    monthGroups[key].count++;
    monthGroups[key].totalCents += p.amountCents;
  }

  // 打印每月汇总
  let grandTotalCents = 0;
  let grandCount = 0;
  log.info(`${'月份'.padEnd(10)} | ${'工程数'.padStart(4)} | ${'月度总额(元)'.padStart(14)} | ${'每人分账(元)'.padStart(14)}`);
  log.info('-'.repeat(55));

  for (const key of Object.keys(monthGroups).sort()) {
    const g = monthGroups[key];
    const monthYuan = g.totalCents / 100;
    const perPerson = monthYuan / 3;
    log.info(`${key.padEnd(10)} | ${String(g.count).padStart(4)} | ${monthYuan.toFixed(2).padStart(14)} | ${perPerson.toFixed(4).padStart(14)}`);
    grandTotalCents += g.totalCents;
    grandCount += g.count;
  }

  log.info('-'.repeat(55));
  log.info(`${'合计'.padEnd(10)} | ${String(grandCount).padStart(4)} | ${(grandTotalCents / 100).toFixed(2).padStart(14)} | ${(grandTotalCents / 100 / 3).toFixed(4).padStart(14)}`);

  // 打印测试基线
  log.info('\n===== 测试基线（手工可复核的绝对真值）=====');
  log.info(`  工程总数     : ${grandCount} 个`);
  log.info(`  工程总额     : ${(grandTotalCents / 100).toFixed(2)} 元`);
  const workerNames = CONSTRUCTOR_USERS.length > 0
    ? CONSTRUCTOR_USERS.map(u => u.nickname).join('、')
    : '（数据库读取）';
  log.info(`  施工人员     : 3 人（${workerNames}）`);
  log.info(`  分配方式     : 平均分配`);
  log.info(`  每人应得总额 : ${(grandTotalCents / 100 / 3).toFixed(4)} 元`);
  log.info(`  三人合计     : ${((grandTotalCents / 100 / 3) * 3).toFixed(2)} 元`);
};

/**
 * 执行数据写入（事务）
 * @param {Array} projects - 工程计划列表
 */
const writeData = async (projects) => {
  const client = await pool.connect();
  let projectCount = 0;
  let subprojectCount = 0;
  let workerCount = 0;
  let historyCount = 0;

  try {
    await client.query('BEGIN');

    for (const p of projects) {
      // 1. 创建工程（状态 completed，平均分配）
      const projectResult = await client.query(
        `INSERT INTO projects (name, description, status, total_amount, salary_distribution, created_by, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $7)
         RETURNING id`,
        [
          p.name,
          '结算功能测试数据 - 自动生成',
          'completed',           // 已完工状态，可结算
          p.amountYuan,          // 工程总额（NUMERIC 会自动处理精度）
          'average',             // 平均分配
          DOC_USER_ID,
          p.date,
        ]
      );
      const projectId = projectResult.rows[0].id;
      projectCount++;

      // 2. 创建子项目（1 个，金额 = 工程金额）
      // length/width 用固定值（厘米），quantity = 1，amount = 工程金额
      // 结算按 subprojects.amount 分配，所以 amount 必须精确
      await client.query(
        `INSERT INTO subprojects (project_id, space_type_id, construction_plan_id, length, width, quantity, amount, status, created_by, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $10)`,
        [
          projectId,
          SPACE_TYPE_ID,
          CONSTRUCTION_PLAN_ID,
          1000,               // length: 1000cm = 10m（固定值，不影响金额）
          1000,               // width:  1000cm = 10m
          1,                  // quantity: 1
          p.amountYuan,       // amount: 工程金额（精确）
          'completed',        // 子项目已完工
          DOC_USER_ID,
          p.date,
        ]
      );
      subprojectCount++;

      // 3. 关联 3 个施工人员（平均分配，不设工日）
      for (const worker of CONSTRUCTOR_USERS) {
        await client.query(
          'INSERT INTO project_workers (project_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
          [projectId, worker.id]
        );
        workerCount++;
      }

      // 4. 添加工程历史记录（创建 + 完工）
      await client.query(
        `INSERT INTO project_history (project_id, action, description, performed_by, created_at)
         VALUES ($1, $2, $3, $4, $5)`,
        [projectId, 'CREATE_PROJECT', `创建工程：${p.name}`, DOC_USER_ID, p.date]
      );
      historyCount++;

      // 完工历史（创建时间后 7~30 天）
      const completedDate = new Date(p.date.getTime() + randomInt(7, 30) * 24 * 60 * 60 * 1000);
      await client.query(
        `INSERT INTO project_history (project_id, action, description, performed_by, created_at)
         VALUES ($1, $2, $3, $4, $5)`,
        [projectId, 'UPDATE_PROJECT', '工程状态变更为：completed', DOC_USER_ID, completedDate]
      );
      historyCount++;
    }

    await client.query('COMMIT');

    log.success(`\n数据写入完成：`);
    log.success(`  工程     : ${projectCount} 条`);
    log.success(`  子项目   : ${subprojectCount} 条`);
    log.success(`  施工人员 : ${workerCount} 条`);
    log.success(`  历史记录 : ${historyCount} 条`);

    return { projectCount, subprojectCount, workerCount, historyCount };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
};

/**
 * 清理之前的测试数据
 */
const cleanTestData = async () => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 查找测试工程 ID 列表
    const result = await client.query(
      `SELECT id FROM projects WHERE name LIKE $1`,
      [`${TEST_PREFIX}%`]
    );
    const projectIds = result.rows.map(r => r.id);

    if (projectIds.length === 0) {
      log.info('未找到测试数据，无需清理');
      await client.query('COMMIT');
      return 0;
    }

    log.info(`找到 ${projectIds.length} 个测试工程，开始清理...`);

    // 按依赖顺序删除
    await client.query('DELETE FROM wage_distributions WHERE subproject_id IN (SELECT id FROM subprojects WHERE project_id = ANY($1::int[]))', [projectIds]);
    await client.query('DELETE FROM project_user_status WHERE project_id = ANY($1::int[])', [projectIds]);
    await client.query('DELETE FROM project_history WHERE project_id = ANY($1::int[])', [projectIds]);
    await client.query('DELETE FROM subprojects WHERE project_id = ANY($1::int[])', [projectIds]);
    await client.query('DELETE FROM project_workers WHERE project_id = ANY($1::int[])', [projectIds]);
    await client.query('DELETE FROM files WHERE project_id = ANY($1::int[])', [projectIds]);
    await client.query('DELETE FROM projects WHERE id = ANY($1::int[])', [projectIds]);

    await client.query('COMMIT');
    log.success(`清理完成，删除 ${projectIds.length} 个测试工程及其关联数据`);
    return projectIds.length;
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
};

/**
 * 写入后验证：查询数据库确认数据一致性
 */
const verifyData = async () => {
  log.info('\n===== 写入后验证 =====');

  // 1. 工程总数和总额
  const projectResult = await pool.query(
    `SELECT COUNT(*) AS cnt, COALESCE(SUM(total_amount), 0) AS total
     FROM projects WHERE name LIKE $1`,
    [`${TEST_PREFIX}%`]
  );
  const projectCount = parseInt(projectResult.rows[0].cnt);
  const projectTotal = parseFloat(projectResult.rows[0].total);

  // 2. 子项目总额
  const subResult = await pool.query(
    `SELECT COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS total
     FROM subprojects WHERE project_id IN (SELECT id FROM projects WHERE name LIKE $1)`,
    [`${TEST_PREFIX}%`]
  );
  const subCount = parseInt(subResult.rows[0].cnt);
  const subTotal = parseFloat(subResult.rows[0].total);

  // 3. 施工人员关联数
  const workerResult = await pool.query(
    `SELECT COUNT(*) AS cnt FROM project_workers
     WHERE project_id IN (SELECT id FROM projects WHERE name LIKE $1)`,
    [`${TEST_PREFIX}%`]
  );
  const workerCount = parseInt(workerResult.rows[0].cnt);

  // 4. 每人分账明细
  const perUserResult = await pool.query(
    `SELECT u.nickname, COALESCE(SUM(s.amount), 0) AS total_amount
     FROM users u
     JOIN project_workers pw ON pw.user_id = u.id
     JOIN subprojects s ON s.project_id = pw.project_id
     WHERE pw.project_id IN (SELECT id FROM projects WHERE name LIKE $1)
     GROUP BY u.id, u.nickname
     ORDER BY u.id`,
    [`${TEST_PREFIX}%`]
  );

  log.info(`  工程总数     : ${projectCount}（子项目 ${subCount}）`);
  log.info(`  工程总额     : ${projectTotal.toFixed(2)} 元`);
  log.info(`  子项目总额   : ${subTotal.toFixed(2)} 元`);
  log.info(`  工程总额 = 子项目总额: ${Math.abs(projectTotal - subTotal) < 0.01 ? '✓ 一致' : '✗ 不一致'}`);
  log.info(`  施工人员关联 : ${workerCount} 条（应为 ${projectCount * 3}）`);

  log.info(`\n  每人分账明细（从 subprojects.amount 聚合）：`);
  let userSum = 0;
  for (const row of perUserResult.rows) {
    const amount = parseFloat(row.total_amount);
    userSum += amount;
    log.info(`    ${row.nickname}: ${amount.toFixed(4)} 元`);
  }

  // 每人应得 = 总额 / 3
  const expectedPerPerson = projectTotal / 3;
  log.info(`\n  每人应得（总额÷3）: ${expectedPerPerson.toFixed(4)} 元`);
  log.info(`  三人合计           : ${userSum.toFixed(4)} 元`);
  log.info(`  总额 = 三人合计    : ${Math.abs(projectTotal - userSum) < 0.01 ? '✓ 一致' : '✗ 不一致'}`);

  // 最终结论
  log.info('\n===== 测试基线（请记录以下数字用于手工复核）=====');
  log.info(`┌──────────────────────────────────────────────┐`);
  log.info(`│ 工程总数     : ${String(projectCount).padEnd(28)} │`);
  log.info(`│ 工程总额     : ${projectTotal.toFixed(2).padEnd(28)} │`);
  log.info(`│ 施工人员     : 3 人                        │`);
  log.info(`│ 分配方式     : 平均分配                      │`);
  log.info(`│ 每人应得总额 : ${expectedPerPerson.toFixed(4).padEnd(28)} │`);
  log.info(`│ 三人合计     : ${(expectedPerPerson * 3).toFixed(2).padEnd(28)} │`);
  log.info(`└──────────────────────────────────────────────┘`);
  log.info('\n测试方法：');
  log.info('  1. 在统计页面选择全部工程执行结算');
  log.info('  2. 结算后每人"已结算金额"应 = 上表"每人应得总额"');
  log.info('  3. 三人"已结算金额"之和应 = 工程总额');
  log.info('  4. 结算后"待结算金额"应为 0');
  log.info('  5. 可按月分批结算，对照月度基线验证累计值');
};

// ===================== 命令行参数解析 =====================

const parseArgs = () => {
  const args = process.argv.slice(2);
  return {
    dryRun: args.includes('--dry-run'),
    clean: args.includes('--clean'),
    yes: args.includes('--yes') || args.includes('-y'),
    help: args.includes('--help') || args.includes('-h'),
  };
};

// ===================== 主函数 =====================

const main = async () => {
  const opts = parseArgs();

  if (opts.help) {
    console.log(`
用法: node scripts/seed-settlement-test-data.js [选项]

选项:
  --dry-run   仅预览金额分配方案，不写数据库
  --clean     清理之前的测试数据（按工程名前缀 "${TEST_PREFIX}" 匹配）
  --yes, -y   跳过 3 秒确认倒计时
  --help, -h  显示帮助

数据规格:
  总额: 1,432,698.50 元
  月份: 2025-01 ~ 2025-12（共 140 个工程）
  人员: 3 人平均分配，每人应得 477,566.1666... 元
  状态: 全部 completed（可结算）
`);
    process.exit(0);
  }

  log.info('===== 结算功能测试数据生成 =====');
  log.info(`目标总额: ${TOTAL_AMOUNT.toFixed(2)} 元`);
  log.info(`数据库  : ${process.env.DB_NAME || 'salary'}@${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 5432}`);

  // --clean 模式：只清理不生成
  if (opts.clean) {
    log.info('\n模式: 清理测试数据');
    await cleanTestData();
    await pool.end();
    return;
  }

  // --dry-run 模式：只预览金额分配，不连接数据库
  if (opts.dryRun) {
    log.info('\n模式: 预览（dry-run，不写库，不连数据库）');
    const projects = generateProjectPlan();
    printPlanPreview(projects);
    log.success('\n预览完成，未写入任何数据');
    log.info('如需正式生成数据，请在服务器上执行:');
    log.info('  docker compose exec app node scripts/seed-settlement-test-data.js --yes');
    return;
  }

  // 正常模式：生成并写入
  log.info('模式: 生成并写入数据');

  // 安全确认
  if (!opts.yes) {
    log.warn('\n⚠️  即将向数据库写入 140 个测试工程！');
    log.warn(`⚠️  目标数据库: ${process.env.DB_NAME || 'salary'}`);
    log.warn('⚠️  建议在测试库执行，或先备份');
    log.warn('3 秒后开始执行，按 Ctrl+C 取消...');
    await new Promise(resolve => setTimeout(resolve, 3000));
  }

  const startTime = Date.now();

  try {
    // 1. 加载字典数据
    await loadDictionaryData();

    // 2. 生成分配方案
    const projects = generateProjectPlan();
    printPlanPreview(projects);

    // 3. 写入数据库
    log.info('\n开始写入数据...');
    await writeData(projects);

    // 4. 写入后验证
    await verifyData();

    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    log.success(`\n===== 全部完成 | 耗时 ${elapsed}s =====`);
    log.info('\n下一步：');
    log.info('  1. 在 Android 端登录，进入统计页面查看待结算工程');
    log.info('  2. 按月分批结算，对照月度基线验证');
    log.info('  3. 全部结算后验证每人已结算金额 = 477,566.1666... 元');
    log.info('  4. 测试完毕后运行: node scripts/seed-settlement-test-data.js --clean');

  } catch (error) {
    log.error(`执行失败: ${error.message}`);
    console.error(error.stack);
    process.exit(1);
  } finally {
    await pool.end();
  }
};

main();
