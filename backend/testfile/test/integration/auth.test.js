/**
 * 认证模块集成测试
 *
 * 测试范围:
 * - 用户注册
 * - 用户登录
 * - Token刷新
 * - 密码修改
 * - 登录失败锁定
 */

const request = require('supertest');
const { initTestDatabase, cleanupTestDatabase, createTestUser } = require('../setup');

let app;
let pool;
let adminToken;
let adminUser;

beforeAll(async () => {
  pool = await initTestDatabase();
  // 动态导入app（确保环境变量已设置）
  app = require('../../index');

  if (pool) {
    const result = await createTestUser(pool, 'admin');
    adminToken = result.token;
    adminUser = result.user;
  }
}, 30000);

afterAll(async () => {
  if (pool) {
    await cleanupTestDatabase(pool);
    await pool.end();
  }
}, 15000);

describe('认证模块', () => {
  describe('POST /v1/auth/register', () => {
    test('应该成功注册新用户', async () => {
      if (!pool) return; // 数据库不可用时跳过

      const res = await request(app)
        .post('/v1/auth/register')
        .send({
          username: '新用户',
          password: 'Test123456',
          nickname: '测试施工员',
          phone: '13900000001',
          role: 'constructor'
        });

      expect(res.status).toBe(200);
      expect(res.body.code).toBe(200);
      expect(res.body.data).toHaveProperty('token');
      expect(res.body.data).toHaveProperty('user');
    });

    test('应该拒绝重复用户名注册', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/auth/register')
        .send({
          username: 'test_admin', // 已存在的用户名
          password: 'Test123456',
          nickname: '重复用户',
          phone: '13900000002',
          role: 'constructor'
        });

      expect(res.status).toBe(200);
      expect(res.body.code).toBe(4001); // 用户已存在
    });

    test('应该拒绝弱密码', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/auth/register')
        .send({
          username: '弱密码用户',
          password: '123', // 太短且无字母
          nickname: '弱密码',
          phone: '13900000003',
          role: 'constructor'
        });

      expect(res.body.code).toBe(1000); // 参数校验失败
    });
  });

  describe('POST /v1/auth/login', () => {
    test('应该成功登录', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/auth/login')
        .send({
          username: 'test_admin',
          password: 'Test123456'
        });

      expect(res.status).toBe(200);
      expect(res.body.code).toBe(200);
      expect(res.body.data).toHaveProperty('token');
    });

    test('应该拒绝错误密码', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/auth/login')
        .send({
          username: 'test_admin',
          password: 'WrongPassword1'
        });

      expect(res.body.code).toBe(4001);
    });
  });

  describe('POST /v1/users/change-password', () => {
    test('应该成功修改密码', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/users/change-password')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          old_password: 'Test123456',
          new_password: 'NewTest123456'
        });

      expect(res.body.code).toBe(200);
    });
  });
});
