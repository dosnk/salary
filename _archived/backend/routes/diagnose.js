const Router = require('koa-router');
const diagnoseController = require('../controllers/diagnose');
const auth = require('../middleware/auth');
const { requireAdmin } = require('../middleware/rbac');

const router = new Router({
  prefix: '/v1/diagnose'
});

// 诊断API（仅管理员可访问，统一使用RBAC中间件）
router.get('/settlement', auth.authenticate, requireAdmin(), async (ctx, next) => {
  await diagnoseController.diagnose(ctx);
});

module.exports = router;
