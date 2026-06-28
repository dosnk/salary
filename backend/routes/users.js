const Router = require('koa-router');
const userController = require('../controllers/users');
const auth = require('../middleware/auth');
const validation = require('../middleware/validation');
const { deduplicate } = require('../middleware/deduplicate');
const { requireAdmin } = require('../middleware/rbac');

const router = new Router({
  prefix: '/v1/users'
});

// 注意：具体路径必须在参数化路径（/:id）之前定义
router.get('/', auth.authenticate, requireAdmin(), validation(userController.getUsersSchema), userController.getUsers);

router.get('/constructors', auth.authenticate, userController.getConstructors);

router.get('/profile', auth.authenticate, userController.getProfile);

router.post('/', auth.authenticate, requireAdmin(), deduplicate({ duration: 10 }), validation(userController.createUserSchema), userController.createUser);

router.put('/profile', auth.authenticate, deduplicate({ duration: 10 }), validation(userController.updateProfileSchema), userController.updateProfile);

router.post('/change-password', auth.authenticate, deduplicate({ duration: 10 }), validation(userController.changePasswordSchema), userController.changePassword);

router.get('/:id', auth.authenticate, validation(userController.getUserSchema, { includeParams: true }), userController.getUser);

router.put('/:id', auth.authenticate, deduplicate({ duration: 10 }), validation(userController.updateUserSchema, { includeParams: true }), userController.updateUser);

router.delete('/:id', auth.authenticate, requireAdmin(), validation(userController.deleteUserSchema, { includeParams: true }), userController.deleteUser);

router.post('/:id/reset-password', auth.authenticate, requireAdmin(), deduplicate({ duration: 10 }), validation(userController.resetPasswordSchema, { includeParams: true }), userController.resetPassword);

module.exports = router;
