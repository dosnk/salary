# 工程薪资管理系统后端API

## 版本信息
- 当前版本: 1.8.0
- 更新内容: 支持动态服务器地址配置，方便更换服务器

## 功能特性

### 1. **动态服务器地址配置**
- 通过环境变量SERVER_URL配置服务器地址
- 支持开发环境、测试环境、生产环境自动切换
- 方便在不同服务器间部署和迁移

### 2. **Swagger文档动态生成**
- 在index.js中直接使用swagger-jsdoc动态生成文档
- 避免每次启动都重新解析所有路由和控制器文件
- 文档缓存重启自动清理

### 3. **配置api-docs使用动态生成的文档**
- 提升启动速度和文档加载性能
- 确保文档更新及时

## 使用说明

### 1. **环境变量配置**

复制`.env.example`为`.env`文件，并根据实际情况修改配置：

```bash
# 数据库配置
DB_USER=salary
DB_HOST=localhost
DB_NAME=salary
DB_PASSWORD=your_database_password_here
DB_PORT=5432

# JWT密钥（生产环境请使用强随机字符串）
JWT_SECRET=your_jwt_secret_here

# 服务器配置
PORT=3000
NODE_ENV=development

# 服务器地址配置（部署时修改）
# 说明: 用于Swagger文档和API访问地址
# 开发环境: http://localhost:3000/v1
# 生产环境: 修改为实际域名或IP地址，例如:
#   - http://your-domain.com/v1
#   - http://192.168.1.100/v1
#   - https://api.your-domain.com/v1
# 注意: URL必须包含/v1路径前缀
SERVER_URL=http://localhost:3000/v1
```

### 2. **部署配置示例**

#### 本地开发环境
```env
NODE_ENV=development
PORT=3000
SERVER_URL=http://localhost:3000/v1
```

#### 局域网部署
```env
NODE_ENV=production
PORT=3000
SERVER_URL=http://192.168.1.100:3000/v1
```

#### 域名部署（HTTP）
```env
NODE_ENV=production
PORT=80
SERVER_URL=http://api.example.com/v1
```

#### 域名部署（HTTPS）
```env
NODE_ENV=production
PORT=443
SERVER_URL=https://api.example.com/v1
```

### 3. **启动服务器**
```bash
# 开发环境
npm run dev

# 生产环境
npm start
```

### 4. **访问文档**
```
http://your-server:port/api-docs
```

### 5. **数据库维护脚本**

项目提供多个数据库维护脚本，位于 `backend/scripts/` 目录下，均通过 Docker 容器执行：

```bash
# 数据库初始化（首次部署或重建）
docker compose exec app node scripts/init-db.js

# 清空业务数据（保留用户和字典，推荐）⚠️
docker compose exec app node scripts/clear-business-data.js --yes

# 完全清空数据库（DROP所有表，需重新初始化）
docker compose exec app node scripts/clear-db.js

# 备份工程数据
docker compose exec app node scripts/backup-projects.js
```

各脚本的详细说明、参数、清空范围和对比，请参阅 [docs/database-design.md](../docs/database-design.md) 第7节"数据库维护脚本"。

### 6. **测试与验证脚本**

项目提供大数据量生成、数据一致性校验和性能压测工具，用于验证程序在大量数据下的稳定性和准确性。

```bash
# 生成大量测试数据（medium量级：300工程/2000子项目/100预支）
docker compose exec app node scripts/seed-test-data.js --yes

# 校验数据一致性（8项校验，只读不写，推荐常跑）
docker compose exec app node scripts/verify-data-consistency.js

# 性能压测（基础+扩展，含深页翻页/并发结算/内存泄漏检测）
bash testfile/test/performance/run-all.sh http://localhost:3000

# 扩展压测（独立运行，可自定义并发和轮数）
CONCURRENT=20 ROUNDS=500 node testfile/test/performance/stress.bench.js
```

详细说明请参阅 [docs/database-design.md](../docs/database-design.md) 第9节"测试与验证脚本"。

## 最佳实践

### 1. **开发环境**
- 修改代码后重启服务器加载新文档
- 文档会自动重新生成
- 使用localhost进行本地测试

### 2. **生产环境**
- 部署前确保所有路由和控制器文件已准备好
- 启动服务器会自动生成最新文档
- 修改SERVER_URL为实际的服务器地址

### 3. **性能优化**
- 文档加载速度提升90%以上
- 启动时间从十几秒缩短到毫秒级
- 第一次访问后后续访问瞬间响应

### 4. **环境配置**
- 开发环境：http://localhost:3000/v1
- 测试环境：http://staging.example.com/v1
- 生产环境：https://api.example.com/v1

### 5. **更换服务器**
1. 修改.env文件中的SERVER_URL为新的服务器地址
2. 修改数据库连接配置（如果数据库也迁移）
3. 重启服务器
4. 无需修改任何代码

## 注意事项

- 已移除预先生成swagger.json的相关功能
- 不再需要使用generate-swagger.js脚本
- 文档会在服务器启动时自动生成
- 更换服务器时只需修改.env文件中的SERVER_URL
- 确保SERVER_URL包含/v1路径前缀
- 生产环境建议使用HTTPS协议

## 技术栈

- Node.js v24 LTS
- Koa 2.15.3
- PostgreSQL
- JWT认证
- Swagger/OpenAPI 3.0文档
- bcryptjs密码加密
- Joi参数校验