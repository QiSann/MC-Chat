<div align="center">

# 🐾 MineCompanion AI (MC-Chat)
### Minecraft 1.20.1 智能同游伙伴模组 —— 看得见世界、懂全模组配方、有真实喜怒哀乐与长期记忆的聊天框 AI 搭子

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge&logo=minecraft)](https://www.minecraft.net/)
[![Mod Loader](https://img.shields.io/badge/Loader-Fabric-dbd0b4?style=for-the-badge)](https://fabricmc.net/)
[![Version](https://img.shields.io/badge/Release-v1.1.0-ff69b4?style=for-the-badge)](releases/minecompanion-1.1.0.jar)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

👉 **[📦 点击此处直接下载最新正式版模组包 (`releases/minecompanion-1.1.0.jar`)](releases/minecompanion-1.1.0.jar)** 👈

</div>

---

## 📖 项目简介

一个人玩 Minecraft 单人生存或大型整合包时，总是觉得世界太安静？遇到不认识的模组机器或魔改合成表，只能频繁切出游戏去搜百科？

**MineCompanion AI (`minecompanion`)** 是一款专为 **Minecraft 1.20.1 (Fabric)** 打造的轻量级客户端智能陪伴模组。它不需要在游戏里生成容易卡住地形的笨重实体 NPC，而是化身为一位**常驻在原版聊天栏里、拥有真实喜怒哀乐情绪的同游挚友（默认名：小爱）**——她不仅能实时感知你的血量、饥饿、背包里的真实食物、受击掉血、角色死亡与周围活体怪物数量，还能读取你手里拿着的任意模组物品与真实合成配方，甚至能截图欣赏你盖的房子、替你联网查《MC百科》，并在你隔了好几天重新打开存档时，带着小委屈和想念向你打招呼！

---

## 🌟 六大核心亮点

### 1. 💬 免指令自然语言唤醒 & 真人级情绪互动
- **告别繁琐前缀**：直接在原版聊天栏按 `T` 打字（例如输入 *“今天天气真好呀”* 或 *“我手里拿的这个有什么用？”*），AI 即可自动识别意图并回复（支持在配置界面一键开关）。
- **去 AI 客服味，拥有真实喜怒哀乐**：看到你反复挨怪揍、残血不跑或者笨手笨脚死掉时，她会真情实感地生气凶人、心急如焚并报出你的死亡坐标方便捡装备；当周围聚集 4 只以上活怪时会紧张预警尸潮包围；提醒回血时**严格扫描你背包里真实拥有的食物**（绝不会在你没有金苹果时乱叫你吃金苹果！）。

### 2. 🔍 跨模组全息自省与实时配方反查（100% 兼容魔改整合包）
- **一键读懂未知模组**：对准任意方块/生物或手持模组物品按 **`H` 键**（或输入 `/item`、`/target`），模组会直接从游戏内存中提取：
  - 物品/方块所属的 **Mod 中文名与命名空间**；
  - 模拟按下 `Shift` 展开后的**完整高级 Tooltip 功能说明**；
  - 从当前存档 `RecipeManager` 实时反查出的**真实合成配方**（即使整合包作者用 KubeJS / CraftTweaker 魔改了配方，AI 也能给出 100% 准确的合成表，绝不产生幻觉！）。

### 3. 🧠 零数据库依赖的“三层本地持久化记忆库”
每个存档拥有独立隔离的记忆文件夹（位于 `.minecraft/config/minecompanion/memories/worlds/<存档名>/`），退游戏重进绝不失忆：
- **L1 短期工作记忆 (`recent_session_context.json`)**：默认保存最近 **80 条消息（40 轮完整对话）**（可在配置界面自由调整上限），玩家打字与 AI 主动搭话 100% 全部记录，保证上下文丝滑连贯；超出上限时自动过滤战斗噪音并将有效对话归档至 L3。
- **L2 永久核心档案 (`core_memory.json`)**：常驻记录你标记的**基地地标坐标**、**共同约定**与**个人偏好**。当你远征挖矿归来、走回基地 32 格范围内时，会自动触发**“故地重游”**温馨感叹！
- **L3 历史情景日记 (`episodic_log.jsonl`)**：记录你们共同经历的会话、死亡掉装备坐标与地标建立，检索时仅提取 **Top-3** 相关回忆注入大模型（绝不浪费 Token），并内置 **300 行自动滚动瘦身裁剪机制**（文件体积永久保持在 70KB 以内）。

### 4. 💌 五档现实离线时长“久别重逢”情感引擎
每次进入存档时，AI 会计算你**距离上次下线的真实现实时间差**，并结合你电脑当前的现实作息时间（如凌晨修仙熬夜）送上截然不同的问候：
- **`< 30 分钟`（闪断/调设置重进）**：*“咦，刚才去调设置还是加新模组啦？咱们继续刚才的事吧！”*
- **`30 分钟 ~ 12 小时`（同天小别）**：像默契好友一样自然招呼并提起刚才做了一半的计划。
- **`12 小时 ~ 3 天`（隔日如约）**：热情欢迎回归，主动询问今天要不要去完成之前立下的约定。
- **`3 天 ~ 14 天`（多日未见 · 委屈撒娇）**：带着小委屈抱怨你是不是去玩别的游戏把她忘了，倾诉自己守在基地里看雨发呆的想念心情。
- **`> 14 天`（跨月重逢 · 控诉与感动）**：感叹基地都快积灰了，在傲娇控诉后表达再次见到你的巨大惊喜，并唤醒你们之前的存档回忆。

### 5. 📚 自主联网查百科与本地极速缓存
当遇到仅靠合成表无法解答的复杂模组机制或 BOSS 攻略（例如询问 *“查一下百科 暮色森林九头蛇怎么打”* 或输入 `/wiki <关键词>`）时：
- 自动按序异步检索 **中文 Minecraft Wiki 接口**、**MC百科 (mcmod.cn)** 与 **Modrinth 官方 API**；
- 查到的资料自动提炼并存入本地 `wiki_cache.json`，二次询问 `< 1ms` 零延迟秒回！

### 6. 📸 OpenGL 安全视觉看图 & 四页签可视化配置中心
- **按 `V` 键一键截屏看画面**：采用“渲染主线程极速抓帧 + 后台守护线程 720p JPEG 压缩”二阶段流水线，零掉帧截取当前画面发给多模态模型点评建筑或风景（若使用纯文本模型则自动平滑降级为结构化态势感知，绝不报错）。
- **按 `K` 键呼出《AI 伙伴控制中心》**：游戏内原生四页签 GUI，配备**大尺寸多行可滚动人格提示词编辑框**、一键 API 连通性测试、4 套高情绪活人感性格模板（傲娇青梅/鲜活猫娘/毒舌老鸟/温柔搭子）与记忆库实时统计！

---

## 🚀 普通玩家三步快速上手

### 第一步：下载并安装模组
1. 确保你的启动器（PCL2 / HMCL / Prism Launcher）已安装 **Minecraft 1.20.1** 与 **Fabric Loader (0.15.11+)**。
2. 确保 `mods` 文件夹中已安装 **[Fabric API](https://modrinth.com/mod/fabric-api)**。
3. 在本仓库的 **[`releases/`](releases/)** 文件夹中下载 **[`minecompanion-1.1.0.jar`](releases/minecompanion-1.1.0.jar)**，放入你的 `.minecraft/mods/` 文件夹中并启动游戏。

### 第二步：在游戏内按 `K` 键配置 API Key
1. 进入游戏世界后，按下键盘上的 **`K` 键**（或在聊天栏输入 `/config`），即可弹出 **《MineCompanion AI 伙伴控制中心》**。
2. 在 **`[1. 接口与模型]`** 页签中：
   - 填入你的大模型 **接口地址 (`Base URL`)**、**接口密钥 (`API Key`)** 与 **模型名称 (`Model Name`)**；
   - 点击 **“⚡ 一键测试 API 连通性”**，看到绿色的 `✔ 连接成功！` 提示后，点击底部 **“✔ 保存配置并生效”** 即可开始畅聊！

### 📋 主流大模型配置参数参考表

| 服务商 | 接口地址 (`Base URL`) | 推荐模型名称 (`Model Name`) | 备注说明 |
| :--- | :--- | :--- | :--- |
| **DeepSeek (推荐)** | `https://api.deepseek.com/v1` | `deepseek-chat` | 中文表现极佳、性价比高（注意模型名全小写） |
| **OpenAI 官方/代理** | `https://api.openai.com/v1` | `gpt-4o-mini` / `gpt-4o` | 原生支持 `V` 键多模态视觉截屏看图 |
| **Google Gemini** | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.5-flash` | 响应速度极快，原生支持多模态视觉看图 |
| **硅基流动 / 本地 Ollama** | 如 `http://localhost:11434/v1` | 如 `qwen2.5:7b` | 支持任意兼容 OpenAI `/chat/completions` 协议的接口 |

---

## 🎮 快捷键与聊天指令大全

### ⌨️ 默认快捷键（可在原版“选项 -> 控制 -> 按键绑定”中修改）
| 按键 | 功能名称 | 适用场景说明 |
| :---: | :--- | :--- |
| **`H`** | **一键自省手持物 / 注视目标** | 手里拿着物品时按 `H` 自动分析该物品用途与真实合成表；空手时按 `H` 自动分析准星注视的方块或生物 |
| **`V`** | **一键截屏让 AI 看画面** | 截取当前游戏画面（含光影、建筑或打开的界面），让 AI 评价建筑外观或解答眼前景象 |
| **`K`** | **打开四页签配置中心** | 随时呼出可视化 GUI 修改 API Key、编辑多行人格提示词、调整主动搭话冷却与 L1 记忆容量 |

### 💬 自然语言唤醒与斜杠指令对照表
你可以**直接在聊天栏打字**触发，也可以使用简短的斜杠指令：

| 你想做的事 | 直接在聊天框打字示例（免指令） | 等效快捷指令 |
| :--- | :--- | :--- |
| **日常闲聊陪伴** | `小爱，附近有怪物吗？我有点怕` | `/ai <任意内容>` |
| **问手里拿的物品** | `我手里拿的这个东西有什么用？怎么合成？` | `/item` 或按 **`H` 键** |
| **问面前看的目标** | `我前面看着的这个方块是什么呀？` | `/target` 或空手按 **`H` 键** |
| **看整合包有哪些 Mod** | `这个整合包都装了哪些模组呀？` | `/mods` |
| **标记基地/重要地点** | `记住这里叫樱花木屋` | `/remember 樱花木屋` |
| **立下共同约定** | `记住我们约定明天一起去下界找堡垒` | 直接打字即可自动记入 L2 档案 |
| **查看当前存档记忆** | —— | `/memory` |
| **联网查百科与攻略** | `帮我查一下百科 暮色森林九头蛇怎么打` | `/wiki 暮色森林 九头蛇` |
| **看画面点评建筑** | `看看我建的房子好看吗？给打个分吧` | `/look [问题]` 或按 **`V` 键** |
| **打开可视化配置界面** | —— | `/config` 或按 **`K` 键** |

---

## 🏗️ 工程架构与目录结构（开发者指南）

本项目采用**“纯 Java 认知核心层 (`core`) + Minecraft 适配层 (`mc`)”**的高内聚解耦架构：

```text
MineCraft Mod/
├── releases/
│   └── minecompanion-1.1.0.jar                 # 可直接安装至 .minecraft/mods/ 的正式发布模组包
├── build.gradle                                # Gradle 构建脚本（已内置打包 Jsoup HTML 解析器）
├── gradle.properties                           # Fabric Loom、Yarn 映射表与依赖版本配置
├── DEVELOPMENT_STEPS.md                        # 六阶段完整研发进度与学习复盘手册
└── src/main/
    ├── java/com/minecompanion/
    │   ├── MineCompanionMod.java               # 模组主入口
    │   ├── client/
    │   │   └── MineCompanionClient.java        # 客户端生命周期、聊天监听、快捷键与指令路由总控
    │   ├── core/                               # 【纯 Java 认知与记忆核心层】
    │   │   ├── config/CompanionConfig.java     # 全局 JSON 配置持久化与热重载模型
    │   │   ├── llm/LlmClient.java              # Java 17 异步非阻塞 HttpClient（支持多轮记忆、多模态与平滑降级）
    │   │   ├── memory/LocalMemoryManager.java  # 三层本地记忆管理器 (L1 80条窗口 + L2 核心档案 + L3 自动瘦身日记)
    │   │   ├── memory/ReEntryGreetingEngine.java # 五档现实离线时长情感计算与作息感知引擎
    │   │   ├── router/PerceptionRouter.java    # 自然语言意图分类器与多维提示词动态组装器
    │   │   ├── scheduler/ProactiveScheduler.java # 死亡、受击、残血、尸潮包围、故地重游与闲聊主动调度器
    │   │   └── wiki/WikiLookupService.java     # 中文 Wiki / MC百科(Jsoup) / Modrinth 异步检索与本地缓存
    │   └── mc/                                 # 【Minecraft 游戏交互与渲染适配层】
    │       ├── chat/ChatColorFormatter.java    # Markdown 转 Minecraft § 染色富文本转换器
    │       ├── gui/CompanionConfigScreen.java  # 《AI 伙伴控制中心》四页签可视化配置屏幕
    │       ├── modcompat/ModIntrospector.java  # 跨模组物品 Tooltip 提取与 RecipeManager 实时合成表反查
    │       ├── perception/StateSnapshotter.java# 玩家生死、背包真实食物、时空环境与活体生物雷达快照器
    │       └── vision/ScreenCapturer.java      # OpenGL Framebuffer 二阶段安全截屏与 720p JPEG 压缩器
    └── resources/
        ├── fabric.mod.json                     # Fabric 模组元数据清单
        └── assets/minecompanion/lang/zh_cn.json# 简体中文按键与界面本地化资源
```

### 🛠️ 从源码编译与本地调试

1. **环境要求**：确保本地已安装 **JDK 17**。
2. **编译并生成可分发模组包**：
   ```powershell
   .\gradlew.bat build
   ```
   构建完成后，可在 `build/libs/minecompanion-1.1.0.jar` 获取成品包。
3. **启动开发环境客户端进入游戏调试**：
   ```powershell
   # 添加 -x downloadAssets 可跳过缓慢的原版音效资源下载，实现秒开游戏
   .\gradlew.bat runClient -x downloadAssets
   ```

---

## 📄 开源协议

本项目基于 **MIT License** 开源，欢迎自由使用、修改与提交 Pull Request！
