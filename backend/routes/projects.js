const Router = require('koa-router');
const projectController = require('../controllers/projects');
const auth = require('../middleware/auth');
const validation = require('../middleware/validation');
const { deduplicate } = require('../middleware/deduplicate');
const { requireProjectView, requireProjectModify, requireProjectDelete, requireSubprojectManage, requireFileModify } = require('../middleware/rbac');
const pool = require('../config/database');
const logger = require('../config/logger');

const router = new Router({
  prefix: '/v1/projects'
});

// 验证规则缓存
const validationCache = {
  data: null,
  timestamp: 0,
  TTL: 5 * 60 * 1000 // 5分钟缓存
};

// 获取缓存的验证规则数据
const getCachedValidationData = async () => {
  const now = Date.now();
  
  if (validationCache.data && (now - validationCache.timestamp) < validationCache.TTL) {
    return validationCache.data;
  }
  
  const [schemesResult, typesResult] = await Promise.all([
    pool.query('SELECT name FROM construction_plans ORDER BY id'),
    pool.query('SELECT name FROM space_types ORDER BY id')
  ]);
  
  validationCache.data = {
    schemes: schemesResult.rows.map(row => row.name),
    types: typesResult.rows.map(row => row.name)
  };
  validationCache.timestamp = now;
  
  return validationCache.data;
};

// 动态验证中间件 - 创建工程
const dynamicCreateProjectValidation = async (ctx, next) => {
  try {
    const { schemes, types } = await getCachedValidationData();
    
    // 创建动态验证规则
    const schema = projectController.createProjectSchema.fork(['spaceType'], (field) => {
      return field.valid(...types).messages({ 'any.only': '空间类型不合法' });
    }).fork(['constructionScheme'], (field) => {
      return field.valid(...schemes).messages({ 'any.only': '施工方案不合法' });
    });
    
    // 使用验证中间件
    await validation(schema)(ctx, next);
  } catch (error) {
    logger.error('动态验证失败:', error);
    ctx.fail(5001, '验证规则生成失败');
  }
};

// 动态验证中间件 - 更新子项目
const dynamicUpdateSubprojectValidation = async (ctx, next) => {
  try {
    const { schemes, types } = await getCachedValidationData();
    
    // 创建动态验证规则
    const schema = projectController.updateSubprojectSchema.fork(['spaceType'], (field) => {
      return field.valid(...types).messages({ 'any.only': '空间类型不合法' });
    }).fork(['constructionScheme'], (field) => {
      return field.valid(...schemes).messages({ 'any.only': '施工方案不合法' });
    });
    
    // 使用验证中间件，包含路径参数
    await validation(schema, { includeParams: true })(ctx, next);
  } catch (error) {
    logger.error('动态验证失败:', error);
    ctx.fail(5001, '验证规则生成失败');
  }
};

/**
 * @swagger
 * /v1/projects:
 *   post:
 *     summary: 创建工程
 *     description: 创建新的工程，包含空间类型、施工方案、尺寸等信息
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - name
 *               - spaceType
 *               - constructionScheme
 *               - length
 *               - width
 *               - constructors
 *             properties:
 *               name:
 *                 type: string
 *                 description: 工程名称
 *                 example: '张三住宅装修工程'
 *                 maxLength: 100
 *               spaceType:
*                 type: string
*                 description: 空间类型
*                 enum: ['客厅', '餐厅', '厨房', '公卫', '主卫', '大阳台', '小阳台', '房间', '走道', '入户', 'XX凹位（房间/公卫/主卫）', '楼梯口', '电梯口', '凹位', '其它']
*                 example: '客厅'
*               constructionScheme:
*                 type: string
*                 description: 施工方案
*                 enum: ['蜂窝平面', '半吊', '二级平面', '铝扣平面', '窗帘盒', '发光走边', '水坑', '工程板', '其它方案']
*                 example: '蜂窝平面'
 *               length:
 *                 type: number
 *                 description: 长度（米）
 *                 example: 5.5
 *                 minimum: 0
 *               width:
 *                 type: number
 *                 description: 宽度（米）
 *                 example: 4.2
 *                 minimum: 0
 *               salaryDistribution:
 *                 type: string
 *                 description: 工资分配方式
 *                 enum: [average, work_days]
 *                 example: 'average'
 *               constructors:
 *                 type: array
 *                 description: 施工人员列表
 *                 items:
 *                   type: object
 *                   properties:
 *                     userId:
 *                       type: integer
 *                       description: 施工人员用户ID
 *                       example: 2
 *           examples:
 *             livingRoomProject:
 *               summary: 客厅装修工程
 *               value:
 *                 name: '张三住宅装修工程'
 *                 spaceType: '客厅'
 *                 constructionScheme: '蜂窝平面'
 *                 length: 5.5
 *                 width: 4.2
 *                 salaryDistribution: 'average'
 *                 constructors:
 *                   - userId: 2
 *                   - userId: 3
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
 *                     name: '张三住宅装修工程'
 *                     user_id: 1
 *                     total_amount: 23100
 *                     salary_distribution: 'average'
 *                     created_at: '2025-12-27T10:30:00.000Z'
 *                     updated_at: '2025-12-27T10:30:00.000Z'
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
router.post('/', auth.authenticate, deduplicate({ duration: 5 }), dynamicCreateProjectValidation, projectController.createProject);

/**
 * @swagger
 * /v1/projects:
 *   get:
 *     summary: 获取工程列表
 *     description: 获取工程列表，支持分页、按月份筛选、关键词搜索和排序
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: query
 *         name: page
 *         schema:
 *           type: integer
 *           default: 1
 *           minimum: 1
 *           description: 页码
 *           example: 1
 *       - in: query
 *         name: size
 *         schema:
 *           type: integer
 *           default: 10
 *           minimum: 1
 *           maximum: 100
 *           description: 每页数量
 *           example: 10
 *       - in: query
 *         name: month
 *         schema:
 *           type: integer
 *           minimum: 1
 *           maximum: 12
 *           description: 月份筛选（1-12，仅按月份）
 *           example: 12
 *       - in: query
 *         name: yearMonth
 *         schema:
 *           type: string
 *           pattern: '^\\d{4}-\\d{2}$'
 *           description: 年月筛选（YYYY-MM格式，精确到年月）
 *           example: '2025-12'
 *       - in: query
 *         name: keyword
 *         schema:
 *           type: string
 *           description: 关键词搜索（工程名称和描述）
 *           example: '张三'
 *       - in: query
 *         name: status
 *         schema:
 *           type: string
 *           enum: ['preparing', 'constructing', 'completed', 'canceled']
 *           description: 状态筛选
 *           example: 'constructing'
 *       - in: query
 *         name: creatorNickname
 *         schema:
 *           type: string
 *           description: 创建人昵称搜索
 *           example: '张三'
 *       - in: query
 *         name: workerNickname
 *         schema:
 *           type: string
 *           description: 施工员昵称搜索
 *           example: '李四'
 *       - in: query
 *         name: startDate
 *         schema:
 *           type: string
 *           format: date
 *           description: 开始日期（YYYY-MM-DD）
 *           example: '2025-01-01'
 *       - in: query
 *         name: endDate
 *         schema:
 *           type: string
 *           format: date
 *           description: 结束日期（YYYY-MM-DD）
 *           example: '2025-12-31'
 *       - in: query
 *         name: settlementStatus
 *         schema:
 *           type: string
 *           enum: ['settled', 'unsettled']
 *           description: 结算状态筛选
 *           example: 'unsettled'
 *       - in: query
 *         name: sort
 *         schema:
 *           type: string
 *           default: 'created_at:desc'
 *           description: 排序字段和方向（字段:方向）
 *           example: 'created_at:desc'
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
 *                         name: '张三住宅装修工程'
 *                         user_id: 1
 *                         total_amount: 23100
 *                         salary_distribution: 'average'
 *                         created_at: '2025-12-27T10:30:00.000Z'
 *                         updated_at: '2025-12-27T10:30:00.000Z'
 *                       - id: 2
 *                         name: '李四商铺装修工程'
 *                         user_id: 1
 *                         total_amount: 18900
 *                         salary_distribution: 'work_days'
 *                         created_at: '2025-12-26T15:20:00.000Z'
 *                         updated_at: '2025-12-26T15:20:00.000Z'
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
router.get('/', auth.authenticate, validation(projectController.getProjectsSchema), projectController.getProjects);

/**
 * @swagger
 * /v1/projects/{id}:
 *   get:
 *     summary: 获取工程详情
 *     description: 根据工程ID获取工程详细信息，包括子项目信息
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *           description: 工程ID
 *           example: 1
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
 *                     name: '张三住宅装修工程'
 *                     user_id: 1
 *                     total_amount: 23100
 *                     salary_distribution: 'average'
 *                     created_at: '2025-12-27T10:30:00.000Z'
 *                     updated_at: '2025-12-27T10:30:00.000Z'
 *                     sub_projects:
 *                       - id: 1
 *                         project_id: 1
 *                         space_type: '客厅'
 *                         construction_scheme: '蜂窝平面'
 *                         length: 5.5
 *                         width: 4.2
 *                         unit_price: 1000
 *                         unit_type: '平方米'
 *                         amount: 23100
 *                         remark: '包含吊顶和墙面处理'
 *                         created_at: '2025-12-27T10:30:00.000Z'
 *                         updated_at: '2025-12-27T10:30:00.000Z'
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
 *         description: 工程不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               projectNotFound:
 *                 summary: 工程不存在
 *                 value:
 *                   code: 3001
 *                   data: null
 *                   msg: '工程不存在'
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
router.get('/:id', auth.authenticate, requireProjectView(), validation(projectController.getProjectDetailSchema, { includeParams: true }), projectController.getProjectDetail);

/**
 * @swagger
 * /v1/projects/{id}:
 *   put:
 *     summary: 更新工程
 *     description: 更新工程信息，包括名称、空间类型、施工方案、尺寸等
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *           description: 工程ID
 *           example: 1
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               name:
 *                 type: string
 *                 description: 工程名称
 *                 example: '张三住宅装修工程（更新）'
 *                 maxLength: 100
 *               spaceType:
 *                 type: string
 *                 description: 空间类型
 *                 enum: [客厅, 餐厅, 厨房, 公卫, 主卫, 大阳台, 小阳台, 房间, 走道, 入户, XX凹位（房间/公卫/主卫）, 楼梯口, 电梯口, 凹位, 其它]
 *                 example: '客厅'
 *               constructionScheme:
 *                 type: string
 *                 description: 施工方案
 *                 enum: [蜂窝平面, 半吊, 二级平面, 铝扣平面, 窗帘盒, 发光走边, 水坑, 工程板, 其它方案]
 *                 example: '二级平面'
 *               length:
 *                 type: number
 *                 description: 长度（米）
 *                 example: 6.0
 *                 minimum: 0
 *               width:
 *                 type: number
 *                 description: 宽度（米）
 *                 example: 4.5
 *                 minimum: 0
 *               salaryDistribution:
 *                 type: string
 *                 description: 工资分配方式
 *                 enum: [average, work_days]
 *                 example: 'work_days'
 *               constructors:
 *                 type: array
 *                 description: 施工人员列表
 *                 items:
 *                   type: object
 *                   properties:
 *                     userId:
 *                       type: integer
 *                       description: 施工人员用户ID
 *                       example: 2
 *           examples:
 *             updateProject:
 *               summary: 更新工程信息
 *               value:
 *                 name: '张三住宅装修工程（更新）'
 *                 spaceType: '客厅'
 *                 constructionScheme: '二级平面'
 *                 length: 6.0
 *                 width: 4.5
 *                 salaryDistribution: 'work_days'
 *                 constructors:
 *                   - userId: 2
 *                   - userId: 3
 *     responses:
 *       200:
 *         description: 更新成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 更新成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     id: 1
 *                     name: '张三住宅装修工程（更新）'
 *                     user_id: 1
 *                     total_amount: 27000
 *                     salary_distribution: 'work_days'
 *                     created_at: '2025-12-27T10:30:00.000Z'
 *                     updated_at: '2025-12-27T11:00:00.000Z'
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
 *               noPermission:
 *                 summary: 无操作权限
 *                 value:
 *                   code: 4002
 *                   data: null
 *                   msg: '无操作权限'
 *       404:
 *         description: 工程不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               projectNotFound:
 *                 summary: 工程不存在
 *                 value:
 *                   code: 3001
 *                   data: null
 *                   msg: '工程不存在'
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
router.put('/:id', auth.authenticate, requireProjectModify(), deduplicate({ duration: 10 }), validation(projectController.updateProjectSchema), projectController.updateProject);

/**
 * @swagger
 * /v1/projects/{id}:
 *   delete:
 *     summary: 删除工程
 *     description: 根据工程ID删除工程及其关联的子项目
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *           description: 工程ID
 *           example: 1
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
 *                   data: null
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
 *               noPermission:
 *                 summary: 无操作权限
 *                 value:
 *                   code: 4002
 *                   data: null
 *                   msg: '无操作权限'
 *       404:
 *         description: 工程不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               projectNotFound:
 *                 summary: 工程不存在
 *                 value:
 *                   code: 3001
 *                   data: null
 *                   msg: '工程不存在'
 *               projectDeleted:
 *                 summary: 工程已删除
 *                 value:
 *                   code: 3004
 *                   data: null
 *                   msg: '工程已删除'
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
router.delete('/:id', auth.authenticate, requireProjectDelete(), validation(projectController.deleteProjectSchema, { includeParams: true }), projectController.deleteProject);

/**
 * @swagger
 * /v1/projects/{id}/history:
 *   get:
 *     summary: 获取工程历史记录
 *     description: 根据工程ID获取工程操作历史记录
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *           description: 工程ID
 *           example: 1
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
 *                     - id: 1
 *                       project_id: 1
 *                       action: 'create'
 *                       action_name: '创建工程'
 *                       description: '创建工程：张三住宅装修工程'
 *                       performed_by: 1
 *                       username: 'admin'
 *                       nickname: '管理员'
 *                       created_at: '2025-12-27T10:30:00.000Z'
 *                     - id: 2
 *                       project_id: 1
 *                       action: 'update'
 *                       action_name: '更新工程'
 *                       description: '更新工程信息'
 *                       performed_by: 1
 *                       username: 'admin'
 *                       nickname: '管理员'
 *                       created_at: '2025-12-27T11:00:00.000Z'
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
 *               noPermission:
 *                 summary: 无操作权限
 *                 value:
 *                   code: 4002
 *                   data: null
 *                   msg: '无操作权限'
 *       404:
 *         description: 工程不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               projectNotFound:
 *                 summary: 工程不存在
 *                 value:
 *                   code: 3001
 *                   data: null
 *                   msg: '工程不存在'
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
router.get('/:id/history', auth.authenticate, requireProjectView(), validation(projectController.getProjectHistorySchema, { includeParams: true }), projectController.getProjectHistory);

/**
 * @swagger
 * /v1/projects/{id}/subprojects/{subprojectId}:
 *   put:
 *     summary: 更新子项目
 *     description: 更新指定工程的子项目信息
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 工程ID
 *       - in: path
 *         name: subprojectId
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 子项目ID
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               spaceType:
 *                 type: string
 *                 description: 空间类型
 *               constructionScheme:
 *                 type: string
 *                 description: 施工方案
 *               length:
 *                 type: number
 *                 description: 长度（米）
 *               width:
 *                 type: number
 *                 description: 宽度（米）
 *               salaryDistribution:
 *                 type: string
 *                 description: 工资分配方式
 *               remark:
 *                 type: string
 *                 description: 备注
 *     responses:
 *       200:
 *         description: 更新成功
 *       401:
 *         description: 未授权
 *       403:
 *         description: 无操作权限
 *       404:
 *         description: 子项目不存在
 *       500:
 *         description: 服务器内部错误
 */
router.put('/:id/subprojects/:subprojectId', auth.authenticate, requireSubprojectManage(), dynamicUpdateSubprojectValidation, projectController.updateSubproject);

/**
 * @swagger
 * /v1/projects/{id}/subprojects/{subprojectId}:
 *   delete:
 *     summary: 删除子项目
 *     description: 删除指定工程的子项目
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 工程ID
 *       - in: path
 *         name: subprojectId
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 子项目ID
 *     responses:
 *       200:
 *         description: 删除成功
 *       401:
 *         description: 未授权
 *       403:
 *         description: 无操作权限
 *       404:
 *         description: 子项目不存在
 *       500:
 *         description: 服务器内部错误
 */
router.delete('/:id/subprojects/:subprojectId', auth.authenticate, requireSubprojectManage(), validation(projectController.deleteSubprojectSchema, { includeParams: true }), projectController.deleteSubproject);

/**
 * @swagger
 * /v1/projects/{id}/subprojects/{subprojectId}/transfer:
 *   post:
 *     summary: 转交子项目
 *     description: 将子项目转交给其他用户，包括子项目所有权和工资分配
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 工程ID
 *       - in: path
 *         name: subprojectId
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 子项目ID
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - toUserId
 *             properties:
 *               toUserId:
 *                 type: integer
 *                 description: 目标用户ID
 *                 example: 3
 *               transferReason:
 *                 type: string
 *                 description: 转交原因
 *                 example: '工作调整，转交给同事'
 *                 maxLength: 500
 *     responses:
 *       200:
 *         description: 转交成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 转交成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     message: '转交成功'
 *                     fromUserId: 2
 *                     toUserId: 3
 *                     toUserName: '李四'
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
 *               noPermission:
 *                 summary: 无操作权限
 *                 value:
 *                   code: 4002
 *                   data: null
 *                   msg: '无操作权限'
 *       404:
 *         description: 子项目或目标用户不存在
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
 *                 summary: 目标用户不存在
 *                 value:
 *                   code: 3002
 *                   data: null
 *                   msg: '目标用户不存在'
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
router.post('/:id/subprojects/:subprojectId/transfer', auth.authenticate, requireSubprojectManage(), deduplicate({ duration: 5 }), validation(projectController.transferSubprojectSchema, { includeParams: true }), projectController.transferSubproject);

/**
 * @swagger
 * /v1/projects/{id}/subprojects/{subprojectId}/status:
 *   put:
 *     summary: 更新子项目状态
 *     description: 更新指定工程子项目的状态
 *     tags: [Projects]
 *     security:
 *       - bearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 工程ID
 *       - in: path
 *         name: subprojectId
 *         required: true
 *         schema:
 *           type: integer
 *           minimum: 1
 *         description: 子项目ID
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - status
 *             properties:
 *               status:
 *                 type: string
 *                 enum: [pending, completed, canceled]
 *                 description: 子项目状态
 *     responses:
 *       200:
 *         description: 状态更新成功
 *       401:
 *         description: 未授权
 *       403:
 *         description: 无操作权限
 *       404:
 *         description: 子项目不存在
 *       500:
 *         description: 服务器内部错误
 */
router.put('/:id/subprojects/:subprojectId/status', auth.authenticate, requireSubprojectManage(), validation(projectController.updateSubprojectStatusSchema), projectController.updateSubprojectStatus);

// 上传工程附件（仅constructor可操作，admin/documenter不能上传）
router.post('/:id/files', auth.authenticate, requireFileModify(), projectController.uploadFile);

// 删除工程附件（仅constructor可操作，admin/documenter不能删除）
router.delete('/:id/files/:fileId', auth.authenticate, requireFileModify(), projectController.deleteFile);

router.get('/:id/workers', auth.authenticate, requireProjectView(), projectController.getProjectWorkers);

module.exports = router;