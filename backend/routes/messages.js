const Router = require('koa-router');
const messagesController = require('../controllers/messages');
const auth = require('../middleware/auth');
const validation = require('../middleware/validation');

const router = new Router({
  prefix: '/v1/messages'
});

/**
 * @swagger
 * /v1/messages:
 *   get:
 *     summary: 获取消息列表
 *     description: 获取当前登录用户的消息列表，支持分页和筛选
 *     tags: [Messages]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: query
 *         name: page
 *         schema:
 *           type: integer
 *           default: 1
 *           minimum: 1
 *         description: 页码
 *         example: 1
 *       - in: query
 *         name: size
 *         schema:
 *           type: integer
 *           default: 20
 *           minimum: 1
 *           maximum: 100
 *         description: 每页数量
 *         example: 20
 *       - in: query
 *         name: isRead
 *         schema:
 *           type: boolean
 *         description: 是否已读（可选）
 *         example: false
 *       - in: query
 *         name: type
 *         schema:
 *           type: string
 *         description: 消息类型（可选）：advance_created-预支创建，advance_settled-预支结算，project_updated-工程更新，subproject_updated-子项目更新，settlement_completed-结算完成，subproject_transferred-子项目转交
 *         example: 'advance_created'
 *     responses:
 *       200:
 *         description: 获取成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/PaginatedResponse'
 *             examples:
 *               success:
 *                 summary: 获取成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     list:
 *                       - id: 1
 *                         user_id: 2
 *                         title: '您有一笔新的预支记录'
 *                         content: '管理员为您创建了一笔预支记录，金额：5000.00元'
 *                         type: 'advance_created'
 *                         is_read: false
 *                         related_type: 'advance'
 *                         related_id: 1
 *                         created_at: '2025-12-15T10:00:00.000Z'
 *                         created_by: 1
 *                         creator_name: 'admin'
 *                     total: 1
 *                     page: 1
 *                     size: 20
 *                     hasNext: false
 *                   msg: 'ok'
 *       401:
 *         description: 未授权
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               tokenExpired:
 *                 summary: Token过期或无效
 *                 value:
 *                   code: 4001
 *                   data: null
 *                   msg: 'Token过期或无效'
 *       500:
 *         description: 服务器内部错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               databaseError:
 *                 summary: 数据库异常
 *                 value:
 *                   code: 5001
 *                   data: null
 *                   msg: '获取消息列表失败'
 */
router.get('/', auth.authenticate, validation(messagesController.getMessagesSchema), messagesController.getMessages);

/**
 * @swagger
 * /v1/messages/unread/count:
 *   get:
 *     summary: 获取未读消息数量
 *     description: 获取当前登录用户的未读消息数量
 *     tags: [Messages]
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: 获取成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 获取成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     count: 5
 *                   msg: 'ok'
 *       401:
 *         description: 未授权
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               tokenExpired:
 *                 summary: Token过期或无效
 *                 value:
 *                   code: 4001
 *                   data: null
 *                   msg: 'Token过期或无效'
 *       500:
 *         description: 服务器内部错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               databaseError:
 *                 summary: 数据库异常
 *                 value:
 *                   code: 5001
 *                   data: null
 *                   msg: '获取未读消息数量失败'
 */
router.get('/unread/count', auth.authenticate, messagesController.getUnreadCount);

/**
 * @swagger
 * /v1/messages/{id}/read:
 *   put:
 *     summary: 标记消息为已读
 *     description: 将指定消息标记为已读
 *     tags: [Messages]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 消息ID
 *         example: 1
 *     responses:
 *       200:
 *         description: 标记成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 标记成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     id: 1
 *                     user_id: 2
 *                     title: '您有一笔新的预支记录'
 *                     content: '管理员为您创建了一笔预支记录，金额：5000.00元'
 *                     type: 'advance_created'
 *                     is_read: true
 *                     related_type: 'advance'
 *                     related_id: 1
 *                     created_at: '2025-12-15T10:00:00.000Z'
 *                     created_by: 1
 *                   msg: 'ok'
 *       401:
 *         description: 未授权
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               tokenExpired:
 *                 summary: Token过期或无效
 *                 value:
 *                   code: 4001
 *                   data: null
 *                   msg: 'Token过期或无效'
 *       404:
 *         description: 消息不存在或无权操作
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               notFound:
 *                 summary: 消息不存在或无权操作
 *                 value:
 *                   code: 1002
 *                   data: null
 *                   msg: '消息不存在或无权操作'
 *       500:
 *         description: 服务器内部错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               databaseError:
 *                 summary: 数据库异常
 *                 value:
 *                   code: 5001
 *                   data: null
 *                   msg: '标记消息已读失败'
 */
router.put('/:id/read', auth.authenticate, messagesController.markAsRead);

/**
 * @swagger
 * /v1/messages/read-all:
 *   put:
 *     summary: 批量标记所有消息为已读
 *     description: 将当前用户的所有未读消息标记为已读
 *     tags: [Messages]
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: 标记成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 标记成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     count: 5
 *                   msg: 'ok'
 *       401:
 *         description: 未授权
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               tokenExpired:
 *                 summary: Token过期或无效
 *                 value:
 *                   code: 4001
 *                   data: null
 *                   msg: 'Token过期或无效'
 *       500:
 *         description: 服务器内部错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               databaseError:
 *                 summary: 数据库异常
 *                 value:
 *                   code: 5001
 *                   data: null
 *                   msg: '批量标记消息已读失败'
 */
router.put('/read-all', auth.authenticate, messagesController.markAllAsRead);

/**
 * @swagger
 * /v1/messages/{id}:
 *   delete:
 *     summary: 删除消息
 *     description: 删除指定的消息
 *     tags: [Messages]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 消息ID
 *         example: 1
 *     responses:
 *       200:
 *         description: 删除成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 删除成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     message: '删除成功'
 *                   msg: 'ok'
 *       401:
 *         description: 未授权
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               tokenExpired:
 *                 summary: Token过期或无效
 *                 value:
 *                   code: 4001
 *                   data: null
 *                   msg: 'Token过期或无效'
 *       404:
 *         description: 消息不存在或无权操作
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               notFound:
 *                 summary: 消息不存在或无权操作
 *                 value:
 *                   code: 1002
 *                   data: null
 *                   msg: '消息不存在或无权操作'
 *       500:
 *         description: 服务器内部错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               databaseError:
 *                 summary: 数据库异常
 *                 value:
 *                   code: 5001
 *                   data: null
 *                   msg: '删除消息失败'
 */
router.delete('/:id', auth.authenticate, messagesController.deleteMessage);

module.exports = router;
