# 公网部署指南（Ubuntu 22.04 + IP + 端口）

目标：把项目部署到一台云服务器，通过 `http://<公网IP>:28089/ai/chat` 访问。

> 本方案不使用域名和 HTTPS，适合简历演示/面试展示。后续要绑域名、上 HTTPS，再加 Nginx 和证书即可。

## 1. 服务器配置建议

- **2 核 2G 内存**起步（应用 + BGE 模型 + MySQL + Redis 约 1-1.5G）
- Ubuntu 22.04 / 24.04 LTS
- 40G 硬盘、3Mbps 带宽
- 国内厂商学生机约 ¥10-30/月；不想备案可选**香港/新加坡**区域
- 安全组/防火墙只开放：**22（SSH）**、**28089（应用）**
- **不要**对公网开放 3306（MySQL）和 6379（Redis）

## 2. 上传文件

在本地项目根目录执行（把 `<IP>` 换成服务器公网 IP）：

```bash
# 先构建
mvn -DskipTests package

# 上传部署文件
ssh root@<IP> "mkdir -p /opt/newbee-ai"
scp target/newbee-mall-1.0.0-SNAPSHOT.jar root@<IP>:/opt/newbee-ai/app.jar
scp src/main/resources/newbee_mall_schema.sql root@<IP>:/opt/newbee-ai/newbee_mall_schema.sql
scp docs/ai-module.sql root@<IP>:/opt/newbee-ai/ai-module.sql
scp deploy/newbee-ai.service root@<IP>:/opt/newbee-ai/newbee-ai.service
scp deploy/setup-ubuntu.sh root@<IP>:/opt/newbee-ai/setup-ubuntu.sh
```

## 3. 在服务器上创建 .env

登录服务器：

```bash
ssh root@<IP>
nano /opt/newbee-ai/.env
```

内容示例（把占位符换成真实值）：

```properties
AI_API_KEY=sk-你的有效密钥
AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_CHAT_MODEL=qwen-plus
AI_REQUIRE_LOGIN=true

DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=newbee_mall_db
DB_USERNAME=newbee
DB_PASSWORD=你的数据库密码
```

> `AI_REQUIRE_LOGIN=true` 很重要：公网环境下只允许登录用户聊天，避免匿名刷你的 API 额度。

## 4. 一键安装

```bash
cd /opt/newbee-ai
sudo DB_PASSWORD='你的数据库密码' bash setup-ubuntu.sh
```

脚本会：

1. 安装 JDK 17、MySQL、Redis
2. 创建 2G swap（2G 内存机器建议保留）
3. 创建数据库/用户并导入表结构
4. 安装并启动 systemd 服务 `newbee-ai`
5. 配置 ufw 放行 22 / 28089

## 5. 验证

```bash
systemctl status newbee-ai
journalctl -u newbee-ai -f
```

浏览器打开：

```text
http://<IP>:28089/ai/chat
```

看到聊天页面即成功。注册/登录商城账号后即可使用 AI 导购（因为 `AI_REQUIRE_LOGIN=true`）。

## 6. 更新版本

本地重新构建后：

```bash
scp target/newbee-mall-1.0.0-SNAPSHOT.jar root@<IP>:/opt/newbee-ai/app.jar
ssh root@<IP> "chown newbee:newbee /opt/newbee-ai/app.jar && systemctl restart newbee-ai"
```

## 7. 安全清单（公网必做）

- [ ] `AI_REQUIRE_LOGIN=true`
- [ ] 云厂商控制台设置模型 API 消费上限/告警
- [ ] 修改商城后台默认管理员密码（默认 `admin / 123456`，务必改掉）
- [ ] 只开放 22 / 28089；MySQL/Redis 只监听 127.0.0.1
- [ ] `.env` 权限 600，且不要提交到 Git
- [ ] 定期 `journalctl -u newbee-ai` 观察异常
- [ ] 不建议把 `/admin` 后台地址公开宣传

## 8. 关于备案

- 国内服务器 + 域名 + 80/443：通常需要 ICP 备案。
- 直接用 `IP:28089` 做演示：一般可用，但不同厂商策略不同。
- 香港/新加坡服务器：免备案，适合快速给面试官看。

## 9. 常见问题

**应用启动失败**：看 `journalctl -u newbee-ai -n 100 --no-pager`，常见原因是 `.env` 配置错误、数据库密码不对、MySQL 未启动。

**内存不足/OOM**：确认 swap 已开启（`free -h`）；或把 `-Xmx768m` 调小到 `-Xmx512m`，或升级到 4G 内存。

**聊天提示未配置/鉴权失败**：检查 `.env` 的 `AI_API_KEY`、`AI_BASE_URL`、`AI_CHAT_MODEL` 是否匹配同一家服务商，改完 `systemctl restart newbee-ai`。

**页面能打开但聊天报错**：先看 `/ai/status`；再查服务日志。
