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
const { sensitiveLimiter } = require('../middleware/rateLimiter');
const backupController = require('../controllers/backup');

/**
 * 触发数据库全量备份
 * 需登录（任意角色，前端启动自动触发场景）；sensitiveLimiter 限制 10次/分钟/用户，
 * 防止恶意刷接口导致磁盘写满（备份文件包含全库敏感数据）
 */
router.post('/database', auth.authenticate, sensitiveLimiter, backupController.backupDatabase);

module.exports = router;