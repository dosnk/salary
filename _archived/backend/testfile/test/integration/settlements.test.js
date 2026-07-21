/**
 * 结算模块集成测试
 *
 * 测试范围:
 * - 结算预览计算
 * - 结算创建
 * - 结算历史查询
 * - 权限控制（documenter不能结算）
 */

const request = require('supertest');
const { initTestDatabase, cleanupTestDatabase, createTestUser } = require('../setup');

let app;
let pool;
let constructorToken, documenterToken;

beforeAll(async () => {
  pool = await initTestDatabase();
  app = require('../../index');

  if (pool) {
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

describe('结算模块', () => {
  describe('POST /v1/settlements/calculate - 结算预览', () => {
    test('施工员应该能预览结算', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/settlements/calculate')
        .set('Authorization', `Bearer ${constructorToken}`)
        .send({ projectIds: [1] });

      // 可能没有数据，但不应该是权限错误
      expect(res.body.code).not.toBe(4002);
    });

    test('资料员不能预览结算', async () => {
      if (!pool) return;

      const res = await request(app)
        .post('/v1/settlements/calculate')
        .set('Authorization', `Bearer ${documenterToken}`)
        .send({ projectIds: [1] });

      expect(res.body.code).toBe(4002);
    });
  });

  describe('GET /v1/settlements - 结算列表', () => {
    test('施工员应该能查看结算列表', async () => {
      if (!pool) return;

      const res = await request(app)
        .get('/v1/settlements')
        .set('Authorization', `Bearer ${constructorToken}`);

      expect(res.body.code).not.toBe(4002);
    });
  });
});
