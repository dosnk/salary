const pg = require('pg');
const { Pool } = pg;
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

// 将NUMERIC(OID=1700)、DECIMAL类型统一转为JS数字，避免前端反序列化字符串数字的兼容问题
// pg驱动默认将NUMERIC类型作为字符串返回，这里改为float，保持其他类型默认行为不变
pg.types.setTypeParser(1700, function (val) {
  return val === null ? null : parseFloat(val);
});

// 配置验证
const requiredConfig = ['DB_PASSWORD'];
const missingConfig = requiredConfig.filter(key => !process.env[key]);

if (missingConfig.length > 0) {
  console.error('❌ 以下必填配置缺失：', missingConfig.join(', '));
  console.error('💡 请在.env文件中配置这些参数');
  process.exit(1);
}

// 创建数据库连接池（优化：添加连接池配置参数，移除启动时的连接验证以加快启动速度）
const pool = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'default_db',
  password: process.env.DB_PASSWORD,
  port: parseInt(process.env.DB_PORT, 10) || 5432,
  max: 20,                    // 最大连接数
  min: 0,                     // 最小连接数（改为0，启动时不建立连接，按需建立）
  idleTimeoutMillis: 30000,   // 空闲连接超时30秒
  connectionTimeoutMillis: 5000, // 连接超时5秒
  statement_timeout: 300000,  // 语句超时5分钟（支持大量数据操作）
  query_timeout: 300000,      // 查询超时5分钟
  timezone: 'Asia/Shanghai',
});

module.exports = pool;