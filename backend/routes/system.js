const Router = require('koa-router');
const systemController = require('../controllers/system');
const auth = require('../middleware/auth');
const { requireAdmin } = require('../middleware/rbac');

const router = new Router({
  prefix: '/v1/system'
});

/**
 * 数据一致性校验（仅 admin 可访问）
 * GET /v1/system/data-consistency/verify?userId=&tolerance=
 *
 * 查询参数：
 *   - userId: 可选，指定用户ID进行校验
 *   - tolerance: 可选，金额容差（元），默认 0.01
 */
router.get('/data-consistency/verify', auth.authenticate, requireAdmin(), async (ctx, next) => {
  await systemController.verifyDataConsistencyHandler(ctx);
});

module.exports = router;
