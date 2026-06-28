/**
 * 查询构建器 - 统一封装分页/筛选/排序逻辑
 *
 * 解决问题：
 * 1. projects.js 和 settlements.js 中分页查询代码大量重复
 * 2. count 查询需要重复构建条件
 * 3. 参数索引管理容易出错
 *
 * 使用方式：
 *   const builder = new QueryBuilder(pool);
 *   builder.select('*').from('projects p').where('p.status = ?', 'constructing');
 *   const result = await builder.paginate(1, 10);
 */

const logger = require('../config/logger');

class QueryBuilder {
  /**
   * @param {import('pg').Pool} pool - 数据库连接池
   */
  constructor(pool) {
    this.pool = pool;
    this._select = '*';
    this._from = '';
    this._joins = [];
    this._conditions = [];
    this._params = [];
    this._orderBy = '';
    this._groupBy = '';
  }

  /** 设置查询字段 */
  select(fields) {
    this._select = fields;
    return this;
  }

  /** 设置查询表 */
  from(table) {
    this._from = table;
    return this;
  }

  /** 添加 JOIN */
  join(type, table, on) {
    this._joins.push(`${type} JOIN ${table} ON ${on}`);
    return this;
  }

  /** 添加 LEFT JOIN */
  leftJoin(table, on) {
    return this.join('LEFT', table, on);
  }

  /** 添加 INNER JOIN */
  innerJoin(table, on) {
    return this.join('INNER', table, on);
  }

  /**
   * 添加 WHERE 条件（参数化）
   * @param {string} condition - 条件表达式，如 'p.status = $1' 或 'p.name ILIKE ?'
   * @param {*} value - 参数值
   *
   * 支持两种占位符风格：
   * - '?' 风格：自动替换为 $N 风格
   * - 直接使用 $N 风格：需要手动管理索引（不推荐）
   */
  where(condition, ...values) {
    // 将 ? 占位符替换为 $N 格式
    let paramIndex = this._params.length + 1;
    let processedCondition = condition;
    for (const value of values) {
      processedCondition = processedCondition.replace('?', `$${paramIndex}`);
      this._params.push(value);
      paramIndex++;
    }
    this._conditions.push(processedCondition);
    return this;
  }

  /** 添加 ORDER BY */
  orderBy(clause) {
    this._orderBy = clause;
    return this;
  }

  /** 添加 GROUP BY */
  groupBy(clause) {
    this._groupBy = clause;
    return this;
  }

  /** 构建完整 SQL */
  _buildSQL() {
    let sql = `SELECT ${this._select} FROM ${this._from}`;
    if (this._joins.length > 0) {
      sql += ' ' + this._joins.join(' ');
    }
    if (this._conditions.length > 0) {
      sql += ' WHERE ' + this._conditions.join(' AND ');
    }
    if (this._groupBy) {
      sql += ` GROUP BY ${this._groupBy}`;
    }
    if (this._orderBy) {
      sql += ` ORDER BY ${this._orderBy}`;
    }
    return sql;
  }

  /** 构建 COUNT SQL */
  _buildCountSQL() {
    let sql = `SELECT COUNT(*) as total FROM ${this._from}`;
    if (this._joins.length > 0) {
      sql += ' ' + this._joins.join(' ');
    }
    if (this._conditions.length > 0) {
      sql += ' WHERE ' + this._conditions.join(' AND ');
    }
    return sql;
  }

  /**
   * 执行分页查询
   * @param {number} page - 页码（从1开始）
   * @param {number} size - 每页数量
   * @returns {Promise<{list: Array, total: number, page: number, size: number, hasNext: boolean}>}
   */
  async paginate(page = 1, size = 10) {
    const pageNum = Math.max(1, parseInt(page, 10) || 1);
    const sizeNum = Math.max(1, Math.min(100, parseInt(size, 10) || 10));
    const offset = (pageNum - 1) * sizeNum;

    // 并行查询数据和总数
    const [dataResult, countResult] = await Promise.all([
      this.pool.query(
        `${this._buildSQL()} LIMIT $${this._params.length + 1} OFFSET $${this._params.length + 2}`,
        [...this._params, sizeNum, offset]
      ),
      this.pool.query(this._buildCountSQL(), this._params)
    ]);

    const total = parseInt(countResult.rows[0].total, 10);

    return {
      list: dataResult.rows,
      total,
      page: pageNum,
      size: sizeNum,
      hasNext: pageNum * sizeNum < total
    };
  }

  /**
   * 执行普通查询（不分页）
   * @returns {Promise<Array>}
   */
  async execute() {
    const result = await this.pool.query(this._buildSQL(), this._params);
    return result.rows;
  }

  /**
   * 查询单条记录
   * @returns {Promise<Object|null>}
   */
  async first() {
    const rows = await this.execute();
    return rows.length > 0 ? rows[0] : null;
  }

  /**
   * 快捷方式：构建筛选条件
   * @param {Object} filters - 筛选条件对象
   * @param {Object} fieldMap - 字段映射 { 参数名: { column, operator, type } }
   *
   * 示例:
   *   builder.applyFilters(
   *     { keyword: '测试', status: 'constructing' },
   *     {
   *       keyword: { column: 'p.name', operator: 'ILIKE', transform: v => `%${v}%` },
   *       status: { column: 'p.status', operator: '=' }
   *     }
   *   );
   */
  applyFilters(filters, fieldMap) {
    if (!filters || !fieldMap) return this;

    for (const [key, config] of Object.entries(fieldMap)) {
      const value = filters[key];
      if (value === undefined || value === null || value === '') continue;

      const column = config.column || key;
      const operator = config.operator || '=';
      const transform = config.transform || (v => v);
      const processedValue = transform(value);

      if (operator.toUpperCase() === 'ILIKE' || operator.toUpperCase() === 'LIKE') {
        this.where(`${column} ${operator} ?`, processedValue);
      } else {
        this.where(`${column} ${operator} ?`, processedValue);
      }
    }

    return this;
  }
}

module.exports = QueryBuilder;
