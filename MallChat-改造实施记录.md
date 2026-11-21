# MallChat 改造实施记录

> 依据《MallChat-项目简化方案.md》与《MallChat-代码审查报告.md》实际执行的改造清单。
> 执行环境：JDK 17 + Maven 3.9.6 + Node/pnpm。
> 验证结论：后端 `mvn clean package` 通过、前端 `vue-tsc` 零错误且生产构建通过、应用可启动（8080/8090 双端口）。

---

## 一、改造概览

| 分类 | 项数 | 说明 |
| --- | --- | --- |
| 🔴 安全修复 | 2 | 密钥泄露、OSS 匿名上传 |
| 🟠 简化裁剪 | 5 | 商城秒杀、死模块、重复实现、废弃依赖 |
| 🟡 缺陷修复 | 9 | NPE、限流竞态、重试重复、前端 token 等 |
| 🔵 工程修正 | 4 | 编译失败、包扫描、tsconfig、类型检查 |

**最重要的一点**：改造前项目**根本无法编译**——25 个文件中残留 `com.abin.mallchat.common.*` 的 import，而对应类早已迁到 `com.catfish.mallchat.*`。这是审查报告中"包名混乱(B18)"的实际后果，已一并修复。

---

## 二、逐项变更清单

### 2.1 安全修复

#### S1｜移除配置中的真实密钥
| 文件 | 变更 |
| --- | --- |
| `application-test.properties` | MySQL 密码、微信 appId/secret、AI API key、OSS/RocketMQ 密钥 → `${MALLCHAT_*}` 环境变量占位符 |
| `application-pro.properties` | 全量重写为环境变量占位；清除 `todo 补充配置` 遗留 |
| `application-test.properties.local`（仓库根） | 清除全部真实值，仅保留模板与填写说明 |
| `MallChat-main/.gitignore` | 移除会忽略整个 `*.yml` 的规则（导致真正生效的 application.yml 无法入库），改为 `*.local` 忽略 |

> ⚠️ **需要你手动完成**：旧密钥已随文件历史存在于磁盘，请**立即到微信公众平台/AI 服务商处吊销并重新生成**。

#### S2｜本地 OSS 上传接口加鉴权
- 新增 `OssUploadTicket.java`：HMAC-SHA256 无状态票据，绑定「过期时间 + 文件路径」。
- `LocalOssTemplate` 在返回 uploadUrl 时附发票据；`OssResp` 增加 `uploadTicket` 字段。
- `LocalOssUploadController` 校验票据（签名、过期、路径绑定），非法返回 403。
- `TokenInterceptor` 放行范围从 `/oss/` 收窄为 `/oss/upload/`（下载仍公开，因对象名含随机 UUID）。
- 前端 `useUpload.ts` / `useEmojiUpload.ts` 在 PUT 时携带 `X-Upload-Ticket` 头。
- `OssProperties` 增加 `uploadTicketSecret`，多实例部署需显式配置。

### 2.2 简化裁剪

#### L2｜删除商城/秒杀模块
| 删除对象 | 数量 |
| --- | --- |
| 后端 `common/mall` 包 | 27 个 Java 文件 |
| `resources/seckill.lua` | 1 |
| 仓库根 `mallchat-mall.sql`（5 张 `tb_*` 商城表） | 1 |
| 前端 `views/Home/Mall/` + 路由 + ToolBar 入口 | 1 页面 |
| 前端 `apis.ts` / `urls.ts` / `types.ts` 中商城段落 | 约 40 行 |

#### L1｜消除重复实现
- **RedisUtils 双份 → 单份**：先给 starter 版补齐缺失的 `integerInc`（限流使用）与 `setIfAbsent`（登录态并发治理使用）两个方法，再切换 9 个业务文件 import，删除 chat-server 内 1164 行副本。另给 `CursorUtils` 补充 import。
- **删除死模块 `mallchat-frequency-control`**：33 个文件，全仓库零引用（实际用的是 chat-server 内的实现）。
- **删除空模块 `mallchat-redis`**：仅有一个空 pom。
- **删除死代码 `ChatGPTUtils`**：手写 OkHttp GPT 调用，无任何外部调用方（已由 LangChain4j 取代）。
- **删除 `spy.properties`**：p6spy 调试配置。

#### L1｜清理废弃依赖
| 依赖 | 处置 | 原因 |
| --- | --- | --- |
| `p6spy` | 删除 | SQL 调试代理，用 MyBatis-Plus 自带日志 |
| `jtokkit` | 删除 | 仅被已删的 `ChatGPTUtils` 使用 |
| `okhttp` | 删除 | 仅被已删的 `ChatGPTUtils` 使用 |
| `maven-resources-plugin:2.4.3` | 删除 | 过旧遗留声明 |

**保留未动**：`netty-all`、`rocketmq-spring-boot-starter`、`redisson-spring-boot-starter`、`weixin-java-mp`（四个都是亮点能力的支撑）。

### 2.3 缺陷修复（对应审查报告）

| 编号 | 问题 | 修复方式 | 文件 |
| --- | --- | --- | --- |
| B3 | 非热房间首次访问 `contact` 为 null 导致 NPE | 判空并抛业务异常"您不在该会话中" | `ChatServiceImpl` |
| B1 | 限流"先读后写"并发下必然超限 | 新增 `RedisUtils.tryAcquire`（Lua 原子"判断+自增+设过期"），抽象类加 `tryAcquireAtomically` 钩子 | `RedisUtils`、`AbstractFrequencyControlService`、`TotalCountWithInFixTimeFrequencyController` |
| B2 | SecureInvoke 定时重试多实例重复重放 | 加 Redisson `tryLock`，仅一个节点执行扫描 | `SecureInvokeService`、`TransactionAutoConfiguration` |
| S5 | `@SecureInvoke` 标注非 void 方法时静默返回 null | 切面校验返回类型，非 void 直接抛异常 | `SecureInvokeAspect` |
| B13 | WS 畸形报文导致断连、未知类型 NPE | try-catch 解析 + 空类型判断，单条报文不影响连接 | `NettyWebSocketServerHandler` |
| B17 | 生产 Service 残留 `main` 方法、`System.out` 调试输出 | 删除 | `LoginServiceImpl`、`ChatServiceImpl`、`SecureInvokeService` |

### 2.4 前端修复

| 编号 | 问题 | 修复方式 |
| --- | --- | --- |
| S6 | token 拼在 WS URL query（进访问日志） | 改为连接后首条 `AUTHORIZE` 消息鉴权；后端新增 AUTHORIZE 分支 |
| — | 断线重连后 token 被清空导致无法再鉴权 | `onConnectClose` 不再置空 token |
| — | 重复 initWS 遗留僵尸 socket | 重连前主动 `close()` 旧连接 |
| S7 | TokenExpired 后仍复用旧 token | 补 `computedToken.clear()` |
| S8 | 会话列表为空时 TypeError | 增加空列表判空直接返回，并改用 `sessionList[0]` 而非 `data.list[0]` |
| F3 | WS 重连队列竞态（消息重复/丢失） | 先取快照再清空队列；连接已断开时保留任务 |
| F4 | 动态正则注入（消息含 `a(b)` 即渲染崩溃） | 新增 `escapeRegExp` 转义链接 |
| F5 | 链接协议不安全（`httpfoo`、`//`） | 用 `new URL()` 校验，仅放行 http/https |
| F11 | WS 消息 JSON.parse 无容错 | 两处均加 try-catch |
| — | 加好友时 uid 可能为 undefined | 发送前判空并提示 |

### 2.5 工程修正

| 问题 | 修复 |
| --- | --- |
| **25 个文件 import 指向不存在的包 → 无法编译** | 删除重复的 `com.abin.mallchat.common.*` import（同文件已有对应 `com.catfish.*` 导入） |
| **启动类包扫描错误**：`scanBasePackages` 只写 `com.abin.mallchat`，业务代码全在 `com.catfish.mallchat` | 改为同时扫描两个包；`@MapperScan` 同步修正 |
| pom 使用非法 `${version}` 占位符 | 改为 `${project.version}` |
| pom 全局 `<skipTests>true</skipTests>` | 移除，允许 CI 跑测试 |
| `tsconfig.json` 的 `lib: ["WebWorker"]` 写在顶层（无效） | 移入 `compilerOptions` 并补全 `ES2022/DOM` |
| JSX 类型报错（缺 `@vue/jsx-runtime`） | 在 `env.d.ts` 做最小 JSX 命名空间声明 |

---

## 三、四大亮点能力核验

全部保留且经源码逐项确认：

### ① Netty WebSocket 长连接服务
| 能力点 | 证据 |
| --- | --- |
| WS 与 HTTP 端口分离 | `NettyWebSocketServer.java`：`@Value("${netty.websocket.port:8090}")`；HTTP `server.port=8080` |
| channel↔uid 双向索引 | `WebSocketServiceImpl.java:65` `ONLINE_WS_MAP`、`:69` `ONLINE_UID_MAP`(uid→`CopyOnWriteArrayList<Channel>`) |
| 多端在线 | 同上，uid 映射到 Channel 列表 |
| 推送统一线程池 | `WebSocketServiceImpl.java:88` `@Qualifier(ThreadPoolConfig.WS_EXECUTOR)` |
| MQ 广播跨节点推送 | `PushConsumer.java`：`messageModel = MessageModel.BROADCASTING` |

### ② 微信扫码登录全链路
| 环节 | 证据 |
| --- | --- |
| snsapi_userinfo 授权 | `WxMsgService.java:38` |
| 扫码事件回调 | `WxMsgService.scan()`：解析 `loginCode` → 发 `LOGIN_MSG_TOPIC` |
| OAuth2 换用户信息 → Token 签发 | `LoginService.login()` |
| loginCode→Channel 等待态 | `WebSocketServiceImpl`：`LOGIN_CODE` key、`handleLoginReq(Channel)`、`scanLoginSuccess(loginCode, uid)` |
| MQ 广播定位持有连接的实例 | `LOGIN_MSG_TOPIC` / `SCAN_MSG_TOPIC` |

### ③ 登录态并发治理
| 能力点 | 证据 |
| --- | --- |
| Redisson 分布式锁（按 uid） | `LoginServiceImpl.java:83` `executeWithLock(TOKEN_LOCK_KEY + uid, ...)` |
| SET NX 原子写 | `:106` `RedisUtils.setIfAbsent(key, newToken, ...)` |
| 锁超时降级复用 | `:36` `LOCK_WAIT_MILLIS=3000`；`:87` 捕获后复用已有 token |

### ④ RocketMQ 异步消息管道
| 能力点 | 证据 |
| --- | --- |
| 主链路仅落库+投递事件 | `ChatServiceImpl.java:107` `publishEvent(MessageSendEvent)` |
| 写扩散异步化 | `MessageSendListener`：`@TransactionalEventListener` + `sendSecureMsg(SEND_MSG_TOPIC)` |
| 事务同步 | `SecureInvokeService`：`registerSynchronization` + `afterCommit` |
| 指数退避重试 | `SecureInvokeService.java:97` `Math.pow(RETRY_INTERVAL_MINUTES, retryTimes)`（2/4/8/16 分钟） |
| 广播模式各实例推本地连接 | `BROADCASTING` + `sendToUid` 只查本地会话表 |

---

## 四、验证方式

```bash
# 后端：编译 + 打包（全部模块）
cd MallChat-main && mvn clean package -DskipTests

# 前端：类型检查 + 生产构建
cd MallChatWeb-main && npx vue-tsc --noEmit && npm run build-only
```

**实际启动验证**（已验证通过）：
```bash
export MALLCHAT_MYSQL_PASSWORD=<你的密码>
export MALLCHAT_JWT_SECRET=<32位以上随机串>
java -jar mallchat-chat-server/target/mallchat-chat-server-1.0-SNAPSHOT.jar
```
验证结果：Tomcat 8080 就绪、Netty WS 8090 就绪、Redis(Redisson) 连接正常、业务 Bean 全部扫描成功。
唯一报错来自 RocketMQ（`connect to 127.0.0.1:9876 failed`），属环境依赖，本地启动 NameServer 即可。

---

## 五、待你确认/后续事项

1. **吊销旧密钥**（必做）：微信 appId/secret、AI API key 已明文存在于文件历史中。
2. **启动 RocketMQ**：本地开发需运行 NameServer + Broker，否则消费者注册失败。
3. **OSS 票据密钥**：多实例部署时需统一配置 `oss.uploadTicketSecret`。
4. **可选继续裁剪**（本方案 L3，未执行）：
   - AI 模块 `common/chatai`（17 文件）：如不需要 AI 陪聊可整包删除；
   - 消息链接预览（约 8 文件）：可一并移除，顺带消除 SSRF 面。
5. **建议补充测试**：当前项目测试覆盖极薄，核心的登录并发、限流原子性建议补单测。
