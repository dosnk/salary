/**
 * 大数据量测试数据生成脚本
 *
 * 用途：模拟 1~3 年的真实业务数据规模，用于验证程序在大量数据下的稳定性和准确性
 * 生成数据：工程、子项目、施工人员、工程历史、预支记录
 * （不生成结算单，结算单需通过 HTTP 接口产生，保证业务流程完整）
 *
 * 用法：
 *   docker compose exec app node scripts/seed-test-data.js                       # 默认 medium
 *   docker compose exec app node scripts/seed-test-data.js --scale=small         # 小量（快速验证）
 *   docker compose exec app node scripts/seed-test-data.js --scale=large         # 大量（压力测试）
 *   docker compose exec app node scripts/seed-test-data.js --scale=medium --years=2  # 中量跨2年
 *
 * 量级说明：
 *   small  : 50 工程,  250 子项目,  10 预支      （快速验证用）
 *   medium : 300 工程, 2000 子项目, 100 预支     （常规压测用，推荐）
 *   large  : 1000 工程, 8000 子项目, 500 预支    （深度压力测试）
 *
 * 注意：
 *   1. 执行前请确保已运行 init-db.js，字典数据已就绪
 *   2. 建议在测试库执行，或执行前先用 clear-business-data.js 清空业务数据
 *   3. 生成后可运行 verify-data-consistency.js 校验数据一致性
 */

const { Pool } = require('pg');
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

// ===================== 日志工具 =====================
const log = {
  info: (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  success: (msg) => console.log(`[${new Date().toISOString()}] [✓] ${msg}`),
  warn: (msg) => console.warn(`[${new Date().toISOString()}] [!] ${msg}`),
  error: (msg) => console.error(`[${new Date().toISOString()}] [✗] ${msg}`)
};

// ===================== 量级配置 =====================
const SCALE_CONFIG = {
  small: { projects: 50, subprojectsPerProject: [3, 8], advances: 10 },
  medium: { projects: 300, subprojectsPerProject: [5, 12], advances: 100 },
  large: { projects: 1000, subprojectsPerProject: [6, 15], advances: 500 }
};

// ===================== 数据池 =====================
// 空间类型和施工方案 ID 在脚本运行时从数据库动态读取
let SPACE_TYPE_IDS = [];
let CONSTRUCTION_PLANS = []; // [{id, unit, price}]
let CONSTRUCTOR_USERS = []; // [{id, username, nickname}]
let DOC_USER_ID = null; // 资料员用户ID（用作 created_by）

// ===================== 工具函数 =====================
const randomInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const randomFloat = (min, max, decimals = 2) => parseFloat((Math.random() * (max - min) + min).toFixed(decimals));
const randomChoice = (arr) => arr[Math.floor(Math.random() * arr.length)];
const randomChoices = (arr, count) => {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, Math.min(count, arr.length));
};

/**
 * 生成随机工程名（带时间戳保证唯一）
 */
const generateProjectName = (index, date) => {
  const communities = ['阳光花园', '万科城', '保利春天', '绿地新都会', '中海锦城', '碧桂园', '融创玖樟台', '龙湖天街', '金地名峰', '招商雍景湾'];
  const buildings = ['1栋', '2栋', '3栋', '5栋', '6栋', '7栋', '8栋', '9栋', '10栋'];
  const units = ['1单元', '2单元', '3单元'];
  const rooms = ['101', '201', '301', '502', '603', '805', '906', '1001', '1203', '1502'];
  const dateStr = date.toISOString().slice(0, 10).replace(/-/g, '');
  return `${randomChoice(communities)}${randomChoice(buildings)}${randomChoice(units)}${randomChoice(rooms)}-${dateStr}-${index}`;
};

/**
 * 根据施工方案单位类型计算子项目数量和金额
 * 复用 services/calculation.js 的逻辑（单位：厘米）
 */
const calculateSubproject = (plan, lengthCm, widthCm) => {
  const lengthM = lengthCm / 100;
  const widthM = widthCm / 100;
  let quantity;

  switch (plan.unit) {
    case 'length':
      quantity = lengthM;
      break;
    case 'perimeter':
      quantity = (lengthM + widthM) * 2;
      break;
    case 'area':
    default:
      quantity = lengthM * widthM;
  }

  // NUMERIC(10,2) 保留2位小数
  quantity = Math.round(quantity * 100) / 100;
  // NUMERIC(14,4) 保留4位小数
  const amount = Math.round(quantity * plan.price * 10000) / 10000;

  return { quantity, amount };
};

// ===================== 主流程 =====================
const pool = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'salary_system',
  password: process.env.DB_PASSWORD,
  port: parseInt(process.env.DB_PORT, 10) || 5432,
  max: 10
});

/**
 * 解析命令行参数
 * 支持两种形式：--scale large 和 --scale=large
 */
const parseArgs = () => {
  const args = process.argv.slice(2);
  const config = { scale: 'medium', years: 2 };

  // 辅助函数：从参数中提取值（支持 --key value 和 --key=value 两种形式）
  const getArgValue = (arg, nextArg, key) => {
    const equalForm = `${key}=`;
    if (arg.startsWith(equalForm)) {
      return arg.slice(equalForm.length);
    }
    if (arg === key && nextArg !== undefined) {
      return nextArg;
    }
    return null;
  };

  for (let i = 0; i < args.length; i++) {
    const scaleVal = getArgValue(args[i], args[i + 1], '--scale');
    if (scaleVal !== null) {
      const scale = scaleVal.toLowerCase();
      if (SCALE_CONFIG[scale]) {
        config.scale = scale;
      } else {
        log.error(`无效的 scale 参数：${scale}，可选值：small/medium/large`);
        process.exit(1);
      }
      // 等号形式不需要跳过下一个参数
      if (!args[i].startsWith('--scale=')) i++;
      continue;
    }

    const yearsVal = getArgValue(args[i], args[i + 1], '--years');
    if (yearsVal !== null) {
      const years = parseInt(yearsVal, 10);
      if (years >= 1 && years <= 5) {
        config.years = years;
      } else {
        log.error(`无效的 years 参数：${years}，范围 1-5`);
        process.exit(1);
      }
      if (!args[i].startsWith('--years=')) i++;
      continue;
    }

    if (args[i] === '--help' || args[i] === '-h') {
      console.log(`
用法: node scripts/seed-test-data.js [选项]

选项:
  --scale <small|medium|large>  数据量级（默认 medium）
  --years <1-5>                 跨度年数（默认 2）
  --help, -h                    显示帮助

量级说明:
  small  : 50 工程,  250 子项目,  10 预支
  medium : 300 工程, 2000 子项目, 100 预支
  large  : 1000 工程, 8000 子项目, 500 预支
`);
      process.exit(0);
    }
  }

  return config;
};

/**
 * 加载字典数据（空间类型、施工方案、施工员用户）
 */
const loadDictionaryData = async () => {
  // 空间类型
  const stResult = await pool.query('SELECT id FROM space_types ORDER BY id');
  SPACE_TYPE_IDS = stResult.rows.map(r => r.id);
  if (SPACE_TYPE_IDS.length === 0) {
    throw new Error('空间类型为空，请先运行 init-db.js');
  }

  // 施工方案
  const cpResult = await pool.query('SELECT id, unit, price FROM construction_plans ORDER BY id');
  CONSTRUCTION_PLANS = cpResult.rows;
  if (CONSTRUCTION_PLANS.length === 0) {
    throw new Error('施工方案为空，请先运行 init-db.js');
  }

  // 施工员用户（constructor 角色）
  const userResult = await pool.query("SELECT id, username, nickname FROM users WHERE role = 'constructor' AND username != 'admin' ORDER BY id");
  CONSTRUCTOR_USERS = userResult.rows;
  if (CONSTRUCTOR_USERS.length === 0) {
    throw new Error('没有 constructor 角色用户，请先运行 init-db.js');
  }

  // 资料员用户（用作 created_by，避免权限问题）
  const docResult = await pool.query("SELECT id FROM users WHERE role = 'documenter' LIMIT 1");
  DOC_USER_ID = docResult.rows[0]?.id || CONSTRUCTOR_USERS[0].id;

  log.info(`字典数据加载完成：空间类型 ${SPACE_TYPE_IDS.length} 个，施工方案 ${CONSTRUCTION_PLANS.length} 个，施工员 ${CONSTRUCTOR_USERS.length} 人`);
};

/**
 * 生成跨多年的随机日期
 * @param {number} yearsBack - 向前推几年
 * @returns {Date}
 */
const randomDateInYears = (yearsBack) => {
  const now = new Date();
  const past = new Date();
  past.setFullYear(now.getFullYear() - yearsBack);
  return new Date(past.getTime() + Math.random() * (now.getTime() - past.getTime()));
};

/**
 * 生成工程数据（含子项目、施工人员、历史记录）
 * 使用事务确保每条工程的数据一致性
 * @param {number} totalCount - 工程总数
 * @param {number} yearsBack - 跨度年数
 * @param {number[]} spRange - 子项目数量范围 [min, max]
 * @param {number} batchSize - 每批事务大小
 */
const seedProjects = async (totalCount, yearsBack, spRange, batchSize = 20) => {
  log.info(`开始生成 ${totalCount} 个工程（跨 ${yearsBack} 年，每工程 ${spRange[0]}-${spRange[1]} 个子项目）...`);

  const startDate = Date.now();
  let completed = 0;
  let totalSubprojects = 0;
  let totalWorkers = 0;
  let totalHistory = 0;

  // 分批处理，避免单事务过大
  for (let batchStart = 0; batchStart < totalCount; batchStart += batchSize) {
    const batchEnd = Math.min(batchStart + batchSize, totalCount);
    const client = await pool.connect();

    try {
      await client.query('BEGIN');

      for (let i = batchStart; i < batchEnd; i++) {
        // 生成工程创建时间（跨多年）
        const createdAt = randomDateInYears(yearsBack);

        // 随机工程状态分布：60% completed, 25% constructing, 10% preparing, 5% canceled
        const statusRand = Math.random();
        let status;
        if (statusRand < 0.6) status = 'completed';
        else if (statusRand < 0.85) status = 'constructing';
        else if (statusRand < 0.95) status = 'preparing';
        else status = 'canceled';

        // 随机分配方式：70% average, 30% work_days
        const salaryDistribution = Math.random() < 0.7 ? 'average' : 'work_days';

        // 工程名（带索引保证唯一）
        const projectName = generateProjectName(i + 1, createdAt);

        // 创建工程
        const projectResult = await client.query(
          `INSERT INTO projects (name, description, status, salary_distribution, created_by, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, $6, $6)
           RETURNING id`,
          [
            projectName,
            `测试工程-${i + 1}，自动生成`,
            status,
            salaryDistribution,
            DOC_USER_ID,
            createdAt
          ]
        );
        const projectId = projectResult.rows[0].id;

        // 随机分配施工人员（2-6人）
        const workerCount = randomInt(2, Math.min(6, CONSTRUCTOR_USERS.length));
        const workers = randomChoices(CONSTRUCTOR_USERS, workerCount);

        for (const worker of workers) {
          // work_days 模式下随机工日 1-15 天
          const workdays = salaryDistribution === 'work_days' ? randomInt(1, 15) : null;
          if (workdays !== null) {
            await client.query(
              'INSERT INTO project_workers (project_id, user_id, workdays) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING',
              [projectId, worker.id, workdays]
            );
          } else {
            await client.query(
              'INSERT INTO project_workers (project_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
              [projectId, worker.id]
            );
          }
          totalWorkers++;
        }

        // 生成子项目（按 spRange 范围随机）
        const [minSp, maxSp] = spRange;
        const spCount = randomInt(minSp, maxSp);

        for (let j = 0; j < spCount; j++) {
          const plan = randomChoice(CONSTRUCTION_PLANS);
          const spaceTypeId = randomChoice(SPACE_TYPE_IDS);

          // 随机尺寸（厘米）：长 200-800cm, 宽 150-600cm
          const lengthCm = randomInt(200, 800);
          const widthCm = randomInt(150, 600);

          const { quantity, amount } = calculateSubproject(plan, lengthCm, widthCm);

          // 子项目状态：completed 工程下子项目全 completed，其他状态混合
          let spStatus;
          if (status === 'completed') {
            spStatus = 'completed';
          } else if (status === 'constructing') {
            spStatus = Math.random() < 0.5 ? 'completed' : 'pending';
          } else {
            spStatus = 'pending';
          }

          await client.query(
            `INSERT INTO subprojects (project_id, space_type_id, construction_plan_id, length, width, quantity, amount, status, created_by, created_at, updated_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $10)`,
            [
              projectId,
              spaceTypeId,
              plan.id,
              lengthCm,
              widthCm,
              quantity,
              amount,
              spStatus,
              DOC_USER_ID,
              createdAt
            ]
          );
          totalSubprojects++;
        }

        // 重新计算工程总额（与子项目金额之和保持一致）
        await client.query(
          'UPDATE projects SET total_amount = (SELECT COALESCE(SUM(amount), 0) FROM subprojects WHERE project_id = $1) WHERE id = $2',
          [projectId, projectId]
        );

        // 添加工程历史记录
        await client.query(
          `INSERT INTO project_history (project_id, action, description, performed_by, created_at)
           VALUES ($1, $2, $3, $4, $5)`,
          [
            projectId,
            'CREATE_PROJECT',
            `创建工程：${projectName}`,
            DOC_USER_ID,
            createdAt
          ]
        );
        totalHistory++;

        // completed 状态额外加一条完工历史
        // 注意：action 字段有外键约束到 action_types(code)，且 code VARCHAR(20)
        // 可用值：CREATE_PROJECT/UPDATE_PROJECT/DELETE_PROJECT/ADD_SUBPROJECT 等
        if (status === 'completed') {
          const completedAt = new Date(createdAt.getTime() + randomInt(7, 60) * 24 * 60 * 60 * 1000);
          await client.query(
            `INSERT INTO project_history (project_id, action, description, performed_by, created_at)
             VALUES ($1, $2, $3, $4, $5)`,
            [
              projectId,
              'UPDATE_PROJECT',
              `工程状态变更为：completed`,
              DOC_USER_ID,
              completedAt
            ]
          );
          totalHistory++;
        }

        completed++;
      }

      await client.query('COMMIT');

      // 进度日志（每批打印一次）
      if (completed % batchSize === 0 || completed === totalCount) {
        const elapsed = ((Date.now() - startDate) / 1000).toFixed(1);
        const progress = ((completed / totalCount) * 100).toFixed(1);
        log.info(`进度: ${completed}/${totalCount} (${progress}%) | 耗时 ${elapsed}s | 子项目 ${totalSubprojects} | 施工人员 ${totalWorkers} | 历史 ${totalHistory}`);
      }
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  const elapsed = ((Date.now() - startDate) / 1000).toFixed(1);
  log.success(`工程数据生成完成：${completed} 个工程, ${totalSubprojects} 个子项目, ${totalWorkers} 条施工人员, ${totalHistory} 条历史 | 耗时 ${elapsed}s`);
  return { completed, totalSubprojects, totalWorkers, totalHistory };
};

/**
 * 生成预支记录
 */
const seedAdvances = async (totalCount, yearsBack) => {
  log.info(`开始生成 ${totalCount} 条预支记录...`);

  const startDate = Date.now();
  const client = await pool.connect();
  let completed = 0;

  try {
    await client.query('BEGIN');

    for (let i = 0; i < totalCount; i++) {
      const user = randomChoice(CONSTRUCTOR_USERS);
      const advanceDate = randomDateInYears(yearsBack);
      const amount = randomFloat(100, 5000, 2);

      await client.query(
        `INSERT INTO wage_advances (user_id, advance_amount, advance_date, settled, created_by, created_at, remark)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [
          user.id,
          amount,
          advanceDate,
          false, // 全部未结算，避免影响后续结算测试
          DOC_USER_ID,
          advanceDate,
          `测试预支-${i + 1}`
        ]
      );
      completed++;
    }

    await client.query('COMMIT');
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }

  const elapsed = ((Date.now() - startDate) / 1000).toFixed(1);
  log.success(`预支记录生成完成：${completed} 条 | 耗时 ${elapsed}s`);
  return { completed };
};

/**
 * 打印生成后的数据统计
 */
const printStats = async () => {
  const queries = [
    { name: '工程总数', sql: "SELECT COUNT(*) AS cnt FROM projects WHERE name NOT LIKE '%测试工程%'" },
    { name: '测试工程', sql: "SELECT COUNT(*) AS cnt FROM projects WHERE description LIKE '%自动生成%'" },
    { name: '子项目总数', sql: 'SELECT COUNT(*) AS cnt FROM subprojects' },
    { name: '施工人员关联', sql: 'SELECT COUNT(*) AS cnt FROM project_workers' },
    { name: '工程历史', sql: 'SELECT COUNT(*) AS cnt FROM project_history' },
    { name: '预支记录', sql: 'SELECT COUNT(*) AS cnt FROM wage_advances' },
    { name: '已完工工程', sql: "SELECT COUNT(*) AS cnt FROM projects WHERE status = 'completed'" },
    { name: '已完工子项目', sql: "SELECT COUNT(*) AS cnt FROM subprojects WHERE status = 'completed'" }
  ];

  log.info('===== 数据统计 =====');
  for (const q of queries) {
    const result = await pool.query(q.sql);
    log.info(`  ${q.name}: ${result.rows[0].cnt}`);
  }
};

// 缓存命令行参数（避免多次解析）
let parseArgsCache;
const getCachedArgs = () => {
  if (!parseArgsCache) parseArgsCache = parseArgs();
  return parseArgsCache;
};

/**
 * 主函数
 */
const main = async () => {
  const config = getCachedArgs();
  const scaleConfig = SCALE_CONFIG[config.scale];

  log.info('===== 大数据量测试数据生成 =====');
  log.info(`量级: ${config.scale} | 工程: ${scaleConfig.projects} | 预支: ${scaleConfig.advances} | 跨度: ${config.years} 年`);
  log.info(`数据库: ${process.env.DB_NAME || 'salary_system'}@${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 5432}`);
  log.info('=========================================\n');

  // 安全确认（除非 --yes）
  const hasYesFlag = process.argv.includes('--yes') || process.argv.includes('-y');
  if (!hasYesFlag) {
    log.warn('⚠️  即将在数据库中生成大量测试数据！');
    log.warn(`⚠️  目标数据库: ${process.env.DB_NAME || 'salary_system'}`);
    log.warn('⚠️  建议在测试库执行，或先备份生产库');
    log.warn('3 秒后开始执行，按 Ctrl+C 取消...');
    await new Promise(resolve => setTimeout(resolve, 3000));
  }

  const startTime = Date.now();

  try {
    // 1. 加载字典数据
    await loadDictionaryData();

    // 2. 生成工程数据（含子项目、施工人员、历史）
    const projectStats = await seedProjects(scaleConfig.projects, config.years, scaleConfig.subprojectsPerProject);

    // 3. 生成预支记录
    const advanceStats = await seedAdvances(scaleConfig.advances, config.years);

    // 4. 打印统计
    await printStats();

    const totalElapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    log.success(`\n===== 全部完成 =====`);
    log.success(`总耗时: ${totalElapsed}s`);
    log.success(`工程: ${projectStats.completed} | 子项目: ${projectStats.totalSubprojects} | 施工人员: ${projectStats.totalWorkers} | 历史: ${projectStats.totalHistory} | 预支: ${advanceStats.completed}`);
    log.info('\n下一步建议：');
    log.info('  1. 运行 verify-data-consistency.js 校验数据一致性');
    log.info('  2. 运行 test:perf 进行性能压测');
    log.info('  3. 通过 Android 端登录验证页面加载速度');

  } catch (error) {
    log.error(`执行失败: ${error.message}`);
    console.error(error.stack);
    process.exit(1);
  } finally {
    await pool.end();
  }
};

main();
