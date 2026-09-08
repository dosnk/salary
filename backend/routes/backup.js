/**
 * 备份管理路由
 *
 * 职责：仅声明路由 + 鉴权中间件
 * 业务逻辑位于 controllers/backup.js、services/backupService.js
 *
 * 路由清单：
 * POST /v1/backup/database - 触发数据库全量备份（需登录，任意角色）
 */

const Router = require('koa-router');
const router = new Router({
  prefix: '/v1/backup'
});
const auth = require('../middleware/auth');
const backupController = require('../controllers/backup');

/** 触发数据库全量备份（需登录；任意已认证角色可触发，返回不暴露业务数据） */
router.post('/database', auth.authenticate, backupController.backupDatabase);

module.exports = router;