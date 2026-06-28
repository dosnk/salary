const Router = require('koa-router');
const dictionaryController = require('../controllers/dictionary');
const auth = require('../middleware/auth');
const validation = require('../middleware/validation');
const { requireAdmin } = require('../middleware/rbac');

const router = new Router({
  prefix: '/v1/dictionary'
});

// 空间类型 - 查询（所有登录用户）
router.get('/space-types', auth.authenticate, dictionaryController.getSpaceTypes);

// 空间类型 - 增删改（仅管理员）
router.post('/space-types', auth.authenticate, requireAdmin(), validation(dictionaryController.createSpaceTypeSchema), dictionaryController.createSpaceType);
router.put('/space-types/:id', auth.authenticate, requireAdmin(), validation(dictionaryController.updateSpaceTypeSchema, { includeParams: true }), dictionaryController.updateSpaceType);
router.delete('/space-types/:id', auth.authenticate, requireAdmin(), validation(dictionaryController.deleteSpaceTypeSchema, { includeParams: true }), dictionaryController.deleteSpaceType);

// 施工方案 - 查询（所有登录用户）
router.get('/construction-plans', auth.authenticate, dictionaryController.getConstructionPlans);

// 施工方案 - 增删改（仅管理员）
router.post('/construction-plans', auth.authenticate, requireAdmin(), validation(dictionaryController.createConstructionPlanSchema), dictionaryController.createConstructionPlan);
router.put('/construction-plans/:id', auth.authenticate, requireAdmin(), validation(dictionaryController.updateConstructionPlanSchema, { includeParams: true }), dictionaryController.updateConstructionPlan);
router.delete('/construction-plans/:id', auth.authenticate, requireAdmin(), validation(dictionaryController.deleteConstructionPlanSchema, { includeParams: true }), dictionaryController.deleteConstructionPlan);

// 其他字典数据
router.get('/wage-distribution-types', auth.authenticate, dictionaryController.getWageDistributionTypes);
router.get('/project-statuses', auth.authenticate, dictionaryController.getProjectStatuses);
router.get('/settlement-statuses', auth.authenticate, dictionaryController.getSettlementStatuses);
router.get('/construction-units', auth.authenticate, dictionaryController.getConstructionUnits);

module.exports = router;
