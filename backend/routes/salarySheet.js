const Router = require('koa-router');
const auth = require('../middleware/auth');
const { deduplicate } = require('../middleware/deduplicate');
const { requireSettlementAccess } = require('../middleware/rbac');
const router = new Router({ prefix: '/v1/salary-sheet' });
const salarySheetController = require('../controllers/salarySheet');

// 查看类接口：所有已登录用户均可访问（controller内按user_id过滤数据）
router.get('/construction-plans', auth.authenticate, salarySheetController.getConstructionPlans);
router.get('/projects', auth.authenticate, salarySheetController.getProjects);
router.get('/advances', auth.authenticate, salarySheetController.getAdvances);
router.get('/settled-projects', auth.authenticate, salarySheetController.getSettledProjects);
router.get('/settled-advances', auth.authenticate, salarySheetController.getSettledAdvances);
router.get('/settlement-history', auth.authenticate, salarySheetController.getSettlementHistory);
// 结算接口：仅施工员可操作，添加防重复提交保护（6分钟）
router.post('/settle', auth.authenticate, requireSettlementAccess(), deduplicate({ duration: 360 }), salarySheetController.settle);

module.exports = router;
