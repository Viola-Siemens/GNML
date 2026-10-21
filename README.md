# Good Night Minecraft Launcher

Good Night Minecraft Launcher（简称 GNML）是一个基于 JavaAgent + ASM 技术的 HMCL 启动器引导工具。它并不修改 HMCL 本体，而是在 HMCL 运行时将其 `LocaleUtils.IS_CHINA_MAINLAND` 与 `RegionUtils`（较新版本 HMCL）的机器区域判定强制为 `false`，使 HMCL 不再走"中国大陆特判"逻辑，从而要求玩家先完成正版（Microsoft）登录，之后才能使用离线登录功能，帮助玩家遵守 Minecraft EULA。

## 功能定位

- GNML 与 HMCL 启动器深度绑定，仅服务于 HMCL；
- GNML 不内置、不下载、不分发 HMCL 启动器本体，请自行从 [HMCL 官方 Releases](https://github.com/HMCL-dev/HMCL/releases) 下载；
- 制品只有一个跨平台的 jar 文件，不提供 exe、deb 等系统安装包。

## 工作原理

1. **主程序（Main）**：扫描当前工作目录与 GNML 所在目录下的 `*.jar` / `*.exe` / `*.sh` 文件，通过读取 zip 内 `META-INF/MANIFEST.MF` 的 `Main-Class` 是否为 `org.jackhuang.hmcl.Main` 来识别 HMCL 启动器；随后以 `java -javaagent:<GNML.jar> -jar <HMCL文件>` 的方式拉起 HMCL，并等待其退出、透传退出码。
2. **Agent（Premain）**：GNML 以 JavaAgent 形式挂载进 HMCL 的 JVM，使用 ASM 将 `org.jackhuang.hmcl.util.i18n.LocaleUtils#isChinaMainland()` 与 `org.jackhuang.hmcl.util.RegionUtils#checkMachineIsChinaMainland()`（HMCL 较新版本引入）的方法体整体替换为直接返回 `false`，使 HMCL 认为当前环境不在中国大陆。

GNML 对 HMCL jar 文件本身不做任何写入，HMCL 的数字签名不受影响。

## 环境要求

- Java 运行环境：JRE/JDK 8 及以上，且必须自带 JavaFX（HMCL 依赖 JavaFX 运行界面）。推荐：
  - BellSoft Liberica **Full** JDK/JRE（8 或 17+）；
  - Azul Zulu FX；
  - Oracle JDK/JRE 8（自带 JavaFX）。
- 操作系统：Windows、macOS、Linux 均可（GNML 及 HMCL 均为纯 Java）。

## 使用方法

1. 从 [HMCL 官方 Releases](https://github.com/HMCL-dev/HMCL/releases) 下载 HMCL 的 jar 或 exe，放置在某个目录中；
2. 将 `GNML-x.x.x.jar` 放到同一目录（或任意位置）；
3. 双击 `GNML-x.x.x.jar`，或在终端执行：

```powershell
java -jar GNML-x.x.x.jar
```

GNML 随后会自动找到 HMCL 并将其启动。

## 行为说明

| 场景 | GNML 行为 |
| --- | --- |
| 当前目录未找到 HMCL，但 GNML 所在目录有 | 使用 GNML 所在目录的 HMCL |
| 两个目录都没有 HMCL | 自动打开浏览器跳转 HMCL Releases 页面，并弹出警告窗口引导下载；浏览器打开失败时仅弹窗展示下载地址 |
| 找到多个 HMCL 文件 | 从文件名解析版本号，选择最高版本；同版本时优先 jar 而非 exe |
| java 可执行文件解析 | 依次尝试：当前运行 GNML 的 JVM 同款 java、环境变量 `HMCL_JAVA_HOME`、环境变量 `JAVA_HOME`、系统 `PATH` |
| HMCL 运行时异常退出 | GNML 等待 HMCL 退出并透传其退出码 |
| HMCL 未来版本修改了内部实现，导致改造失败 | 启动前直接报错中止，不会放行未经改造的 HMCL |

GNML 不支持向 HMCL 透传命令行参数，所有 HMCL 设置请在 HMCL 界面内配置。

## 常见问题

**问：双击后提示"未找到 Java"？**

请安装带 JavaFX 的 JRE/JDK，并确保 `java` 在系统 `PATH` 中，或将 `HMCL_JAVA_HOME` / `JAVA_HOME` 指向合适的 JDK 目录。

**问：弹出警告窗口说 HMCL 不兼容？**

HMCL 升级后可能重命名或移除 `LocaleUtils#isChinaMainland`。GNML 为保证"正版优先"目标不会放行未经改造的 HMCL，请关注 GNML 的更新。

**问：GNML 会修改我的 HMCL 文件吗？**

不会。GNML 只在运行时改写 JVM 内存中的字节码，磁盘上的 HMCL 文件与数字签名保持原样。

## 项目说明

- 本项目与 Mojang、Microsoft、HMCL 开发组无任何关联；
- 本项目不绕过、不削弱任何正版验证；相反，它用于强制 HMCL 优先要求正版登录；
- Minecraft 是 Mojang Synergies AB 的商标，请遵守 [Minecraft EULA](https://www.minecraft.net/eula)。

## 许可证

本项目使用 [Artistic License 2.0](LICENSE) 开源。
