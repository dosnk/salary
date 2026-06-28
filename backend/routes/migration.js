const Router = require('koa-router');
const migrationController = require('../controllers/migration');
const auth = require('../middleware/auth');
const { requireAdmin } = require('../middleware/rbac');

const router = new Router({
  prefix: '/v1/migration'
});

// 所有迁移接口都需要管理员权限（统一使用RBAC中间件）
router.post('/login', auth.authenticate, requireAdmin(), migrationController.login);
router.get('/projects', auth.authenticate, requireAdmin(), migrationController.getProjects);
router.get('/project/:id', auth.authenticate, requireAdmin(), migrationController.getProjectById);
router.post('/migrate-project', auth.authenticate, requireAdmin(), migrationController.migrateProject);
router.post('/migrate-all', auth.authenticate, requireAdmin(), migrationController.migrateAll);
router.get('/progress', auth.authenticate, requireAdmin(), migrationController.getProgress);
router.get('/check-database', auth.authenticate, requireAdmin(), migrationController.checkDatabase);

module.exports = router;
