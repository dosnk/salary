# 三人行吊顶管理系统 - 后端Docker镜像
# 多阶段构建：安装依赖 → 运行
# 支持热重载：通过 nodemon 监听文件变化自动重启
# 构建时需要宿主机网络：docker compose build --network host 或 docker-compose.yml中配置network: host

# ===== 阶段1: 安装依赖 =====
# 使用 alpine3.20 而非默认的 edge：Alpine 3.20 搭配 Python 3.12，兼容 node-gyp 8.x
# Alpine edge (Python 3.14) 已移除 distutils 模块，会导致 sqlite3 等原生模块编译失败
FROM node:24-alpine3.20 AS deps

WORKDIR /app

# 复制依赖声明文件
COPY package.json package-lock.json* ./

# 安装编译依赖（编译原生模块需要）并安装npm包
# docker-compose.yml中已配置 network: host，使用宿主机网络
# 显式设置NODE_ENV=development确保devDependencies（nodemon等）被安装
ENV NODE_ENV=development

# 配置国内镜像源加速依赖下载，避免预编译二进制下载超时回退到源码编译
# sqlite3 等原生模块通过 prebuild-install 下载预编译包，使用 npmmirror 镜像加速
# prebuild-install 读取环境变量 prebuild_mirror 或 npm_config_prebuild_mirror
RUN npm config set registry https://registry.npmmirror.com
ENV prebuild_mirror=https://npmmirror.com/mirrors
RUN apk add --no-cache python3 build-base \
    && npm install --no-audit --no-fund \
    # 验证关键依赖是否安装成功（失败则构建中止）
    && node -e "require('dotenv'); require('koa'); require('pg'); require('nodemon'); console.log('依赖安装验证通过')" \
    && apk del python3 build-base

# ===== 阶段2: 运行镜像 =====
FROM node:24-alpine3.20

LABEL maintainer="三人行科技"
LABEL description="三人行吊顶管理系统 - 后端服务（支持热重载）"

WORKDIR /app

# 安装运行时依赖
# - libstdc++: 原生模块运行时依赖
# - su-exec: 轻量级权限切换工具（类似gosu），用于从root降权到node用户
RUN apk add --no-cache libstdc++ su-exec

# 从deps阶段复制node_modules到两个位置：
# 1. /app/node_modules: 运行时使用（会被docker-compose卷挂载处理）
# 2. /opt/node_modules_seed: 持久种子副本，不受 ./:/app 绑定挂载影响
#    当匿名卷为空或残缺时，启动脚本会从种子副本恢复
COPY --from=deps /app/node_modules /app/node_modules
RUN cp -r /app/node_modules /opt/node_modules_seed

# 复制应用代码（.dockerignore已排除宿主机node_modules，避免覆盖镜像内的依赖）
COPY . .

# 复制nodemon配置
COPY nodemon.json ./

# 创建必要目录（upload目录运行时由docker-compose挂载到宿主机，此处仅占位）
RUN mkdir -p temp logs upload

# 创建非root用户运行应用，提升安全性
# node:24-alpine镜像已内置node用户(uid=1000)，直接复用
# 确保app目录和子目录归属node用户，避免权限问题
RUN chown -R node:node /app /opt/node_modules_seed

# 注意：不在此处切换USER，容器以root启动以便修复卷挂载目录权限
# 启动脚本中通过su-exec降权到node用户运行应用

# 暴露端口
EXPOSE 3000

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:3000/v1/auth/login || exit 1

# 启动脚本（root启动→修复权限→降权到node运行应用）：
# 1. 修复挂载卷目录权限（绑定挂载和命名卷默认root归属，node用户无法写入）
# 2. 检查node_modules完整性，残缺则从种子副本恢复
# 3. 通过su-exec降权到node用户运行nodemon，应用以非root身份运行
# 安全性：仅在启动瞬间需要root权限修复目录，应用进程本身以node用户运行
CMD ["sh", "-c", "\
  chown -R node:node /app/logs /app/temp /app/upload /app/node_modules 2>/dev/null || true; \
  if [ ! -d /app/node_modules/dotenv ]; then \
    echo 'node_modules不完整，从种子副本恢复...'; \
    rm -rf /app/node_modules; \
    cp -r /opt/node_modules_seed /app/node_modules; \
    chown -R node:node /app/node_modules; \
  fi; \
  exec su-exec node npx nodemon index.js"]