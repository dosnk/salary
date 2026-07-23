const Joi = require('joi');
const pool = require('../config/database');
const logger = require('../config/logger');

// 缓存施工方案列表，避免频繁查询数据库
let constructionSchemesCache = null;
let spaceTypesCache = null;
let cacheExpiry = 0;
const CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存

// 获取施工方案列表（带缓存）
const getConstructionSchemes = async () => {
  const now = Date.now();
  if (constructionSchemesCache && now < cacheExpiry) {
    return constructionSchemesCache;
  }
  
  try {
    const result = await pool.query('SELECT name FROM construction_plans ORDER BY id');
    constructionSchemesCache = result.rows.map(row => row.name);
    cacheExpiry = now + CACHE_DURATION;
    return constructionSchemesCache;
  } catch (error) {
    logger.error('获取施工方案列表失败:', error);
    // 如果查询失败，返回默认列表
    return ['蜂窝平面', '半吊', '二级平面', '铝扣平面', '窗帘盒', '发光走边', '水坑', '工程板', '封梁', '装饰条', '其它方案'];
  }
};

// 获取空间类型列表（带缓存）
const getSpaceTypes = async () => {
  const now = Date.now();
  if (spaceTypesCache && now < cacheExpiry) {
    return spaceTypesCache;
  }
  
  try {
    const result = await pool.query('SELECT name FROM space_types ORDER BY id');
    spaceTypesCache = result.rows.map(row => row.name);
    cacheExpiry = now + CACHE_DURATION;
    return spaceTypesCache;
  } catch (error) {
    logger.error('获取空间类型列表失败:', error);
    // 如果查询失败，返回默认列表
    return ['客厅', '餐厅', '厨房', '公卫', '主卫', '大阳台', '小阳台', '房间', '走道', '入户', 'XX凹位（房间/公卫/主卫）', '楼梯口', '电梯口', '凹位', '其它'];
  }
};

const validation = (schema, options = {}) => {
  return async (ctx, next) => {
    try {
      const data = {
        ...ctx.request.query,
        ...ctx.request.body
      };
      
      // 如果选项中指定需要验证路径参数，则包含 params
      if (options.includeParams) {
        Object.assign(data, ctx.params);
      }
      
      await schema.validateAsync(data, { abortEarly: false });
      
      await next();
    } catch (error) {
      // 字段名中文映射
      const fieldNameMap = {
        username: '用户名',
        password: '密码',
        nickname: '昵称',
        phone: '手机号',
        role: '角色',
        name: '名称',
        unit: '单位',
        price: '单价',
        id: 'ID',
        project_id: '工程ID',
        user_id: '用户ID',
        subproject_id: '子项目ID',
        settlement_id: '结算ID',
        advance_amount: '预支金额',
        workdays: '工作天数',
        remark: '备注',
        status: '状态',
        amount: '金额',
        quantity: '数量',
        length: '长度',
        width: '宽度',
        page: '页码',
        size: '每页数量'
      };
      
      // 将字段名转换为中文
      const translateFieldName = (field) => {
        return fieldNameMap[field] || field;
      };
      
      // 将 Joi 错误转换为友好的中文提示
      let errorMessage = '参数验证失败';
      
      if (error.details && Array.isArray(error.details)) {
        const messages = error.details.map(detail => {
          const field = detail.path.join('.');
          const type = detail.type;
          const fieldName = translateFieldName(field);
          
          // 根据错误类型转换为中文提示
          switch (type) {
            case 'string.empty':
              return `${fieldName}不能为空`;
            case 'string.min':
              return `${fieldName}长度不足`;
            case 'string.max':
              return `${fieldName}长度超出限制`;
            case 'string.pattern.base':
              return detail.message || `${fieldName}格式不正确`;
            case 'any.required':
              return `${fieldName}为必填项`;
            case 'any.only':
              return detail.message || `${fieldName}值不合法`;
            case 'number.base':
              return `${fieldName}必须为数字`;
            case 'number.positive':
              return `${fieldName}必须大于0`;
            case 'number.min':
              return `${fieldName}值太小`;
            case 'number.max':
              return `${fieldName}值太大`;
            default:
              return detail.message || `${fieldName}验证失败`;
          }
        });
        errorMessage = messages.join('，');
      } else if (error.message) {
        errorMessage = error.message;
      }
      
      logger.warn(`参数验证失败: ${errorMessage}`);
      ctx.fail(1001, errorMessage);
    }
  };
};

module.exports = validation;

module.exports.rules = {
  username: Joi.string().pattern(/^[\u4e00-\u9fa5a-zA-Z0-9_]{2,50}$/).required().messages({
    'string.pattern.base': '用户名只能包含中文、字母、数字、下划线，长度2-50位'
  }),
  
  phone: Joi.string().pattern(/^1[3-9]\d{9}$/).required().messages({
    'string.pattern.base': '手机号格式不正确'
  }),
  
  phoneOptional: Joi.string().pattern(/^1[3-9]\d{9}$/).messages({
    'string.pattern.base': '请输入有效的手机号码'
  }),
  
  email: Joi.string().email().required().messages({
    'string.email': '邮箱格式不正确'
  }),
  
  nickname: Joi.string().min(1).max(50).required().messages({
    'string.min': '昵称至少需要1个字符',
    'string.max': '昵称不能超过50个字符'
  }),
  
  nicknameOptional: Joi.string().min(1).max(50).messages({
    'string.min': '昵称至少需要1个字符',
    'string.max': '昵称不能超过50个字符'
  }),
  
  page: Joi.number().integer().min(1).default(1).messages({
    'number.min': '页码必须大于0'
  }),
  
  size: Joi.number().integer().min(1).max(100).default(10).messages({
    'number.min': '每页数量必须大于0',
    'number.max': '每页数量最多100条'
  }),
  
  projectId: Joi.number().integer().positive().required().messages({
    'number.positive': '工程ID必须为正整数'
  }),
  
  length: Joi.number().positive().max(100000).required().messages({
    'number.positive': '长度必须大于0',
    'number.max': '长度不能超过100000厘米（1000米）'
  }),
  
  width: Joi.number().positive().max(100000).required().messages({
    'number.positive': '宽度必须大于0',
    'number.max': '宽度不能超过100000厘米（1000米）'
  }),
  
  area: Joi.number().positive().max(100000000).required().messages({
    'number.positive': '面积必须大于0',
    'number.max': '面积不能超过100000000平方厘米（10000平方米）'
  }),
  
  unitPrice: Joi.number().positive().max(100000).required().messages({
    'number.positive': '单价必须大于0',
    'number.max': '单价不能超过100000元'
  }),
  
  amount: Joi.number().positive().max(1000000).required().messages({
    'number.positive': '金额必须大于0',
    'number.max': '金额不能超过1000000元'
  }),
  
  spaceType: Joi.string().valid(
    '客厅', '餐厅', '厨房', '公卫', '主卫', '大阳台', '小阳台', 
    '房间', '走道', '入户', 'XX凹位（房间/公卫/主卫）', '楼梯口', 
    '电梯口', '凹位', '其它'
  ).required().messages({
    'any.only': '空间类型不合法'
  }),
  
  constructionScheme: Joi.string().valid(
    '蜂窝平面', '半吊', '二级平面', '铝扣平面', '窗帘盒', 
    '发光走边', '水坑', '工程板', '其它方案'
  ).required().messages({
    'any.only': '施工方案不合法'
  }),
  
  role: Joi.string().valid('admin', 'documenter', 'constructor').required().messages({
    'any.only': '角色必须是admin、documenter或constructor'
  }),
  
  status: Joi.string().valid('preparing', 'constructing', 'completed', 'canceled').required().messages({
    'any.only': '状态必须是preparing、constructing、completed或canceled'
  }),
  
  subprojectStatus: Joi.string().valid('pending', 'completed', 'canceled').required().messages({
    'any.only': '子项目状态必须是pending、completed或canceled'
  }),
  
  wageDistributionCode: Joi.string().valid('average', 'custom').required().messages({
    'any.only': '工资分配方式必须是average或custom'
  }),
  
  workdays: Joi.number().min(0).max(31).required().messages({
    'number.min': '工作天数不能为负数',
    'number.max': '工作天数不能超过31天'
  }),
  
  month: Joi.string().pattern(/^\d{4}-\d{2}$/).required().messages({
    'string.pattern.base': '月份格式必须为YYYY-MM'
  }),
  
  date: Joi.date().iso().required().messages({
    'date.format': '日期格式不正确'
  }),
  
  remark: Joi.string().max(500).allow('').messages({
    'string.max': '备注长度不能超过500字符'
  }),
  
  keyword: Joi.string().max(50).allow('').messages({
    'string.max': '关键词长度不能超过50字符'
  }),
  
  userId: Joi.number().integer().positive().required().messages({
    'number.positive': '用户ID必须为正整数'
  }),
  
  subprojectId: Joi.number().integer().positive().required().messages({
    'number.positive': '子项目ID必须为正整数'
  }),
  
  settlementId: Joi.number().integer().positive().required().messages({
    'number.positive': '结算ID必须为正整数'
  }),
  
  advanceAmount: Joi.number().positive().max(100000).required().messages({
    'number.positive': '预支金额必须大于0',
    'number.max': '预支金额不能超过100000元'
  }),
  
  constructors: Joi.array().items(Joi.number().integer().positive()).min(1).required().messages({
    'array.min': '至少需要选择一名施工员',
    'number.positive': '施工员ID必须为正整数'
  })
};