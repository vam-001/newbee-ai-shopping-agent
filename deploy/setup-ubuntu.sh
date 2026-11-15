#!/usr/bin/env bash
set -euo pipefail

# 在 Ubuntu 22.04/24.04 上安装 NewBee AI Shopping Agent（IP + 端口方案）
# 用法：
#   sudo DB_PASSWORD='你的数据库密码' bash setup-ubuntu.sh
#
# 前置条件：以下文件已经上传到 /opt/newbee-ai/
#   app.jar
#   newbee_mall_schema.sql
#   ai-module.sql
#   newbee-ai.service
#   .env  （至少包含 AI_API_KEY / DB_* 配置；AI_REQUIRE_LOGIN=true）

if [ "$(id -u)" -ne 0 ]; then
  echo "请使用 root 运行：sudo DB_PASSWORD='...' bash setup-ubuntu.sh"
  exit 1
fi

APP_DIR=/opt/newbee-ai
DB_NAME=newbee_mall_db
DB_USER=newbee
DB_PASSWORD="${DB_PASSWORD:-}"

if [ -z "$DB_PASSWORD" ]; then
  echo "缺少 DB_PASSWORD 环境变量"
  exit 1
fi

for f in app.jar newbee_mall_schema.sql ai-module.sql newbee-ai.service .env; do
  if [ ! -f "$APP_DIR/$f" ]; then
    echo "缺少文件：$APP_DIR/$f"
    exit 1
  fi
done

if grep -q '^AI_API_KEY=not-configured' "$APP_DIR/.env"; then
  echo "警告：$APP_DIR/.env 里的 AI_API_KEY 仍是 not-configured，部署后聊天不可用。"
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y openjdk-17-jre-headless mysql-server redis-server

if [ ! -f /swapfile ]; then
  echo "创建 2G swap..."
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

systemctl enable --now mysql redis-server

id -u newbee >/dev/null 2>&1 || useradd -r -s /bin/false newbee
chown -R newbee:newbee "$APP_DIR"
chmod 600 "$APP_DIR/.env"

mysql -e "CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -e "CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';"
mysql -e "ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';"
mysql -e "GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost'; FLUSH PRIVILEGES;"

if ! mysql -N -e "SELECT 1 FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='tb_newbee_mall_goods_info'" | grep -q 1; then
  echo "导入数据库表结构..."
  mysql "${DB_NAME}" < "$APP_DIR/newbee_mall_schema.sql"
  mysql "${DB_NAME}" < "$APP_DIR/ai-module.sql"
else
  echo "数据库已存在业务表，跳过导入（避免覆盖数据）。"
fi

cp "$APP_DIR/newbee-ai.service" /etc/systemd/system/newbee-ai.service
systemctl daemon-reload
systemctl enable --now newbee-ai

if command -v ufw >/dev/null 2>&1; then
  ufw allow 22/tcp
  ufw allow 28089/tcp
  ufw --force enable
fi

echo
echo "部署完成。检查状态："
systemctl --no-pager --lines=10 status newbee-ai || true
echo
echo "日志：journalctl -u newbee-ai -f"
echo "访问：http://<服务器公网IP>:28089/ai/chat"
