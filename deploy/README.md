# 生产部署手册

从零到小程序上线，按顺序做完这五个阶段。**每一步都有检查点**，检查点不过不要往下走。

---

## 0. 先理解一件事：备案决定整个时间表

上线的时间瓶颈不是技术，是**ICP 备案**。它是唯一一个你无法加速的环节：

| 阶段 | 耗时 | 能不能并行 |
|---|---|---|
| 买域名 + 实名认证 | 1–3 天 | — |
| 提交备案 → 管局通过 | **7–20 个工作日** | 期间可以并行做服务器和部署 |
| 域名解析 + 申请证书 | 30 分钟 | 必须等备案通过 |
| 微信后台配域名 + 提交审核 | 1–3 天 | 必须等证书就绪 |

所以正确的顺序是：**买完域名当天就提交备案**，然后一边等审核一边把后端在服务器上跑通。
不要等备案过了才开始装环境，那会白白多花两周。

⚠️ 备案没通过之前，大陆服务器的 **80 和 443 端口是被封的**，域名打不开。
但用 `http://服务器IP:8080` 直连不受影响 —— 所以阶段一不需要域名也能完整验证。

---

## 1. 买什么

### 服务器

选**轻量应用服务器**（腾讯云 Lighthouse / 阿里云轻量），不要选 ECS/CVM 标准型。
轻量的套餐打包了带宽和流量，账单可预测；标准型带宽单独计费，一不小心比机器还贵。

配置就一个要求：**内存 ≥ 4G**。

原因是这个项目要同时跑 JVM 和 MySQL：

```
MySQL 8        常驻 ~400MB
Spring Boot    -Xmx512m，常驻 ~700MB
Nginx + OS     ~300MB
                ─────────
                约 1.4GB
```

2G 机器塞得进去，但没有余量 —— 一次 `mysqldump` 或者一次 Maven 编译就能触发 OOM Killer 把后端杀掉，
而且现象是「容器莫名其妙重启」，很难联想到是内存。

| 配置 | 价格参考（2026-10） | 适合 |
|---|---|---|
| 2核4G / 5M | 腾讯云学生机 **156 元/年** | ✅ 推荐，学生认证即可，不用抢 |
| 2核4G / 200M峰值不限流量 | 阿里云轻量 ~199–379 元/年 | 带宽大，但要走活动 |
| 2核2G / 3M | 阿里云 ECS 经济型 e **99 元/年，续费同价** | 预算极紧，需手工调小 JVM 和 MySQL |
| 2核2G / 200M峰值 | 两家秒杀 38–68 元/年 | ⚠️ 首年便宜，续费回 459 元/年，差 7 倍 |

**地域**选离学校近的（上海 / 广州 / 北京），班级用户都在一个城市，延迟差别不大。

### 域名

自己的域名是必须的 —— 微信小程序**不接受 IP 地址**。

- `.com` 首年约 60–80 元，续费同价
- `.cn` 首年 10–30 元，但个人注册要实名，且部分后缀不支持个人备案
- 建议 `.com`，省心

### 第一年总成本

| 项 | 金额 |
|---|---|
| 服务器 2核4G | 156 |
| 域名 | 10–80 |
| SSL 证书 | **0**（Let's Encrypt 免费） |
| 备案 | **0**（免费） |
| **合计** | **约 170–240 元/年** |

**真正的成本在第二年**：轻量机的 38/68 元是获客补贴，续费按原价（约 459 元/年）。
如果打算长期挂，优先选标了「续费同价」的机型。

---

## 2. 阶段一：让后端在服务器上跑起来

这一步**不需要域名、不需要备案**。

### 2.1 连上服务器

```bash
ssh root@你的服务器IP
```

首次登录会让你确认指纹、修改密码。云厂商控制台也能重置密码。

先确认内存和地域：

```bash
free -h              # 看 total 那一列
cat /etc/os-release  # 确认是 Ubuntu 22.04/24.04
```

### 2.2 装 Docker

```bash
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker compose version    # 能看到 v2.x 就对了
```

> 国内机器如果下载慢，可以换镜像源：
> `curl -fsSL https://get.docker.com | sh -s -- --mirror Aliyun`

### 2.3 建目录

```bash
mkdir -p /opt/class-assistant/{app,certbot/conf,certbot/www}
cd /opt/class-assistant
```

最终目录结构（`app/` 放 jar，`certbot/` 让证书有地方待）：

```
/opt/class-assistant/
├── docker-compose.yml
├── nginx.conf
├── nginx.http.conf        # 备份用
├── nginx.https.conf       # 阶段三才用
├── .env                   # 权限设 600
├── app/
│   └── class_assistant_server.jar
└── certbot/
    ├── conf/
    └── www/
```

### 2.4 本地打包并上传

在**你自己的电脑**上：

```bash
cd class-assistant/backend/class_assistant_server
mvn -B clean package -DskipTests
ls -lh target/*.jar
```

会生成两个 jar：`class_assistant_server-0.0.1-SNAPSHOT.jar`（可执行，几十 MB）
和 `....jar.original`（原始包，小）。**传那个大的**。

```bash
scp target/class_assistant_server-0.0.1-SNAPSHOT.jar \
    root@你的服务器IP:/opt/class-assistant/app/class_assistant_server.jar
```

配置文件一起传：

```bash
cd ../..    # 回到 class-assistant 根目录
scp deploy/docker-compose.yml deploy/nginx.conf deploy/nginx.http.conf deploy/nginx.https.conf \
    root@你的服务器IP:/opt/class-assistant/
```

### 2.5 配环境变量

回到服务器：

```bash
cd /opt/class-assistant
```

生成一个够强的 JWT 密钥（必须是这一步生成，不要自己编）：

```bash
openssl rand -base64 48
```

然后创建 `.env`：

```bash
cat > .env <<'EOF'
MYSQL_ROOT_PASSWORD=填一个强密码
DB_USER=classapp
DB_PASSWORD=填另一个强密码
MYSQL_BUFFER_POOL=256M
JWT_SECRET=粘贴上一步openssl的输出
WX_APPID=你的小程序AppID
WX_SECRET=你的小程序AppSecret
EOF

chmod 600 .env    # 只有 root 能读
```

> `MYSQL_ROOT_PASSWORD` 和 `DB_PASSWORD` 填完**立刻记到密码管理器**。
> root 密码忘了只能删数据卷重来，数据全丢。

### 2.6 启动

```bash
docker compose up -d
docker compose ps
```

三个容器都应该是 `running`。然后跟一下后端日志：

```bash
docker compose logs -f backend
```

等到出现这行就说明启动成功：

```
Started ClassAssistantServerApplication in x.xxx seconds
```

按 `Ctrl+C` 退出日志（不会停容器）。

**如果后端反复重启**，看它退出前的最后几行 —— 通常是数据库连不上或者密码错了。

### 2.7 导入数据库

把 SQL 传到服务器：

```bash
# 在你自己的电脑上，class-assistant 根目录
scp -r database root@你的服务器IP:/opt/class-assistant/
```

然后在服务器上导入：

```bash
cd /opt/class-assistant

# 建表
docker compose exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" class_assistant' \
  < database/01_schema.sql

# 演示数据（可选。想从空库开始就不要执行这一条）
docker compose exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" class_assistant' \
  < database/02_seed.sql

# 确认表建好了
docker compose exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" class_assistant -e "SHOW TABLES;"'
```

应该看到 8 张表：`t_user` `t_class` `t_student` `t_leave_request` `t_todo` `t_exam` `t_score` `t_seat`。

> ⚠️ `01_schema.sql` 开头有 `DROP TABLE IF EXISTS`。对一个空库无害，
> 但**绝对不要对着已有数据的库重复执行** —— 那是清库操作。

### 2.8 验证

在服务器上：

```bash
# ① 服务活着吗（期望 code=1002「未登录」，这说明拦截器生效了）
curl -s http://127.0.0.1:8080/api/teacher/home/overview

# ② 参数校验生效吗（期望 code=1001「code不能为空」）
curl -s -X POST http://127.0.0.1:8080/api/auth/wx-login \
  -H "Content-Type: application/json" -d '{"code":""}'
```

然后从**你自己的电脑**上再试一次 `http://你的服务器IP:8080/api/teacher/home/overview`。

**打不开就先看云厂商的「安全组 / 防火墙」** —— 默认不放行 8080。临时加一条规则，
但建议只放行你自己的公网 IP，不要 `0.0.0.0/0`。

### ✅ 检查点 1

- `docker compose ps` 三个容器都是 running
- 两条 curl 分别返回 1002 和 1001
- 从外网能用 IP:8080 访问

**到这里后端已经完全可用**，剩下的都是把它"藏"到域名和 HTTPS 后面。

---

## 3. 阶段二：备案

### 前置条件（缺一样都会被打回）

1. 域名已完成**实名认证**（个人备案传身份证），通常 1–3 天
2. 服务器购买时长 **≥ 3 个月**（1 个月的不给备）
3. 服务器是**大陆地域**（香港/海外不能备案，而小程序又必须要备案 —— 死路）
4. 备案主体 = 域名持有者 = 小程序主体，三者必须一致

### 流程

1. 登录云厂商控制台 → 搜「备案」→ 进入备案系统
2. 填主体信息（身份证、手机号、地址）、网站信息（域名、服务内容）
3. 上传证件、人脸核验
4. 提交 → 厂商初审（1–2 工作日，**会打电话核实**，注意接听）
5. 管局审核（7–20 个工作日）
6. 通过后收到备案号，按要求展示在小程序里

### 容易被驳回的两个点

- **网站名称不要用「教育」「培训」「学校」「班级管理」这类词**。
  个人主体备案不能涉及需要前置审批的行业，这类名称大概率被要求补资质。
  填中性的技术类名称（如「个人学习管理系统」）通过率更高。
  备案名称和你的小程序名称不需要一致，不影响使用。
- **服务内容不要选「涉及前置审批」的类目**。选「其他」或「信息服务」即可。

### 备案期间能做什么

- 继续在阶段一的环境上开发和联调（用 IP:8080）
- 把小程序前端构建好、上传为体验版，在**开发者工具**里用「不校验合法域名」调试

---

## 4. 阶段三：域名解析 + HTTPS

### 4.1 解析

在域名控制台加一条 A 记录：

| 主机记录 | 记录类型 | 记录值 | TTL |
|---|---|---|---|
| `api` | A | 你的服务器公网 IP | 600 |

验证（等 1–10 分钟）：

```bash
nslookup api.你的域名.com
# 返回的 Address 应该是你的服务器 IP
```

### 4.2 改 Nginx 的域名

```bash
cd /opt/class-assistant
nano nginx.conf      # 把 server_name api.example.com 改成你的真实域名
docker compose restart nginx
```

### 4.3 申请证书

先确认 HTTP 能通：

```bash
curl -I http://api.你的域名.com/ping
# 期望 HTTP/1.1 200 OK
```

然后申请（**域名和邮箱换成你自己的**）：

```bash
docker run --rm \
  -v /opt/class-assistant/certbot/conf:/etc/letsencrypt \
  -v /opt/class-assistant/certbot/www:/var/www/certbot \
  certbot/certbot certonly --webroot -w /var/www/certbot \
  -d api.你的域名.com \
  --email 你的邮箱@example.com \
  --agree-tos --no-eff-email
```

看到 `Congratulations! Your certificate and chain have been saved at...` 就成了。

证书在 `/opt/class-assistant/certbot/conf/live/api.你的域名.com/`，有效期 90 天。

### 4.4 切到 HTTPS 配置

```bash
cd /opt/class-assistant
cp nginx.https.conf nginx.conf
nano nginx.conf     # 改 3 处：server_name 和两个 ssl_certificate 路径里的域名
docker compose restart nginx
```

出问题想回退：

```bash
cp nginx.http.conf nginx.conf && docker compose restart nginx
```

### 4.5 验证

```bash
# ① ping 通（期望 pong）
curl https://api.你的域名.com/ping

# ② 接口通（期望 code=1002）
curl https://api.你的域名.com/api/teacher/home/overview

# ③ HTTP 自动跳 HTTPS（期望 301）
curl -I http://api.你的域名.com/ping
```

第 ③ 条如果没跳转，说明 `server_name` 没改对。

**证书链必须完整** —— 微信小程序对这点很严格，缺中间证书会直接拒绝请求。
用 `fullchain.pem`（不是 `cert.pem`）就已经处理好了，配置里别改。

可以用 [myssl.com](https://myssl.com) 之类在线检测一下，评级 A 以上就放心。

### 4.6 设置自动续期

证书 90 天过期，忘了续期小程序会突然全线报错。

```bash
cat > /etc/cron.d/certbot-renew <<'EOF'
0 3 * * 1 cd /opt/class-assistant && \
  docker run --rm \
    -v /opt/class-assistant/certbot/conf:/etc/letsencrypt \
    -v /opt/class-assistant/certbot/www:/var/www/certbot \
    certbot/certbot renew --quiet && \
  docker compose exec -T nginx nginx -s reload
EOF
```

每周一凌晨 3 点检查一次，没到期不会做任何事。

### ✅ 检查点 2

- `https://你的域名/ping` 返回 pong
- 浏览器打开没有任何证书警告
- HTTP 自动跳 HTTPS

**这时可以删掉 compose 里的 8080 映射了**（见第 6 节）。

---

## 5. 阶段四：小程序侧

### 5.1 构建时指定线上地址

**这是最容易翻车的一步**：忘了传环境变量，打出来的包还连着 `localhost`，
上传后手机上所有接口都失败，但你在开发者工具里可能看不出来。

推荐用文件而不是命令行参数 —— 建一次就不用管了：

```bash
cd class-assistant/frontend/uni-app/my-mp
cp .env.production.example .env.production
# 编辑 .env.production，把域名换成你的
```

然后构建：

```bash
npm run build:mp-weixin
```

### 5.2 验证产物里没有 localhost

```bash
grep -rn "localhost:8080" dist/build/mp-weixin/ && echo "❌ 还有 localhost，别上传" || echo "✅ 干净"
```

必须输出「✅ 干净」。如果命中了，检查 `.env.production` 的文件名和内容是否正确
（Vite 只认 `.env.production`，不认 `.env.prod`），然后 `rm -rf dist` 重新构建。

再确认一下线上地址真的写进去了：

```bash
grep -ro "https://api\.[a-z0-9.-]*" dist/build/mp-weixin/ | head -3
```

### 5.3 微信后台配置域名

登录 [mp.weixin.qq.com](https://mp.weixin.qq.com) → 开发 → 开发管理 → 开发设置 → 服务器域名：

| 类型 | 填什么 |
|---|---|
| request 合法域名 | `https://api.你的域名.com` |
| uploadFile 合法域名 | `https://api.你的域名.com` |

**两个都要加** —— 名单导入用的是 `uploadFile`，只配 request 的话导入会失败。

注意：
- 必须带 `https://`，不带协议头会报格式错误
- **不能填 IP、不能带路径**（`https://api.xxx.com/api` 是错的，只要域名）
- **每月只能修改 5 次**，想好一次填对

### 5.4 上传审核

1. 微信开发者工具打开 `dist/build/mp-weixin` 目录
2. 点「上传」，填版本号和备注
3. 到公众平台 → 版本管理 → 提交审核
4. 审核通过后点「发布」

首次提审需要先完成**小程序备案**（和小程序主体绑定的那个，和域名备案是两件事），
在公众平台「设置 → 基本设置 → 小程序备案」里提交。

---

## 6. 上线后必须做的安全加固

服务器一上线，22 端口每天会被爆破几百次。这四条必须做：

### 6.1 关掉后端的公网端口

编辑 `docker-compose.yml`，删掉 backend 的这两行：

```yaml
    ports:
      - "8080:8080"      # ← 删掉
```

```bash
docker compose up -d
```

之后后端只能被 Nginx 通过容器内网访问。**这一步不做，等于把没穿衣服的 API 挂公网**
（虽然接口要 token，但绕过 Nginx 就直接跳过了限流和日志）。

### 6.2 改用密钥登录，禁掉密码

在**你自己的电脑**上：

```bash
ssh-keygen -t ed25519 -C "class-assistant"
ssh-copy-id root@你的服务器IP
ssh root@你的服务器IP    # 能免密登录才算成功
```

确认能免密登录后，在服务器上：

```bash
sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart ssh
```

⚠️ **改之前先开另一个终端确认密钥能登录**，否则会把自己锁在外面。

### 6.3 防火墙

```bash
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
ufw status
```

云厂商的「安全组」是另一层，两处都要只放行这三（四）个端口。

### 6.4 装 fail2ban

```bash
apt install -y fail2ban
systemctl enable --now fail2ban
```

---

## 7. 日常运维

### 每天自动备份数据库

```bash
mkdir -p /opt/backup
cat > /etc/cron.daily/backup-class-assistant <<'EOF'
#!/bin/bash
cd /opt/class-assistant
set -a; . ./.env; set +a
docker compose exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --set-gtid-purged=OFF class_assistant' \
  | gzip > /opt/backup/class_assistant_$(date +%F).sql.gz
find /opt/backup -name '*.sql.gz' -mtime +7 -delete
EOF
chmod +x /etc/cron.daily/backup-class-assistant
```

`--set-gtid-purged=OFF` 不能省，否则恢复时会报 `GTID_PURGED cannot be changed`。

手动跑一次确认能出文件：

```bash
/etc/cron.daily/backup-class-assistant && ls -lh /opt/backup/
```

### 看日志

```bash
cd /opt/class-assistant
docker compose logs -f --tail=200 backend    # 后端
docker compose logs --tail=100 nginx         # 网关
docker compose ps                            # 容器状态
docker stats --no-stream                     # 内存/CPU 占用
```

### 更新后端版本

```bash
# 本地重新打包
mvn -B clean package -DskipTests

# 上传并重启（只重启后端，数据库和 Nginx 不动）
scp target/class_assistant_server-0.0.1-SNAPSHOT.jar \
    root@你的服务器IP:/opt/class-assistant/app/class_assistant_server.jar
ssh root@你的服务器IP "cd /opt/class-assistant && docker compose restart backend"
```

### 数据库表结构变更

`database/migrations/` 下的增量脚本要手动执行，别重跑 `01_schema.sql`：

```bash
scp database/migrations/03_alter_student_fields.sql root@IP:/tmp/
ssh root@IP "cd /opt/class-assistant && docker compose exec -T mysql sh -c 'exec mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" class_assistant' < /tmp/03_alter_student_fields.sql"
```

---

## 8. 故障速查

| 现象 | 最可能的原因 | 怎么查 |
|---|---|---|
| 小程序报 `url not in domain list` | 域名没配 / 写错 | 微信后台检查，注意带 `https://` |
| 小程序报 `no_icp_domain` | 域名没备案 | 等备案；备案号和域名要对上 |
| 小程序所有请求失败，电脑上正常 | 构建时没传 `VITE_API_BASE_URL` | `grep localhost dist/build/mp-weixin/` |
| 微信报证书错误 | 用了 `cert.pem` 而不是 `fullchain.pem` | 在 myssl.com 检测证书链 |
| 登录返回 `2002` | `WX_APPID` / `WX_SECRET` 没填 | `docker compose exec backend env \| grep WX` |
| 所有老师登录成同一个人 | `WX_MOCK` 还是 `true` | compose 里应为 `"false"` |
| 上传名单报 413 | Nginx 的 `client_max_body_size` 太小 | 应为 `8m`，看是否改过 |
| 后端启动后立刻退出 | 数据库没起来 / 密码错 | `docker compose logs backend` 找 `SQLException` |
| 502 Bad Gateway | backend 容器挂了 | `docker compose ps` + `restart backend` |
| 容器反复重启，日志被截断 | 内存不足被 OOM Killer 杀 | `dmesg \| grep -i killed`，降 `-Xmx` 或升配 |
| 数据库连接超时 | `SPRING_DATASOURCE_URL` 里写了 `localhost` | 容器内必须写服务名 `mysql` |
| 恢复备份报 GTID 错 | mysqldump 少了参数 | 加 `--set-gtid-purged=OFF` |
| 中文变成问号 | 字符集不对 | 库和表都应是 `utf8mb4` |

---

## 附：目录与文件对照

| 文件 | 作用 | 要改的地方 |
|---|---|---|
| `docker-compose.yml` | 三个容器的编排 | 上线后删 backend 的 `ports` |
| `nginx.conf` | 当前生效的反代配置 | `server_name` 改成你的域名 |
| `nginx.http.conf` | 阶段一的备份 | — |
| `nginx.https.conf` | 阶段三的模板 | 3 处域名 |
| `.env.example` | 环境变量模板 | 复制成 `.env` 再填 |
