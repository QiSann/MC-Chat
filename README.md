# 🌸 MineCompanion AI (Minecraft 1.20.1+)

<div align="center">

**为 Minecraft 打造的高沉浸感、跨模组自省、具备长期记忆与现实时间共鸣的 AI 游戏同游伙伴**

[系统架构设计文档](./minecraft_ai_companion_mod_design.md) • [开发进度与学习手册](./DEVELOPMENT_STEPS.md)

</div>

---

## 🌟 核心特性亮点

- ⚡ **原生融入聊天栏 (`ChatHud`)**：100% 融入原版聊天栏，无需割裂的实体模型或繁重的独立大面板，支持轻量 Markdown 染色与流式打字机效果。
- 👁️ **混合感知架构**：
  - **日常状态流**：超低延迟、极低 Token 消耗的结构化游戏状态采集（生命、群系、时间、手持物品所属 Mod、周围实体）。
  - **按需视觉感知**：在询问“看我建的房子怎么样/看光影风景”时，异步捕获当前 OpenGL Framebuffer 渲染帧画面，直达多模态视觉大模型。
- 🔍 **跨模组全息自省与自主查百科**：
  - 运行时自省已安装模组注册表与完整 Tooltip 说明。
  - 实时反查 `RecipeManager` 内存真实合成表（天然兼容 KubeJS / CraftTweaker 整合包魔改配方）。
  - 支持大模型 Tool Calling 自主异步查询 [MC百科](https://www.mcmod.cn/)、[中文 Minecraft Wiki](https://zh.minecraft.wiki/) 与 [Modrinth API](https://docs.modrinth.com/api/)，并缓存在本地。
- 🧠 **三层本地持久化记忆**：
  - **L1 短期记忆**：会话工作环形缓冲区，保证重进存档瞬间无缝续聊。
  - **L2 核心事实**：结构化小文档（`core_memory.json`），永久铭记基地地标坐标、共同约定与大事记。
  - **L3 情景日记**：只追加日志流（`episodic_log.jsonl`），支持故地重游与关键词场景唤醒。
- ⏰ **现实离线时长情感反馈（重逢问候）**：
  - 自动感知现实生活作息与离线时长（5 档情绪阶梯），从同天小别的默契续聊，到隔周未上线的委屈抱怨与撒娇想念。
- 🌐 **多模型与双模型路由兼容**：
  - 采用标准 OpenAI 兼容协议，支持自由配置 Base URL、API Key 与 Model Name。
  - 支持接入 OpenAI (`gpt-4o`)、Gemini (`gemini-2.5-flash`)、DeepSeek (`deepseek-chat`) 等主流服务商。

---

## 🛠️ 技术栈与环境要求

| 组件 | 版本 / 规范 | 说明 |
| :--- | :--- | :--- |
| **游戏版本** | Minecraft `1.20.1` | 客户端首发支持 |
| **模组加载器** | Fabric Loader (`0.15.11+`) | 轻量、高兼容性 |
| **开发语言** | Java `17` LTS | 纯 Java 解耦核心 + Fabric API 适配层 |
| **构建工具** | Gradle + Fabric Loom | 标准 Minecraft Mod 构建流 |

---

## 📂 项目结构规范

```text
MineCompanion-AI/
├── minecraft_ai_companion_mod_design.md   # 系统详细设计规范与架构白皮书
├── DEVELOPMENT_STEPS.md                  # 详细开发步骤与学习进度跟踪手册
├── .gitignore                            # 编译缓存与敏感 API Key 隔离规则
├── build.gradle                          # Fabric Loom 构建脚本
├── gradle.properties                     # 模组元数据与本地 JDK 路径配置
└── src/
    └── main/
        ├── java/com/minecompanion/
        │   ├── core/                     # 纯 Java 核心解耦层（LLM、记忆、百科、路由）
        │   └── mc/                       # Minecraft 1.20.1 客户端适配层（事件、UI、截图）
        └── resources/                    # 模组资源、Mixin 配置与元数据
```

---

## 🚀 快速上手与更新指南

### 1. 克隆代码库
```bash
git clone <repository-url>
cd "MineCraft Mod"
```

### 2. 构建工程
```bash
./gradlew build
```

---

## 📄 许可与致谢

本项目参考并吸收了开源社区 [Dwinovo/minecraft-numen](https://github.com/Dwinovo/minecraft-numen) 的优秀架构思想（双件套落盘哲学与解耦规范）。
本项目采用 [MIT 许可证](LICENSE)。
