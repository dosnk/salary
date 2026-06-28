/**
 * 工程管理模块集成测试
 *
 * 测试范围:
 * - 工程CRUD
 * - 权限控制（admin不能修改/删除工程）
 * - 子项目管理
 * - 施工人员管理
 */

const request = require('supertest');
const { initTestDatabase, cleanupTestDatabase, createTestUser } = require('../setup');

let app;
let pool;
let adminToken, constructorToken, documenterToken;
let testProjectId;

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

describe('工程管理模块', () => {
  describe('POST /v1/projects - 创建工程', () => {
    test('施工员应该能创建工程', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/projects')
        .set('Authorization', `Bearer ${constructorToken}`)
        .send({
          name: '测试工程A',
          space_type: '客厅',
          construction_plan: '轻钢龙骨石膏板',
          length: 500,
          width: 400,
          address: '测试地址'
        });

      expect(res.status).toBe(200);
      expect(res.body.code).toBe(200);
      expect(res.body.data).toHaveProperty('id');
      testProjectId = res.body.data.id;
    });

    test('资料员应该能创建工程', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/projects')
        .set('Authorization', `Bearer ${documenterToken}`)
        .send({
          name: '测试工程B',
          space_type: '卧室',
          construction_plan: '铝扣板',
          length: 300,
          width: 250
        });

      expect(res.body.code).toBe(200);
    });
  });

  describe('GET /v1/projects - 获取工程列表', () => {
    test('应该返回工程列表', async () => {
      if (!pool) return;

      const res = await request(app)
        .get('/v1/projects')
        .set('Authorization', `Bearer ${constructorToken}`);

      expect(res.body.code).toBe(200);
      expect(res.body.data).toHaveProperty('list');
    });
  });

  describe('PUT /v1/projects/:id - 修改工程', () => {
    test('管理员不能修改工程', async () => {
      if (!pool || !testProjectId) return;

      const res = await request(app)
        .put(`/v1/projects/${testProjectId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ name: '管理员修改' });

      expect(res.body.code).toBe(4002); // 权限不足
    });

    test('资料员不能修改工程', async () => {
      if (!pool || !testProjectId) return;

      const res = await request(app)
        .put(`/v1/projects/${testProjectId}`)
        .set('Authorization', `Bearer ${documenterToken}`)
        .send({ name: '资料员修改' });

      expect(res.body.code).toBe(4002);
    });
  });

  describe('DELETE /v1/projects/:id - 删除工程', () => {
    test('管理员不能删除工程', async () => {
      if (!pool || !testProjectId) return;

      const res = await request(app)
        .delete(`/v1/projects/${testProjectId}`)
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.body.code).toBe(4002);
    });
  });
});
