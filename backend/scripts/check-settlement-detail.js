/**
 * 结算单分配明细核查脚本（纯只读，不连数据库）
 *
 * 用途：解析旧库 pg_dump 导出文件，核查每个结算单的分配明细情况，
 *       确认"有总额但无分配明细"的结算单是旧库数据本身缺失，
 *       还是迁移过程丢失。
 *
 * 背景：数据一致性校验发现结算单6(20260325_9_01) total=1396.8 分配之和=0，
 *       需要区分两种情况：
 *         1. 旧库导出文件中就没有该结算单的分配明细 → 旧库数据本身缺失
 *         2. 旧库导出文件中有分配明细，但迁移后丢失 → 迁移脚本问题
 *
 * 用法：
 *   docker compose exec app node scripts/check-settlement-detail.js                    # 默认路径
 *   docker compose exec app node scripts/check-settlement-detail.js /path/to/dump.sql  # 指定文件
 *
 * 输出：
 *   1. 两张表的原始数据统计（wage_settlements / wage_distributions）
 *   2. 每个结算单的总额、明细数、明细之和、差额
 *   3. 异常结算单清单（无明细 / 金额不符）
 *   4. 结算单6的原始数据行内容（供人工回旧库核对）
 */

const path = require('path');
const fs = require('fs');

// ===================== 日志工具 =====================
const log = {
  info: (msg) => console.log(`[INFO] ${msg}`),
  success: (msg) => console.log(`[✓ PASS] ${msg}`),
  error: (msg) => console.error(`[✗ ERROR] ${msg}`),
  warn: (msg) => console.warn(`[! WARN] ${msg}`)
};

// ===================== 配置 =====================
const DUMP_FILE = process.argv.slice(2).find(a => !a.startsWith('-')) || '/app/scripts/old_data_dump.sql';
// 金额容差：旧库浮点运算误差一般在 0.01-2 元级别，超过该值才标记异常
const TOLERANCE = 0.01;

// ===================== COPY 格式解析（与 migrate-from-old.js 逻辑一致） =====================

/**
 * 解析 COPY 数据行为值数组（tab 分隔，\N 为 NULL）
 */
function parseCopyDataLine(line) {
  const values = [];
  let current = '';
  let i = 0;

  while (i < line.length) {
    const char = line[i];
    if (char === '\t') {
      values.push(current === '\u0000' ? null : current);
      current = '';
      i++;
    } else if (char === '\\' && i + 1 < line.length) {
      const next = line[i + 1];
      if (next === 'N') {
        current += '\u0000'; // 临时标记 NULL
        i += 2;
      } else if (next === 't') {
        current += '\t';
        i += 2;
      } else if (next === 'n') {
        current += '\n';
        i += 2;
      } else if (next === 'r') {
        current += '\r';
        i += 2;
      } else if (next === '\\') {
        current += '\\';
        i += 2;
      } else {
        current += char;
        i++;
      }
    } else {
      current += char;
      i++;
    }
  }
  values.push(current === '\u0000' ? null : current);
  return values;
}

/**
 * 解析 COPY 语句头，提取表名和列名
 */
function parseCopyStatement(copyStmt) {
  const tableMatch = copyStmt.match(/COPY\s+(?:public\.)?(\w+)/i);
  const tableName = tableMatch ? tableMatch[1] : null;
  const columnsMatch = copyStmt.match(/\(([^)]+)\)/);
  let columns = null;
  if (columnsMatch) {
    columns = columnsMatch[1].split(',').map(c => c.trim());
  }
  return { tableName, columns };
}

// ===================== INSERT 格式解析（与 migrate-from-old.js 逻辑一致） =====================

/**
 * 解析 INSERT INTO 语句，提取表名、列名和值组
 */
function parseInsertStatement(sql) {
  const tableMatch = sql.match(/INSERT\s+INTO\s+(?:public\.)?(\w+)/i);
  const tableName = tableMatch ? tableMatch[1] : null;

  let columns = null;
  const columnsMatch = sql.match(/INSERT\s+INTO\s+(?:public\.)?\w+\s*\(([^)]+)\)\s*VALUES/i);
  if (columnsMatch) {
    columns = columnsMatch[1].split(',').map(c => c.trim());
  }

  const valuesMatch = sql.match(/VALUES\s+(.+?);?\s*$/i);
  if (!valuesMatch) {
    return { tableName, columns, valueGroups: null };
  }

  const valuesPart = valuesMatch[1].trim();
  const valueGroups = [];
  let currentGroup = [];
  let current = '';
  let inString = false;
  let i = 0;

  while (i < valuesPart.length) {
    const char = valuesPart[i];
    if (!inString) {
      if (char === "'") {
        inString = true;
        current += char;
        i++;
      } else if (char === '(') {
        current = '';
        i++;
      } else if (char === ')') {
        currentGroup.push(parseSqlValue(current.trim()));
        current = '';
        valueGroups.push(currentGroup);
        currentGroup = [];
        i++;
      } else if (char === ',') {
        if (currentGroup.length > 0 || current.trim() !== '') {
          currentGroup.push(parseSqlValue(current.trim()));
          current = '';
        }
        i++;
      } else {
        current += char;
        i++;
      }
    } else {
      if (char === "'" && valuesPart[i + 1] === "'") {
        current += "''";
        i += 2;
      } else if (char === "'") {
        inString = false;
        current += char;
        i++;
      } else {
        current += char;
        i++;
      }
    }
  }

  return { tableName, columns, valueGroups };
}

/**
 * 解析单个 SQL 值字面量（数字保留字符串避免精度损失）
 */
function parseSqlValue(raw) {
  if (raw === '' || raw.toUpperCase() === 'NULL') return null;
  if (raw.startsWith("'") && raw.endsWith("'")) {
    return raw.slice(1, -1).replace(/''/g, "'");
  }
  if (raw.toUpperCase() === 'TRUE') return true;
  if (raw.toUpperCase() === 'FALSE') return false;
  return raw;
}

// ===================== 目标表数据提取 =====================

/**
 * 从 dump 内容中提取指定表的数据行（列名→值的对象数组）
 * 同时支持 COPY 和 INSERT 两种格式
 *
 * @param {string} content - dump 文件内容
 * @param {string} targetTable - 目标表名
 * @param {Array} rawLines - 输出参数：原始数据行文本（供人工核对）
 * @returns {Array<Object>} 行对象数组
 */
function extractTableData(content, targetTable, rawLines) {
  const rows = [];
  const lines = content.split('\n');
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // 跳过空行和注释
    if (!line.trim() || line.trim().startsWith('--')) {
      i++;
      continue;
    }

    // COPY 格式
    if (/^COPY\s+/i.test(line.trim())) {
      const { tableName, columns } = parseCopyStatement(line);
      if (tableName === targetTable) {
        i++;
        while (i < lines.length && lines[i] !== '\\.') {
          const values = parseCopyDataLine(lines[i]);
          const row = {};
          // columns 为 null 时无法映射列名，跳过（实际 pg_dump 始终带列名）
          if (columns) {
            columns.forEach((col, idx) => { row[col] = values[idx]; });
          }
          rows.push(row);
          rawLines.push(lines[i]);
          i++;
        }
      }
      i++;
      continue;
    }

    // INSERT 格式
    if (/^INSERT\s+INTO/i.test(line.trim())) {
      let fullStatement = line;
      while (i + 1 < lines.length && !fullStatement.trim().endsWith(';')) {
        i++;
        fullStatement += '\n' + lines[i];
      }
      const parsed = parseInsertStatement(fullStatement);
      if (parsed.tableName === targetTable && parsed.valueGroups && parsed.valueGroups.length > 0) {
        for (const values of parsed.valueGroups) {
          const row = {};
          if (parsed.columns) {
            parsed.columns.forEach((col, idx) => { row[col] = values[idx]; });
          }
          rows.push(row);
          rawLines.push(fullStatement.substring(0, 500));
        }
      }
      i++;
      continue;
    }

    i++;
  }

  return rows;
}

// ===================== 主流程 =====================

/**
 * 核查主函数
 */
function check() {
  log.info('==========================================');
  log.info('  结算单分配明细核查（只读 dump 文件，不连数据库）');
  log.info(`  文件: ${DUMP_FILE}`);
  log.info('==========================================');

  // 1. 前置检查
  if (!fs.existsSync(DUMP_FILE)) {
    log.error(`导出文件不存在: ${DUMP_FILE}`);
    log.error('请确认 dump 文件路径，或通过参数指定: node scripts/check-settlement-detail.js /path/to/dump.sql');
    process.exit(1);
  }

  const fileSize = fs.statSync(DUMP_FILE).size;
  log.info(`文件大小: ${(fileSize / 1024 / 1024).toFixed(2)} MB`);

  const content = fs.readFileSync(DUMP_FILE, 'utf8');

  // 2. 提取两张表数据
  const settlementsRaw = [];
  const distributionsRaw = [];
  const settlements = extractTableData(content, 'wage_settlements', settlementsRaw);
  const distributions = extractTableData(content, 'wage_distributions', distributionsRaw);

  log.info('');
  log.info(`[数据统计]`);
  log.info(`  wage_settlements     (结算单): ${settlements.length} 条`);
  log.info(`  wage_distributions   (分配明细): ${distributions.length} 条`);

  if (settlements.length === 0) {
    log.warn('未从 dump 文件中解析到结算单数据，请检查文件格式');
    process.exit(1);
  }

  // 3. 列名自适应（不同版本表结构可能不同）
  const settleCols = Object.keys(settlements[0]);
  log.info('');
  log.info(`[表结构] wage_settlements 列: ${settleCols.join(', ')}`);
  if (distributions.length > 0) {
    log.info(`[表结构] wage_distributions 列: ${Object.keys(distributions[0]).join(', ')}`);
  }

  // 自适应查找关键列名（兼容 id/settlement_no/total_amount 等命名）
  const idCol = settleCols.find(c => c === 'id') || 'id';
  const noCol = settleCols.find(c => c.includes('settlement_no') || c.includes('no')) || null;
  const totalCol = settleCols.find(c => c.includes('total_amount') || c.includes('total')) || null;
  const distSettleIdCol = distributions.length > 0
    ? Object.keys(distributions[0]).find(c => c.includes('settlement_id')) : null;
  const distAmountCol = distributions.length > 0
    ? Object.keys(distributions[0]).find(c => c.includes('amount')) : null;

  // 4. 按结算单聚合分配明细
  const distBySettlement = new Map(); // settlementId -> { count, sum }
  for (const d of distributions) {
    const sid = d[distSettleIdCol];
    if (sid === null || sid === undefined) continue;
    const amount = parseFloat(d[distAmountCol] || 0);
    const entry = distBySettlement.get(String(sid)) || { count: 0, sum: 0 };
    entry.count++;
    entry.sum += amount;
    distBySettlement.set(String(sid), entry);
  }

  // 5. 逐结算单核查
  const anomalies = [];       // 异常结算单
  const missingDetail = [];   // 无明细结算单
  console.log('');
  console.log('  ┌─────────────────────────────────────────────────────────────────────────────┐');
  console.log('  │ 逐结算单核查（总额 vs 分配明细之和）                                        │');
  console.log('  ├──────┬──────────────────┬────────┬──────────────┬──────────────┬─────────┤');
  console.log('  │ ID   │ 结算单号          │ 总额    │ 明细数        │ 明细之和      │ 差额    │');
  console.log('  ├──────┼──────────────────┼────────┼──────────────┼──────────────┼─────────┤');

  settlements.forEach(s => {
    const sid = String(s[idCol]);
    const no = noCol ? (s[noCol] || '-') : '-';
    const total = parseFloat(s[totalCol] || 0);
    const dist = distBySettlement.get(sid) || { count: 0, sum: 0 };
    const diff = total - dist.sum;

    const noStr = String(no).substring(0, 16).padEnd(16);
    const totalStr = total.toFixed(2).padStart(10);
    const cntStr = String(dist.count).padStart(12);
    const sumStr = dist.sum.toFixed(2).padStart(12);
    const diffStr = (diff >= 0 ? '' : '') + diff.toFixed(2).padStart(9);

    // 标记异常
    let isAnomaly = false;
    if (dist.count === 0 && total > 0) {
      missingDetail.push({ id: sid, no, total });
      isAnomaly = true;
    } else if (Math.abs(diff) > TOLERANCE) {
      anomalies.push({ id: sid, no, total, count: dist.count, sum: dist.sum, diff });
      isAnomaly = true;
    }
    const mark = isAnomaly ? ' ←' : '  ';
    console.log(`  │ ${String(sid).padEnd(4)} │ ${noStr} │${totalStr} │${cntStr} │${sumStr} │${diffStr}│${mark}`);
  });

  console.log('  └──────┴──────────────────┴────────┴──────────────┴──────────────┴─────────┘');

  // 6. 孤儿明细核查（settlement_id 指向不存在的结算单）
  const settleIds = new Set(settlements.map(s => String(s[idCol])));
  const orphans = distributions.filter(d => {
    const sid = d[distSettleIdCol];
    return sid !== null && sid !== undefined && !settleIds.has(String(sid));
  });

  // 7. 汇总结论
  log.info('');
  log.info('==========================================');
  log.info('  核查结论');
  log.info('==========================================');

  if (missingDetail.length === 0 && anomalies.length === 0 && orphans.length === 0) {
    log.success('✅ dump 文件中所有结算单均有分配明细且金额一致，无孤儿明细');
    log.success('   若迁移后校验仍发现"分配之和=0"，则为迁移过程丢失，需排查迁移脚本');
  } else {
    if (missingDetail.length > 0) {
      log.warn(`⚠️  发现 ${missingDetail.length} 个"有总额但无分配明细"的结算单（旧库数据本身缺失）：`);
      missingDetail.forEach(m => {
        log.warn(`     结算单${m.id}(${m.no}) 总额=${m.total.toFixed(2)} 明细=0条`);
      });
      log.warn('   → 这些结算单的分配明细在旧库导出文件中就不存在，');
      log.warn('     迁移后"分配之和=0"属旧库遗留问题，非迁移丢失。');
      log.warn('   → 建议回旧系统界面人工核对，必要时手工补录分配明细。');
    }
    if (anomalies.length > 0) {
      log.warn(`⚠️  发现 ${anomalies.length} 个"总额与明细之和不符"的结算单：`);
      anomalies.forEach(a => {
        log.warn(`     结算单${a.id}(${a.no}) 总额=${a.total.toFixed(2)} 明细${a.count}条之和=${a.sum.toFixed(2)} 差额=${a.diff.toFixed(2)}`);
      });
    }
    if (orphans.length > 0) {
      log.warn(`⚠️  发现 ${orphans.length} 条孤儿分配明细（settlement_id 无对应结算单）：`);
      orphans.slice(0, 10).forEach(o => {
        log.warn(`     settlement_id=${o[distSettleIdCol]} amount=${o[distAmountCol]}`);
      });
    }
  }

  // 8. 输出无明细结算单的原始数据行（供人工回旧库核对）
  if (missingDetail.length > 0) {
    log.info('');
    log.info('==========================================');
    log.info('  无明细结算单的原始数据行（dump 文件原文）');
    log.info('==========================================');
    const missingIds = new Set(missingDetail.map(m => m.id));
    settlements.forEach((s, idx) => {
      if (missingIds.has(String(s[idCol]))) {
        log.info(`wage_settlements #${s[idCol]}:`);
        log.info(`  ${settlementsRaw[idx]}`);
      }
    });
    // 同时列出该结算单相关的分配明细原始行（预期应无，双重确认）
    log.info('');
    log.info('  以上结算单在 wage_distributions 中的相关原始数据行（应为空）：');
    let foundAny = false;
    distributions.forEach((d, idx) => {
      if (missingIds.has(String(d[distSettleIdCol]))) {
        foundAny = true;
        log.info(`  ${distributionsRaw[idx]}`);
      }
    });
    if (!foundAny) {
      log.info('  （确认无任何分配明细数据行）');
    }
  }

  log.info('');
  log.info('核查完成（纯只读，未修改任何数据）');
}

// 执行
check();
