const pool = require('../config/database');
const logger = require('../config/logger');

// 重置密码API
const resetPassword = async (ctx) => {
  try {
    const { username, newPassword } = ctx.request.body;
    
    if (!username || !newPassword) {
      ctx.fail(1001, '用户名和新密码不能为空');
      return;
    }

    const bcrypt = require('bcryptjs');
    
    // 生成密码哈希
    const saltRounds = 10;
    const passwordHash = await bcrypt.hash(newPassword, saltRounds);
    
    const result = await pool.query(
      'UPDATE users SET password = $1 WHERE username = $2',
      [passwordHash, username]
    );
    
    if (result.rowCount > 0) {
      ctx.success({ message: '密码重置成功' });
    } else {
      ctx.fail(2001, '用户不存在');
    }
    
  } catch (error) {
    logger.error('重置密码失败:', error);
    ctx.fail(5001, '重置密码失败');
  }
};

module.exports = {
  resetPassword
};
