const Router = require('koa-router');
const settlementController = require('../controllers/settlements');
const auth = require('../middleware/auth');
const validation = require('../middleware/validation');
const { deduplicate } = require('../middleware/deduplicate');
const { requireSettlementAccess } = require('../middleware/rbac');

const router = new Router({
  prefix: '/v1/settlements'
});

/**
 * @swagger
 * /v1/settlements/wage-distributions:
 *   post:
 *     summary: 创建工资分配记录
 *     description: 为指定用户在指定子项目创建工资分配记录
 *     tags: [Settlements]
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - subprojectId
 *               - userId
 *               - amount
 *             properties:
 *               subprojectId:
 *                 type: integer
 *                 description: 子项目ID
 *                 example: 1
 *                 minimum: 1
 *               userId:
 *                 type: integer
 *                 description: 用户ID
 *                 example: 2
 *                 minimum: 1
 *               workdays:
 *                 type: number
 *                 description: 工作天数
 *                 example: 5
 *                 minimum: 0.01
 *                 default: 1
 *               amount:
 *                 type: number
 *                 description: 分配金额
 *                 example: 5000.00
 *                 minimum: 0.01
 *               remark:
 *                 type: string
 *                 description: 备注
 *                 example: '5天工作量'
 *                 maxLength: 500
 *           examples:
 *             normalDistribution:
 *               summary: 正常分配
 *               value:
 *                 subprojectId: 1
 *                 userId: 2
 *                 workdays: 5
 *                 amount: 5000.00
 *                 remark: '5天工作量'
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
 *                     subproject_id: 1
 *                     user_id: 2
 *                     workdays: 5
 *                     amount: 5000.0000
 *                     status: 'unsettled'
 *                     remark: '5天工作量'
 *                     created_at: '2025-12-29T10:00:00.000Z'
 *                   msg: 'ok'
 *       400:
 *         description: 参数错误或已存在分配记录
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
 *               alreadyExists:
 *                 summary: 已存在分配记录
 *                 value:
 *                   code: 3012
 *                   data: null
 *                   msg: '该用户在该子项目已存在工资分配记录'
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
 *         description: 子项目或用户不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               subprojectNotFound:
 *                 summary: 子项目不存在
 *                 value:
 *                   code: 3009
 *                   data: null
 *                   msg: '子项目不存在'
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
 *                   msg: '数据库异常'
 */
router.post('/wage-distributions', auth.authenticate, requireSettlementAccess(), validation(settlementController.createWageDistributionSchema), settlementController.createWageDistribution);

/**
 * @swagger
 * /v1/settlements/wage-distributions/batch:
 *   post:
 *     summary: 批量创建工资分配记录
 *     description: 为指定子项目的所有施工人员批量创建工资分配记录
 *     tags: [Settlements]
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - subprojectId
 *               - distributionType
 *             properties:
 *               subprojectId:
 *                 type: integer
 *                 description: 子项目ID
 *                 example: 1
 *                 minimum: 1
 *               distributionType:
 *                 type: string
 *                 description: 分配类型
 *                 enum: [average, by_workday]
 *                 example: 'average'
 *           examples:
 *             averageDistribution:
 *               summary: 平均分配
 *               value:
 *                 subprojectId: 1
 *                 distributionType: 'average'
 *             workdayDistribution:
 *               summary: 按工日分配
 *               value:
 *                 subprojectId: 1
 *                 distributionType: 'by_workday'
 *     responses:
 *       200:
 *         description: 批量创建成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 批量创建成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     message: '批量创建成功'
 *                     distributions:
 *                       - id: 1
 *                         subproject_id: 1
 *                         user_id: 2
 *                         workdays: 1
 *                         amount: 2500.0000
 *                         status: 'unsettled'
 *                         remark: '平均分配'
 *                         created_at: '2025-12-29T10:00:00.000Z'
 *                       - id: 2
 *                         subproject_id: 1
 *                         user_id: 3
 *                         workdays: 1
 *                         amount: 2500.0000
 *                         status: 'unsettled'
 *                         remark: '平均分配'
 *                         created_at: '2025-12-29T10:00:00.000Z'
 *                   msg: 'ok'
 *       400:
 *         description: 参数错误或没有施工人员
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
 *               noWorkers:
 *                 summary: 没有施工人员
 *                 value:
 *                   code: 3013
 *                   data: null
 *                   msg: '该工程没有施工人员'
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
 *         description: 子项目不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               subprojectNotFound:
 *                 summary: 子项目不存在
 *                 value:
 *                   code: 3009
 *                   data: null
 *                   msg: '子项目不存在'
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
 *                   msg: '数据库异常'
 */
router.post('/wage-distributions/batch', auth.authenticate, requireSettlementAccess(), validation(settlementController.createBatchWageDistributionsSchema), settlementController.createBatchWageDistributions);

/**
 * @swagger
 * /v1/settlements:
 *   post:
 *     summary: 创建工资结算
 *     description: 根据指定月份范围内的已完工子项目创建工资结算记录
 *     tags: [Settlements]
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - startMonth
 *               - endMonth
 *             properties:
 *               startMonth:
 *                 type: string
 *                 description: 开始月份（格式：YYYY-MM）
 *                 example: '2025-12'
 *                 pattern: '^\\d{4}-\\d{2}$'
 *               endMonth:
 *                 type: string
 *                 description: 结束月份（格式：YYYY-MM）
 *                 example: '2025-12'
 *                 pattern: '^\\d{4}-\\d{2}$'
 *               remark:
 *                 type: string
 *                 description: 备注
 *                 example: '12月份工资结算'
 *           examples:
 *             singleMonth:
 *               summary: 单月结算
 *               value:
 *                 startMonth: '2025-12'
 *                 endMonth: '2025-12'
 *                 remark: '12月份工资结算'
 *             multiMonth:
 *               summary: 多月结算
 *               value:
 *                 startMonth: '2025-10'
 *                 endMonth: '2025-12'
 *                 remark: '10-12月份工资结算'
 *     responses:
 *       200:
 *         description: 结算成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 结算成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     settlement_id: 1
 *                     settlement_no: 'ST202512290001'
 *                     start_month: '2025-12-01'
 *                     end_month: '2025-12-31'
 *                     total_amount: 50000.0000
 *                     advance_amount: 10000.0000
 *                     actual_amount: 40000.0000
 *                     remark: '12月份工资结算'
 *                     settled_at: '2025-12-29T10:30:00.000Z'
 *                     wage_distributions:
 *                       - id: 1
 *                         user_id: 2
 *                         username: '张三'
 *                         subproject_id: 1
 *                         amount: 5000.0000
 *                         status: 'settled'
 *                         settled_at: '2025-12-29T10:30:00.000Z'
 *                   msg: 'ok'
 *       400:
 *         description: 参数错误或没有可结算的子项目
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
 *               noSettlement:
 *                 summary: 没有可结算的完工子项目
 *                 value:
 *                   code: 4001
 *                   data: null
 *                   msg: '指定月份范围内没有可结算的完工子项目'
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
 *                   msg: '数据库异常'
 */
router.post('/', auth.authenticate, requireSettlementAccess(), deduplicate({ duration: 10 }), validation(settlementController.createSettlementSchema), settlementController.createSettlement);

/**
 * @swagger
 * /v1/settlements:
 *   get:
 *     summary: 获取工资结算列表
 *     description: 获取工资结算列表，支持分页和排序
 *     tags: [Settlements]
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
 *       - in: query
 *         name: sort
 *         schema:
 *           type: string
 *           default: 'settled_at:desc'
 *         description: 排序字段和方向（字段:方向）
 *         example: 'settled_at:desc'
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
 *                         settlement_no: 'ST202512290001'
 *                         start_month: '2025-12-01'
 *                         end_month: '2025-12-31'
 *                         total_amount: 50000.0000
 *                         advance_amount: 10000.0000
 *                         actual_amount: 40000.0000
 *                         settled_by: 1
 *                         settled_by_username: 'admin'
 *                         settled_at: '2025-12-29T10:30:00.000Z'
 *                         remark: '12月份工资结算'
 *                       - id: 2
 *                         settlement_no: 'ST202511280001'
 *                         start_month: '2025-11-01'
 *                         end_month: '2025-11-30'
 *                         total_amount: 45000.0000
 *                         advance_amount: 8000.0000
 *                         actual_amount: 37000.0000
 *                         settled_by: 1
 *                         settled_by_username: 'admin'
 *                         settled_at: '2025-11-28T15:20:00.000Z'
 *                         remark: '11月份工资结算'
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
 *                   msg: '数据库异常'
 */
router.get('/', auth.authenticate, validation(settlementController.getSettlementsSchema), settlementController.getSettlements);

/**
 * @swagger
 * /v1/settlements/history:
 *   get:
 *     summary: 获取用户结算历史
 *     description: 获取当前用户的结算历史记录
 *     tags: [Settlements]
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
 *       - in: query
 *         name: confirmed
 *         schema:
 *           type: boolean
 *         description: 是否已确认
 *         example: true
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
 *                       - settlement_id: 1
 *                         settlement_no: 'ST202512290001'
 *                         start_month: '2025-12-01'
 *                         end_month: '2025-12-31'
 *                         total_amount: 50000.00
 *                         advance_amount: 10000.00
 *                         actual_amount: 40000.00
 *                         confirmed: true
 *                         confirmed_at: '2025-12-29T10:30:00.000Z'
 *                         settled_by: 1
 *                         settled_by_username: 'admin'
 *                         settled_by_nickname: '管理员'
 *                         settled_at: '2025-12-29T10:00:00.000Z'
 *                         remark: '年终结算'
 *                         user_amount: 40000.00
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
 *                   msg: '数据库异常'
 */
router.get('/history', auth.authenticate, validation(settlementController.getUserSettlementHistorySchema), settlementController.getUserSettlementHistory);

/**
 * @swagger
 * /v1/settlements/{id}:
 *   get:
 *     summary: 获取工资结算详情
 *     description: 根据结算ID获取工资结算详细信息
 *     tags: [Settlements]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 结算ID
 *         example: 1
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
 *                     id: 1
 *                     settlement_no: 'ST202512290001'
 *                     start_month: '2025-12-01'
 *                     end_month: '2025-12-31'
 *                     total_amount: 50000.0000
 *                     advance_amount: 10000.0000
 *                     actual_amount: 40000.0000
 *                     settled_by: 1
 *                     settled_by_username: 'admin'
 *                     settled_at: '2025-12-29T10:30:00.000Z'
 *                     remark: '12月份工资结算'
 *                     wage_distributions:
 *                       - id: 1
 *                         user_id: 2
 *                         username: '张三'
 *                         subproject_id: 1
 *                         amount: 5000.0000
 *                         status: 'settled'
 *                         settled_at: '2025-12-29T10:30:00.000Z'
 *                       - id: 2
 *                         user_id: 3
 *                         username: '李四'
 *                         subproject_id: 1
 *                         amount: 5000.0000
 *                         status: 'settled'
 *                         settled_at: '2025-12-29T10:30:00.000Z'
 *                     wage_advances:
 *                       - id: 1
 *                         user_id: 2
 *                         username: '张三'
 *                         advance_amount: 5000.0000
 *                         advance_date: '2025-12-15'
 *                         settled: true
 *                         remark: '预支生活费'
 *                         created_at: '2025-12-15T10:00:00.000Z'
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
 *         description: 结算记录不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               settlementNotFound:
 *                 summary: 结算记录不存在
 *                 value:
 *                   code: 4002
 *                   data: null
 *                   msg: '结算记录不存在'
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
 *                   msg: '数据库异常'
 */
router.get('/:id', auth.authenticate, settlementController.getSettlementDetail);

/**
 * @swagger
 * /v1/settlements/{id}/confirm:
 *   post:
 *     summary: 用户确认结算
 *     description: 用户确认已收到结算款项，创建结算历史快照
 *     tags: [Settlements]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 结算ID
 *         example: 1
 *     responses:
 *       200:
 *         description: 确认成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 确认成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     message: '结算确认成功！'
 *                     settlementNo: 'ST202512290001'
 *                     totalAmount: 50000.00
 *                     advanceAmount: 10000.00
 *                     actualAmount: 40000.00
 *                     startMonth: '2025-12-01'
 *                     endMonth: '2025-12-31'
 *                     snapshotInfo: '结算历史快照已保存'
 *                     settlementId: 1
 *                     confirmedAt: '2025-12-29T10:30:00.000Z'
 *                   msg: 'ok'
 *       400:
 *         description: 参数错误或已确认
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
 *               alreadyConfirmed:
 *                 summary: 已确认
 *                 value:
 *                   code: 3014
 *                   data: null
 *                   msg: '该结算已确认'
 *               noPermission:
 *                 summary: 无权限
 *                 value:
 *                   code: 4002
 *                   data: null
 *                   msg: '您无权确认此结算'
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
 *         description: 结算记录不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               settlementNotFound:
 *                 summary: 结算记录不存在
 *                 value:
 *                   code: 3010
 *                   data: null
 *                   msg: '结算记录不存在'
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
 *                   msg: '数据库异常'
 */
router.post('/:id/confirm', auth.authenticate, validation(settlementController.confirmSettlementSchema), settlementController.confirmSettlement);

/**
 * @swagger
 * /v1/settlements/calculate:
 *   post:
 *     summary: 计算结算金额
 *     description: 根据选择的工程和预支记录计算结算金额，用于前端结算单预览
 *     tags: [Settlements]
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - projectIds
 *             properties:
 *               projectIds:
 *                 type: array
 *                 description: 工程ID列表
 *                 items:
 *                   type: integer
 *                 example: [1, 2, 3]
 *               advanceIds:
 *                 type: array
 *                 description: 预支记录ID列表（可选）
 *                 items:
 *                   type: integer
 *                 example: [1, 2]
 *     responses:
 *       200:
 *         description: 计算成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 计算成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     planTotals:
 *                       1:
 *                         totalQuantity: 50.00
 *                         totalAmount: 5000.00
 *                       2:
 *                         totalQuantity: 30.00
 *                         totalAmount: 3000.00
 *                     grandTotal: 8000.00
 *                     totalAdvance: 1000.00
 *                     finalTotal: 7000.00
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
 *                   msg: '请选择要结算的工程'
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
 *                   msg: '数据库异常'
 */
router.post('/calculate', auth.authenticate, requireSettlementAccess(), validation(settlementController.calculateSettlementSchema), settlementController.calculateSettlement);

router.get('/history/export/:settlementId', auth.authenticate, settlementController.exportSettlementByIdToExcel);

module.exports = router;
