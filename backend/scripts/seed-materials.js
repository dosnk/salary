/**
 * 材料参数预设数据
 *
 * 预设吊顶常用材料:
 * - 面材: 石膏板、铝扣板、硅酸钙板等
 * - 龙骨: 轻钢龙骨(主龙骨/副龙骨)
 * - 收边: 收边条、角线
 * - 配件: 吊杆、挂件、自攻螺丝等
 */

const pool = require('../config/database');

const seedMaterials = async () => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 插入材料分类
    const categories = [
      { name: '面材', description: '吊顶面板材料', sort_order: 1 },
      { name: '主龙骨', description: '承载龙骨', sort_order: 2 },
      { name: '副龙骨', description: '覆面龙骨', sort_order: 3 },
      { name: '收边条', description: '收边/角线材料', sort_order: 4 },
      { name: '配件', description: '吊杆/挂件/螺丝等', sort_order: 5 },
    ];

    const categoryIds = {};
    for (const cat of categories) {
      const result = await client.query(
        `INSERT INTO material_categories (name, description, sort_order)
         VALUES ($1, $2, $3)
         ON CONFLICT (name) DO UPDATE SET description = $2, sort_order = $3
         RETURNING id`,
        [cat.name, cat.description, cat.sort_order]
      );
      categoryIds[cat.name] = result.rows[0].id;
    }

    // 插入面材参数
    const panels = [
      { name: '标准石膏板', brand: '泰山', specification: '1200×2400×9.5mm', unit: '张', unit_price: 28.00, width_cm: 120, length_cm: 240, thickness_cm: 0.95, coverage_area: 2.88 },
      { name: '标准石膏板', brand: '泰山', specification: '1200×2400×12mm', unit: '张', unit_price: 35.00, width_cm: 120, length_cm: 240, thickness_cm: 1.2, coverage_area: 2.88 },
      { name: '防水石膏板', brand: '泰山', specification: '1200×2400×9.5mm', unit: '张', unit_price: 38.00, width_cm: 120, length_cm: 240, thickness_cm: 0.95, coverage_area: 2.88 },
      { name: '铝扣板(条形)', brand: '欧普', specification: '100×3000×0.6mm', unit: '张', unit_price: 18.00, width_cm: 10, length_cm: 300, thickness_cm: 0.06, coverage_area: 0.3 },
      { name: '铝扣板(方形)', brand: '欧普', specification: '300×300×0.6mm', unit: '张', unit_price: 8.00, width_cm: 30, length_cm: 30, thickness_cm: 0.06, coverage_area: 0.09 },
      { name: '铝扣板(方形)', brand: '欧普', specification: '600×600×0.6mm', unit: '张', unit_price: 22.00, width_cm: 60, length_cm: 60, thickness_cm: 0.06, coverage_area: 0.36 },
      { name: '硅酸钙板', brand: '埃特尼特', specification: '1220×2440×8mm', unit: '张', unit_price: 55.00, width_cm: 122, length_cm: 244, thickness_cm: 0.8, coverage_area: 2.9768 },
    ];

    for (const p of panels) {
      await client.query(
        `INSERT INTO material_params (category_id, name, brand, specification, unit, unit_price, width_cm, length_cm, thickness_cm, coverage_area)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
         ON CONFLICT DO NOTHING`,
        [categoryIds['面材'], p.name, p.brand, p.specification, p.unit, p.unit_price, p.width_cm, p.length_cm, p.thickness_cm, p.coverage_area]
      );
    }

    // 插入主龙骨参数
    const mainKeels = [
      { name: '轻钢主龙骨', brand: '龙牌', specification: '60×27×1.2mm 3m/根', unit: '根', unit_price: 12.00, length_cm: 300, keel_spacing_cm: 100 },
      { name: '轻钢主龙骨', brand: '龙牌', specification: '60×27×1.0mm 3m/根', unit: '根', unit_price: 9.50, length_cm: 300, keel_spacing_cm: 100 },
    ];

    for (const k of mainKeels) {
      await client.query(
        `INSERT INTO material_params (category_id, name, brand, specification, unit, unit_price, length_cm, keel_spacing_cm)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
         ON CONFLICT DO NOTHING`,
        [categoryIds['主龙骨'], k.name, k.brand, k.specification, k.unit, k.unit_price, k.length_cm, k.keel_spacing_cm]
      );
    }

    // 插入副龙骨参数
    const subKeels = [
      { name: '轻钢副龙骨', brand: '龙牌', specification: '50×19×0.5mm 3m/根', unit: '根', unit_price: 5.50, length_cm: 300, keel_spacing_cm: 40 },
      { name: '轻钢副龙骨', brand: '龙牌', specification: '50×19×0.5mm 4m/根', unit: '根', unit_price: 7.00, length_cm: 400, keel_spacing_cm: 40 },
    ];

    for (const k of subKeels) {
      await client.query(
        `INSERT INTO material_params (category_id, name, brand, specification, unit, unit_price, length_cm, keel_spacing_cm)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
         ON CONFLICT DO NOTHING`,
        [categoryIds['副龙骨'], k.name, k.brand, k.specification, k.unit, k.unit_price, k.length_cm, k.keel_spacing_cm]
      );
    }

    // 插入收边条参数
    const trims = [
      { name: 'L型收边条', brand: '通用', specification: '22×22×0.3mm 3m/根', unit: '根', unit_price: 3.50, length_cm: 300 },
      { name: 'T型龙骨', brand: '通用', specification: '32×32×0.3mm 3m/根', unit: '根', unit_price: 4.00, length_cm: 300 },
      { name: '阴角线', brand: '通用', specification: '30×30mm 2.5m/根', unit: '根', unit_price: 5.00, length_cm: 250 },
    ];

    for (const t of trims) {
      await client.query(
        `INSERT INTO material_params (category_id, name, brand, specification, unit, unit_price, length_cm)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         ON CONFLICT DO NOTHING`,
        [categoryIds['收边条'], t.name, t.brand, t.specification, t.unit, t.unit_price, t.length_cm]
      );
    }

    // 插入配件参数
    const accessories = [
      { name: '吊杆', brand: '通用', specification: 'Φ8×1000mm', unit: '根', unit_price: 2.50 },
      { name: '主龙骨吊件', brand: '通用', specification: '60系列', unit: '个', unit_price: 0.80 },
      { name: '副龙骨挂件', brand: '通用', specification: '50系列', unit: '个', unit_price: 0.30 },
      { name: '自攻螺丝', brand: '通用', specification: 'Φ3.5×25mm 1000个/盒', unit: '盒', unit_price: 15.00 },
      { name: '膨胀螺栓', brand: '通用', specification: 'M8×60mm', unit: '个', unit_price: 0.50 },
      { name: '接长件(主龙骨)', brand: '通用', specification: '60系列', unit: '个', unit_price: 1.00 },
      { name: '接长件(副龙骨)', brand: '通用', specification: '50系列', unit: '个', unit_price: 0.60 },
    ];

    for (const a of accessories) {
      await client.query(
        `INSERT INTO material_params (category_id, name, brand, specification, unit, unit_price)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT DO NOTHING`,
        [categoryIds['配件'], a.name, a.brand, a.specification, a.unit, a.unit_price]
      );
    }

    await client.query('COMMIT');
    console.log('材料参数预设数据插入成功');
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('材料参数预设数据插入失败:', error.message);
    throw error;
  } finally {
    client.release();
  }
};

// 直接运行时执行
if (require.main === module) {
  seedMaterials()
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
}

module.exports = { seedMaterials };
