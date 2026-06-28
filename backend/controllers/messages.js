const pool = require('../config/database');
const Joi = require('joi');
const logger = require('../config/logger');

const messagesController = {
  /**
   * 获取消息列表的参数校验规则
   */
  getMessagesSchema: Joi.object({
    page: Joi.number().integer().min(1).default(1).messages({
      'number.min': '页码必须大于0'
    }),
    size: Joi.number().integer().min(1).max(100).default(20).messages({
      'number.min': '每页数量必须大于0',
      'number.max': '每页数量最多100条'
    }),
    isRead: Joi.boolean().optional().messages({
      'boolean.base': '已读状态必须是布尔值'
    }),
    type: Joi.string().optional().messages({
      'string.base': '消息类型必须是字符串'
    })
  }),

  /**
   * 获取消息列表
   */
  getMessages: async (ctx) => {
    const { page = 1, size = 20, isRead, type } = ctx.query;
    const currentUserId = ctx.state.user.id;

    try {
      const offset = (page - 1) * size;
      let whereClause = 'WHERE m.user_id = $1';
      let params = [currentUserId];
      let paramIndex = 2;

      if (isRead !== undefined) {
        whereClause += ` AND m.is_read = $${paramIndex}`;
        params.push(isRead);
        paramIndex++;
      }

      if (type) {
        whereClause += ` AND m.type = $${paramIndex}`;
        params.push(type);
        paramIndex++;
      }

      const query = `
        SELECT 
          m.id,
          m.user_id,
          m.title,
          m.content,
          m.type,
          m.is_read,
          m.related_type,
          m.related_id,
          TO_CHAR(m.created_at, 'YYYY-MM-DD HH24:MI') as created_at,
          m.created_by,
          COALESCE(u.nickname, '系统') as creator_name
        FROM messages m
        LEFT JOIN users u ON m.created_by = u.id
        ${whereClause}
        ORDER BY m.created_at DESC
        LIMIT $${paramIndex} OFFSET $${paramIndex + 1}
      `;
      params.push(size, offset);

      const result = await pool.query(query, params);

      const countQuery = `
        SELECT COUNT(*) as total
        FROM messages m
        ${whereClause}
      `;
      const countResult = await pool.query(countQuery, params.slice(0, paramIndex - 1));

      ctx.success({
        list: result.rows,
        total: parseInt(countResult.rows[0].total),
        page: parseInt(page),
        size: parseInt(size),
        hasNext: offset + result.rows.length < parseInt(countResult.rows[0].total)
      });
    } catch (error) {
      logger.error('获取消息列表失败:', error);
      ctx.fail(5001, '获取消息列表失败');
    }
  },

  /**
   * 获取未读消息数量
   */
  getUnreadCount: async (ctx) => {
    const currentUserId = ctx.state.user.id;

    try {
      const query = `
        SELECT COUNT(*) as count
        FROM messages
        WHERE user_id = $1 AND is_read = false
      `;
      const result = await pool.query(query, [currentUserId]);

      ctx.success({
        count: parseInt(result.rows[0].count)
      });
    } catch (error) {
      logger.error('获取未读消息数量失败:', error);
      ctx.fail(5001, '获取未读消息数量失败');
    }
  },

  /**
   * 标记消息为已读
   */
  markAsRead: async (ctx) => {
    const { id } = ctx.params;
    const currentUserId = ctx.state.user.id;

    try {
      const query = `
        UPDATE messages
        SET is_read = true
        WHERE id = $1 AND user_id = $2
        RETURNING *
      `;
      const result = await pool.query(query, [id, currentUserId]);

      if (result.rows.length === 0) {
        ctx.fail(1002, '消息不存在或无权操作');
        return;
      }

      ctx.success(result.rows[0]);
    } catch (error) {
      logger.error('标记消息已读失败:', error);
      ctx.fail(5001, '标记消息已读失败');
    }
  },

  /**
   * 批量标记消息为已读
   */
  markAllAsRead: async (ctx) => {
    const currentUserId = ctx.state.user.id;

    try {
      const query = `
        UPDATE messages
        SET is_read = true
        WHERE user_id = $1 AND is_read = false
        RETURNING *
      `;
      const result = await pool.query(query, [currentUserId]);

      ctx.success({
        count: result.rows.length
      });
    } catch (error) {
      logger.error('批量标记消息已读失败:', error);
      ctx.fail(5001, '批量标记消息已读失败');
    }
  },

  /**
   * 删除消息
   */
  deleteMessage: async (ctx) => {
    const { id } = ctx.params;
    const currentUserId = ctx.state.user.id;

    try {
      const query = `
        DELETE FROM messages
        WHERE id = $1 AND user_id = $2
        RETURNING *
      `;
      const result = await pool.query(query, [id, currentUserId]);

      if (result.rows.length === 0) {
        ctx.fail(1002, '消息不存在或无权操作');
        return;
      }

      ctx.success({ message: '删除成功' });
    } catch (error) {
      logger.error('删除消息失败:', error);
      ctx.fail(5001, '删除消息失败');
    }
  },

  /**
   * 创建消息（内部使用）
   */
  createMessage: async (userId, title, content, type, relatedType = null, relatedId = null, createdBy = null) => {
    try {
      const query = `
        INSERT INTO messages (user_id, title, content, type, related_type, related_id, created_by)
        VALUES ($1, $2, $3, $4, $5, $6, $7)
        RETURNING *
      `;
      const result = await pool.query(query, [userId, title, content, type, relatedType, relatedId, createdBy]);
      return result.rows[0];
    } catch (error) {
      logger.error('创建消息失败:', error);
      throw error;
    }
  }
};

module.exports = messagesController;
