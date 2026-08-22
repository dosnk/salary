/**
 * 结算单6分配明细补录脚本（一次性修复工具）
 *
 * 背景：
 *   结算单6(20260325_9_01) 总额1396.80元，旧库导出文件中无任何分配明细
 *   （旧系统2026-03-25生成该结算单时写入故障：settled_by字段粘连+明细未落库）。
 *   经人工决策：该笔金额归属用户ID 7。
 *
 * 处理逻辑：
 *   1. 前置校验：结算单6存在、总额=1396.80、当前无分配明细（幂等保护）
 *   2. 选择挂靠子项目：工程90下优先选"已完工且未结算"的子项目；
 *      若全部已结算则选任意子项目（仅作外键锚点，不影响其已结算状态）
 *   3. 事务内插入 wage_distributions（settlement_id=6, user_id=7, amount=1396.80）
 *      created_at 使用结算单 settled_at（2026-03-25），保证月度统计口径正确
 *   4. 刷新物化视图 mv_project_user_settlement_status
 *   5. 复核：结算单6总额 = 分配明细之和
 *
 * 影响说明：
 *   - 用户7的"已结算收入"统计将增加1396.80元（2026年3月口径）
 *   - 若挂靠的子项目原为"未结算"，补录后该子项目视为已结算（不可再单独结算）
 *
 * 用法：
 *   docker compose exec app node scripts/fix-settlement-6.js            # 执行补录
 *   docker compose exec app node scripts/fix-settlement-6.js --dry-run   # 仅预览，不执行
 */

const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });
const { Pool } = require('pg');

// ===================== 配置 =====================
const DRY_RUN = process.argv.includes('--dry-run');
// 补录参数（人工决策结果）
const SETTLEMENT_ID = 6;          // 目标结算单
const TARGET_USER_ID = 7;         // 金额归属用户
const AMOUNT = '1396.80';         // 补录金额（字符串传参避免浮点损失）

// ===================== 日志工具 =====================
const log = {
  info: (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  success: (msg) => console.log(`[${new Date().toISOString()}] [SUCCESS] ${msg}`),
  warn: (msg) => console.warn(`[${new Date().toISOString()}] [WARN] ${msg}`),
  error: (msg) => console.error(`[${new Date().toISOString()}] [ERROR] ${msg}`)
};

// ===================== 数据库连接 =====================
let pool;
try {
  pool = require('../config/database');
} catch (e) {
  pool = new Pool({
    user: process.env.DB_USER || 'postgres',
    host: process.env.DB_HOST || 'localhost',
    database: process.env.DB_NAME || 'salary_system',
    password: process.env.DB_PASSWORD,
    port: parseInt(process.env.DB_PORT, 10) || 5432,
    max: 5
  });
}

/**
 * 补录主函数
 */
async function fix() {
  let client;
  try {
    client = await pool.connect();

    log.info('==========================================');
    log.info('  结算单6分配明细补录（1396.80元 → 用户7）');
    log.info(`  模式: ${DRY_RUN ? 'Dry Run（仅预览，不执行）' : '执行补录'}`);
    log.info('==========================================');

    // ========== 步骤1：前置校验 ==========
    log.info('');
    log.info('[步骤 1] 前置校验...');
    const settleResult = await client.query(`
      SELECT ws.id, ws.settlement_no, ws.user_id, ws.project_id, ws.project_ids,
             ws.total_amount, ws.confirmed, ws.paid, ws.settled_at,
             COALESCE((SELECT COUNT(*) FROM wage_distributions wd WHERE wd.settlement_id = ws.id), 0) AS dist_count
      FROM wage_settlements ws
      WHERE ws.id = $1
    `, [SETTLEMENT_ID]);

    if (settleResult.rows.length === 0) {
      log.error(`结算单${SETTLEMENT_ID}不存在，退出`);
      process.exit(1);
    }

    const settle = settleResult.rows[0];
    log.info(`  结算单: #${settle.id} ${settle.settlement_no}`);
    log.info(`  结算人(user_id): ${settle.user_id} | 工程ID: ${settle.project_id} | project_ids: ${JSON.stringify(settle.project_ids)}`);
    log.info(`  总额: ${settle.total_amount} | 已确认: ${settle.confirmed} | 已支付: ${settle.paid}`);
    log.info(`  现有分配明细: ${settle.dist_count} 条`);

    // 幂等保护：已有明细时不允许补录（避免重复执行）
    if (parseInt(settle.dist_count) > 0) {
      log.error(`结算单${SETTLEMENT_ID}已有 ${settle.dist_count} 条分配明细，拒绝补录（幂等保护，防重复执行）`);
      process.exit(1);
    }

    // 金额核对
    if (Math.abs(parseFloat(settle.total_amount) - parseFloat(AMOUNT)) > 0.005) {
      log.error(`结算单总额(${settle.total_amount})与补录金额(${AMOUNT})不一致，请人工确认`);
      process.exit(1);
    }

    // 目标用户存在性校验
    const userResult = await client.query(
      'SELECT id, nickname, role FROM users WHERE id = $1',
      [TARGET_USER_ID]
    );
    if (userResult.rows.length === 0) {
      log.error(`用户${TARGET_USER_ID}不存在，退出`);
      process.exit(1);
    }
    const user = userResult.rows[0];
    log.info(`  归属用户: #${user.id} ${user.nickname} (${user.role})`);

    // ========== 步骤2：选择/创建挂靠子项目 ==========
    log.info('');
    log.info('[步骤 2] 选择挂靠子项目...');
    const projectId = settle.project_id;
    if (!projectId) {
      log.error('结算单未关联工程(project_id为空)，无法选择挂靠子项目，需人工处理');
      process.exit(1);
    }

    // 查询工程当前状态（供人工确认）
    const projResult = await client.query(`
      SELECT p.id, p.name, p.status, p.total_amount
      FROM projects p WHERE p.id = $1
    `, [projectId]);
    if (projResult.rows.length === 0) {
      log.error(`工程${projectId}不存在，退出`);
      process.exit(1);
    }
    const proj = projResult.rows[0];
    log.info(`  关联工程: #${proj.id} ${proj.name} (状态=${proj.status}, 总额=${proj.total_amount})`);

    // 优先：已完工且未结算的子项目
    const unsettledCompleted = await client.query(`
      SELECT sp.id, sp.space_type_id, sp.construction_plan_id, sp.status, sp.amount
      FROM subprojects sp
      WHERE sp.project_id = $1
        AND sp.status = 'completed'
        AND sp.id NOT IN (SELECT subproject_id FROM wage_distributions WHERE subproject_id IS NOT NULL)
      ORDER BY sp.id
      LIMIT 1
    `, [projectId]);

    // 次选：该工程任意子项目
    const anySub = await client.query(`
      SELECT sp.id, sp.space_type_id, sp.construction_plan_id, sp.status, sp.amount
      FROM subprojects sp
      WHERE sp.project_id = $1
      ORDER BY sp.id
      LIMIT 1
    `, [projectId]);

    let anchorSub;
    let anchorMode;
    let needCreateAnchor = false;

    if (unsettledCompleted.rows.length > 0) {
      anchorSub = unsettledCompleted.rows[0];
      anchorMode = '已完工且未结算（补录后该子项目视为已结算）';
    } else if (anySub.rows.length > 0) {
      anchorSub = anySub.rows[0];
      anchorMode = '工程下首个子项目（仅作外键锚点）';
    } else {
      // 工程下无任何子项目（旧库中该工程的子项目已被删除，但结算单仍在）
      // wage_distributions.subproject_id 为 NOT NULL 外键，必须创建补录锚点子项目
      needCreateAnchor = true;
      anchorMode = '工程下无子项目，需创建补录锚点子项目';
      log.warn(`  工程${projectId}下无任何子项目（旧库子项目已被删除，但结算单仍在）`);
      log.warn('  将创建补录锚点子项目：status=completed（防重复结算），amount=1396.80，并同步工程总额');
    }

    if (!needCreateAnchor) {
      log.info(`  挂靠子项目: #${anchorSub.id} (空间类型ID=${anchorSub.space_type_id}, 状态=${anchorSub.status}, 金额=${anchorSub.amount})`);
      log.info(`  选择策略: ${anchorMode}`);
    }

    // ========== 步骤3：执行补录 ==========
    // created_at 对齐结算时间，月度统计口径正确；结算时间为空时用当前时间兜底
    const createdAt = settle.settled_at || new Date();

    // 锚点子项目创建参数（仅 needCreateAnchor 时使用）
    let anchorCreateInfo = null;
    if (needCreateAnchor) {
      // 查询有效字典（取ID最小的作为占位，remark 标记补录来源）
      const stResult = await client.query('SELECT id, name FROM space_types ORDER BY id LIMIT 1');
      const planResult = await client.query('SELECT id, name, unit FROM construction_plans ORDER BY id LIMIT 1');
      if (stResult.rows.length === 0 || planResult.rows.length === 0) {
        log.error('字典数据缺失（space_types/construction_plans 为空），无法创建锚点子项目，退出');
        process.exit(1);
      }
      anchorCreateInfo = {
        spaceTypeId: stResult.rows[0].id,
        spaceTypeName: stResult.rows[0].name,
        planId: planResult.rows[0].id,
        planName: planResult.rows[0].name,
        remark: `旧库结算明细缺失补录锚点（结算单${settle.settlement_no}），原始子项目已在旧库删除`
      };
      log.info(`  锚点字典: 空间类型=${anchorCreateInfo.spaceTypeName}(#${anchorCreateInfo.spaceTypeId}), 施工方案=${anchorCreateInfo.planName}(#${anchorCreateInfo.planId})`);
    }

    log.info('');
    if (DRY_RUN) {
      log.info('[Dry Run] 预览待执行的操作：');
      if (needCreateAnchor) {
        log.info(`  1. INSERT INTO subprojects (工程${projectId}, quantity=1, amount=${AMOUNT}, status='completed', remark='旧库结算明细缺失补录锚点...')`);
        log.info(`  2. UPDATE projects SET total_amount = total_amount + ${AMOUNT} (工程${projectId}总额同步)`);
        log.info(`  3. INSERT INTO wage_distributions (新锚点子项目ID, 用户${TARGET_USER_ID}, 工日=1, 数量=0, 金额=${AMOUNT}, 结算单${SETTLEMENT_ID})`);
      } else {
        log.info(`  1. INSERT INTO wage_distributions (子项目${anchorSub.id}, 用户${TARGET_USER_ID}, 工日=1, 数量=0, 金额=${AMOUNT}, 结算单${SETTLEMENT_ID}, created_at='${createdAt.toISOString()}')`);
      }
      log.info('  4. REFRESH MATERIALIZED VIEW mv_project_user_settlement_status');
      log.info('[Dry Run] 未执行更新。去掉 --dry-run 参数执行补录。');
      return;
    }

    log.info('[步骤 3] 事务内执行补录...');
    await client.query('BEGIN');
    try {
      let anchorSubId;
      if (needCreateAnchor) {
        // 创建补录锚点子项目：
        // - status='completed'：防止出现在可结算子项目列表被重复结算
        // - quantity=1：占位（旧库明细丢失，无从得知真实数量）
        // - amount=1396.80：还原历史业务量，工程总额同步更新
        // - created_at 对齐结算时间，保证月度统计口径
        const insertSub = await client.query(`
          INSERT INTO subprojects (project_id, space_type_id, construction_plan_id, length, width, quantity, amount, status, remark, created_at, updated_at)
          VALUES ($1, $2, $3, NULL, NULL, 1, $4, 'completed', $5, $6, $6)
          RETURNING id
        `, [projectId, anchorCreateInfo.spaceTypeId, anchorCreateInfo.planId, AMOUNT, anchorCreateInfo.remark, createdAt]);
        anchorSubId = insertSub.rows[0].id;
        log.success(`补录锚点子项目已创建: #${anchorSubId}`);

        // 同步工程总额（保持"工程总额=子项目之和"校验通过）
        await client.query(`
          UPDATE projects
          SET total_amount = (SELECT COALESCE(SUM(amount), 0) FROM subprojects WHERE project_id = $1),
              updated_at = CURRENT_TIMESTAMP
          WHERE id = $1
        `, [projectId]);
        log.success(`工程${projectId}总额已同步`);
      } else {
        anchorSubId = anchorSub.id;
      }

      await client.query(`
        INSERT INTO wage_distributions (subproject_id, user_id, workdays, quantity, amount, settlement_id, created_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7)
      `, [
        anchorSubId,
        TARGET_USER_ID,
        1,                          // 工日默认1（旧明细丢失，无从得知真实工日）
        0,                          // 数量0（无从得知真实数量）
        AMOUNT,                     // 金额1396.80（字符串传参保证精度）
        SETTLEMENT_ID,
        createdAt                   // created_at 对齐结算时间（2026-03-25），月度统计口径正确
      ]);
      await client.query('COMMIT');
      log.success('分配明细已插入');
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    }

    // ========== 步骤4：刷新物化视图 ==========
    log.info('');
    log.info('[步骤 4] 刷新物化视图...');
    try {
      await client.query('REFRESH MATERIALIZED VIEW CONCURRENTLY mv_project_user_settlement_status');
      log.success('物化视图已刷新');
    } catch (e) {
      log.warn(`并发刷新失败，降级为非并发刷新（短暂锁表）: ${e.message}`);
      await client.query('REFRESH MATERIALIZED VIEW mv_project_user_settlement_status');
      log.success('物化视图已刷新（非并发模式）');
    }

    // ========== 步骤5：复核 ==========
    log.info('');
    log.info('[步骤 5] 复核补录结果...');
    const verifyResult = await client.query(`
      SELECT ws.total_amount,
             COALESCE(SUM(wd.amount), 0) AS distribution_total,
             ws.total_amount - COALESCE(SUM(wd.amount), 0) AS diff
      FROM wage_settlements ws
      LEFT JOIN wage_distributions wd ON wd.settlement_id = ws.id
      WHERE ws.id = $1
      GROUP BY ws.id, ws.total_amount
    `, [SETTLEMENT_ID]);

    const v = verifyResult.rows[0];
    log.info(`  结算单总额: ${v.total_amount}`);
    log.info(`  分配明细之和: ${v.distribution_total}`);
    if (Math.abs(parseFloat(v.diff)) <= 0.005) {
      log.success(`✅ 复核1通过：结算单${SETTLEMENT_ID}总额与分配明细之和一致（差异=${v.diff}）`);
    } else {
      log.error(`⚠️ 复核1失败：差异=${v.diff}，请人工检查`);
      process.exit(1);
    }

    // 复核2：若创建了锚点子项目，验证工程总额=子项目之和（校验1不被破坏）
    if (needCreateAnchor) {
      const projVerify = await client.query(`
        SELECT p.total_amount,
               COALESCE((SELECT SUM(sp.amount) FROM subprojects sp WHERE sp.project_id = p.id), 0) AS sub_total
        FROM projects p
        WHERE p.id = $1
      `, [projectId]);
      const pv = projVerify.rows[0];
      log.info(`  工程${projectId}总额: ${pv.total_amount} | 子项目之和: ${pv.sub_total}`);
      if (Math.abs(parseFloat(pv.total_amount) - parseFloat(pv.sub_total)) <= 0.005) {
        log.success(`✅ 复核2通过：工程${projectId}总额与子项目之和一致`);
      } else {
        log.error(`⚠️ 复核2失败：工程总额与子项目之和差异=${parseFloat(pv.total_amount) - parseFloat(pv.sub_total)}，请人工检查`);
        process.exit(1);
      }
    }

    log.info('');
    log.info('==========================================');
    log.success('  补录完成！');
    log.info('==========================================');
    log.info('后续影响：');
    log.info(`  1. 用户${TARGET_USER_ID}(${user.nickname})的已结算收入统计增加 ${AMOUNT} 元（2026年3月口径）`);
    if (needCreateAnchor) {
      log.info(`  2. 工程${projectId}(${proj.name})新增一条补录锚点子项目（金额${AMOUNT}，工程总额同步更新，App工程详情可见备注说明）`);
    }
    log.info('  3. 数据一致性校验的"校验2：结算单总额一致性"将转为通过');

  } catch (err) {
    log.error(`补录失败: ${err.message}`);
    if (err.stack) {
      log.error(err.stack);
    }
    process.exit(1);
  } finally {
    if (client) {
      client.release();
    }
    await pool.end();
  }
}

// 执行
fix();
