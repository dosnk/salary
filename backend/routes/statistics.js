const Router = require('koa-router');
const statisticsController = require('../controllers/statistics');
const auth = require('../middleware/auth');
const { requireStatisticsAccess } = require('../middleware/rbac');

const router = new Router({
  prefix: '/v1/statistics'
});

// 月度统计（支持多月份，参数 months=2025-01,2025-02）
router.get('/monthly', auth.authenticate, requireStatisticsAccess(), statisticsController.getMonthlyStatistics);

// 工程状态统计（按状态+结算状态分组）
router.get('/project-status', auth.authenticate, requireStatisticsAccess(), statisticsController.getProjectStatistics);

// 收入统计（结算相关统计）
router.get('/income', auth.authenticate, requireStatisticsAccess(), statisticsController.getIncomeStatistics);

// 施工方案统计
router.get('/construction-plans', auth.authenticate, requireStatisticsAccess(), statisticsController.getConstructionPlanStatistics);

// 人员统计
router.get('/workers', auth.authenticate, requireStatisticsAccess(), statisticsController.getWorkerStatistics);

// 仪表盘卡片统计（统计页顶部4个卡片，所有计算由后端完成）
router.get('/dashboard', auth.authenticate, requireStatisticsAccess(), statisticsController.getDashboard);

module.exports = router;
