/**
 * RBAC权限矩阵集成测试
 *
 * 验证V2.0权限矩阵的所有规则
 */

const request = require('supertest');
const { initTestDatabase, cleanupTestDatabase, createTestUser } = require('../setup');

let app;
let pool;
let adminToken, constructorToken, documenterToken;

beforeAll(async () => {
  pool = await initTestDatabase();
  app = require('../../index');

  if (pool) {
    const admin = await createTestUser(pool, 'admin');
    adminToken = admin.token;
    const constructor = await createTestUser(pool, 'constructor');
    constructorToken = constructor.token;
    const documenter = await createTestUser(pool, 'documenter');
    documenterToken = documenter.token;
  }
}, 30000);

afterAll(async () => {
  if (pool) {
    await cleanupTestDatabase(pool);
    await pool.end();
  }
}, 15000);

describe('RBAC权限矩阵', () => {
  describe('字典管理 - 仅admin', () => {
    test('admin应该能创建空间类型', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/dictionary/space-types')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ name: '测试空间', description: '集成测试' });

      expect(res.body.code).toBe(200);
    });

    test('施工员不能创建空间类型', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/dictionary/space-types')
        .set('Authorization', `Bearer ${constructorToken}`)
        .send({ name: '非法创建' });

      expect(res.body.code).toBe(4002);
    });

    test('资料员不能创建空间类型', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/dictionary/space-types')
        .set('Authorization', `Bearer ${documenterToken}`)
        .send({ name: '非法创建' });

      expect(res.body.code).toBe(4002);
    });
  });

  describe('用户管理 - 仅admin', () => {
    test('admin应该能获取用户列表', async () => {
      if (!pool) return;

      const res = await request(app)
        .get('/v1/users')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.body.code).toBe(200);
    });

    test('施工员不能获取用户列表', async () => {
      if (!pool) return;

      const res = await request(app)
        .get('/v1/users')
        .set('Authorization', `Bearer ${constructorToken}`);

      expect(res.body.code).toBe(4002);
    });
  });

  describe('统计功能 - documenter无权', () => {
    test('资料员不能访问统计', async () => {
      if (!pool) return;

      const res = await request(app)
        .get('/v1/statistics/monthly')
        .set('Authorization', `Bearer ${documenterToken}`);

      expect(res.body.code).toBe(4002);
    });

    test('施工员能访问统计', async () => {
      if (!pool) return;

      const res = await request(app)
        .get('/v1/statistics/monthly')
        .set('Authorization', `Bearer ${constructorToken}`);

      // 可能返回空数据，但不应该是权限错误
      expect(res.body.code).not.toBe(4002);
    });
  });

  // 数据迁移模块已归档至 legacy/sqlite-migration/，相关测试移除
});
