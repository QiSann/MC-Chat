# MineCompanion AI 开发步骤与学习进度跟踪手册

> **项目名称**：MineCompanion AI（Minecraft 1.20.1 智能同游伙伴 Mod）  
> **设计基准文档**：[`minecraft_ai_companion_mod_design.md`](file:///D:/studying/Antigravity/MineCraft%20Mod/minecraft_ai_companion_mod_design.md)  
> **目标版本**：Minecraft 1.20.1 (Fabric Loader)  
> **开发语言与环境**：Java 17 LTS (`D:\java\jdk-17`) + Gradle Loom  
> **创建时间**：2026-10-19  

---

## 📌 项目开发总体进度看板

| 阶段里程碑 | 核心主题 | 当前状态 | 预计完成度 |
| :--- | :--- | :---: | :---: |
| **Phase 1** | Fabric 1.20.1 工程骨架与开发环境搭建 | ✅ **已完成** | 100% |
| **Phase 2** | 纯 Java 解耦核心：通用 LLM 异步通信与配置系统 | ✅ **已完成** | 100% |
| **Phase 3** | 原生 ChatHud 聊天交互与轻量 Markdown 富文本染色 | ✅ **已完成** | 100% |
| **Phase 4** | 跨模组物品全息自省、实时合成表反查与主动感知 | ✅ **已完成** | 100% |
| **Phase 5** | 三层本地持久化记忆库、久别重逢情感引擎与自主查百科 | ✅ **已完成** | 100% |
| **Phase 6** | OpenGL Framebuffer 关键时刻视觉截图与四页签配置屏 | ✅ **已完成** | 100% |

---

## 📋 详细分步开发与学习任务清单

### Phase 1：Fabric 1.20.1 工程骨架与开发环境搭建（基础底座）
- [x] **Step 1.1**：拉取/创建 Fabric 1.20.1 标准 Gradle 工程结构（包含 `gradlew`、`build.gradle`、`settings.gradle`）
- [x] **Step 1.2**：配置本地 JDK 17 锁定（`gradle.properties` 绑定 `D:\java\jdk-17`），避免全局环境变量干扰
- [x] **Step 1.3**：配置依赖库（Fabric API、Gson、Jsoup、MixinExtras）与 UTF-8 编码支持
- [x] **Step 1.4**：编写模组元数据清单 `fabric.mod.json` 与 Mixin 配置 `minecompanion.mixins.json`
- [x] **Step 1.5**：创建模组客户端主入口 `MineCompanionClient.java`，执行首次 Gradle 编译验证（产出 `minecompanion-1.0.0-alpha.jar`）

---

### Phase 2：纯 Java 解耦核心：通用 LLM 异步通信与配置系统（M1 核心）
- [x] **Step 2.1**：设计并实现模组全局配置模型 `CompanionConfig.java`（支持 JSON 自动落盘与热重载）
- [x] **Step 2.2**：编写纯 Java 17 标准库实现的 `LlmClient.java`（无外部重量依赖，支持 OpenAI、Gemini、DeepSeek 协议）
- [x] **Step 2.3**：实现异步非阻塞 CompletableFuture 回调机制与状态码解析
- [x] **Step 2.4**：支持多轮记忆上下文与外部百科知识动态注入（`chatWithMemoryAsync`）
- [x] **Step 2.5**：编写一键连通性测试模块（极低 Token 快速验证 API Key 与 Base URL 是否可用）

---

### Phase 3：原生 ChatHud 聊天交互与轻量 Markdown 富文本染色（M1 闭环）
- [x] **Step 3.1**：通过 Fabric Client 事件监听玩家原生聊天输入与 `/ai` 快捷指令集
- [x] **Step 3.2**：编写 `ChatColorFormatter.java`，将大模型回复中的 Markdown（加粗、斜体、列表、代码块）转换为 Minecraft 原生文本样式（`§b` 染色）
- [x] **Step 3.3**：实现主线程安全回切，将 LLM 异步响应平滑推送到客户端 `ChatHud`
- [x] **Step 3.4**：在游戏内完成第一句端到端对话互动测试（“你好呀主人，我是你的游戏伙伴小爱喵~”）

---

### Phase 4：跨模组物品全息自省、实时合成表反查与主动感知（M2 核心）
- [x] **Step 4.1**：编写 `ModIntrospector.java`，扫描整合包所有已装模组元数据与中文名称
- [x] **Step 4.2**：实现物品与方块全息数据提取（反查模组命名空间、模拟按下 Shift 提取完整高级 Tooltip）
- [x] **Step 4.3**：实时反查当前世界 `RecipeManager` 内存中的真实合成配方（完美兼容 KubeJS / CraftTweaker 整合包魔改）
- [x] **Step 4.4**：注册 `H` 键快捷键，实现“在世界中按 H 键一键自省手中或面前的目标”
- [x] **Step 4.5**：编写 `StateSnapshotter.java`，采集玩家生命值、饱食度、坐标、群系、周围实体等结构化文本
- [x] **Step 4.6**：编写 `ProactiveScheduler.java`，实现同类事件防抖、全局冷却控制（Cooldown）与静默闲聊定时器

---

### Phase 5：三层本地持久化记忆库、久别重逢情感引擎与自主查百科（M3 核心）
- [x] **Step 5.1**：初始化按存档隔离的持久化目录结构（`.minecraft/config/minecompanion/memories/worlds/<存档名>/`）
- [x] **Step 5.2**：实现 L1 短期工作记忆（`recent_session_context.json` 内存滑动窗口，退存档时落盘，进存档恢复）
- [x] **Step 5.3**：实现 L2 永久核心档案小文档 `core_memory.json`（基地地标坐标、共同约定、玩家喜好）与故地重游空间唤醒
- [x] **Step 5.4**：实现 L3 只追加情景日记流 `episodic_log.jsonl`（记录共同经历的高光时刻，支持双字词重合度与空间距离混合检索）
- [x] **Step 5.5**：编写 `ReEntryGreetingEngine.java`，计算玩家现实离线时长（五档阶梯）与现实作息时间，生成委屈撒娇/想念的重逢开场白
- [x] **Step 5.6**：编写 `WikiLookupService.java`（异步检索中文 Minecraft Wiki、MC百科 mcmod.cn、Modrinth API）与本地百科缓存 `wiki_cache.json`

---

### Phase 6：OpenGL 关键时刻视觉截图与四页签配置屏（M4 核心）
- [x] **Step 6.1**：在渲染主线程调用 `ScreenshotRecorder.takeScreenshot(client.getFramebuffer())` 安全捕获画面
- [x] **Step 6.2**：在后台守护线程异步将图像缩放至 1280×720 并进行 Quality 78% JPEG Base64 编码
- [x] **Step 6.3**：编写 `PerceptionRouter.java` 视觉意图识别器，配合快捷键 `V`（一键看图）与双模型路由（日常文本模型 + 视觉多模态模型 + 纯文本平滑自动降级）
- [x] **Step 6.4**：编写 `CompanionConfigScreen.java` 四页签独立配置中心（API 与模型设置、人格提示词工坊、主动发言频率、记忆与百科管理）
- [x] **Step 6.5**：注册 `K` 键一键呼出配置中心，支持游戏内保存并即时生效

---

## 📝 研发日常实战记录日志（Changelog）

| 日期 | 执行阶段与步骤 | 详细变更与学习记录 | 执行人 |
| :--- | :--- | :--- | :--- |
| **2026-10-19** | 架构审阅与准备 | 完成设计文档 [`minecraft_ai_companion_mod_design.md`](file:///D:/studying/Antigravity/MineCraft%20Mod/minecraft_ai_companion_mod_design.md) 全面解析；侦测并确认本地 JDK 17 (`D:\java\jdk-17`) 就绪；建立本进度跟踪手册 | 主人 & 双子猫娘 |
| **2026-10-19** | Phase 1 (Step 1.1~1.5) | 搭建 Fabric 1.20.1 标准 Gradle 工程（Gradle 8.8 + Loom 1.7.4 + Yarn 映射表），绑定 JDK 17，添加 Gson/Jsoup 依赖，编写入口类并通过首轮构建，生成 `minecompanion-1.0.0-alpha.jar` | 主人 & 双子猫娘 |
| **2026-10-20** | Phase 2 (Step 2.1~2.5) | 编写并打通 `CompanionConfig`（配置自动持久化）与 `LlmClient`（纯 Java 17 原生非阻塞 HttpClient 通用通信客户端，支持连通性测试），全中文提交推送至 GitHub | 主人 & 双子猫娘 |
| **2026-10-21** | Phase 3 (Step 3.1~3.4) | 实现 `ChatColorFormatter` 与客户端 `/ai` 聊天指令监听，成功在 Minecraft 1.20.1 原生单人世界中完成第一句端到端对话互动测试（M1 阶段圆满闭环） | 主人 & 双子猫娘 |
| **2026-10-21** | Phase 4 (Step 4.1~4.6) | 编写 `ModIntrospector` 跨模组自省引擎、`PerceptionRouter` 意图路由器、`StateSnapshotter` 生存态势感知、`ProactiveScheduler` 主动发言调度器与 `H` 键快捷自省，全面打通自然语言免指令唤醒（M2 阶段圆满闭环） | 主人 & 双子猫娘 |
| **2026-10-24** | Phase 5 (Step 5.1~5.6) | 实现 `LocalMemoryManager` 三层持久化记忆库（L1 滑动窗口、L2 核心档案、L3 情景日记流）、`ReEntryGreetingEngine` 五档阶梯式重逢问候引擎、`WikiLookupService` 多渠道百科异步检索与本地缓存，并接入故地重游空间唤醒（M3 阶段圆满闭环） | 主人 & 双子猫娘 |
| **2026-10-24** | Phase 6 (Step 6.1~6.5) | 实现 `ScreenCapturer` 二阶段安全 OpenGL 截屏与 JPEG Base64 编码、`LlmClient.chatVisionAsync` 多模态路由与纯文本平滑降级、`CompanionConfigScreen` 四页签可视化配置中心，并注册 `V` 键看图与 `K` 键配置热键（M4 阶段圆满闭环，全六阶段 100% 达成！） | 主人 & 双子猫娘 |

---

## 🎉 全阶段里程碑圆满完成总结
**MineCompanion AI (v1.0.0)** 的六大核心研发阶段已全部 100% 交付并通过构建验证！产出的模组包位于 `build/libs/minecompanion-1.0.0-alpha.jar`，可直接放入任何 Minecraft 1.20.1 Fabric 整合包的 `mods` 文件夹中使用！
