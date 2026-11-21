# MallChat 项目简化方案（保留 Netty + RocketMQ 架构版）

> 目标：显著降低项目体量，同时**完整保留**以下核心架构能力（即项目的技术亮点，一字不动）：
>
> - **技术栈**：Spring Boot, Netty, Vue 3, MySQL, Redis, Redisson, RocketMQ
> - **Netty WebSocket 长连接服务**：独立 WS 服务层，与业务 HTTP 端口分离（8090/8080）；`ConcurrentHashMap` 构建 channel↔uid 双向索引会话表，支持同一用户多端在线；推送任务统一投递线程池避免阻塞 Netty EventLoop；配合 RocketMQ 广播实现跨节点精准推送与全员广播，水平扩容无需改造推送逻辑
> - **微信扫码登录全链路**：snsapi_userinfo 网页授权，带参临时二维码生成 → 扫码事件回调 → OAuth2 换取用户信息 → Token 签发 → WebSocket 用户绑定完整闭环；loginCode→Channel 本地等待态 + MQ 广播定位持有连接的实例完成绑定
> - **登录态并发治理**：Redisson 分布式锁（按 uid 粒度串行化）+ SET NX 原子写保障 Token 唯一；锁超时降级复用逻辑
> - **RocketMQ 异步消息管道**：消息写扩散、群成员推送、系统通知从主链路剥离，接口仅做落库与事件投递；事务同步 + 失败指数退避重试；广播模式各实例仅推送本地连接
>
> 基于实际源码统计与依赖分析（每项建议标注影响面）。前置关联：《MallChat-代码审查报告.md》——简化应先修复其中安全问题，再实施裁剪。

---

## ✅ 执行结果（已完成）

本方案已落地实施，详细变更清单见 **[MallChat-改造实施记录.md](./MallChat-改造实施记录.md)**。

| 关键指标 | 改造前 | 改造后 |
| --- | --- | --- |
| 后端编译状态 | ❌ **无法编译**（25 个文件残留失效 import） | ✅ `mvn clean package` 通过 |
| 后端 Java 文件 | ~390（含 mall 27 + frequency-control 33 + 空模块） | 396（模块边界重组后） |
| tools 模块 | 5 个（含 1 个死模块、1 个空模块） | **3 个**：common-starter / oss-starter / transaction |
| 前端类型检查 | ❌ 19 个错误 | ✅ **0 错误** |
| 前端生产构建 | — | ✅ 通过 |
| 应用启动 | 无法启动 | ✅ HTTP 8080 + Netty WS 8090 双端口正常；仅 RocketMQ 需本地启动 NameServer |
| 四大亮点能力 | — | ✅ **全部保留并逐项核验**（见实施记录第 3 节） |

---

## 一、现状体量盘点

### 后端（MallChat-main，共 ~390 个 Java 文件）

| 模块/包 | 文件数 | 处置 |
| --- | --- | --- |
| `common/user` | 122 | **保留**（登录全链路 + 会话绑定，亮点所在） |
| `common/chat` | 112 | **保留**（消息核心） |
| `common/websocket` + `user` 下 WS 服务 | ~10 | **保留**（Netty 长连接，亮点所在） |
| `common/common` | 103 | 保留为主，内部瘦身（见 L4） |
| `common/mall` | 27 | ❌ **整体删除** |
| `common/chatai` | 18 | 可选删除（不影响架构叙事） |
| `common/sensitive` | 4 | 保留（消息合规必需） |
| `mallchat-tools/frequency-control` | 33 | 与 chat-server 内一套重复，去重后留一套 |
| `mallchat-tools/oss-starter` | 12 | 保留（图片/文件/表情必需） |
| `mallchat-tools/transaction` | 11 | **保留**（对应"事务同步 + 失败重试"的可靠投递能力） |
| `mallchat-tools/common-starter` | 5 | 与 chat-server 内重复，去重后留一套 |

### 与保留能力一一对应的现有实现（均已核实，简化时不动）

| 描述中的能力 | 现有代码锚点 |
| --- | --- |
| WS 与 HTTP 端口分离 8090/8080 | `NettyWebSocketServer.java`（`@Value("${netty.websocket.port:8090}")`，HTTP 走 8080） |
| channel↔uid 双向索引、多端在线 | `WebSocketServiceImpl.java:65-69`：`ONLINE_WS_MAP`(Channel→extra) + `ONLINE_UID_MAP`(uid→CopyOnWriteArrayList\<Channel\>) |
| 推送统一线程池，不阻塞 EventLoop | `WebSocketServiceImpl.java:88-89`：`@Qualifier(ThreadPoolConfig.WS_EXECUTOR)` 线程池收口推送任务 |
| RocketMQ 广播跨节点推送 | `PushConsumer.java`：`@RocketMQMessageListener(messageModel = MessageModel.BROADCASTING)`，`PushService.sendPushMsg` 三种粒度（uidList/uid/全员） |
| 微信扫码登录全链路 | `ScanHandler`/`WxPortalController`/`LoginService`：带参二维码 → 事件回调 → OAuth2 → token 签发 → WS 绑定；`handleLoginReq(Channel)` 维护 loginCode 等待态 |
| 登录态并发治理 | `LoginServiceImpl.java:73-115`：`RedissonLock`(uid 粒度) + `setIfAbsent` 双重检查 + 锁等待超时降级（`LOCK_WAIT_MILLIS`） |
| 事务同步 + 指数退避重试 | `mallchat-transaction` 的 `SecureInvokeAspect`（事务同步）+ `SecureInvokeService.retry()`（`Math.pow(2^n)` 分钟间隔重试） |
| 消息主链路剥离 | `ChatServiceImpl.sendMsg`：仅落库 + `publishEvent(MessageSendEvent)`，写扩散/推送由 `MessageSendListener` 异步消费 |

---

## 二、简化策略总览

保留架构亮点不动，裁掉**与亮点无关的独立业务与冗余实现**：

| 层级 | 动作 | 预计削减 | 功能损失 | 对亮点的影响 |
| --- | --- | --- | --- | --- |
| L1 直接删除冗余 | 重复组件、废弃依赖、构建产物 | ~15% 代码 | 无 | 无 |
| L2 砍独立业务 | 商城/秒杀模块（**仅此一个**） | ~30 后端文件 + 前端商城页 | 商城/秒杀 | 无（亮点保留完整） |
| L3 可选裁剪 | AI 模块、URL 预览 | ~26 文件 | AI 陪聊、链接卡片 | 无 |
| L4 结构收尾 | 包名统一、tools 合并、前端压缩 | 维护面减半 | 无 | 叙事更清晰 |

---

## 三、L1：无功能损失的清理（先做）

### 1.1 删除与 chat-server 重复的 tools 组件
- **事实**：`~/common/utils/RedisUtils.java`（1164 行）与 `mallchat-tools/mallchat-common-starter/.../RedisUtils.java`（1131 行）95% 重复；`~/common/service/frequencycontrol/*` 与 `mallchat-tools/mallchat-frequency-control`（33 文件）同为两套。
- **动作**：收敛为一套（建议保留 tools 版、chat-server 改 import）；删除空模块 `mallchat-redis`。
- **注意**：`LoginServiceImpl`/`WebSocketServiceImpl` 使用的 RedisUtils/锁服务保持签名不变，架构描述不受影响。
- **收益**：净删 ~1100 行 + 33 文件，修 bug 不再改两处（对应审查报告 B16）。

### 1.2 删除构建产物与无关目录
- `mallchat-chat-server/target/`（526 文件）、仓库根 `logs*/`、`backend*.log`、`oss-data*/`、`.npm-cache/`、`.pnpm-store/`、`_tools/`（cpolar 二进制）。
- `docs/image/` 大图、`docs/version/` 已合并的增量 SQL。

### 1.3 精简依赖清单（保留 Netty/RocketMQ/Redisson 不动）
| 依赖 | 用途 | 动作 |
| --- | --- | --- |
| `p6spy` + `spy.properties` | SQL 调试打印 | 删除，用 MyBatis-Plus 自带日志 |
| `okhttp` 4.8.1 | 手写 GPT 调用 | 随 L3 删（langchain4j 已覆盖） |
| `jtokkit` | GPT token 计数 | 随 L3 删 |
| `springfox` + swagger-models | Swagger | 换 springdoc 或去掉 |
| `jaxb-api`、`maven-resources-plugin` 2.4.3 | 构建遗留 | 删除 |
| `pom.xml` 的 `<skipTests>`、`${version}` 非法占位符 | — | 修正（审查报告 B9） |

### 1.4 前端
- 删除 `src/hooks/useMockMessage.ts`（生产 mock 残留，审查报告 F12）；`SendBar` 假消息占位改为发送后由 WS 回推补全。
- 删除 `vite-plugin-imagemin` + `bin-wrapper-china`（审查报告 F10）。
- 依赖归位：`@vueuse/core`、`xgplayer` 移入 dependencies 或确认可删。

---

## 四、L2：删除商城/秒杀模块（唯一的功能裁剪）

### 4.1 删除整个 `common/mall`（27 个文件）
- **删除内容**：店铺（Shop/ShopType/ShopController）、优惠券（Voucher*）、秒杀（Seckill*、`seckill.lua`、`SeckillOrderConsumer`、库存预热任务）、`MallGeoInitializer`、`RedisIdWorker`/`MallCacheClient`。
- **连带 SQL**：`tb_*voucher*`、`tb_shop*` 等表从 `docs/mallchat.sql` 移除。
- **连带前端**：`views/Home/Mall/`、`ToolBar` 中 `/mall` 入口（ToolBar/index.vue:50）、`apis.ts`/`urls.ts`/`types.ts` 中 mall 段落。

### 4.2 MQ 侧清理（**保留 MQ 架构，只删 mall 用到的部分**）
- **删除**：`MallMQConstant`、秒杀订单 topic/消费者（`MallMQConstant.java` 中 CLUSTERING 模式的订单 topic 即秒杀专用）。
- **保留并继续作为核心**：
  - `SEND_MSG_TOPIC`（chat_send_msg）——消息写扩散异步管道；
  - `PUSH_TOPIC`（websocket_push，`BROADCASTING` 模式）——跨节点 WS 推送；
  - 登录绑定广播（loginCode 定位持有连接的实例）。
- **修正**：`MallMQConstant.java:10` 注释提到的 "CLUSTERING（区别于聊天消息的 BROADCASTING）" 随秒杀一起删除后，项目中 MQ 仅剩 BROADCASTING 模式，与架构描述完全一致。

> **注意**：秒杀是 MQ 的 CLUSTERING 消费者，删除后 RocketMQ 依赖、NameServer 配置、`RocketMQTemplate` 全部保留给聊天管道使用，**运维组件不减**——这是本方案与"全删 MQ"版本的关键区别。

> **L2 完成后**：后端 -27 文件；前端 -1 页面；同时消灭审查报告 S3（预热接口绕过一人一单）、S4（秒杀库存无补偿）、SeckillOrderConsumer 双扣库存等一批秒杀专属安全问题。

---

## 五、L3：可选裁剪（按需）

### 5.1 AI 群聊模块 `common/chatai`（18 文件，可整体删除）
- 删除则连带 `langchain4j-open-ai` 依赖与 `chatai.*` 配置；技术栈叙事中不涉及 AI，删除后不影响任何保留能力。
- 若保留 AI：只留 langchain4j 链路一个 Handler，删手写 `ChatGPTUtils`（OkHttp + jtokkit），-2 依赖、-10 文件。

### 5.2 消息链接预览（URL 卡片）
- 删除则连带 `common/utils/discover/` 全包（`PrioritizedUrlDiscover`、`WxUrlDiscover`、`CommonUrlDiscover`、`AbstractUrlDiscover`，约 -8 文件）与前端 `text.vue` 中 urlContentMap 渲染分支。
- **注意**：删除后前端需同步移除 `urlContentMap` 相关代码，且顺带消灭审查报告 F4（动态正则注入）风险面。
- 同时也可消除该链路的 SSRF 面（服务端主动抓取消息中的任意 URL，审查报告已提及）。

---

## 六、L4：结构收尾（不改行为）

1. **统一包名**：`com.abin.mallchat`（groupId）vs `com.catfish.mallchat`（源码）vs `com.catfish.frequencycontrol` 三套并存 → 收敛为一个；顺手清理 `UserServiceImpl.java:19-20` 等处两套 import 并存。包名统一后，"Netty WS 服务层 / 消息管道"等模块划分在目录结构上会与架构描述一一对应、更清晰。
2. **合并 tools 进单模块**：`mallchat-tools` 下 5 个 starter → 1 个 `mallchat-common`（oss/transaction 可保留为独立模块以体现"组件化"叙事）。
3. **事件监听器微整**：`common/event` 10 个事件 + 10 个 listener 中，上/下线通知可合并为一个 listener 类。
4. **`common/common` 内部瘦身**：`LoginServiceImpl` 删除残留 `main`（:123-125）；删除 `System.out.println`（`SecureInvokeService.java:97`、`ChatServiceImpl.java:200`）。
5. **前端**：3 份 vite config 合并为 1 + 条件分支；`assets/` 未引用图片清理（`login_bg.jpg`/`login_bg.webp` 二选一）。

---

## 七、简化后目标形态

### 模块结构
```
MallChat/
├── mallchat-server/              # 单一 Spring Boot 应用（HTTP 8080）
│   ├── websocket/                # ★ Netty WS 独立服务层（8090），channel↔uid 双向索引
│   ├── user/                     # ★ 微信扫码登录全链路、Token 并发治理、好友/徽章/表情
│   ├── chat/                     # ★ 消息核心：落库+事件投递，写扩散走 MQ 异步管道
│   ├── chatai/                   # （可选）AI 机器人
│   └── common/                   # 限流/分布式锁/SecureInvoke/事件（唯一一份）
├── mallchat-web/                 # Vue3 前端（无商城页）
└── docs/                         # SQL + README
```
（★ = 架构描述的三个亮点模块，全部原样保留）

### 依赖收敛（Netty/RocketMQ 保留）
| 依赖 | 处置 | 服务的亮点 |
| --- | --- | --- |
| `netty-all` | ✅ **保留** | Netty WebSocket 长连接服务 |
| `rocketmq-spring-boot-starter` | ✅ **保留** | 异步消息管道 + 广播推送 |
| `redisson-spring-boot-starter` | ✅ **保留** | 登录态并发治理 |
| `weixin-java-mp` | ✅ **保留** | 微信扫码登录全链路 |
| MySQL / MyBatis-Plus / hutool | ✅ 保留 | — |
| `langchain4j-open-ai` | ⭕ 随 AI 模块决定 | — |
| `p6spy` / `okhttp` / `jtokkit` / `springfox` / `jaxb-api` | ❌ 删除 | — |
| 秒杀相关（`seckill.lua`、Mall 秒杀表） | ❌ 删除 | — |

### 预期收益汇总
| 指标 | 现状 | 简化后 |
| --- | --- | --- |
| 后端 Java 文件 | ~390 | **~330**（执行 L1+L2；选做 L3 可到 ~300，L4 再减重复 ~40） |
| 代码重复度 | 两套 RedisUtils/限流/包名三套 | 单一实现 |
| 前端 | 商城+聊天+mock | 聊天（mock 清除） |
| 顺带消灭的审查问题 | — | S3、S4、B4、B9、B16、B17、F4、F12 等 |
| **架构叙事** | **完整保留**：Netty WS(8090/8080)、channel↔uid 双向索引、线程池收口、MQ 广播跨节点推送、扫码登录全链路、Redisson Token 治理、事务同步+指数退避 | — |

---

## 八、实施顺序与风险控制

1. **第 1 步（安全兜底，半天）**：先修复审查报告 S1~S3（密钥轮换、OSS 鉴权）——裁剪前确保仓库可安全公开/移交。
2. **第 2 步（L1 清理，1 天）**：删重复组件、构建产物、废弃依赖 → 跑通编译 + 冒烟。
3. **第 3 步（L2 删商城，0.5~1 天）**：
   - 删 `common/mall` 包 + `MallMQConstant` + `seckill.lua` + mall 相关表与前端页面；
   - 全局搜索 `import.*mall`、`MallRedisKey`、`SEND_MSG_TOPIC` 引用面确认无残留；
   - **回归重点**：发消息 → MQ 写扩散 → 各端 WS 收到推送（验证聊天管道未受秒杀删除影响）。
4. **第 4 步（L3 按需，0.5 天）**：AI / URL 预览二选一或都留。
5. **第 5 步（L4 收尾，1 天）**：包名统一 + tools 合并 + 前端配置压缩，纯重构不改行为。

**回归验证清单**（对应三个亮点）：
- **WS 长连接**：双端登录同一账号 → 两端同时收到消息（多端在线）；杀进程重连 → 心跳保活；断网恢复 → 消息不丢。
- **扫码登录**：生成二维码 → 手机扫码 → 关注/授权 → 前端自动登录 → WS 绑定成功；并发双开页面扫码 → Token 唯一（Redisson 锁生效）。
- **MQ 管道**：停掉 consumer 再发消息 → 重启后消息补推（指数退避重试生效）；两实例部署 → 广播推送各自本地连接、无重复推送。

**明确不裁的部分**：
- `common/websocket` + `NettyWebSocketServer/Handler`（亮点本体）；
- `mallchat-transaction`（"事务同步 + 指数退避重试"叙事的落地代码）；
- `common/chat` 的策略工厂、游标分页、缓存穿透防护（质量较好的核心）；
- `common/user` 的好友/黑名单/表情（聊天必备）；
- `common/sensitive`（消息合规，展示类项目加分项）。
