# 三人行吊顶管理系统 - 后端Docker镜像
# 多阶段构建：安装依赖 → 运行
# 支持热重载：通过 nodemon 监听文件变化自动重启
# 构建时需要宿主机网络：docker compose build --network host 或 docker-compose.yml中配置network: host

# ===== 阶段1: 安装依赖 =====
FROM node:24-alpine AS deps

WORKDIR /app

# 复制依赖声明文件
COPY package.json package-lock.json* ./

# 安装编译依赖（编译原生模块需要）并安装npm包
# docker-compose.yml中已配置 network: host，使用宿主机网络
RUN apk add --no-cache python3 build-base \
    && npm install \
    && apk del python3 build-base

# ===== 阶段2: 运行镜像 =====
FROM node:24-alpine

LABEL maintainer="三人行科技"
LABEL description="三人行吊顶管理系统 - 后端服务（支持热重载）"

WORKDIR /app

# 安装运行时依赖
RUN apk add --no-cache libstdc++

# 从deps阶段复制node_modules（含nodemon）
COPY --from=deps /app/node_modules ./node_modules

# 复制应用代码
COPY . .

# 复制nodemon配置
COPY nodemon.json ./

# 创建必要目录
RUN mkdir -p temp logs

# 暴露端口
EXPOSE 3000

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:3000/v1/auth/login || exit 1

# 使用nodemon启动，支持热重载
# docker-compose.yml中挂载源码目录后，文件变化会自动触发重启
CMD ["npx", "nodemon", "index.js"]