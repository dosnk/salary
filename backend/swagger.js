const swaggerJSDoc = require('swagger-jsdoc');

let swaggerSpec = null;

const serverUrl = process.env.SERVER_URL || `http://localhost:${process.env.PORT || 3000}`;
const env = process.env.NODE_ENV || 'development';
const serverDescription = env === 'production' ? '生产环境' : (env === 'staging' ? '测试环境' : '开发环境');

const swaggerDefinition = {
  openapi: '3.0.0',
  info: {
    title: '三人行管理系统',
    version: '1.21.0',
    description: '三人行管理系统API文档 - 生产环境版本，支持动态服务器地址配置和灵活日志目录配置，新增工资结算和月度统计功能，支持文档导出功能，新增字典数据接口',
  },
  servers: [
    {
      url: serverUrl,
      description: serverDescription
    }
  ],
  components: {
    securitySchemes: {
      bearerAuth: {
        type: 'http',
        scheme: 'bearer',
        bearerFormat: 'JWT'
      }
    },
    schemas: {
      User: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          username: { type: 'string' },
          nickname: { type: 'string' },
          phone: { type: 'string' },
          role: { type: 'string' },
          created_at: { type: 'string', format: 'date-time' }
        }
      },
      Project: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          name: { type: 'string' },
          user_id: { type: 'integer' },
          total_amount: { type: 'number' },
          salary_distribution: { type: 'string' },
          created_at: { type: 'string', format: 'date-time' },
          updated_at: { type: 'string', format: 'date-time' }
        }
      },
      SubProject: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          project_id: { type: 'integer' },
          space_type: { type: 'string' },
          construction_scheme: { type: 'string' },
          length: { type: 'number' },
          width: { type: 'number' },
          unit_price: { type: 'number' },
          unit_type: { type: 'string' },
          amount: { type: 'number' },
          remark: { type: 'string' },
          created_at: { type: 'string', format: 'date-time' },
          updated_at: { type: 'string', format: 'date-time' }
        }
      },
      SuccessResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: { type: 'object' },
          msg: { type: 'string', example: 'ok' }
        }
      },
      FailResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 1001 },
          data: { type: 'object', nullable: true },
          msg: { type: 'string', example: '参数错误' }
        }
      },
      // 错误码详细定义
      ErrorResponse: {
        type: 'object',
        properties: {
          code: { 
            type: 'integer',
            description: '错误码',
            enum: [
              // 通用错误 (1xxx)
              1001, 1002, 1003, 1004, 1005,
              // 用户错误 (2xxx)
              2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009,
              // 权限错误 (4xxx)
              4001, 4002, 4003, 4004, 4005,
              // 工程错误 (3xxx)
              3001, 3002, 3003, 3004, 3005, 3006, 3007, 3008, 3009, 3010, 3011, 3012, 3013, 3014, 3015,
              // 文件上传错误 (6xxx)
              6001, 6002, 6003, 6004, 6005, 6006, 6007, 6008,
              // 服务错误 (5xxx)
              5001, 5002, 5003, 5004, 5005, 5006,
              // 业务错误 (7xxx)
              7001, 7002, 7003, 7004, 7005
            ]
          },
          data: { type: 'object', nullable: true },
          msg: { type: 'string', description: '错误信息' }
        }
      },
      // 分页响应
      PaginatedResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: {
            type: 'object',
            properties: {
              list: { type: 'array', items: { type: 'object' } },
              total: { type: 'integer', example: 100 },
              page: { type: 'integer', example: 1 },
              size: { type: 'integer', example: 10 },
              hasNext: { type: 'boolean', example: true }
            }
          },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 文件上传响应
      FileUploadResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: {
            type: 'object',
            properties: {
              url: { 
                type: 'string',
                example: 'https://your-domain.com/upload/202512/salary/uuid-filename.jpg'
              }
            }
          },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 登录响应
      LoginResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: {
            type: 'object',
            properties: {
              token: { type: 'string', example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' },
              user: { $ref: '#/components/schemas/User' }
            }
          },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 用户详情响应
      UserDetailResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: { $ref: '#/components/schemas/User' },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 分页用户响应
      PaginatedUserResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: {
            type: 'object',
            properties: {
              list: {
                type: 'array',
                items: { $ref: '#/components/schemas/User' }
              },
              total: { type: 'integer', example: 100 },
              page: { type: 'integer', example: 1 },
              size: { type: 'integer', example: 10 },
              hasNext: { type: 'boolean', example: true }
            }
          },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 分页工程响应
      PaginatedProjectResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: {
            type: 'object',
            properties: {
              list: {
                type: 'array',
                items: { $ref: '#/components/schemas/Project' }
              },
              total: { type: 'integer', example: 100 },
              page: { type: 'integer', example: 1 },
              size: { type: 'integer', example: 10 },
              hasNext: { type: 'boolean', example: true }
            }
          },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 工程详情响应
      ProjectDetailResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: { $ref: '#/components/schemas/Project' },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 工资结算
      WageSettlement: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          settlement_no: { type: 'string', description: '结算编号' },
          start_month: { type: 'string', format: 'date', description: '开始月份' },
          end_month: { type: 'string', format: 'date', description: '结束月份' },
          total_amount: { type: 'number', description: '总金额' },
          advance_amount: { type: 'number', description: '预支金额' },
          actual_amount: { type: 'number', description: '实际金额' },
          settled_by: { type: 'integer', description: '结算人ID' },
          settled_at: { type: 'string', format: 'date-time', description: '结算时间' },
          remark: { type: 'string', description: '备注' }
        }
      },
      // 预支工资
      WageAdvance: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          user_id: { type: 'integer', description: '用户ID' },
          advance_amount: { type: 'number', description: '预支金额' },
          advance_date: { type: 'string', format: 'date', description: '预支日期' },
          settled: { type: 'boolean', description: '是否已结算' },
          settlement_id: { type: 'integer', description: '结算ID' },
          created_by: { type: 'integer', description: '创建人ID' },
          created_at: { type: 'string', format: 'date-time', description: '创建时间' },
          remark: { type: 'string', description: '备注' }
        }
      },
      // 月度统计
      MonthlyStatistics: {
        type: 'object',
        properties: {
          year: { type: 'integer', description: '年份' },
          month: { type: 'integer', description: '月份' },
          project_statistics: {
            type: 'array',
            description: '工程统计',
            items: {
              type: 'object',
              properties: {
                project_id: { type: 'integer' },
                project_name: { type: 'string' },
                completed_count: { type: 'integer', description: '完工数量' },
                completed_amount: { type: 'number', description: '完工金额' },
                first_completed_at: { type: 'string', format: 'date-time', description: '首次完工时间' },
                last_completed_at: { type: 'string', format: 'date-time', description: '最后完工时间' }
              }
            }
          },
          user_statistics: {
            type: 'array',
            description: '用户统计',
            items: {
              type: 'object',
              properties: {
                user_id: { type: 'integer' },
                username: { type: 'string' },
                nickname: { type: 'string' },
                completed_count: { type: 'integer', description: '完工数量' },
                completed_amount: { type: 'number', description: '完工金额' }
              }
            }
          },
          summary: {
            type: 'object',
            description: '汇总信息',
            properties: {
              total_projects: { type: 'integer', description: '总工程数' },
              total_completed_count: { type: 'integer', description: '总完工数量' },
              total_completed_amount: { type: 'number', description: '总完工金额' },
              total_users: { type: 'integer', description: '总用户数' }
            }
          }
        }
      },
      // 月度工资统计
      MonthlyWageStatistics: {
        type: 'object',
        properties: {
          year: { type: 'integer', description: '年份' },
          month: { type: 'integer', description: '月份' },
          wage_distribution: {
            type: 'array',
            description: '工资分配统计',
            items: {
              type: 'object',
              properties: {
                user_id: { type: 'integer' },
                username: { type: 'string' },
                nickname: { type: 'string' },
                total_amount: { type: 'number', description: '总金额' },
                settled_amount: { type: 'number', description: '已结算金额' },
                unsettled_amount: { type: 'number', description: '未结算金额' }
              }
            }
          },
          wage_advances: {
            type: 'array',
            description: '预支工资统计',
            items: {
              type: 'object',
              properties: {
                user_id: { type: 'integer' },
                username: { type: 'string' },
                nickname: { type: 'string' },
                total_advance_amount: { type: 'number', description: '总预支金额' },
                settled_advance_amount: { type: 'number', description: '已结算预支金额' },
                unsettled_advance_amount: { type: 'number', description: '未结算预支金额' }
              }
            }
          },
          wage_settlements: {
            type: 'array',
            description: '工资结算记录',
            items: {
              type: 'object',
              properties: {
                settlement_no: { type: 'string' },
                start_month: { type: 'string', format: 'date' },
                end_month: { type: 'string', format: 'date' },
                total_amount: { type: 'number' },
                advance_amount: { type: 'number' },
                actual_amount: { type: 'number' },
                settled_at: { type: 'string', format: 'date-time' }
              }
            }
          },
          summary: {
            type: 'object',
            description: '汇总信息',
            properties: {
              total_wage_amount: { type: 'number', description: '总工资金额' },
              total_settled_amount: { type: 'number', description: '总已结算金额' },
              total_unsettled_amount: { type: 'number', description: '总未结算金额' },
              total_advance_amount: { type: 'number', description: '总预支金额' },
              total_settlement_count: { type: 'integer', description: '总结算次数' }
            }
          }
        }
      },
      // 工资结算响应
      WageSettlementResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: { $ref: '#/components/schemas/WageSettlement' },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 预支工资响应
      WageAdvanceResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: { $ref: '#/components/schemas/WageAdvance' },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 月度统计响应
      MonthlyStatisticsResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: { $ref: '#/components/schemas/MonthlyStatistics' },
          msg: { type: 'string', example: 'ok' }
        }
      },
      // 月度工资统计响应
      MonthlyWageStatisticsResponse: {
        type: 'object',
        properties: {
          code: { type: 'integer', example: 200 },
          data: { $ref: '#/components/schemas/MonthlyWageStatistics' },
          msg: { type: 'string', example: 'ok' }
        }
      }
    }
  },
  security: [
    {
      bearerAuth: []
    }
  ]
};

const options = {
  swaggerDefinition,
  apis: ['./routes/*.js', './controllers/*.js']
};

// 延迟加载swagger文档
function getSwaggerSpec() {
  if (!swaggerSpec) {
    swaggerSpec = swaggerJSDoc(options);
  }
  return swaggerSpec;
}

module.exports = getSwaggerSpec;