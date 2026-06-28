/**
 * AI数据权限过滤器
 *
 * 自动注入权限过滤条件，确保AI查询的数据符合用户角色权限
 * - admin: 查看全部数据
 * - constructor: 只查看自己参与的数据
 * - documenter: 查看全部数据(只读)
 */

/**
 * 构建权限过滤SQL片段
 * @param {object} user - 用户信息 {id, role}
 * @param {string} tableAlias - 表别名
 * @param {string} filterType - 过滤类型 'project' | 'settlement' | 'advance'
 * @returns {{ sql: string, params: Array, paramIndex: number }}
 */
const buildPermissionFilter = (user, tableAlias, filterType) => {
  const { id: userId, role } = user;
  const params = [];
  let sql = '';
  let paramIndex = 1;

  if (role === 'constructor') {
    switch (filterType) {
      case 'project':
        sql = ` AND ${tableAlias}.id IN (SELECT project_id FROM project_workers WHERE user_id = $${paramIndex})`;
        params.push(userId);
        paramIndex++;
        break;
      case 'settlement':
        sql = ` AND ${tableAlias}.user_id = $${paramIndex}`;
        params.push(userId);
        paramIndex++;
        break;
      case 'advance':
        sql = ` AND ${tableAlias}.user_id = $${paramIndex}`;
        params.push(userId);
        paramIndex++;
        break;
    }
  }
  // admin和documenter无需额外过滤

  return { sql, params, paramIndex };
};

/**
 * 检查用户是否有权访问指定工程
 * @param {object} user - 用户信息
 * @param {number} projectId - 工程ID
 * @returns {Promise<boolean>}
 */
const checkProjectAccess = async (user, projectId) => {
  const pool = require('../../../config/database');

  if (user.role === 'admin' || user.role === 'documenter') {
    return true;
  }

  const result = await pool.query(
    'SELECT 1 FROM project_workers WHERE project_id = $1 AND user_id = $2',
    [projectId, user.id]
  );
  return result.rows.length > 0;
};

module.exports = { buildPermissionFilter, checkProjectAccess };
