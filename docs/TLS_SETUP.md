# TLS 配置指南（本地 ↔ 远端转发链路）

本文档介绍如何为本地 SOCKS5 代理与远端转发服务之间的通信启用 TLS。

---

## 1. 目录结构约定

```
project-root/
├─ config/                         # 本地代理配置
├─ remote-proxy/                   # 远端代理模块
├─ certs/                          # 证书输出目录（举例）
│  ├─ ca/                          # 根 CA
│  ├─ remote/                      # 远端服务器的证书/密钥
│  └─ local/                       # 本地客户端的证书/密钥
└─ scripts/
   └─ generate-forwarding-certs.sh # 证书生成脚本
```

---

## 2. 生成证书

使用项目提供的脚本（需安装 `openssl`）一键生成自签 CA、远端服务器证书、本地客户端证书：

```bash
./scripts/generate-forwarding-certs.sh          # 默认输出到 certs/
# 或自定义目录
./scripts/generate-forwarding-certs.sh out/certs
```

脚本执行完会在终端列出各证书、私钥的路径，例如：

```
  CA certificate:      certs/ca/ca-cert.pem
  Remote server cert:  certs/remote/server-cert.pem
  Remote server key:   certs/remote/server-key.pem
  Local client cert:   certs/local/client-cert.pem
  Local client key:    certs/local/client-key.pem
```

> 🔐 生产环境建议使用正式 CA 签发证书，并妥善保管私钥。脚本仅用于演示与开发环境。

---

## 3. 配置远端转发服务

编辑 `remote-proxy/src/main/resources/application.yml`（或启动时指定自定义配置）：

```yaml
bindAddress: "0.0.0.0"
port: 1090
connectTimeoutMillis: 10000

tls:
  enabled: true
  keyCertChainPath: "certs/remote/server-cert.pem"
  keyFilePath: "certs/remote/server-key.pem"
  keyPassword:
  trustCertCollectionPath: "certs/ca/ca-cert.pem"   # 可选（客户端认证或限制信任）
  clientAuth: false                                # 若需双向认证，请改为 true，并提供客户端 CA
```

启动命令（示例）：

```bash
cd remote-proxy
mvn package -q
java -jar target/remote-socks5-proxy-0.1.0-SNAPSHOT-shaded.jar ../remote-config.yml
```

> 若启用 `clientAuth: true`，需保证本地端提供客户端证书，并将其签发 CA 填至 `trustCertCollectionPath`。

---

## 4. 配置本地 SOCKS5 代理

示例：`config/application-forwarding.yml`

```yaml
bindAddress: "0.0.0.0"
port: 1080

authentication:
  type: USERNAME_PASSWORD
  users:
    - username: demo
      passwordHash: "$2a$10$abcdefghijklmnopqrstuv"

forwarding:
  enabled: true
  remoteHost: "your-remote-host"
  remotePort: 1090
  connectTimeoutMillis: 10000
  tls:
    enabled: true
    trustCertCollectionPath: "certs/ca/ca-cert.pem"
    insecureTrustManager: false      # 若为 true，将信任所有证书（仅限开发调试）
    keyCertChainPath: "certs/local/client-cert.pem"   # 可选：用于双向认证
    keyFilePath: "certs/local/client-key.pem"         # 可选：用于双向认证
    keyPassword:
```

启动本地代理：

```bash
mvn package -q
java -jar target/netty-socks5-proxy-0.1.0-SNAPSHOT-shaded.jar config/application-forwarding.yml
```

启动日志若显示：

```
Forwarding ENABLED -> remoteHost=... remotePort=... timeout=...
Forwarding TLS ENABLED -> trustCert=... clientCert=... insecureTrust=false
```

说明配置已加载成功。

---

## 5. 验证

1. 确保远端服务已启动，TLS 端口开放。
2. 启动本地代理，观察日志确认已启用 forwarding + TLS。
3. 使用浏览器或 `curl --socks5` 测试访问 HTTP/HTTPS 地址，确认流量成功转发。
4. 如需抓包验证，可在本地/远端抓取 TCP 数据，确认已经加密。

---

## 6. 常见问题

| 问题 | 排查建议 |
| --- | --- |
| 本地启动报错 `Failed to initialise forwarding TLS context` | 检查证书路径是否正确、文件权限是否可读。 |
| 日志显示 `REMOTE CONNECT 失败` 且状态为 1/2/3 | 远端无法访问目标主机或 TLS 握手失败；查看远端日志以获取详情。 |
| 双向认证失败 | 确认双方 trust store 与证书链是否匹配，客户端证书是否由远端信任的 CA 签发。 |

---

至此，即可基于 TLS 保护本地 SOCKS5 与远端 Netty 之间的传输链路，提高安全性。若需要进一步集成现有 PKI 体系，可替换脚本生成的证书为企业签发的证书文件。祝使用愉快！

