const Router = require('koa-router');
const advancesController = require('../controllers/advances');
const auth = require('../middleware/auth');
const validation = require('../middleware/validation');
const { requireAdvanceCreate, requireAdvanceDelete } = require('../middleware/rbac');

const router = new Router({
  prefix: '/v1/advances'
});

/**
 * @swagger
 * /v1/advances:
 *   post:
 *     summary: 创建预支记录
 *     description: 为指定用户创建预支工资记录
 *     tags: [Advances]
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - userId
 *               - advanceAmount
 *               - advanceDate
 *             properties:
 *               userId:
 *                 type: integer
 *                 description: 用户ID
 *                 example: 2
 *                 minimum: 1
 *               advanceAmount:
 *                 type: number
 *                 description: 预支金额
 *                 example: 5000.00
 *                 minimum: 0.01
 *               advanceDate:
 *                 type: string
 *                 format: date
 *                 description: 预支日期
 *                 example: '2025-12-15'
 *               remark:
 *                 type: string
 *                 description: 备注
 *                 example: '预支生活费'
 *                 maxLength: 500
 *           examples:
 *             normalAdvance:
 *               summary: 正常预支
 *               value:
 *                 userId: 2
 *                 advanceAmount: 5000.00
 *                 advanceDate: '2025-12-15'
 *                 remark: '预支生活费'
 *     responses:
 *       200:
 *         description: 创建成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 创建成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     id: 1
 *                     user_id: 2
 *                     advance_amount: 5000.0000
 *                     advance_date: '2025-12-15'
 *                     settled: false
 *                     settlement_id: null
 *                     created_by: 1
 *                     created_at: '2025-12-15T10:00:00.000Z'
 *                     remark: '预支生活费'
 *                   msg: 'ok'
 *       400:
 *         description: 参数错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               parameterError:
 *                 summary: 参数错误
 *                 value:
 *                   code: 1001
 *                   data: null
 *                   msg: '参数错误'
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
 *         description: 用户不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               userNotFound:
 *                 summary: 用户不存在
 *                 value:
 *                   code: 2004
 *                   data: null
 *                   msg: '用户不存在'
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
 *                   msg: '创建预支工资失败'
 */
router.post('/', auth.authenticate, requireAdvanceCreate(), validation(advancesController.createAdvanceSchema), advancesController.createAdvance);

/**
 * @swagger
 * /v1/advances:
 *   get:
 *     summary: 获取预支记录列表
 *     description: 获取预支记录列表，支持分页和按用户筛选
 *     tags: [Advances]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: query
 *         name: userId
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 用户ID（可选，不传则查询所有记录）
 *         example: 2
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
 *           default: 10
 *           minimum: 1
 *           maximum: 100
 *         description: 每页数量
 *         example: 10
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
 *                         user_name: '张三'
 *                         advance_amount: 5000.0000
 *                         advance_date: '2025-12-15'
 *                         settled: false
 *                         settlement_id: null
 *                         created_by: 1
 *                         creator_name: 'admin'
 *                         created_at: '2025-12-15T10:00:00.000Z'
 *                         remark: '预支生活费'
 *                       - id: 2
 *                         user_id: 3
 *                         user_name: '李四'
 *                         advance_amount: 3000.0000
 *                         advance_date: '2025-12-10'
 *                         settled: false
 *                         settlement_id: null
 *                         created_by: 1
 *                         creator_name: 'admin'
 *                         created_at: '2025-12-10T14:30:00.000Z'
 *                         remark: '预支生活费'
 *                     total: 2
 *                     page: 1
 *                     size: 10
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
 *                   msg: '获取预支记录失败'
 */
router.get('/', auth.authenticate, validation(advancesController.getAdvancesSchema), advancesController.getAdvances);

/**
 * @swagger
 * /v1/advances/total:
 *   get:
 *     summary: 获取用户预支总额（未结算）
 *     description: 获取指定用户的未结算预支总额
 *     tags: [Advances]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: query
 *         name: userId
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 用户ID
 *         example: 2
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
 *                     total: 5000.00
 *                   msg: 'ok'
 *       400:
 *         description: 参数错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               parameterError:
 *                 summary: 缺少用户ID参数
 *                 value:
 *                   code: 1001
 *                   data: null
 *                   msg: '缺少用户ID参数'
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
 *                   msg: '获取预支总额失败'
 */
router.get('/total', auth.authenticate, validation(advancesController.getAdvanceTotalSchema), advancesController.getAdvanceTotal);

/**
 * @swagger
 * /v1/advances/{id}:
 *   delete:
 *     summary: 删除预支记录
 *     description: 删除指定的预支记录（仅未结算的记录可删除）
 *     tags: [Advances]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 预支记录ID
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
 *       400:
 *         description: 已结算的记录不能删除
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               alreadySettled:
 *                 summary: 已结算的预支记录不能删除
 *                 value:
 *                   code: 4002
 *                   data: null
 *                   msg: '已结算的预支记录不能删除'
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
 *         description: 预支记录不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               notFound:
 *                 summary: 预支记录不存在
 *                 value:
 *                   code: 1002
 *                   data: null
 *                   msg: '预支记录不存在'
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
 *                   msg: '删除预支记录失败'
 */
router.delete('/:id', auth.authenticate, requireAdvanceDelete(), advancesController.deleteAdvance);

/**
 * @swagger
 * /v1/advances/my:
 *   get:
 *     summary: 获取当前用户的预支记录
 *     description: 获取当前登录用户的预支记录列表，支持分页
 *     tags: [Advances]
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
 *           default: 10
 *           minimum: 1
 *           maximum: 100
 *         description: 每页数量
 *         example: 10
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
 *                         advance_amount: 5000.0000
 *                         advance_date: '2025-12-15'
 *                         settled: false
 *                         settlement_id: null
 *                         created_by: 1
 *                         creator_name: 'admin'
 *                         created_at: '2025-12-15T10:00:00.000Z'
 *                         remark: '预支生活费'
 *                     total: 1
 *                     page: 1
 *                     size: 10
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
 *                   msg: '获取我的预支记录失败'
 */
router.get('/my', auth.authenticate, validation(advancesController.getMyAdvancesSchema), advancesController.getMyAdvances);

/**
 * @swagger
 * /v1/advances/my/total:
 *   get:
 *     summary: 获取当前用户的预支总额（未结算）
 *     description: 获取当前登录用户的未结算预支总额
 *     tags: [Advances]
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
 *                     total: 5000.00
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
 *                   msg: '获取我的预支总额失败'
 */
router.get('/my/total', auth.authenticate, advancesController.getMyAdvanceTotal);

module.exports = router;