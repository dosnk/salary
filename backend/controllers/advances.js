const pool = require('../config/database');
const Joi = require('joi');
const messagesController = require('./messages');
const { isAdmin, isConstructor } = require('../middleware/rbac');
const logger = require('../config/logger');

const advancesController = {
  /**
   * 创建预支记录的参数校验规则
   */
  createAdvanceSchema: Joi.object({
    userId: Joi.number().integer().positive().required().messages({
      'number.positive': '用户ID必须为正整数'
    }),
    advanceAmount: Joi.number().positive().max(100000).required().messages({
      'number.positive': '预支金额必须大于0',
      'number.max': '预支金额不能超过100000元'
    }),
    advanceDate: Joi.date().iso().required().messages({
      'date.format': '预支日期格式不正确'
    }),
    remark: Joi.string().max(500).allow('').messages({
      'string.max': '备注长度不能超过500字符'
    })
  }),

  /**
   * 获取预支记录列表的参数校验规则
   */
  getAdvancesSchema: Joi.object({
    userId: Joi.number().integer().positive().optional().messages({
      'number.positive': '用户ID必须为正整数'
    }),
    page: Joi.number().integer().min(1).default(1).messages({
      'number.min': '页码必须大于0'
    }),
    size: Joi.number().integer().min(1).max(100).default(10).messages({
      'number.min': '每页数量必须大于0',
      'number.max': '每页数量最多100条'
    })
  }),

  /**
   * 获取预支总额的参数校验规则
   */
  getAdvanceTotalSchema: Joi.object({
    userId: Joi.number().integer().positive().required().messages({
      'number.positive': '用户ID必须为正整数'
    })
  }),

  /**
   * 获取当前用户的预支记录的参数校验规则
   */
  getMyAdvancesSchema: Joi.object({
    page: Joi.number().integer().min(1).default(1).messages({
      'number.min': '页码必须大于0'
    }),
    size: Joi.number().integer().min(1).max(100).default(10).messages({
      'number.min': '每页数量必须大于0',
      'number.max': '每页数量最多100条'
    }),
    year: Joi.number().integer().min(2020).max(2100).optional().messages({
      'number.min': '年份必须大于等于2020',
      'number.max': '年份必须小于等于2100'
    }),
    month: Joi.number().integer().min(1).max(12).optional().messages({
      'number.min': '月份必须大于等于1',
      'number.max': '月份必须小于等于12'
    })
  }),
  /**
   * 创建预支记录
   * POST /v1/advances
   */
  createAdvance: async (ctx) => {
    const { userId, advanceAmount, advanceDate, remark } = ctx.request.body;
    const currentUserId = ctx.state.user.id;
    const user = ctx.state.user;

    // V2.0: 权限由路由层requireAdvanceCreate中间件控制

    // 施工员只能给自己预支
    const targetUserId = isConstructor(user) ? currentUserId : userId;

    try {
      // 验证用户是否存在
      const userResult = await pool.query('SELECT id, nickname FROM users WHERE id = $1', [targetUserId]);
      if (userResult.rows.length === 0) {
        ctx.fail(2004, '用户不存在');
        return;
      }

      // 创建预支记录
      const result = await pool.query(
        `INSERT INTO wage_advances (user_id, advance_amount, advance_date, settled, created_by, remark)
         VALUES ($1, $2, $3, $4, $5, $6) RETURNING *`,
        [targetUserId, advanceAmount, advanceDate, false, currentUserId, remark]
      );

      // 发送消息通知用户（管理员操作，显示管理员）
      const advance = result.rows[0];
      const creatorName = isConstructor(user) ? '您自己' : '管理员';
      await messagesController.createMessage(
        targetUserId,
        '您有一笔新的预支记录',
        `${creatorName}为您创建了一笔预支记录，金额：${Number(advanceAmount).toFixed(2)}元，日期：${advanceDate}`,
        'advance_created',
        'advance',
        advance.id,
        currentUserId
      );

      ctx.success(result.rows[0]);
    } catch (error) {
      logger.error('创建预支工资失败:', error);
      ctx.fail(5001, '创建预支工资失败');
    }
  },

  /**
   * 获取用户的预支记录列表
   * GET /v1/advances?userId=xxx&page=1&size=10
   */
  getAdvances: async (ctx) => {
    const { userId, page = 1, size = 10 } = ctx.query;
    const currentUserId = ctx.state.user.id;
    const user = ctx.state.user;

    try {
      const offset = (page - 1) * size;
      let query = '';
      let params = [];

      // 权限检查：只有管理员可以查询所有记录或指定用户的记录
      if (!isAdmin(user)) {
        // 普通用户只能查询自己的记录
        query = `
          SELECT wa.*, u.nickname as user_name, c.nickname as creator_name
          FROM wage_advances wa
          LEFT JOIN users u ON wa.user_id = u.id
          LEFT JOIN users c ON wa.created_by = c.id
          WHERE wa.user_id = $1
          ORDER BY wa.advance_date DESC, wa.created_at DESC
          LIMIT $2 OFFSET $3
        `;
        params = [currentUserId, size, offset];
      } else {
        // 管理员可以查询所有记录或指定用户的记录
        if (userId) {
          query = `
            SELECT wa.*, u.nickname as user_name, c.nickname as creator_name
            FROM wage_advances wa
            LEFT JOIN users u ON wa.user_id = u.id
            LEFT JOIN users c ON wa.created_by = c.id
            WHERE wa.user_id = $1
            ORDER BY wa.advance_date DESC, wa.created_at DESC
            LIMIT $2 OFFSET $3
          `;
          params = [userId, size, offset];
        } else {
          query = `
            SELECT wa.*, u.nickname as user_name, c.nickname as creator_name
            FROM wage_advances wa
            LEFT JOIN users u ON wa.user_id = u.id
            LEFT JOIN users c ON wa.created_by = c.id
            ORDER BY wa.advance_date DESC, wa.created_at DESC
            LIMIT $1 OFFSET $2
          `;
          params = [size, offset];
        }
      }

      const result = await pool.query(query, params);

      // 获取总数
      let countQuery = '';
      let countParams = [];
      if (!isAdmin(user)) {
        countQuery = 'SELECT COUNT(*) as total FROM wage_advances WHERE user_id = $1';
        countParams = [currentUserId];
      } else {
        if (userId) {
          countQuery = 'SELECT COUNT(*) as total FROM wage_advances WHERE user_id = $1';
          countParams = [userId];
        } else {
          countQuery = 'SELECT COUNT(*) as total FROM wage_advances';
        }
      }

      const countResult = await pool.query(countQuery, countParams);
      const total = parseInt(countResult.rows[0].total);

      ctx.success({
        list: result.rows,
        total,
        page: parseInt(page),
        size: parseInt(size),
        hasNext: offset + size < total
      });
    } catch (error) {
      logger.error('获取预支记录失败:', error);
      ctx.fail(5001, '获取预支记录失败');
    }
  },

  /**
   * 获取用户的预支总额（未结算）
   * GET /v1/advances/total?userId=xxx
   */
  getAdvanceTotal: async (ctx) => {
    const { userId } = ctx.query;
    const currentUserId = ctx.state.user.id;
    const user = ctx.state.user;

    try {
      let query = '';
      let params = [];

      // 权限检查：只有管理员可以查询指定用户的总额，普通用户只能查询自己的总额
      if (!isAdmin(user)) {
        query = `
          SELECT COALESCE(SUM(advance_amount), 0) as total
          FROM wage_advances
          WHERE user_id = $1 AND settled = FALSE
        `;
        params = [currentUserId];
      } else {
        if (userId) {
          query = `
            SELECT COALESCE(SUM(advance_amount), 0) as total
            FROM wage_advances
            WHERE user_id = $1 AND settled = FALSE
          `;
          params = [userId];
        } else {
          ctx.fail(1001, '缺少用户ID参数');
          return;
        }
      }

      const result = await pool.query(query, params);
      const total = parseFloat(result.rows[0].total);

      ctx.success({ total });
    } catch (error) {
      logger.error('获取预支总额失败:', error);
      ctx.fail(5001, '获取预支总额失败');
    }
  },

  deleteAdvance: async (ctx) => {
    const { id } = ctx.params;

    // V2.0: 权限由路由层requireAdvanceDelete中间件控制

    try {
      // 检查预支记录是否存在且未结算
      const advanceResult = await pool.query(
        'SELECT * FROM wage_advances WHERE id = $1',
        [id]
      );

      if (advanceResult.rows.length === 0) {
        ctx.fail(1002, '预支记录不存在');
        return;
      }

      const advance = advanceResult.rows[0];

      // 如果已结算，不允许删除
      if (advance.settled) {
        ctx.fail(4002, '已结算的预支记录不能删除');
        return;
      }

      // 删除预支记录
      await pool.query('DELETE FROM wage_advances WHERE id = $1', [id]);

      ctx.success({ message: '删除成功' });
    } catch (error) {
      logger.error('删除预支记录失败:', error);
      ctx.fail(5001, '删除预支记录失败');
    }
  },

  /**
   * 获取当前用户的预支记录
   * GET /v1/advances/my?page=1&size=10&year=2026&month=1
   */
  getMyAdvances: async (ctx) => {
    const { page = 1, size = 10, year, month } = ctx.query;
    const currentUserId = ctx.state.user.id;

    try {
      const offset = (page - 1) * size;

      let query = `
        SELECT wa.*, c.nickname as creator_name
        FROM wage_advances wa
        LEFT JOIN users c ON wa.created_by = c.id
        WHERE wa.user_id = $1
      `;
      let params = [currentUserId];
      let paramIndex = 2;

      // 按年月筛选
      if (year && month) {
        query += ` AND EXTRACT(YEAR FROM wa.advance_date) = $${paramIndex}`;
        params.push(parseInt(year));
        paramIndex++;
        
        query += ` AND EXTRACT(MONTH FROM wa.advance_date) = $${paramIndex}`;
        params.push(parseInt(month));
        paramIndex++;
      }

      query += ` ORDER BY wa.advance_date DESC, wa.created_at DESC
        LIMIT $${paramIndex} OFFSET $${paramIndex + 1}`;
      params.push(parseInt(size), offset);

      const result = await pool.query(query, params);

      // 获取总数
      let countQuery = `
        SELECT COUNT(*) as total
        FROM wage_advances
        WHERE user_id = $1
      `;
      let countParams = [currentUserId];
      let countParamIndex = 2;

      // 按年月筛选
      if (year && month) {
        countQuery += ` AND EXTRACT(YEAR FROM advance_date) = $${countParamIndex}`;
        countParams.push(parseInt(year));
        countParamIndex++;
        
        countQuery += ` AND EXTRACT(MONTH FROM advance_date) = $${countParamIndex}`;
        countParams.push(parseInt(month));
        countParamIndex++;
      }

      const countResult = await pool.query(countQuery, countParams);
      const total = parseInt(countResult.rows[0].total);

      ctx.success({
        list: result.rows,
        total,
        page: parseInt(page),
        size: parseInt(size),
        hasNext: offset + size < total
      });
    } catch (error) {
      logger.error('获取我的预支记录失败:', error);
      ctx.fail(5001, '获取我的预支记录失败');
    }
  },

  /**
   * 获取当前用户的预支总额（未结算）
   * GET /v1/advances/my/total
   */
  getMyAdvanceTotal: async (ctx) => {
    const currentUserId = ctx.state.user.id;

    try {
      const query = `
        SELECT COALESCE(SUM(advance_amount), 0) as total
        FROM wage_advances
        WHERE user_id = $1 AND settled = FALSE
      `;
      const result = await pool.query(query, [currentUserId]);
      const total = parseFloat(result.rows[0].total);

      ctx.success({ total });
    } catch (error) {
      logger.error('获取我的预支总额失败:', error);
      ctx.fail(5001, '获取我的预支总额失败');
    }
  }
};

module.exports = advancesController;