// 施工方案单价配置
export const SCHEME_PRICES = {
  '蜂窝平面': { price: 40, unit: 'area' },
  '半吊': { price: 60, unit: 'length' },
  '二级平面': { price: 60, unit: 'area' },
  '铝扣平面': { price: 25, unit: 'area' },
  '窗帘盒': { price: 20, unit: 'length' },
  '发光走边': { price: 30, unit: 'length' },
  '水坑': { price: 35, unit: 'length' },
  '工程板': { price: 20, unit: 'area' }
} as const

// 计算单位类型
export type CalculationUnit = 'area' | 'length' | 'perimeter'

// 单位标签映射
export const UNIT_LABELS: Record<CalculationUnit, string> = {
  area: 'm²',
  length: 'm',
  perimeter: 'm'
}

// 计算标签映射
export const CALCULATION_LABELS: Record<CalculationUnit, string> = {
  area: '面积',
  length: '长度',
  perimeter: '周长'
}

// 分配方式
export const SALARY_DISTRIBUTION_TYPES = {
  AVERAGE: 'average',
  WORK_DAYS: 'work_days'
} as const

export type SalaryDistributionType = typeof SALARY_DISTRIBUTION_TYPES[keyof typeof SALARY_DISTRIBUTION_TYPES]

// 分配方式标签
export const SALARY_DISTRIBUTION_LABELS: Record<SalaryDistributionType, string> = {
  average: '平均',
  work_days: '工时'
}

// 日期范围
export const DATE_RANGE = {
  MIN_YEAR: 2020,
  MAX_YEAR: 2030
}

// 分页配置
export const PAGINATION = {
  DEFAULT_PAGE: 1,
  DEFAULT_SIZE: 10,
  MAX_SIZE: 100
}

// 文件上传配置
export const UPLOAD_CONFIG = {
  MAX_COUNT: 9,
  ACCEPTED_TYPES: ['image/*', 'video/*', 'application/pdf']
}

// 本地存储键名
export const STORAGE_KEYS = {
  LAST_PROJECT_NAME: 'lastProjectName'
}

// 提示消息
export const TOAST_MESSAGES = {
  FEATURE_DEVELOPING: '功能开发中，敬请期待'
}
