# Destiny 2 Inspired Mod（Fabric）

[English](README.md)

这是一个面向 Minecraft 1.21.1 Fabric 的开源实验项目，研究如何用 Minecraft 原生的玩法、渲染、动画与 UI 架构，实现受 Destiny 启发的技能制战斗体验。

> [!WARNING]
> 项目目前处于实验性 Alpha 阶段。系统、存档、平衡和内部 API 都可能发生不兼容变更，暂时没有稳定公开版本。

## 当前内容

- 术士烈日、猎人虚空、泰坦电弧三个职业框架；
- 主动技能、星相、碎片、手雷、职业技能、超能、状态效果及能量/冷却规则；
- 自定义武器与词条数据、护甲 3.0、战利品、HUD 和职业配置界面；
- 以自定义渲染为主体、粒子为细节补充的技能与世界实体特效；
- 能量球、焰灵等可拾取实体，以及光能结晶等制作/进度实验；
- Minecraft 风格的家园工业设备原型，包括供能、制造、加工、存储和物流；
- 针对玩法规则、素材、数据契约与集成边界的自动化测试。

本项目不是对原游戏内容的一比一搬运。重点是探索可维护、可测试并适合 Fabric 的技能战斗架构，同时保持服务端负责玩法判定、客户端负责视觉表现。

## 构建

需要 JDK 21。在项目根目录执行：

```powershell
.\gradlew.bat build
```

可安装的模组文件会生成到 `build/libs/`。不要使用名称带 `-sources.jar` 的源码包。

测试和代码依赖图：

```powershell
.\gradlew.bat test
.\gradlew.bat codeGraph
```

自动化检查不能替代游戏内的视觉、输入、多人和性能验收。

当前分支仍包含 `libs/` 下的项目定制 JAR。公开发布二进制文件前，必须完成来源和再分发许可审计。

## 参与贡献

欢迎提交 Bug、文档、测试、范围清晰的修复或功能提案。提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，当前计划见 [ROADMAP.md](ROADMAP.md)，安全问题请按 [SECURITY.md](SECURITY.md) 处理。

## 许可与声明

项目原创代码使用 [GPL-3.0-only](LICENSE) 许可，补充归属信息见 [NOTICE.md](NOTICE.md)。

这是独立、非商业的同人开源项目，与 Bungie 或 Sony Interactive Entertainment 无隶属、认可或赞助关系。Destiny、Destiny 2 及相关名称和商标归其各自权利人所有。贡献者只能提交有权再分发的代码与素材，不要提交从商业游戏中提取的专有素材。

