#!/bin/bash
# ================================================
# 三人行吊顶管理系统 - 飞牛NAS部署脚本
# ================================================
#
# 前提条件:
#   1. 飞牛NAS已安装Docker和Docker Compose
#   2. 已将backend目录上传到NAS
#   3. 已修改 .env.docker 中的配置
#
# 使用方法:
#   chmod +x deploy-nas.sh
#   ./deploy-nas.sh install   # 首次安装
#   ./deploy-nas.sh update    # 更新应用
#   ./deploy-nas.sh restart   # 重启服务
#   ./deploy-nas.sh status    # 查看状态
#   ./deploy-nas.sh logs      # 查看日志
#   ./deploy-nas.sh backup    # 备份数据
#   ./deploy-nas.sh uninstall # 卸载

set -e

# 加载环境变量
if [ -f .env.docker ]; then
    export $(grep -v '^#' .env.docker | xargs)
fi

APP_PORT=${APP_PORT:-9393}

case "$1" in
    install)
        echo "========================================="
        echo "  三人行吊顶管理系统 - 首次安装"
        echo "========================================="

        # 检查Docker
        if ! command -v docker &> /dev/null; then
            echo "错误: Docker未安装"
            exit 1
        fi

        # 检查配置文件
        if [ ! -f .env.docker ]; then
            echo "错误: .env.docker 配置文件不存在"
            echo "请复制 .env.docker.example 并修改配置"
            exit 1
        fi

        echo ""
        echo "1. 构建Docker镜像..."
        docker compose build

        echo ""
        echo "2. 启动服务..."
        docker compose up -d

        echo ""
        echo "3. 等待数据库就绪..."
        sleep 10

        echo ""
        echo "4. 初始化数据库..."
        docker compose exec app node scripts/init-db.js

        echo ""
        echo "5. 初始化AI表..."
        docker compose exec app node scripts/init-ai-tables.js

        echo ""
        echo "6. 预设材料数据..."
        docker compose exec app node scripts/seed-materials.js

        echo ""
        echo "========================================="
        echo "  安装完成！"
        echo "  访问地址: http://$(hostname -I | awk '{print $1}'):${APP_PORT}"
        echo "  API文档:  http://$(hostname -I | awk '{print $1}'):${APP_PORT}/api-docs"
        echo "========================================="
        ;;

    update)
        echo "更新应用..."
        docker compose build app
        docker compose up -d app
        echo "更新完成！"
        ;;

    restart)
        echo "重启服务..."
        docker compose restart
        echo "重启完成！"
        ;;

    status)
        docker compose ps
        echo ""
        echo "数据库: $(docker compose exec -T db pg_isready -U ${DB_USER:-salary} 2>&1 || echo '不可用')"
        echo "Redis:  $(docker compose exec -T redis redis-cli ping 2>&1 || echo '不可用')"
        ;;

    logs)
        docker compose logs -f --tail=100 app
        ;;

    backup)
        BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
        mkdir -p "$BACKUP_DIR"
        echo "备份数据库到 $BACKUP_DIR ..."
        docker compose exec -T db pg_dump -U ${DB_USER:-salary} ${DB_NAME:-salary} > "$BACKUP_DIR/database.sql"
        echo "备份完成: $BACKUP_DIR/database.sql"
        ;;

    uninstall)
        echo "警告: 这将删除所有容器和数据！"
        read -p "确认卸载？(y/N): " confirm
        if [ "$confirm" = "y" ]; then
            docker compose down -v
            echo "卸载完成"
        else
            echo "已取消"
        fi
        ;;

    *)
        echo "用法: $0 {install|update|restart|status|logs|backup|uninstall}"
        ;;
esac
