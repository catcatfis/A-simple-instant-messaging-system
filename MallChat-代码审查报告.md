# MallChat 项目代码审查报告

> 审查范围：`MallChat-main`（Spring Boot 后端 + mallchat-tools 组件库）、`MallChatWeb-main`（Vue3 + TS + Vite 前端）。
> 所有结论均经实际读取源码核实，标注了文件路径与行号。路径均相对 `D:\WorkSpace2025\MallChat`。

---

## 一、总览

| 严重程度 | 数量 | 典型问题 |
| --- | --- | --- |
| 🔴 严重 | 8 | 真实密钥明文入库、OSS 上传无鉴权、秒杀预热接口可被滥用绕过一人一单、前端 token 泄漏到 WS URL |
| 🟠 中等 | 17 | 限流非原子、SecureInvoke 重试无锁、MQ 不可靠发送、WS 重连竞态、动态正则注入、依赖严重过时 |
| 🟡 轻微 | 15 | 重复代码、死代码、TODO 残留、缓存 null、日志/工程规范问题 |

后端 MyBatis XML 未发现 `${}` SQL 注入（全部使用 `#{}`）；前端未发现 v-html/innerHTML 类 XSS 注入点。

---

## 二、严重问题（建议立即处理）

### S1. 真实敏感凭据明文提交且被 git 追踪【配置】

- `MallChat-main/mallchat-chat-server/src/main/resources/application-test.properties`
  - :8 MySQL root 密码 `59074123777`
  - :21-22 微信 appId / secret 明文
  - :46 AI API key `sk_tr_...`
  - :32-33 minioadmin/minioadmin；:39-40 RocketMQ root/123456
- `application-pro.properties` 同样含密码/secret/AI key；仓库根还有一份 `application-test.properties.local` 复制了同样密钥。
- JWT secret 为弱字符串：test 为 `dsfsdfsdfsdfsd`（:15）、pro 为 `dsadsaddddddasas`。

**建议**：立即吊销并轮换全部密钥；配置改用环境变量/外部配置中心（application.yml 已用 `${mallchat.*}` 占位，只需清空 properties 中的真实值）；用 `git filter-repo` 清除历史；`.gitignore` 补充 `*.local`。

### S2. 本地 OSS 上传/下载完全无鉴权【后端】

- `MallChat-main/mallchat-tools/mallchat-oss-starter/.../LocalOssUploadController.java:45-57`：PUT 上传无任何身份校验。
- `LocalOssDownloadController.java:40-74`：GET 下载无鉴权，uid 仅在路径中混淆、可枚举，可遍历下载他人文件。
- 放行机制：`TokenInterceptor.java:62-70` 对 `/oss/**` 直接放行；`LocalOssTemplate.java:36-40` 注释明确"刻意不放在 /capi 下"以躲开拦截器；写文件用 `REPLACE_EXISTING`（:105）可覆盖已知路径文件。

**建议**：上传/下载改用带过期时间+HMAC 的预签名 URL，或放入 `/capi` 走统一鉴权。

### S3. 秒杀预热接口无权限校验，可绕过"一人一单"【后端】

- `~/mall/controller/SeckillController.java:39-44`：`POST /capi/mall/seckill/warmup/{voucherId}` 任意登录用户可调。
- `SeckillServiceImpl.java:109-112`：`warmUpStock` 会 `delete(orderKey)` 清空已下单用户集合并重置库存。
- 影响：反复调用即可绕过 `seckill.lua` 的 SISMEMBER 一人一单判断，实现重复秒杀，并可制造超卖窗口。

**建议**：预热加管理员权限校验（`IRoleService.hasPower`），或改为启动期/后台任务，不暴露 HTTP 端点。

### S4. 秒杀链路：Redis 预扣减与 MQ 投递间无补偿，MQ 用不可靠普通发送【后端】

- `SeckillServiceImpl.java:78-98`：Lua 扣减成功后 `mqProducer.sendMsg(...)`（普通发送）。MQ 发送失败或应用崩溃时，Redis 库存已扣但订单永不落库。
- `SeckillOrderConsumer.java:84-113`：`createOrder` 无 `@Transactional`——扣库存后 insert 抛非 DuplicateKey 异常，消息重试会**再扣一次库存**；DuplicateKey 回补（:109-112）未回删 Redis 一人一单记录，该用户永久无法再抢。
- 另 `onMessage` 中 `tryLock()` 失败直接丢消息（:57-63）。

**建议**：改用 `sendSecureMsg`（落 SecureInvokeRecord）保证最终送达；`createOrder` 加事务；DuplicateKey 分支同步回删 Redis order set 成员；锁失败改短暂重试。

### S5. SecureInvoke 切面吞掉原方法调用，非 void 方法在事务内永远返回 null【后端】

- `mallchat-tools/mallchat-transaction/.../SecureInvokeAspect.java:38-61`：事务内只构建 record 并 `return null`，原方法体不执行，靠 afterCommit 反射重放。
- **建议**：切面中校验方法返回类型必须为 void，否则启动期抛错。

### S6. 前端 token 泄漏到 WebSocket URL 查询串【前端】

- `MallChatWeb-main/src/utils/worker.ts:98`：`new WebSocket(\`${VITE_WS_URL}...?token=${token}\`)`，token 进入代理/服务器访问日志；生产为公网 `wss://api.mallchat.cn`。
- 服务端对应读取在 `HttpHeadersHandler.java:26`。
- 另外 token 明文长期存于 `localStorage`（`websocket.ts:144-145`），无过期策略，XSS/脚本注入即可窃取。

**建议**：改为连接后首条认证消息或 `Sec-WebSocket-Protocol` 传递；token 加过期策略或改内存+刷新令牌。

### S7. TokenExpired 后 HTTP 请求仍复用旧 token【前端】

- `src/services/request.ts:6-23` 的 `computedToken` 闭包缓存 token；`src/utils/websocket.ts:186-192` 处理 TokenExpired 只清了 localStorage，**未调用 `computedToken.clear()`**，此后所有请求的 `Authorization` 头仍带失效 token，造成 401 循环弹错。
- **建议**：该分支补 `computedToken.clear()`。

### S8. getSessionList 空列表崩溃【前端】

- `src/stores/chat.ts:224-228`：`sessionList[0].unreadCount = 0` 与 `data.list[0].roomId` 未判空，新用户/接口异常时直接 TypeError，并阻断初始化流程。
- **建议**：先判 `data.list?.length`。

---

## 三、后端问题（Java / Spring Boot）

### 中等

| # | 问题 | 证据 | 建议 |
| --- | --- | --- | --- |
| B1 | 频控"检查-执行-计数"非原子，并发下必超限 | `AbstractFrequencyControlService.java:36-46`、`TotalCountWithInFixTimeFrequencyController.java:33-55`（先 mget 读、业务执行后普通 INCR） | 判断与自增合并进单个 Lua 脚本 |
| B2 | SecureInvoke 定时重试无分布式锁，多实例重复发 MQ | `SecureInvokeService.java:38-44`（`*/5s` 直接扫表重放）；:97 有 `System.out.println` | `retry()` 加 tryLock，或按 `status+retry_times` 条件更新抢占 |
| B3 | 消息列表对非热房间首次访问 NPE | `ChatServiceImpl.java:187-196`：`contactDao.get(...)` 无记录时 `contact.getLastMsgId()` NPE | 判空返回 empty 页或业务异常 |
| B4 | JWT 续期 read-modify-write 竞态，注释与阈值不符 | `LoginServiceImpl.java:63-69`（注释写"小于一天"，实际 `TOKEN_RENEWAL_DAYS=2`） | Lua 原子实现"当 TTL<X 时 set TTL=Y" |
| B5 | `RedisUtils.del` 使用 KEYS 阻塞命令 | `RedisUtils.java:207, 765`（chat-server 与 common-starter 两份相同） | 改 SCAN 或精确 key 删除 |
| B6 | `RedisUtils.scan` 连接泄漏：迭代异常时连接不归还 | `RedisUtils.java:127-175`（`while(cursor.hasNext())` 在 try 外） | try-with-resources 包 Cursor/Connection |
| B7 | `@Async` 事件监听器吞异常 + NPE 风险 | `MessageMarkListener.java:40-45`、`MessageSendListener.java:74-79`（`messageDao.getById` 不判空即取字段） | 判空+告警日志，关键路径加重试 |
| B8 | 依赖严重过时含已知漏洞 | `pom.xml:19` Spring Boot 2.6.7（EOL）、:34 mysql-connector 8.0.29、:36 jjwt 0.9.1（实际代码用 com.auth0 jwt）、:41 netty 4.1.76、:30 springfox 3.0.0（停更） | 升级 Boot 3.x（Java 17 已满足）或至少 2.7.18；springfox → springdoc |
| B9 | pom 非法 `${version}` 占位符 + 全局 skipTests | `pom.xml:54,59,64` 与 `:27` | 改 `${project.version}`；CI 强制跑测试 |
| B10 | SQL 设计缺索引/唯一约束 | `docs/mallchat.sql:34-51`（message 仅单列索引，缺 `(room_id,id)` 复合索引）；:57-70 message_mark 缺 `(msg_id,uid,type)` 唯一键，点赞可重复插 | 补复合索引与业务唯一键；引入 Flyway 管理 `docs/version/` 增量 SQL |
| B11 | 拦截器仅覆盖 `/capi/**`，且默认 profile 为 test | `InterceptorConfig.java:26-31`；`application.yml:14` `active: test`、:9 SQL stdout 日志 | 生产禁用 stdout SQL；`spring.profiles.active` 不默认 test |

### 轻微

| # | 问题 | 证据 |
| --- | --- | --- |
| B12 | `getMemberList` 的 `@Cacheable` 缓存 null | `ChatServiceImpl.java:232-246`（非大群返回 null 被缓存，永不回源）→ 加 `unless = "#result == null"` |
| B13 | WS 消息解析无防御，非法 JSON 直接断连 | `NettyWebSocketServerHandler.java:125-138`（`JSONUtil.toBean` 抛异常进 `exceptionCaught` close；未知 type 的 switch NPE） |
| B14 | 信任 `X-Real-IP` 头，可伪造绕过 IP 频控 | `HttpHeadersHandler.java:26, 32-36` |
| B15 | 微信回调吞异常、请求体打 info 日志（隐私） | `WxPortalController.java:49-61, 74` |
| B16 | 两套 RedisUtils / 两套 frequency-control 95% 重复，包名不一致 | `~/common/utils/RedisUtils.java`（1164 行） vs `mallchat-tools/.../RedisUtils.java`（1131 行）；`com.catfish.mallchat` vs `com.catfish.frequencycontrol` |
| B17 | 生产 Service 里残留 `main` 方法 | `LoginServiceImpl.java:123-125` |
| B18 | 包名混乱：groupId `com.abin.mallchat` vs 源码 `com.catfish.mallchat`，import 两套并存 | `pom.xml:7`；`UserServiceImpl.java:19-20` 同时 import `com.abin.*` 与 `com.catfish.*` |

---

## 四、前端问题（Vue3 + TS）

### 中等

| # | 问题 | 证据 | 建议 |
| --- | --- | --- | --- |
| F1 | 模块级 `isFirstInit` 永不复位 | `src/stores/chat.ts:19`（登出重登/HMR 后不再初始化会话） | 移入 store 或提供 reset |
| F2 | WS 重连任务竞态：消息重复/丢失 | `src/utils/websocket.ts:79-99`（open 后置 ready=true，但 500ms 后才发送并清空 `#tasks`；未登录分支 `#tasks` 永不清空） | 回调内用快照发送并同步 splice，去掉 500ms 延迟 |
| F3 | 重连上限逻辑自相矛盾 | `worker.ts:42-68`（达上限后 `lockReconnect` 永久 true 吞掉所有事件）；visibilitychange 每次触发 initWS 又清零计数（`websocket.ts:33-36` + `worker.ts:113`），退避形同虚设 | 统一退避策略 |
| F4 | 动态正则注入（崩溃/ReDoS） | `src/components/RenderMessage/text.vue:14`：`new RegExp(\`...${keys.join('|')}...\`)`，keys 来自服务器消息体 | `keys.map(escapeRegExp)` 或字符串匹配 |
| F5 | 链接跳转协议不安全 | `text.vue:19-23`：`url.startsWith('http') ? url : '//' + url`，放行 `httpfoo`，缺 http/https 白名单 | `new URL()` 校验 protocol |
| F6 | messageMap 按房间无限增长（内存泄漏） | `src/stores/chat.ts:34-42, 171-191` | 非活跃房间做 LRU 淘汰 |
| F7 | WS 消息 JSON.parse 无容错 | `websocket.ts:56, 119`（118 行自留 FIXME） | try/catch 包裹 |
| F8 | store 内 useRoute + 硬编码 `route.path === '/'` 判断聊天页 | `chat.ts:22, 329, 335`；与 `global.ts:46` 重复发送 markMsgRead | 用路由元信息/命名路由 |
| F9 | 重复 initWS 造成 socket 本体泄漏 | `worker.ts:91-107`（重连只解绑事件未 `close()` 旧连接） | initConnection 前 `connection?.close()` |
| F10 | devDependencies 含运行时依赖 | `package.json:34, 47`：`@vueuse/core`（useDownload.ts 实际 import）、`xgplayer`；`vite-plugin-imagemin@0.6.1`+`bin-wrapper-china` 新 Node/CI 常装失败 | 归位依赖；imagemin 换 sharp 或移除 |

### 轻微

| # | 问题 | 证据 |
| --- | --- | --- |
| F11 | 死代码：`typeof msg === 'string'` 永远 false | `websocket.ts:101-105`；`virtual.ts:228` `if (!givenIndex)` 把 0 与 undefined 混淆 |
| F12 | mock 残留在生产路径：随机 id 假消息占位，无失败清理、无对账替换 | `src/hooks/useMockMessage.ts` 全文件 + `SendBar/index.vue:159, 207` |
| F13 | scrollToBottom 用 3ms 轮询重试，动态高度下抖动 | `VirtualList/index.tsx:257-267` → 建议 ResizeObserver 聚合刷新 |
| F14 | 全局事件监听无清理（单例下影响小） | `websocket.ts:30-43` |
| F15 | 请求拦截器对所有请求强制 JSON Content-Type；dev/prod 前缀逻辑靠约定 | `request.ts:27, 40`；`baseURL: ''` + dev proxy `/capi` |
| F16 | 图片 src 接受任意 URL，可被用作追踪像素 | `RenderMessage/image.vue:47-48` → 建议域名白名单 |

---

## 五、工程与仓库规范

1. **构建产物/日志混入仓库目录**：`mallchat-chat-server/target/`（526 个文件）残留；仓库根有 `backend*.log`、`logs/`、`logs-8081/8083`、`.idea/`（内含明文数据库连接信息）、`.npm-cache/`、`.pnpm-store/`、`oss-data/` 等。建议 `git rm --cached` + 本地清理。
2. **`.gitignore` 规则互相打架**：`MallChat-main/.gitignore:77` 用 `*.yml` 忽略全部 yml（真实生效的 application.yml 不入库、反而被强制 add 追踪），而含密钥的 `.properties` 却被提交。建议统一"模板入库、真实密钥出库"。
3. **TODO/FIXME 残留 7 处**：如 `RecallMsgHandler.java:59`（消息覆盖问题未实现）、`UserCache.java:116`（二级缓存）、`application-pro.properties:1`。建议建 issue 跟踪。
4. **测试覆盖薄弱**：后端仅 5 个测试类（DaoTest、SensitiveTest、WebSocket 模拟类），且 pom 全局 `skipTests=true`。

---

## 六、已核实无问题的点

- MyBatis XML 全部使用 `#{}`，无 SQL 注入。
- 前端无 v-html/innerHTML/DOMPurify 使用，消息渲染均为插值绑定，无直接 XSS。
- `LockService.executeWithLockThrows` 解锁前有 `isHeldByCurrentThread` 判断，正确。
- `LoginServiceImpl.login` 分布式锁 + setIfAbsent 双重检查逻辑正确；`RedissonLockAspect` `@Order(0)` 先于事务，正确。
- 前端 `.env` 仅含地址无密钥；未发现显式放宽的 CORS 配置。
- JWT 校验结合 Redis 单点比对（`LoginServiceImpl.verify`），支持单设备登录失效，设计合理。

---

## 七、修复优先级建议

1. **当天**：轮换全部泄露密钥（S1）；OSS 上传接口加鉴权（S2）；秒杀预热加管理员校验（S3）。
2. **本周**：秒杀可靠性（S4）、前端 token 三连（S6/S7/S8）、消息列表 NPE（B3）、SecureInvoke 返回值校验（S5）。
3. **迭代内**：限流原子化（B1）、SecureInvoke 重试加锁（B2）、依赖升级（B8）、正则注入与链接协议（F4/F5）、内存泄漏（F6/F9）。
4. **持续**：去重两套 RedisUtils/频控实现、补测试、清理 target/日志/IDE 文件、统一包名。
