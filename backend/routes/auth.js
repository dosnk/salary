const Router = require('koa-router');
const authController = require('../controllers/auth');
const auth = require('../middleware/auth');
// 登录接口限流：10次/分钟/IP，防止暴力破解密码
const { loginLimiter } = require('../middleware/rateLimiter');

const router = new Router({
  prefix: '/v1/auth'
});

/**
 * @swagger
 * /v1/auth/login:
 *   post:
 *     summary: 用户登录
 *     description: 使用用户名和密码登录系统，成功后返回JWT token
 *     tags: [Auth]
 *     security: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - password
 *             properties:
 *               username:
 *                 type: string
 *                 description: 用户名
 *                 example: 'admin'
 *                 minLength: 3
 *                 maxLength: 50
 *               password:
 *                 type: string
 *                 description: 密码
 *                 example: 'admin123'
 *                 minLength: 6
 *                 maxLength: 100
 *           examples:
 *             adminLogin:
 *               summary: 管理员登录
 *               value:
 *                 username: 'admin'
 *                 password: 'admin123'
 *             userLogin:
 *               summary: 普通用户登录
 *               value:
 *                 username: 'testuser'
 *                 password: 'test123'
 *     responses:
 *       200:
 *         description: 登录成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/LoginResponse'
 *             examples:
 *               success:
 *                 summary: 登录成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxLCJ1c2VybmFtZSI6ImFkbWluIiwiaWF0IjoxNzM1MjYwODAwLCJleHAiOjE3MzUzNDcyMDB9.signature'
 *                     user:
 *                       id: 1
 *                       username: 'admin'
 *                       nickname: '管理员'
 *                       phone: '13800138000'
 *                       role: 'admin'
 *                       created_at: '2025-12-01T00:00:00.000Z'
 *                   msg: 'ok'
 *       401:
 *         description: 用户名或密码错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               invalidCredentials:
 *                 summary: 用户名或密码错误
 *                 value:
 *                   code: 2002
 *                   data: null
 *                   msg: '用户名或密码错误'
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
 *               serverError:
 *                 summary: 服务器内部错误
 *                 value:
 *                   code: 5003
 *                   data: null
 *                   msg: '服务器内部错误'
 */
router.post('/login', loginLimiter, authController.login);

// 刷新Token接口
router.post('/refresh', authController.refreshToken);

/**
 * @swagger
 * /v1/auth/register:
 *   post:
 *     summary: 用户注册
 *     description: 创建新用户账号，需要提供用户名、密码和手机号
 *     tags: [Auth]
 *     security: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - password
 *               - phone
 *             properties:
 *               username:
 *                 type: string
 *                 description: 用户名（3-50个字符）
 *                 example: 'newuser'
 *                 minLength: 3
 *                 maxLength: 50
 *               password:
 *                 type: string
 *                 description: 密码（6-100个字符）
 *                 example: 'password123'
 *                 minLength: 6
 *                 maxLength: 100
 *               phone:
 *                 type: string
 *                 description: 手机号（11位数字）
 *                 example: '13912345678'
 *                 pattern: '^1[3-9]\\d{9}$'
 *               nickname:
 *                 type: string
 *                 description: 昵称（可选）
 *                 example: '新用户'
 *                 maxLength: 50
 *           examples:
 *             normalRegister:
 *               summary: 普通用户注册
 *               value:
 *                 username: 'newuser'
 *                 password: 'password123'
 *                 phone: '13912345678'
 *                 nickname: '新用户'
 *             minimalRegister:
 *               summary: 最小化注册（不含昵称）
 *               value:
 *                 username: 'simpleuser'
 *                 password: 'simple123'
 *                 phone: '13887654321'
 *     responses:
 *       200:
 *         description: 注册成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 注册成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     id: 2
 *                     username: 'newuser'
 *                     nickname: '新用户'
 *                     phone: '13912345678'
 *                     role: 'user'
 *                     created_at: '2025-12-27T10:30:00.000Z'
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
 *               phoneFormatError:
 *                 summary: 手机号格式错误
 *                 value:
 *                   code: 2007
 *                   data: null
 *                   msg: '手机号格式错误'
 *               passwordFormatError:
 *                 summary: 密码格式错误
 *                 value:
 *                   code: 2006
 *                   data: null
 *                   msg: '密码格式错误'
 *       409:
 *         description: 用户名或手机号已存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               usernameExists:
 *                 summary: 用户名已存在
 *                 value:
 *                   code: 2001
 *                   data: null
 *                   msg: '用户名已存在'
 *               phoneExists:
 *                 summary: 手机号已存在
 *                 value:
 *                   code: 2003
 *                   data: null
 *                   msg: '手机号已存在'
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
router.post('/register', authController.register);

/**
 * @swagger
 * /v1/auth/me:
 *   get:
 *     summary: 获取当前用户信息
 *     description: 根据JWT token获取当前登录用户的详细信息
 *     tags: [Auth]
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
 *                     id: 1
 *                     username: 'admin'
 *                     nickname: '管理员'
 *                     phone: '13800138000'
 *                     role: 'admin'
 *                     created_at: '2025-12-01T00:00:00.000Z'
 *                   msg: 'ok'
 *       401:
 *         description: 未授权或token无效
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
 *               tokenFormatError:
 *                 summary: Token格式错误
 *                 value:
 *                   code: 4003
 *                   data: null
 *                   msg: 'Token格式错误'
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
 *               serverError:
 *                 summary: 服务器内部错误
 *                 value:
 *                   code: 5003
 *                   data: null
 *                   msg: '服务器内部错误'
 */
router.get('/me', auth.authenticate, authController.getUserInfo);

/**
 * @swagger
 * /v1/auth/logout:
 *   post:
 *     summary: 用户退出登录
 *     description: 用户退出登录，清除客户端token
 *     tags: [Auth]
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: 退出登录成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SuccessResponse'
 *             examples:
 *               success:
 *                 summary: 退出登录成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     message: '退出登录成功'
 *                   msg: 'ok'
 *       401:
 *         description: 未授权或token无效
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
 *               tokenFormatError:
 *                 summary: Token格式错误
 *                 value:
 *                   code: 4003
 *                   data: null
 *                   msg: 'Token格式错误'
 *       500:
 *         description: 服务器内部错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               serverError:
 *                 summary: 服务器内部错误
 *                 value:
 *                   code: 5001
 *                   data: null
 *                   msg: '服务器内部错误'
 */
router.post('/logout', auth.authenticate, authController.logout);

module.exports = router;