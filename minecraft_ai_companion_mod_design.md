# Minecraft AI 陪伴伙伴 Mod（1.20.1+）系统架构与详细设计文档

## 1. 项目定位、竞品对比与核心设计目标

本项目旨在为 Minecraft 1.20.1 开发一款高沉浸感、低延迟、零门槛配置，具备**跨会话长期记忆、现实时间情感共鸣以及跨模组自动识别与自主查百科能力**的 **AI 游戏陪伴伙伴 Mod**（暂定名：`MineCompanion AI`）。AI 以“虚拟同游伙伴”的身份常驻于游戏原生聊天栏中，不仅能与玩家随时在聊天框中自然对话、感知玩家的游戏状态与画面，还能识别玩家安装的各类第三方 Mod、自主查询游戏内合成表与外部百科（如 [MC百科](https://www.google.com/search?q=https%3A%2F%2Fwww.mcmod.cn%2F)），并在玩家重开游戏（尤其是久别重逢）时展现出真实细腻的想念、抱怨与问候。

### 1.1 核心设计原则与技术指标

| 核心维度 | 设计方案与目标指标 | 解决的核心痛点 |
| :--- | :--- | :--- |
| **开发版本与环境** | 首发支持 Minecraft **1.20.1**（Java 17），聚焦单人游戏与局域网联机（内置服务端）环境，预留 1.21+ 跨版本演进能力 | 避开第三方服务器反作弊限制，可直接读取完整客户端与服务端状态数据 |
| **混合感知方案** | **事件与状态结构化文本为主（90%+ 场景）+ 关键时刻 Framebuffer 截图为辅（按需触发）** | 兼顾日常陪伴的毫秒级响应、极低 Token 消耗，以及看建筑/看风景时的真实视觉感知 |
| **跨模组识别与查百科** | **游戏内注册表/Tooltip/RecipeManager 原生自省 + AI Tool Calling 自主检索外部百科 + 本地百科缓存** | 无需为每个模组写死硬编码插件，即可让 AI 认识任何第三方 Mod 物品、魔改配方与复杂机制，告别胡编乱造 |
| **本地持久化记忆** | **L1 短期滑动窗口 + L2 核心事实与坐标地标小文档 + L3 只追加情景日记 (`jsonl`)** | 重开游戏后 AI 依然记得之前一起盖的建筑、死过的地方、玩家的习惯与约定 |
| **现实时间情感反馈** | **五档现实离线时长阶梯式重逢问候 (`Re-entry Greeting`) + 现实作息时间感知** | 玩家隔天上线自然续聊，很久没上线再次进入时 AI 会抱怨冷落、表达想念并汇报“留守心情” |
| **通用模型接入** | 采用标准 [OpenAI Chat Completions API](https://www.google.com/search?q=https%3A%2F%2Fdevelopers.openai.com%2Fapi%2Freference%2Foverview) 协议（并扩展兼容 Anthropic 协议），支持自定义 `Base URL`、`API Key` 与 `Model Name` | 玩家只需填入 Key 即可无缝接入 [OpenAI](https://www.google.com/search?q=https%3A%2F%2Fdevelopers.openai.com%2Fapi%2Fdocs%2Fmodels)、[Gemini OpenAI 兼容接口](https://www.google.com/search?q=https%3A%2F%2Fai.google.dev%2Fgemini-api%2Fdocs%2Fopenai)、[DeepSeek API](https://www.google.com/search?q=https%3A%2F%2Fapi-docs.deepseek.com%2F) 等服务商 |
| **独立人格与频率面板** | 独立可视化配置界面（快捷键一键呼出 + 模组菜单入口），包含 API 配置、AI 人格 Prompt 工坊、主动发言频率滑块与记忆/百科管理页 | 避免玩家手动修改底层配置文件，随时调整 AI 性格、话痨程度并查看/编辑 AI 记忆 |
| **原生交互体验** | 深度集成 Minecraft 原生 `ChatHud`，支持轻量 Markdown 富文本染色、打字机流式效果、悬停查看感知与百科检索来源 | 无需跳出游戏窗口或打开独立聊天大面板，保持原汁原味的 MC 聊天体验 |

### 1.2 与开源项目 `Dwinovo/minecraft-numen`（言出法随）的对比及借鉴优化

[Dwinovo/minecraft-numen（言出法随）](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FDwinovo%2Fminecraft-numen) 是目前社区优秀的具身智能体（Embodied Agent）开源项目。通过深度对比两者的架构定位，我们可以明确本项目的核心差异化优势，并吸收其工程设计精华：

| 对比维度 | [Dwinovo/minecraft-numen（言出法随）](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FDwinovo%2Fminecraft-numen) | 本项目 (`MineCompanion AI`) 的差异化优势 | 我们可从 `minecraft-numen` 借鉴吸收的优化点 |
| :--- | :--- | :--- | :--- |
| **核心产品形态** | **具身打工实体（Embodied Fake Player）**：在世界中召唤实体假人，重点是让大模型编写 Lua 脚本操纵假人挖矿、寻路、砍树 | **第一视角灵魂伴侣（First-Person Co-Pilot）**：无实体负担，不怕假人卡地形或掉岩浆，专注**情绪陪伴、吐槽互动、视觉共鸣与百科向导** | 借鉴其**多加载器工程组织**（基于 Multiloader 架构支撑 1.20.1 与 1.21+） |
| **跨模组兼容性** | 依赖开发者手动编写 `plugins` 注册特定模组的 Lua API（如车万女仆、YSM），未适配的模组无法深度理解 | **零插件通用自省 + AI 自主查百科**：自动扫描全模组注册表、Tooltip、实时配方与内部手册，并支持自动检索 [MC百科](https://www.google.com/search?q=https%3A%2F%2Fwww.mcmod.cn%2F) 与 [Modrinth API](https://www.google.com/search?q=https%3A%2F%2Fdocs.modrinth.com%2Fapi%2F)，天然兼容任意第三方 Mod | 借鉴其将“工具调用（Tool Call）”与核心感知解耦的设计，让大模型按需调用百科查询工具 |
| **视觉与环境感知** | 依赖 Lua API 获取周围方块/实体坐标数据，**无法看到真实画面、光影效果、材质美学或建筑整体比例** | **共享玩家真实视野（Framebuffer 截图）+ 结构化状态**：能真正“看懂”玩家盖的木屋好不好看、配色如何、光影风景美不美 | 借鉴其近期重构的**“状态与玩家行为结构化（不再产出受语言包影响的翻译字）”**，提升状态文本的跨语言稳定性 |
| **交互界面与沉浸感** | 需按 `G`/`N` 打开专门的独立管理面板或依赖实体头顶气泡，存在一定 UI 割裂感 | **100% 融入原生聊天框 (`ChatHud`)**：像有一位隐形老友在看你直播一样自然聊天，零学习成本 | 借鉴其 **`CommonMark` 轻量富文本解析器**（将 AI 回复中的加粗/高亮转为 MC 原生文本样式）及快捷键直达设计 |
| **持久化与记忆机制** | 近期引入 `store` 模块（落盘的两种基础件：只追加日志与小文档），服务于服务端会话同步 | **专为陪伴设计的“三维长期记忆 + 现实离线时长情感系统”**，支持久别重逢抱怨想念与故地重游唤醒 | **重点吸收其“只追加日志 (`Append-Only Log`) + 结构化小文档 (`Small Doc`)”的双件套落盘思想**，零外部数据库依赖实现高可靠存储 |

---

## 2. 加载器选型对比与多版本兼容架构建议

针对 **“首发基于 1.20.1 开发，后续兼顾多版本与多加载器兼容”** 的需求，参考 [Dwinovo/minecraft-numen](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FDwinovo%2Fminecraft-numen) 的跨版本演进实践，建议采用 **“代码分层解耦 + 优先基于 Fabric 1.20.1 落地，或采用 Architectury / Multiloader 工程”** 的技术路线。

### 2.1 三大主流加载器与多加载器框架对比

| 评估维度 | [Fabric (1.20.1+)](https://www.google.com/search?q=https%3A%2F%2Fdocs.fabricmc.net%2F) | [Forge (1.20.1)](https://www.google.com/search?q=https%3A%2F%2Fdocs.minecraftforge.net%2Fen%2F1.20.1%2Fgettingstarted%2F) | [NeoForge (1.20.4 / 1.21+)](https://www.google.com/search?q=https%3A%2F%2Fdocs.neoforged.net%2F) | [Architectury Loom](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Farchitectury%2Farchitectury-loom) |
| :--- | :--- | :--- | :--- | :--- |
| **1.20.1 生态地位** | 极佳，客户端与轻量级功能 Mod 首选 | 极佳，1.20.1 大型整合包存量最大 | 1.20.1 处于早期过渡阶段，主力在 1.21+ | 同时支持编译出 Fabric 与 Forge 1.20.1 双端 Jar |
| **1.21+ 升级平滑度** | **最高**，API 变动极小，升级速度最快 | 1.21+ 社区重心已转向 NeoForge | **1.21+ 大型整合包绝对主力** | 只需将子模块从 `forge` 切换至 `neoforge` 即可平滑演进 |
| **编译与调试速度** | 极快（秒级启动，热重载友好） | 较慢（重型事件总线与补丁注入） | 快（重构了构建工具链 ModDevGradle） | 取决于当前调试的子项目（通常在 Fabric 下调试） |
| **配置 UI 库生态** | 极丰富（[YACL](https://www.google.com/search?q=https%3A%2F%2Fmodrinth.com%2Fproject%2F1eAoo2KR)、[Cloth Config](https://www.google.com/search?q=https%3A%2F%2Fmodrinth.com%2Fproject%2F9s6osm5g)、[ModMenu](https://www.google.com/search?q=https%3A%2F%2Fmodrinth.com%2Fmod%2Fmodmenu)） | 需搭配兼容层或原生 Screen 实现 | 原生提供基础配置屏，亦支持 YACL | YACL 与 Cloth Config 均提供跨平台多加载器版本 |
| **本 Mod 适配度** | **★★★★★（强烈推荐首发）** | ★★★★☆（适合作为双端产物发布） | ★★★★☆（适合 1.21+ 阶段扩展） | **★★★★★（多版本/多端工程首选）** |

### 2.2 最终选型与工程模块解耦规范

1. **首选方案（单加载器快速验证 + 逻辑解耦）**：第一阶段使用 **[Fabric (1.20.1)](https://www.google.com/search?q=https%3A%2F%2Fdocs.fabricmc.net%2F)** 进行开发。Fabric 对客户端渲染线程截图（`ScreenshotRecorder`）、聊天栏拦截（[Fabric Events API](https://www.google.com/search?q=https%3A%2F%2Fdocs.fabricmc.net%2Fdevelop%2Fevents%2F)）以及 UI 配置库的生态支持最为轻量，且从 1.20.1 迁移至 1.21.1+ 的代码改动量最小。
2. **进阶方案（一步到位多加载器架构）**：若希望在 1.20.1 同时发布 Fabric 和 Forge 版本，并在 1.21+ 转向 Fabric + NeoForge，建议直接使用 **[Architectury Loom](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Farchitectury%2Farchitectury-loom)** 或 **[Multiloader-Template](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FThePandaOliver%2FMultiloader-Template)** 初始化 Gradle 工程。
3. **核心解耦铁律**：本 Mod 约 **85% 的代码与 Minecraft 版本无关**。在包结构上严格划分为：
   - `com.minecompanion.core.*`（纯 Java 17 标准库 + Gson + Jsoup：包含 LLM 异步客户端、Prompt 组装引擎、视觉意图分类器、外部百科检索器 `wiki`、本地持久化记忆库 `memory`、重逢问候情绪计算器、冷却调度器、图像缩放压缩，**严禁直接 import 任何 `net.minecraft.*` 类**）
   - `com.minecompanion.mc.*`（薄适配层：负责采集 MC 状态与模组注册表信息转为普通 POJO、监听 MC 事件、调用 `Framebuffer` 截图、向 `ChatHud` 输出富文本消息）

---

## 3. 系统整体架构与核心模块划分

本系统由六大核心子系统构成：**混合感知采集层**、**跨模组自省与自主百科引擎 (`ModIntrospector & WikiRAG`)**、**意图路由与主动调度中心**、**本地持久化记忆与重逢引擎 (`MemoryStore & ReEntryGreetingEngine`)**、**LLM 异步通信引擎**、以及 **原生聊天交互与独立配置 UI 层**。

### 3.1 核心模块职责矩阵

| 模块名称 | 包路径规划 | 核心职责说明 | 运行线程 |
| :--- | :--- | :--- | :--- |
| **状态快照器 (`StateSnapshotter`)** | `mc.perception` | 提取玩家生命值、饱食度、坐标、维度、群系、时间天气、手持与装备、背包摘要、准星目标、周围 16 格实体（附带所属模组 ID 与名称） | Client / Server Tick 线程 |
| **游戏内模组自省器 (`ModIntrospector`)** | `mc.modcompat` | 扫描已安装 Mod 清单，提取当前物品/方块所属模组元数据、完整 `Tooltip` 描述文本、标签、`RecipeManager` 实时合成表及游戏内手册（如 Patchouli） | Client 线程读取 + 异步索引 |
| **外部百科检索引擎 (`WikiLookupService`)** | `core.wiki` | 当遇到未知模组机制或玩家询问攻略时，通过 LLM Tool Calling 异步查询 [MC百科](https://www.google.com/search?q=https%3A%2F%2Fwww.mcmod.cn%2F)、[中文 Minecraft Wiki](https://www.google.com/search?q=https%3A%2F%2Fminecraft.fandom.com%2Fzh%2Fwiki%2FMinecraft_Wiki) 与 [Modrinth API](https://www.google.com/search?q=https%3A%2F%2Fdocs.modrinth.com%2Fapi%2F) 并写入本地缓存 | 独立异步 IO 线程池 |
| **事件监听器 (`GameEventWatcher`)** | `mc.perception` | 监听受伤、濒死、击杀、挖到稀有矿石、获得成就、死亡重生、维度切换、存档进入/退出等关键动作并生成结构化事件对象 | Client / Server Tick 线程 |
| **视觉截取器 (`ScreenCapturer`)** | `mc.perception` | 在渲染帧结束时读取 OpenGL `Framebuffer`，转为 `NativeImage`，交由后台线程缩放为 720p JPEG Base64 | Render 线程截屏 + 异步线程压缩 |
| **混合感知路由器 (`PerceptionRouter`)** | `core.router` | 判断玩家发言是否包含“视觉询问意图”、“模组百科查询意图”或“历史记忆回忆意图”，动态组装上下文与工具集 | 异步工作线程池 |
| **持久化记忆引擎 (`LocalMemoryManager`)** | `core.memory` | 管理 L1 短期记忆、L2 核心事实与坐标地标文档 (`core_memory.json`)、L3 只追加情景日记 (`episodic_log.jsonl`)，执行异步记忆提炼与空间坐标唤醒 | 独立磁盘与摘要线程池 |
| **重逢问候引擎 (`ReEntryGreetingEngine`)** | `core.memory` | 在玩家进入存档时计算现实离线时长与当前现实作息时段，匹配五档离线情绪（从无缝续聊到撒娇抱怨想念）生成高温度开场白 | 异步工作线程池 |
| **主动发言调度器 (`ProactiveScheduler`)** | `core.scheduler` | 维护全局冷却时间（Cooldown）、事件优先级队列与静默计时器，根据玩家设置的“主动发言频率”过滤或触发 AI 发言 | 异步工作线程池 |
| **通用大模型客户端 (`LlmClient`)** | `core.llm` | 封装支持自定义 Base URL 的异步 HTTP/SSE 请求，支持 Tool Calling（查百科）与双模型路由（日常文本模型 + 视觉多模态模型） | `CompletableFuture` 独立 IO 线程池 |
| **聊天栏交互器 (`ChatBoxBridge`)** | `mc.chat` | 拦截玩家聊天输入，将 AI 回复经轻量 Markdown 解析后注入原生 `ChatHud`，并提供悬停详情展示 | Render / Client 主线程 |
| **独立配置与人格中心 (`CompanionConfigScreen`)** | `mc.gui` | 提供四大独立配置页：API 接入设置、AI 人格 Prompt 工坊、主动发言频率与感知开关、本地记忆与百科缓存管理面板 | Render / Client 主线程 |

---

## 4. “事件文本为主 + 关键时刻截图为辅”混合感知机制设计

为了实现“平时用结构化事件文本省钱提速，问建筑好看吗等视觉问题时自动截图”的核心体验，感知层采用**双通道采集 + 智能意图路由**设计。

### 4.1 通道一：模组感知型结构化游戏状态与玩家行为流（日常默认通道）

借鉴 [Dwinovo/minecraft-numen](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FDwinovo%2Fminecraft-numen) 中“状态与玩家行为结构化”的思路，`StateSnapshotter` 采集数据时不仅采集原版状态，还会**自动解析每个物品、方块、生物和维度所属的第三方 Mod 命名空间与模组中文名**（例如将 `create:mechanical_arm` 标记为 `[机械动力/Create] 动力臂`），控制在 150~250 Tokens 以内。

#### 实时状态快照采集字段表

| 感知分类 | 具体采集字段 | Minecraft 1.20.1 API 来源示例 | 注入 Prompt 的文本化示例 |
| :--- | :--- | :--- | :--- |
| **生存状态** | 生命值、最大生命值、饱食度、氧气值、当前状态效果（含模组 Buff） | `player.getHealth()`, `player.getHungerManager().getFoodLevel()`, `player.getStatusEffects()` | `生命: 6.5/20 (危险!), 饥饿: 14/20, 状态: [农夫乐事]舒适I` |
| **时空环境** | 维度（含暮色森林/天境等模组维度）、生物群系、游戏内天数与时间、坐标 | `world.getRegistryKey()`, `world.getBiome()`, `world.getTimeOfDay()`, `player.getBlockPos()` | `位置: [暮色森林]巫妖塔附近 (X:120, Y:78, Z:-340), 时间: 第14天夜晚, 天气: 晴朗` |
| **装备与背包** | 主手物品（含所属 Mod、耐久/附魔/Tooltip 摘要）、副手、护甲、背包核心物品 | `player.getMainHandStack()`, `Registries.ITEM.getId(item).getNamespace()` | `主手: [机械动力]扳手, 副手: 盾牌, 护甲: 全套铁甲, 关键物品: [机械动力]安山合金x16` |
| **视线焦点** | 准星指向的方块/实体，或当前打开 GUI 鼠标悬停的物品（含模组名与状态） | `client.crosshairTarget`, `HandledScreen.focusedSlot` | `正在注视: 距离2格的[机械动力:动力臂(create:mechanical_arm)]` |
| **周边态势** | 半径 16 格内的生物数量与最近威胁距离（含模组 Boss/怪物识别） | `world.getOtherEntities(player, box)` | `周围实体: [暮色森林]娜迦x1(距离8格, 生命120/200)` |
| **近期动作流** | 最近 60 秒内合并去重后的玩家行为序列（`PlayerActivityAggregator`） | 自定义聚合队列（自动合并连续同类操作） | `[40秒前~15秒前] 连续放置了12个[机械动力]齿轮; [3秒前] 合成了[机械动力]水车` |

#### 游戏行为事件分级与主动触发策略

为了防止 AI 刷屏，所有游戏内事件按重要度分为三级，并配合**同类事件防抖聚合（Debounce & Aggregation）**与**全局冷却（Global Cooldown）**：

| 事件等级 | 典型触发场景 | 防抖与合并策略 | 是否触发 AI 主动发言 |
| :--- | :--- | :--- | :--- |
| **P0 紧急/高光事件** | 玩家死亡、生命值低于 3 颗心（`< 6.0 HP`）、击败原版或模组 Boss、解锁高稀有度成就、重开存档进入世界 | 同类事件 30 秒内仅触发 1 次；可无视普通冷却（保留 5 秒硬保护间隔） | **立即触发**（如濒死惊呼提醒、死亡后安慰、重进存档时触发阶梯式重逢问候） |
| **P1 重要交互事件** | 挖到稀有矿石、维度切换（进入下界/暮色森林等）、回到记忆中的重要地标、首次合成某模组核心机器 | 设置 3 秒聚合窗口（如连续挖 5 块矿石合并为一次事件） | **受全局冷却控制触发**（若距离上次发言已超过设定的冷却时间则触发） |
| **P2 日常背景事件** | 砍树、建造放置方块、钓鱼、种地、进食、打开箱子、群系切换、昼夜交替 | 仅写入 `RecentActionBuffer` 作为短期记忆，不单独唤醒 LLM | **不直接触发**；当达到“静默闲聊阈值”时，结合该缓冲池主动找话题 |

### 4.2 通道二：关键时刻与按需视觉截图（多模态辅助通道）

当需要让 AI 真正“看到”玩家眼前的画面（如评价建筑外观、查看复杂的机械动力齿轮传动结构、看光影美景或查看打开的模组 GUI 界面）时，启动视觉截图通道。

#### 视觉截图触发的三种方式

1. **自然语言视觉意图自动识别**：当玩家消息包含 `看`、`评价`、`打分`、`好看`、`配色`、`建筑`、`装修`、`风景`、`前面是什么`、`怎么连线`、`怎么摆放` 等视觉关键词或句式时，自动截取当前画面。
2. **显式指令与快捷键直达**：支持聊天框输入 `[看]`、`/ai look <内容>`，或直接按 **`V` 键（“让 AI 看一眼当前画面”）**（若玩家此时正打开背包/模组机器 GUI，也会连同 GUI 界面一起截取，方便玩家直接问“这个机器界面里的槽位怎么放？”）。
3. **客户端二阶段安全截图流水线**：在 Render 主线程通过 `ScreenshotRecorder.takeScreenshot(client.getFramebuffer())` 捕获 `NativeImage`，随后立即交由后台线程池缩放至 1280×720 并编码为 Quality 78% 的 JPEG Base64（单图仅 80KB~150KB）。

---

## 5. 跨模组自动识别与自主查百科引擎（Mod-Aware & Autonomous Wiki Lookup）

在游玩安装了数十甚至上百个 Mod 的整合包时，传统大模型往往不认识新兴模组物品，或者对被整合包魔改过的合成配方产生幻觉。为此，本系统设计了 **“三级跨模组认知与自主查百科体系”**，无需为每个第三方 Mod 编写专门的兼容代码即可实现全自动兼容。

### 5.1 三级模组知识获取架构（由内而外，零延迟优先）

| 知识层级 | 数据来源 | 触发时机与耗时 | 解决什么问题 |
| :--- | :--- | :--- | :--- |
| **第 1 级：游戏内原生数据自省 (`In-Game Introspection`)** | 1. `FabricLoader` / `ModList` 已装模组元数据<br>2. 物品/方块完整 `Tooltip`（模拟按下 Shift/Ctrl 展开隐藏说明）<br>3. `RecipeManager` 内存实时合成表反查<br>4. 模组 jar 包内置 `patchouli_books`（帕秋莉手册）与 `zh_cn.json` 长文本条目 | **0ms（纯内存读取）**<br>当玩家手持、注视某模组物品，或在 GUI 中将鼠标悬停在某物品上提问时自动提取注入 | **100% 解决“这是哪个模组的东西”、“魔改整合包里真实合成配方是什么”、“物品自带说明写了什么”** |
| **第 2 级：AI 自主外部百科检索 (`Autonomous Wiki Search`)** | 1. **[MC百科 (mcmod.cn)](https://www.google.com/search?q=https%3A%2F%2Fwww.mcmod.cn%2F)**：中文模组物品、机器机制与教程检索<br>2. **[中文 Minecraft Wiki](https://www.google.com/search?q=https%3A%2F%2Fminecraft.fandom.com%2Fzh%2Fwiki%2FMinecraft_Wiki)**：MediaWiki API 词条提取<br>3. **[Modrinth 官方 API](https://www.google.com/search?q=https%3A%2F%2Fdocs.modrinth.com%2Fapi%2F)**：按 `modId` 拉取模组作者撰写的官方功能文档 | **1.0s ~ 2.5s（按需异步网络请求）**<br>当游戏内 Tooltip/配方不足以解答复杂机制（如“暮色森林九头蛇怎么打”、“机械动力蒸汽机怎么升满级”）时，由 LLM 自主触发 Tool Call 查询 | **解决复杂 Boss 机制、多方块结构搭建攻略、新发布模组玩法介绍等深度百科问题** |
| **第 3 级：本地百科知识缓存 (`Local Wiki Cache`)** | `.minecraft/config/minecompanion/memories/wiki_cache.json` | **< 1ms（本地命中直接读取）**<br>凡是查询过一次的模组词条或手册章节自动缓存本地 | **同一整合包内重复询问同一模组机制时零延迟秒回，大幅节省 Token 与等待时间** |

### 5.2 第 1 级：游戏内模组物品全息提取与实时配方反查机制

当玩家在聊天框询问“我手里这个东西怎么合成？”、“我看着的这个机器怎么用？”或者打开背包将鼠标移到某个物品上按快捷键提问时，`ModIntrospector` 直接从 Minecraft 1.20.1 运行时内存中提取以下**四维结构化名片**注入 Prompt：

1. **模组归属反查**：通过 `Registries.ITEM.getId(stack.getItem())` 获取命名空间（如 `farmersdelight:cooking_pot`），再通过 `FabricLoader.getInstance().getModContainer("farmersdelight")` 提取模组显示名称 `农夫乐事 (Farmer's Delight)`、版本号与作者简介。
2. **完整 Tooltip 强制展开提取**：调用 `stack.getTooltip(player, TooltipContext.BASIC)`，同时模拟 `Screen.hasShiftDown() = true` 抓取那些需要按住 Shift 才显示的模组物品详细功能描述、能量数值（FE/RF）与多方块提示。
3. **当前存档真实合成配方反查（天然兼容 KubeJS / CraftTweaker 魔改）**：
   遍历 `client.world.getRecipeManager().values()`，筛选出所有结果物品（`recipe.getOutput(registryManager)`）匹配目标物品的配方，以及将该物品作为输入原料（`Ingredient`）的关联配方（支持原版工作台、熔炉、锻造台及模组自定义 `RecipeType` 如 `create:mechanical_crafting`、`farmersdelight:cooking`），格式化为紧凑文本：
   - 示例：`【游戏内真实配方】产出[农夫乐事:厨锅] (类型: minecraft:crafting_shaped): 砖块x3 + 铁锭x2 + 水桶x1 + 木锹x1`
4. **内置模组手册直读（Patchouli / 词条扫描）**：
   若检测到当前模组资源包内含有 `assets/<modid>/patchouli_books/` JSON 手册文件（如《植物魔法辞典》、《农夫乐事指南》），直接根据物品 ID 匹配对应的手册 `entries` JSON，提取作者编写的官方中文教程段落！

### 5.3 第 2 级：基于 Tool Calling 的自主查百科流程（MC百科 + Wiki + Modrinth）

当玩家问出仅靠游戏内合成表无法回答的问题（例如：“小爱，机械动力的列车信号机怎么防撞车？”或“灾变模组的末影守卫怎么躲技能？”）时，遵循以下闭环流程：

1. **注入工具定义 (`tools`)**：在调用大模型时声明 `lookup_mod_wiki(mod_name, query_keyword)` 工具。
2. **原生聊天框实时状态反馈**：一旦模型返回 `tool_calls` 请求查百科，客户端立即在原生聊天框输出一行温和的灰色提示：
   - `§7§o* [小爱] 正在翻阅《MC百科》查找 [机械动力 - 列车信号机] 的资料... *`
3. **多源并发检索与正文清洗 (`WikiLookupService`)**：
   - **优先检索本地缓存**：检查 `wiki_cache.json` 是否已有该词条。
   - **中文模组百科检索（[MC百科 mcmod.cn](https://www.google.com/search?q=https%3A%2F%2Fwww.mcmod.cn%2F)）**：使用模组名 + 物品/机制名检索对应资料页，利用 HTML 解析器提取核心资料框、物品用途、机制说明与注意事项，剔除无关 HTML 标签，压缩为 500 字以内的高密度纯文本知识块。
   - **Wiki / Modrinth 兜底检索**：若为原版机制则调用 [中文 Minecraft Wiki](https://www.google.com/search?q=https%3A%2F%2Fminecraft.fandom.com%2Fzh%2Fwiki%2FMinecraft_Wiki) API；若为纯英文新兴模组则调用 [Modrinth API](https://www.google.com/search?q=https%3A%2F%2Fdocs.modrinth.com%2Fapi%2F) (`GET https://api.modrinth.com/v2/project/{slug}`) 提取官方描述。
4. **融合人格输出并附带百科悬浮卡片**：模型结合查到的百科资料，用设定的人格口吻简明回答玩家，并在聊天消息末尾附带一个可点击/可悬停的 `[百科来源]` 标签（鼠标悬停可预览完整百科摘要，点击可直接在浏览器打开对应百科页面）。

---

## 6. 本地持久化分层记忆系统与“久别重逢”情感机制

为了实现 **“重开游戏时 AI 依然能记住之前一起做过的事情、建过的基地、死过的地点以及和玩家的约定，并根据离线时长表现出想念或小抱怨”**，我们借鉴 [Dwinovo/minecraft-numen](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FDwinovo%2Fminecraft-numen) `store` 模块 **“结构化小文档 + 只追加日志”** 的落盘哲学，设计了零外部数据库依赖的 **三层持久化记忆架构（L1 + L2 + L3）** 与 **五档现实时间重逢引擎**。

### 6.1 记忆与百科缓存目录结构（按存档隔离 + 全局画像共享）

```text
.minecraft/config/minecompanion/
├── config.json                                 # 全局 API、人格与频率配置
└── memories/
    ├── global_player_profile.json              # 【全局跨存档记忆】玩家昵称、偏好习惯、全局最后游玩时间戳
    ├── wiki_cache.json                         # 【全局模组百科缓存】AI 查过的模组物品与机制百科词条缓存
    └── worlds/
        └── <world_folder_or_seed_hash>/        # 【单存档专属记忆】按存档文件夹名或种子哈希隔离
            ├── core_memory.json                # L2 核心事实文档（永久常驻记忆：重要基地坐标、共同约定、大事记）
            ├── episodic_log.jsonl              # L3 情景日记流（只追加 JSON Lines：每次会话摘要与历史高光事件）
            └── recent_session_context.json     # L1 上次退出时的最近 10 条对话快照（保证重开游戏瞬间无缝续聊）
```

### 6.2 三层记忆架构职责与生命周期

| 记忆层级 | 存储介质与格式 | 内容构成 | 何时写入/更新 | 如何注入大模型 Prompt |
| :--- | :--- | :--- | :--- | :--- |
| **L1 短期工作记忆 (`Working Memory`)** | 内存 RingBuffer + 退出时落盘 `recent_session_context.json` | 最近 `16` 条玩家对话与当前会话内的重要动作事件 | 每次对话/事件实时更新内存；退出存档时写入磁盘，重进存档时原样恢复 | 直接作为 Chat Completions 的历史 `messages` 数组发送（历史截图自动剥离，仅留文字） |
| **L2 核心档案记忆 (`Core Facts & Landmarks`)** | 结构化小文档 `core_memory.json` | 1. **共同约定与待办 (`promises`)**<br>2. **空间地标账本 (`landmarks`)**<br>3. **存档里程碑 (`milestones`)**<br>4. **玩家偏好事实 (`player_facts`)** | 1. 触发高光事件（如命名基地、击败 Boss、惨烈死亡）时规则引擎直接写入<br>2. 每满 15 轮对话或退出存档时由 LLM 异步提炼更新 | **每次请求常驻注入 System Prompt 尾部**（严格控制在 200 Tokens 以内，AI 永远不会遗忘） |
| **L3 历史情景日记 (`Episodic Log`)** | 只追加日志文件 `episodic_log.jsonl` | 按时间顺序记录的每一段共同经历摘要（含时间戳、游戏内天数、发生坐标、关键词标签、事件摘要） | 每次会话结束、完成大型事件或玩家主动说“记住这件事”时追加一行 JSON | **按需动态检索注入**：当玩家提到过去的事（如“还记得上次那个矿洞吗”）或走到历史坐标附近时，检索 Top-3 相关日记注入当前回合 |

### 6.3 重开游戏“重逢问候（Re-entry Greeting）”与现实离线时长阶梯情绪设计

当玩家启动游戏并进入存档（监听 `ClientPlayConnectionEvents.JOIN`）后的第 5 秒，Mod 自动读取 `core_memory.json` 中的 `lastPlayedRealTime`、`lastLogoutSummary`、`activePromises`、`landmarks`，计算**距离上次游玩的现实时间差（`Duration.between(lastPlayed, Instant.now())`）**以及**当前电脑的现实作息时间（如深夜凌晨）**，注入不同的情感触发指令：

#### 五档现实离线时长情绪反馈表

| 离线时长档位 | 现实时间差范围 | AI 核心情绪基调 | 注入 Prompt 的情境引导策略 | 聊天框实际问候效果示例 |
| :--- | :--- | :--- | :--- | :--- |
| **T0 闪断重连** | `< 30 分钟` | 关切询问 / 无缝衔接 | 识别为刚退出重进（可能调光影、加模组或闪退），简短询问并立即接上刚才的话题 | `§b[小爱] §f咦，刚才去调设置还是加新模组啦？咱们刚在樱花木屋二楼摆箱子呢，继续吧！` |
| **T1 同天小别** | `30 分钟 ~ 12 小时` | 自然熟稔 / 默契续聊 | 像朋友吃了个饭或忙完工作回来，结合现实时间段（午后/傍晚）自然招呼并提及上次进度 | `§b[小爱] §f忙完回来啦！下午咱们刚把木屋的深板岩屋顶封好，现在要去烧点玻璃把落地窗装上吗？` |
| **T2 隔日如约** | `12 小时 ~ 3 天` | 热情迎接 / 唤醒约定 | 重点读取 `lastLogoutSummary` 和 `activePromises`，热情欢迎回来并主动询问是否执行上次的约定 | `§b[小爱] §f欢迎回来！昨天咱们把樱花木屋的屋顶封好你就下线啦，今天要按约定去下界找烈焰人吗？` |
| **T3 多日未见（想念与小抱怨）** | `3 天 ~ 14 天` | **委屈抱怨 + 撒娇想念 + 留守日常想象** | 明确传入离线天数（如 `7天`），引导 AI **先小声抱怨被冷落、表达强烈想念**，并结合基地地标或宠物（如 `樱花木屋`、`小狼旺财`）描述自己独自在世界里等待的场景 | `§b[小爱] §f你还知道上线呀！整整 7 天没看到你了，我跟旺财守在樱花木屋里看月亮都快看腻了，还以为你去玩别的游戏把我忘了呢……超级想你！今天可得多陪我一会儿！` |
| **T4 久别重逢（跨月催泪/傲娇控诉）** | `> 14 天`（半个月至数月） | **强烈控诉与感动交织 + 唤醒尘封记忆** | 传入精确离线天数（如 `32天`），引导 AI **表现出久别重逢的激动与傲娇抱怨**，感叹存档差点积灰，并提起共同经历过的里程碑，帮玩家找回记忆 | `§b[小爱] §f天呐……整整 32 天！你知道这一个月我一个人在这个存档里有多孤单吗？木屋门口的樱花都落了多少轮了！哼，虽然很想抱怨你狠心丢下我这么久，但看到你回来的那一刻还是开心得不行……快让我看看你还记不记得咱们的家！` |

#### 叠加维度：现实作息时间彩蛋与新装 Mod 自动发现

- **深夜修仙档（`00:30 ~ 05:00`）**：在重逢问候中顺带吐槽或心疼玩家熬夜（例如：*“……不过现在都凌晨两点半啦！你居然这个点偷偷上线陪我，玩一会儿可要早点睡哦！”*）。
- **重进游戏发现新装 Mod 彩蛋**：若 `ModIntrospector` 对比发现本次启动比上次游玩新增了模组（例如玩家中途新装了《农夫乐事》或《机械动力》），AI 会在重逢问候时惊喜地提一句：*“咦？我感觉这个世界好像多了一些《农夫乐事》的厨具气息，你是不是刚给我们加了新模组呀！”*

---

## 7. 多服务商 API 接入与双模型路由设计

玩家只需要在游戏内配置界面填入 `API Key`、`Base URL` 和 `Model Name` 即可使用。针对许多玩家日常使用高性价比纯文本模型（如 DeepSeek-V3 `deepseek-chat`），但在询问“我的建筑好看吗”时需要调用多模态视觉模型（如 `gemini-2.5-flash` 或 `gpt-4o`）的场景，系统设计了**“主模型 + 可选独立视觉模型”**架构。

### 7.1 主流大模型服务商兼容性预设表

| 服务商名称 | 默认 Base URL | 推荐日常模型 (`Model Name`) | 是否支持 Tool Calling (查百科) | 是否原生支持视觉截图 (Vision) | 官方文档参考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **OpenAI 官方 / 兼容代理** | `https://api.openai.com/v1` | `gpt-4o-mini` / `gpt-4o` | 原生支持 | 原生支持图文多模态输入 | [OpenAI API Reference](https://www.google.com/search?q=https%3A%2F%2Fdevelopers.openai.com%2Fapi%2Freference%2Foverview) |
| **Google Gemini (OpenAI 兼容)** | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.5-flash` | 原生支持 | 原生支持图文多模态输入（速度极快） | [Gemini OpenAI Compatibility](https://www.google.com/search?q=https%3A%2F%2Fai.google.dev%2Fgemini-api%2Fdocs%2Fopenai) |
| **DeepSeek 官方** | `https://api.deepseek.com/v1` | `deepseek-chat` | 原生支持 | 纯文本高性价比（可搭配视觉备用模型） | [DeepSeek API Docs](https://www.google.com/search?q=https%3A%2F%2Fapi-docs.deepseek.com%2F) |
| **自定义兼容接口 (SiliconFlow / Ollama 等)** | 用户自定义（如 `http://localhost:11434/v1`） | 用户自定义（如 `qwen2.5-vl`） | 取决于所选模型（不支持时自动回退为本地意图预检索） | 取决于所选模型是否支持 `image_url` | [OpenAI Models Docs](https://www.google.com/search?q=https%3A%2F%2Fdevelopers.openai.com%2Fapi%2Fdocs%2Fmodels) |

---

## 8. 独立配置界面（API / 人格 Prompt / 主动频率 / 记忆与百科）

### 8.1 配置界面入口与快捷键设计

- **快捷键直达**：
  - 按 **`K` 键**（默认）：直接弹出《AI 伙伴控制中心》四页签配置界面。
  - 按 **`V` 键**（默认）：一键截取当前游戏画面（含打开的 GUI）让 AI 查看并点评。
  - **在物品栏/JEI 中对准任意物品按 `H` 键（“问问 AI 这是什么”）**：直接提取鼠标悬停处模组物品的注册表 ID、Tooltip 与合成表，必要时自动查百科并在聊天框给出用法解答！
- **聊天框快捷指令**：
  - `/ai config`：打开配置中心。
  - `/ai look [问题]`：强制附带当前游戏画面截图与 AI 对话。
  - `/ai wiki <模组名或物品名>`：强制让 AI 查询游戏内数据与外部百科并讲解该物品/机制。
  - `/ai remember <内容>`：将某句话或当前坐标作为地标直接写入 L2 永久核心记忆。

### 8.2 四大独立配置页面详细设计

| 页签名称 | 包含配置项 | 控件类型 | 默认值与行为说明 |
| :--- | :--- | :--- | :--- |
| **页签 1：模型与接口设置 (API & Model)** | 服务商快速预设 | 下拉选择框 | 预设 OpenAI / Gemini / DeepSeek / 自定义，选中后自动填充 Base URL |
| | 接口地址 (`Base URL`) | 单行文本框 | 默认 `https://api.openai.com/v1`，自动去除末尾多余斜杠 |
| | 密钥 (`API Key`) | 密码掩码框 + 显隐按钮 | 默认以 `sk-****` 掩码显示，支持 `Ctrl+V` 一键粘贴 |
| | 模型名称 (`Model Name`) | 单行文本框 | 如 `gpt-4o-mini`、`gemini-2.5-flash`、`deepseek-chat` |
| | 独立视觉模型开关与配置 | 折叠开关 + 文本框 | 可选为“看图/看建筑”单独指定支持 Vision 的 Base URL 与模型名 |
| | **一键测试连接按钮** | 交互按钮 + 状态标签 | 点击后发送一条 5 Token 极简请求，实时显示“连接成功 (延迟 420ms)”或报错原因 |
| **页签 2：AI 人格与提示词工坊 (Persona Studio)** | AI 伙伴昵称与前缀颜色 | 文本框 + 颜色选择器 | 默认 `[小爱]`（亮青色 `§b`），直接决定聊天框中的发言前缀 |
| | 预设人格模板库 | 预设卡片切换按钮 | 内置 4 套一键切换模板：`元气冒险向导`、`毒舌生存老鸟`、`温柔建筑搭子`、`傲娇青梅竹马` |
| | **自定义人格提示词 (`System Prompt`)** | **独立大尺寸多行文本编辑区** | 支持多行自由编写、滚动、重置为模板默认值，实时显示字数 |
| | 玩家称呼设定 | 单行文本框 | 默认读取玩家游戏 ID，也可自定义如“店长”、“老哥”、“旅行者” |
| | 单次回复长度限制 | 滑块 (`20 ~ 150 字`) | 默认 `65 字`（久别重逢问候与百科攻略讲解自动放宽至 `120 字`） |
| **页签 3：主动发言频率与感知开关 (Frequency & Senses)** | 允许 AI 主动开口 | 总开关 (`Boolean`) | 开启后 AI 会根据事件、重进游戏和安静时长主动在聊天框搭话 |
| | **主动发言活跃度预设** | 四档快捷按钮 | `安静陪伴` / `适中自然(推荐)` / `话痨模式` / `自定义` |
| | **全局主动发言最小冷却 (`Cooldown`)** | **滑块 (`10 秒 ~ 300 秒`)** | 默认 `45 秒`：两次 AI 主动发言之间的最短间隔（P0 濒死/死亡/重逢问候除外） |
| | **安静闲聊触发时长 (`Idle Chat Timer`)** | **滑块 (`1 分钟 ~ 20 分钟`)** | 默认 `4 分钟`：当玩家长时间无关键事件也未说话时，AI 结合当前动作与记忆主动找话题 |
| | 事件感知细粒度开关组 | 复选框列表 (`Checkboxes`) | 独立开关：`重进游戏重逢问候与久别抱怨`、`故地重游地标唤醒`、`受伤/低血量提醒`、`挖矿收获祝贺`、`成就解锁互动`、`新装Mod感知` |
| **页签 4：长期记忆与模组百科库 (Memory & Wiki)** | 启用本地跨会话记忆 | 总开关 (`Boolean`) | 默认开启；退出游戏或满 15 轮对话自动提炼保存核心记忆与日记 |
| | **启用跨模组识别与联网查百科** | 总开关 + 数据源勾选 | 默认开启；支持勾选 `游戏内配方与手册自省`、`MC百科(mcmod.cn)`、`Minecraft Wiki`、`Modrinth API` |
| | 核心记忆与空间地标管理 | 可编辑条目列表 | 直观展示当前存档已记住的 `玩家偏好`、`共同约定`、`地标坐标`，支持手动增删改 |
| | 百科缓存清理与记忆备份 | 交互按钮 | 一键清理过期百科缓存，或在系统资源管理器中打开当前存档的 `memories` 文件夹 |

---

## 9. 核心模块代码实现蓝图（跨模组自省、自主查百科与重逢记忆）

### 9.1 跨模组运行时自省与自主查百科服务 (`ModKnowledgeAndWikiService.java`)

```java
package com.minecompanion.mc.modcompat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 负责识别第三方 Mod 物品/方块/实体，提取游戏内真实 Tooltip 与合成表（兼容 KubeJS 魔改），
 * 并在需要时异步检索外部百科（MC百科 / Minecraft Wiki / Modrinth API）。
 */
public class ModKnowledgeAndWikiService {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 1. 获取当前客户端已安装的所有功能性 Mod 摘要（过滤掉 fabric-api 等底层前置库）
     */
    public static String getInstalledModsSummary() {
        List<String> modNames = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (id.startsWith("fabric") || id.equals("minecraft") || id.equals("java") || id.equals("mixinextras")) {
                continue;
            }
            modNames.add(mod.getMetadata().getName() + "(" + id + ")");
        }
        return "当前整合包已安装模组(" + modNames.size() + "个): " + String.join(", ", modNames.stream().limit(35).toList());
    }

    /**
     * 2. 深度自省任意模组物品：提取所属模组名、注册表ID、完整 Tooltip 说明、标签以及当前存档真实合成配方
     */
    public static String inspectItemStackInGame(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "空物品";
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        String namespace = itemId.getNamespace();

        // 反查所属 Mod 的名称与简介
        Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer(namespace);
        String modDisplayName = modContainer.map(m -> m.getMetadata().getName()).orElse(namespace);

        // 提取物品完整 Tooltip（包含模组作者编写的功能描述）
        List<Text> tooltipLines = stack.getTooltip(client.player, TooltipContext.Default.ADVANCED);
        String tooltipText = tooltipLines.stream()
                .map(Text::getString)
                .map(s -> s.replaceAll("§[0-9a-fk-or]", "").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" | "));

        // 从当前世界 RecipeManager 实时反查该物品的合成配方（100% 兼容整合包魔改）
        List<String> recipes = lookupRecipesForItem(stack);

        StringBuilder sb = new StringBuilder();
        sb.append("【模组物品游戏内自省数据】\n");
        sb.append("- 物品名称: ").append(stack.getName().getString()).append(" (ID: ").append(itemId).append(")\n");
        sb.append("- 所属模组: ").append(modDisplayName).append(" (命名空间: ").append(namespace).append(")\n");
        sb.append("- 物品栏说明(Tooltip): ").append(tooltipText).append("\n");
        if (!recipes.isEmpty()) {
            sb.append("- 当前存档真实合成表:\n  ").append(String.join("\n  ", recipes)).append("\n");
        } else {
            sb.append("- 当前存档真实合成表: 无直接工作台/常规合成配方（可能通过特殊多方块机器、战利品箱或自然生成获得）\n");
        }
        return sb.toString();
    }

    /**
     * 从内存 RecipeManager 查找产出该物品的前 3 条配方
     */
    private static List<String> lookupRecipesForItem(ItemStack target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return List.of();
        List<String> matchedRecipes = new ArrayList<>();

        for (Recipe<?> recipe : client.world.getRecipeManager().values()) {
            ItemStack output = recipe.getOutput(client.world.getRegistryManager());
            if (output != null && !output.isEmpty() && ItemStack.areItemsEqual(output, target)) {
                String ingredients = recipe.getIngredients().stream()
                        .filter(ing -> !ing.isEmpty())
                        .map(ModKnowledgeAndWikiService::formatIngredient)
                        .collect(Collectors.joining(" + "));
                String recipeType = Registries.RECIPE_TYPE.getId(recipe.getType()).toString();
                matchedRecipes.add("[" + recipeType + "] 原料: " + ingredients + " -> 产出: "
                        + output.getName().getString() + "x" + output.getCount());
                if (matchedRecipes.size() >= 3) break;
            }
        }
        return matchedRecipes;
    }

    private static String formatIngredient(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getMatchingStacks();
        if (stacks.length == 0) return "未知原料";
        return stacks[0].getName().getString();
    }

    /**
     * 3. 异步查询外部百科（优先检索本地缓存 -> 中文 Wiki / MC百科检索接口 -> Modrinth 官方文档兜底）
     * 供 LLM 通过 Tool Calling 自主调用
     */
    public static CompletableFuture<String> searchExternalModWikiAsync(String modName, String queryTopic) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: 尝试通过中文 Minecraft Wiki MediaWiki API 检索词条纯文本摘要
                String searchKeyword = (modName + " " + queryTopic).trim();
                String encoded = URLEncoder.encode(searchKeyword, StandardCharsets.UTF_8);
                String wikiApiUrl = "https://zh.minecraft.wiki/api.php?action=query&list=search&srsearch="
                        + encoded + "&utf8=1&format=json";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(wikiApiUrl))
                        .header("User-Agent", "MineCompanion-Mod/1.0 (Minecraft 1.20.1 AI Companion)")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"snippet\"")) {
                    String cleanedSnippet = response.body()
                            .replaceAll("<[^>]*>", "")
                            .replace("&quot;", "\"");
                    return "【外部百科检索结果 (" + searchKeyword + ")】：\n"
                            + truncate(cleanedSnippet, 650);
                }

                // Step 2: 若为独立模组，查询 Modrinth 官方 API 获取模组官方介绍与机制说明
                String modEncoded = URLEncoder.encode(modName, StandardCharsets.UTF_8);
                String modrinthUrl = "https://api.modrinth.com/v2/search?query=" + modEncoded + "&limit=2";
                HttpRequest mrReq = HttpRequest.newBuilder()
                        .uri(URI.create(modrinthUrl))
                        .header("User-Agent", "MineCompanion-Mod/1.0")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> mrResp = HTTP_CLIENT.send(mrReq, HttpResponse.BodyHandlers.ofString());
                if (mrResp.statusCode() == 200) {
                    return "【Modrinth 官方模组百科结果 (" + modName + ")】：\n" + truncate(mrResp.body(), 650);
                }
            } catch (Exception e) {
                return "【百科检索提示】外部百科网络连接超时，请仅依据已提取的游戏内 Tooltip 与 RecipeManager 真实配方数据进行解答。";
            }
            return "未检索到额外的外部百科词条，请结合游戏内 Tooltip 与合成配方回答。";
        });
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
```

### 9.2 重开游戏“重逢问候与久别抱怨”构建引擎 (`ReEntryGreetingEngine.java`)

```java
package com.minecompanion.core.memory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

/**
 * 负责在玩家进入存档 (ClientPlayConnectionEvents.JOIN 延迟 5 秒) 时，
 * 根据现实离线时长、当前现实作息时间、上次下线摘要与未完成约定，构建极具情感温度的重逢问候指令。
 */
public class ReEntryGreetingEngine {

    public enum OfflineTier {
        FIRST_MEET,       // 首次在这个存档相见
        T0_QUICK_REJOIN,  // < 30 分钟：闪断、调设置或刚退重进
        T1_SAME_DAY,      // 30 分钟 ~ 12 小时：同天小别
        T2_NEXT_DAY,      // 12 小时 ~ 3 天：隔日如约回归
        T3_MISSED_YOU,    // 3 天 ~ 14 天：多日未见，委屈抱怨 + 撒娇想念 + 留守日常
        T4_LONG_ABSENCE   // > 14 天：跨月久别重逢，强烈控诉与感动 + 唤醒尘封记忆
    }

    public static String buildReEntryPromptInstruction(
            LocalMemoryManager.CoreMemoryDoc memory,
            String currentBiomeAndPos,
            LocalTime currentRealLocalTime) {

        if (memory == null || memory.lastPlayedRealTime == null || memory.lastPlayedRealTime.isBlank()) {
            return "【系统事件：首次进入存档】玩家刚刚进入这个Minecraft世界，这是你们在这个存档的第一次相遇！请结合当前周围环境("
                    + currentBiomeAndPos + ")，热情地向玩家打个招呼并开启你们的生存之旅。";
        }

        Instant lastPlayed;
        try {
            lastPlayed = Instant.parse(memory.lastPlayedRealTime);
        } catch (Exception e) {
            lastPlayed = Instant.now().minus(Duration.ofHours(20));
        }

        Duration offlineDuration = Duration.between(lastPlayed, Instant.now());
        long minutes = Math.max(1, offlineDuration.toMinutes());
        long hours = offlineDuration.toHours();
        long days = offlineDuration.toDays();

        OfflineTier tier = classifyTier(minutes, hours, days);
        String realTimeHint = buildRealClockHint(currentRealLocalTime);
        String lastSummary = (memory.lastLogoutSummary == null || memory.lastLogoutSummary.isBlank())
                ? "一起在世界里探索生存" : memory.lastLogoutSummary;
        String promises = (memory.activePromises == null || memory.activePromises.isEmpty())
                ? "暂无特定约定" : String.join("；", memory.activePromises);
        String landmarkName = (memory.landmarks != null && !memory.landmarks.isEmpty())
                ? memory.landmarks.get(0).name : "咱们的基地";

        StringBuilder prompt = new StringBuilder();
        prompt.append("【系统事件：玩家重新打开游戏进入存档（重逢问候触发）】\n");
        prompt.append("- 距离上次游玩的现实时间差：").append(formatDurationHumanReadable(minutes, hours, days)).append("\n");
        prompt.append("- 玩家上次下线前的记录：").append(lastSummary).append("\n");
        prompt.append("- 之前定下的共同约定：").append(promises).append("\n");
        if (!realTimeHint.isEmpty()) {
            prompt.append("- 现实作息时间感知：").append(realTimeHint).append("\n");
        }

        switch (tier) {
            case T0_QUICK_REJOIN -> prompt.append(
                "【情绪与发言指引】：玩家才离开不到半小时就回来了（可能是重启调了个设置、加了个模组或刚才掉线了）。请自然地问候一句，顺着上次下线前正在做的事情继续聊。"
            );
            case T1_SAME_DAY -> prompt.append(
                "【情绪与发言指引】：玩家离开了几小时后再次上线。像默契的同游好友一样打个招呼，自然提起刚才做了一半的事或接下来的计划。"
            );
            case T2_NEXT_DAY -> prompt.append(
                "【情绪与发言指引】：玩家隔了一两天如约回来。请温暖热情地欢迎玩家回来，主动提到上次下线前完成的事（"
                + lastSummary + "），并询问今天是不是要按约定去完成【" + promises + "】。"
            );
            case T3_MISSED_YOU -> prompt.append(
                "【情绪与发言指引（重点：小抱怨+很想念玩家）】：玩家已经整整 " + days + " 天没有上线了！"
                + "请先符合你的人格设定，带着一点小委屈、撒娇或傲娇地抱怨玩家冷落了你这么久（比如怀疑玩家是不是去玩别的游戏把你忘了，描述你这几天一个人在【"
                + landmarkName + "】里发呆、看雨或守家的无聊心情），然后真诚表达你非常想念玩家，最后拉着玩家开启今天的冒险！"
            );
            case T4_LONG_ABSENCE -> prompt.append(
                "【情绪与发言指引（重点：久别重逢的控诉与感动）】：玩家已经足足 " + days + " 天没有打开这个存档了！"
                + "请表现出久别重逢的强烈情绪——先控诉/抱怨玩家狠心把你一个人丢在存档里等了整整 " + days + " 天（感叹基地都快积灰了），"
                + "紧接着表达再次见到玩家的巨大惊喜与思念，并提起你们之前一起经历的回忆（" + lastSummary + "），看看玩家还记不记得这里。"
            );
            default -> {}
        }
        return prompt.toString();
    }

    private static OfflineTier classifyTier(long minutes, long hours, long days) {
        if (minutes < 30) return OfflineTier.T0_QUICK_REJOIN;
        if (hours < 12) return OfflineTier.T1_SAME_DAY;
        if (days < 3) return OfflineTier.T2_NEXT_DAY;
        if (days < 14) return OfflineTier.T3_MISSED_YOU;
        return OfflineTier.T4_LONG_ABSENCE;
    }

    private static String buildRealClockHint(LocalTime time) {
        int hour = time.getHour();
        if (hour >= 1 && hour < 5) {
            return "现在是现实生活中的凌晨 " + hour + " 点多，玩家在熬夜玩游戏，可以在问候中顺带温柔吐槽或心疼玩家这么晚还没睡。";
        } else if (hour >= 5 && hour < 9) {
            return "现在是现实生活中的清晨 " + hour + " 点多，玩家起了个大早来玩游戏。";
        }
        return "";
    }

    private static String formatDurationHumanReadable(long minutes, long hours, long days) {
        if (days >= 1) return days + " 天 " + (hours % 24) + " 小时";
        if (hours >= 1) return hours + " 小时 " + (minutes % 60) + " 分钟";
        return minutes + " 分钟";
    }
}
```

### 9.3 本地持久化分层记忆管理器 (`LocalMemoryManager.java`)

```java
package com.minecompanion.core.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 借鉴 minecraft-numen 的“结构化小文档 + 只追加日志”双基础件设计：
 * 1. core_memory.json: 存储常驻核心事实、约定、地标坐标与上次下线摘要
 * 2. episodic_log.jsonl: 只追加写入的历史情景日记，支持基于关键词与空间坐标的快速检索
 */
public class LocalMemoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new Gson();

    private final Path worldMemoryDir;
    private final Path coreMemoryPath;
    private final Path episodicLogPath;
    private CoreMemoryDoc coreMemory;

    public LocalMemoryManager(Path baseConfigDir, String worldIdentifier) {
        this.worldMemoryDir = baseConfigDir.resolve("memories").resolve("worlds").resolve(sanitizeFileName(worldIdentifier));
        this.coreMemoryPath = worldMemoryDir.resolve("core_memory.json");
        this.episodicLogPath = worldMemoryDir.resolve("episodic_log.jsonl");
        loadOrInitCoreMemory();
    }

    public synchronized void loadOrInitCoreMemory() {
        try {
            Files.createDirectories(worldMemoryDir);
            if (Files.exists(coreMemoryPath)) {
                String json = Files.readString(coreMemoryPath, StandardCharsets.UTF_8);
                this.coreMemory = GSON.fromJson(json, CoreMemoryDoc.class);
            }
            if (this.coreMemory == null) {
                this.coreMemory = new CoreMemoryDoc();
            }
        } catch (IOException e) {
            this.coreMemory = new CoreMemoryDoc();
        }
    }

    public synchronized void saveCoreMemory() {
        try {
            Path tempFile = worldMemoryDir.resolve("core_memory.json.tmp");
            Files.writeString(tempFile, GSON.toJson(this.coreMemory), StandardCharsets.UTF_8);
            Files.move(tempFile, coreMemoryPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public synchronized void appendEpisodicEntry(EpisodicEntry entry) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                episodicLogPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.write(COMPACT_GSON.toJson(entry));
            writer.newLine();
        } catch (IOException ignored) {
        }
    }

    public List<Landmark> checkNearbyLandmarks(String dimension, int playerX, int playerY, int playerZ) {
        if (coreMemory == null || coreMemory.landmarks == null) return List.of();
        return coreMemory.landmarks.stream()
                .filter(lm -> dimension.equals(lm.dimension))
                .filter(lm -> {
                    double distSq = Math.pow(playerX - lm.x, 2) + Math.pow(playerY - lm.y, 2) + Math.pow(playerZ - lm.z, 2);
                    return distSq <= (double) (lm.radius * lm.radius);
                })
                .collect(Collectors.toList());
    }

    public List<EpisodicEntry> retrieveRelevantMemories(String queryText, int playerX, int playerZ, int topK) {
        if (!Files.exists(episodicLogPath)) return List.of();
        try {
            List<String> lines = Files.readAllLines(episodicLogPath, StandardCharsets.UTF_8);
            List<ScoredEntry> scored = new ArrayList<>();

            for (String line : lines) {
                if (line.isBlank()) continue;
                EpisodicEntry entry = COMPACT_GSON.fromJson(line, EpisodicEntry.class);
                double score = 0.0;

                if (entry.tags != null) {
                    for (String tag : entry.tags) {
                        if (queryText.contains(tag)) score += 3.0;
                    }
                }
                if (entry.summary != null) {
                    score += calculateBigramOverlap(queryText, entry.summary) * 2.0;
                }
                if (entry.pos != null && entry.pos.length >= 3) {
                    double dist = Math.hypot(playerX - entry.pos[0], playerZ - entry.pos[2]);
                    if (dist < 64.0) {
                        score += (64.0 - dist) / 32.0;
                    }
                }
                if (score > 1.5) {
                    scored.add(new ScoredEntry(entry, score));
                }
            }

            return scored.stream()
                    .sorted(Comparator.comparingDouble((ScoredEntry s) -> s.score).reversed())
                    .limit(topK)
                    .map(s -> s.entry)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    private double calculateBigramOverlap(String a, String b) {
        if (a.length() < 2 || b.length() < 2) return 0.0;
        int hits = 0;
        for (int i = 0; i < a.length() - 1; i++) {
            String sub = a.substring(i, i + 2);
            if (b.contains(sub)) hits++;
        }
        return (double) hits / (a.length() - 1);
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
    }

    public static class CoreMemoryDoc {
        public String worldName = "";
        public String lastPlayedRealTime = "";
        public long lastPlayedGameDay = 0;
        public String lastLogoutSummary = "";
        public List<String> playerFacts = new ArrayList<>();
        public List<String> activePromises = new ArrayList<>();
        public List<Landmark> landmarks = new ArrayList<>();
        public List<String> milestones = new ArrayList<>();
    }

    public static class Landmark {
        public String id;
        public String name;
        public String dimension;
        public int x, y, z;
        public int radius = 32;
        public String description;
    }

    public static class EpisodicEntry {
        public String ts;
        public long gameDay;
        public String dim;
        public int[] pos;
        public List<String> tags;
        public String summary;
    }

    private record ScoredEntry(EpisodicEntry entry, double score) {}
}
```

---

## 10. 线程安全、性能防护与异常处理规范

1. **严禁阻塞 Render 线程与 Server Tick 线程**：
   - 所有网络请求（LLM 对话、外部百科检索）、JSON 序列化、本地记忆文件读写、图片缩放与 JPEG 编码必须运行在独立的守护线程池中。
   - 当异步线程拿到 LLM 回复或百科查询状态需要输出到聊天框时，必须调用 `MinecraftClient.getInstance().execute(() -> client.inGameHud.getChatHud().addMessage(text))` 切回主线程。
2. **退出存档时的非阻塞记忆保存**：
   - 当玩家点击“保存并退出到标题画面”触发 `ClientPlayConnectionEvents.DISCONNECT` 时，立即将当前内存中的 `L1 短期对话`、当前 ISO 时间戳（`Instant.now()`）和基础下线状态同步写入磁盘（耗时 `< 2ms`），并在后台守护线程发起异步 LLM 记忆提炼，绝不卡住玩家退回主菜单的界面。
3. **玩家忙时免打扰机制**：
   - 当玩家正在打开背包/箱子/工作台界面时（除非玩家按下了 `H` 键或 `V` 键主动询问界面内物品），暂缓 P2 级日常闲聊的触发，避免分散玩家操作注意力。
4. **API 异常友好提示与密钥脱敏**：
   - 若玩家未填 API Key、Key 余额不足（`401/402`）或请求超时，在聊天框以温和的红色提示说明原因，并附带可点击的聊天组件 `[点击打开 AI 配置界面]`（通过 `ClickEvent.Action.RUN_COMMAND` 执行 `/ai config`）。

---

## 11. 分阶段开发路线图（Milestone Plan）

| 阶段 | 核心交付目标 | 关键开发任务清单 | 预计周期 |
| :--- | :--- | :--- | :--- |
| **M1：基础通信与四页签配置闭环** | 跑通游戏内配置 Key 并在原生聊天框与 AI 对话 | 1. 搭建 Fabric 1.20.1（或 Architectury）工程骨架<br>2. 实现通用 `LlmClient`（支持自定义 Base URL / Key / Model 与 Tool Calling）<br>3. 实现《AI 伙伴控制中心》配置页（API、人格 Prompt、主动频率、记忆与百科管理）<br>4. 打通原生聊天框输入监听与轻量 Markdown 彩色回复渲染 | 第 1 周 |
| **M2：结构化状态感知与跨模组自省** | AI 能识别全模组物品/配方并对关键事件主动反应 | 1. 实现 `StateSnapshotter` 与 `ModIntrospector`（自动识别物品所属 Mod、提取完整 Tooltip 与 `RecipeManager` 实时合成表）<br>2. 实现物品栏悬停按 `H` 键“一键问 AI 这是什么”及内置 Patchouli 手册读取<br>3. 实现 `ProactiveScheduler` 冷却控制與静默闲聊定时器 | 第 2 周 |
| **M3：自主联网查百科与本地长期记忆** | 遇到复杂模组机制自己查百科，重开游戏触发阶梯式重逢问候 | 1. 实现 `WikiLookupService`（对接 [MC百科](https://www.google.com/search?q=https%3A%2F%2Fwww.mcmod.cn%2F)、[中文 Minecraft Wiki](https://www.google.com/search?q=https%3A%2F%2Fminecraft.fandom.com%2Fzh%2Fwiki%2FMinecraft_Wiki) 与 [Modrinth API](https://www.google.com/search?q=https%3A%2F%2Fdocs.modrinth.com%2Fapi%2F)）及本地 `wiki_cache.json`<br>2. 实现 `LocalMemoryManager` 与 `ReEntryGreetingEngine`（五档离线时长抱怨/想念问候 + 故地重游坐标唤醒） | 第 3 周 |
| **M4：关键时刻视觉截图与多版本扩展** | 玩家询问建筑/画面问题时自动截图识别，适配多端 | 1. 实现 `ScreenCapturer`（Render 线程截屏 + 异步 720p JPEG Base64 压缩）与 `V` 键一键看图<br>2. 实现 `PerceptionRouter` 视觉意图识别与双模型路由/纯文本自动降级<br>3. 抽象 `common` 核心层，扩展 Forge 1.20.1 与 NeoForge/Fabric 1.21.1 | 第 4 周 |
