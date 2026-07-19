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
    // bcrypt盐值轮数：12轮（约250ms/次），符合2026年安全标准
    const saltRounds = 12;
    const passwordHash = await bcrypt.hash(newPassword, saltRounds);
    
    // 重置密码（同步设置 password_changed_at，标记用户已主动修改过密码）
    // 这样后续 init-db.js --reset-passwords 默认会跳过该用户，避免覆盖用户改过的密码
    const result = await pool.query(
      'UPDATE users SET password = $1, password_changed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE username = $2',
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
