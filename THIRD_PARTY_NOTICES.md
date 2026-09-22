# 第三方代码来源与许可

## Auxio / Musikr

- 上游仓库：<https://github.com/OxygenCobalt/Auxio>
- 参考 commit：`c05cebc52fe2393bf3a988068e4060fd6dba408f`
- 上游许可证：GNU General Public License v3.0 or later（见上游根目录 `LICENSE`）
- 版权：Copyright (c) 2021-2025 Auxio Project

Auralis Mobile 的 SAF 扫描模块（`com.bobo.auralis.mobile.library.saf`）以 Musikr 的 SAF 实现为基础裁剪、改写而来。直接复制或明显派生的代码如下：

| 上游文件 | Auralis 文件 | 处理方式 |
| --- | --- | --- |
| `musikr/src/main/java/org/oxycblt/musikr/fs/saf/SAF.kt` | `app/src/main/java/com/bobo/auralis/mobile/library/saf/SafScanner.kt` | 裁剪与改写：保留递归查询、多线程目录遍历结构；移除 MediaStore `AddedMs`、`ContentObserver` 实时监听、用户排除目录与 Musikr `FS` 接口；改为 `channelFlow` 事件流；目录查询失败不再中断整个扫描，而是发出诊断事件（Auralis 要求临时不可访问目录可恢复）；为每个文件附加 `provider + documentId` 组成的 `SafDocumentKey` 供扫描期去重 |
| `musikr/src/main/java/org/oxycblt/musikr/fs/saf/QueryUtil.kt` | `app/src/main/java/com/bobo/auralis/mobile/library/saf/SafContentResolver.kt` | 基本直接移植，仅调整注释 |
| `musikr/src/main/java/org/oxycblt/musikr/fs/Location.kt` | `app/src/main/java/com/bobo/auralis/mobile/library/saf/SafLocation.kt` | 裁剪：移除 `DocumentPathFactory` / `VolumeManager` / MediaStore 路径解析链，仅解析 SAF tree 文档 ID；持久权限只请求读权限；新增 `OpenDocumentTree()` 结果入口与持久化恢复入口 `restore()` |
| `musikr/src/main/java/org/oxycblt/musikr/fs/Path.kt` | `app/src/main/java/com/bobo/auralis/mobile/library/saf/SafPath.kt` | 裁剪：移除 Volume 兼容层、Windows 路径格式与 MediaStore 命名，保留 `Components` 路径运算 |
| `musikr/src/main/java/org/oxycblt/musikr/fs/FS.kt` | `app/src/main/java/com/bobo/auralis/mobile/library/saf/SafModels.kt` | 裁剪：移除 `AddedMs` 与 `FS` 接口，保留 `File` / `Directory` 结构；新增扫描事件与 `SafDocumentKey`（provider + documentId，用于重叠根目录的稳定去重） |
| `musikr/src/main/java/org/oxycblt/musikr/util/LangUtil.kt` | 未移植（改用 kotlinx.coroutines 标准 `async` / `awaitAll`） | 仅参考并发组织方式 |
| `musikr/src/main/java/org/oxycblt/musikr/metadata/*` 与 `musikr/src/main/cpp/*` | `app/src/main/java/com/bobo/auralis/mobile/library/metadata/*` 与 `app/src/main/cpp/*` | Phase 2B 移植：剥离 Auxio FS 改用 `MetadataTarget`；JNI 补齐 ID3v2 `USLT` 歌词桥接；保留按容器（ID3v2/MP4/Xiph）分离的原始标签映射与音频属性 |
| `musikr/src/main/java/org/oxycblt/musikr/tag/parse/TagFields.kt` | `app/src/main/java/com/bobo/auralis/mobile/library/metadata/MetadataInterpreter.kt` | Phase 2C 参考：复用各音轨容器（ID3v2/MP4/Xiph）标准标签别名映射关系（tag key aliases）；丢弃其产品优先级、丢弃 `TagParser.kt`、丢弃 `Separators.kt`，按 Auralis 专属规则实现严格多值与解释 |

目录选择流程参考 Auxio 的
`app/src/main/java/org/oxycblt/auxio/music/locations/LocationsDialog.kt`：
使用 `ActivityResultContracts.OpenDocumentTree()` 获取 tree URI，再通过
`SafLocation.fromPickerResult()`（对应 Auxio 的 `Location.open()`）调用
`takePersistableUriPermission` 持久化读权限。

## 许可证影响（重要）

Auxio / Musikr 使用 GPL-3.0-or-later。上述代码进入 Auralis Mobile 后，Auralis Mobile
的整体分发必须满足 GPL-3.0-or-later 的要求，包括但不限于：

- 以 GPL-3.0-or-later 许可整个组合作品；
- 向接收者提供完整对应源代码；
- 保留版权声明、来源说明与许可证文本。

当前仓库顶层 `LICENSE` 已包含 GNU GPL v3 许可证文本。对外分发（发布 APK、上传应用商店或
公开构建产物）时，仍须提供完整对应源代码并保留本文件中的来源与版权说明。

上述许可证全文见：<https://www.gnu.org/licenses/gpl-3.0.html>

## AndroidX Media3 示例

- 上游仓库：<https://github.com/androidx/media>
- 参考文件：`demos/session_service/.../DemoPlaybackService.kt` 与
  `DemoMediaLibrarySessionCallback.kt`
- 参考版本：tag `1.11.1`，commit `8c6678b657ede1e7883fc164ef73ed483c7796c3`
- 上游许可证：Apache License 2.0
- 版权：Copyright 2023 The Android Open Source Project

`AuralisPlaybackService.kt` 的 Service / Session 生命周期结构基于官方示例适配；
曲库解析、身份映射和播放可用性回写为 Auralis 自有实现。

## AndroidLiquidGlass / Backdrop

- 上游仓库：<https://github.com/Kyant0/AndroidLiquidGlass>
- 使用组件：`io.github.kyant0:backdrop:2.0.0`
- 审计参考 commit：`65ab177e90e5c1d8c62e70cf7755841982da65f6`
- 上游许可证：Apache License 2.0
- 版权：Copyright 2025 Kyant

Auralis 通过 Maven 依赖复用 Backdrop 的背景采样、模糊、鲜明度与透镜效果，用于悬浮导航、
独立搜索按钮和迷你播放器。产品布局、状态、导航语义与降级策略由 Auralis 自行实现；未复制
上游示例组件源码。具体版本与兼容性决策见 `docs/LIQUID_GLASS_REUSE_AUDIT.md`。

## Coil

- 上游项目：<https://github.com/coil-kt/coil>
- 使用组件：`io.coil-kt.coil3:coil-compose:3.3.0`
- 上游许可证：Apache License 2.0
- 版权：Copyright 2026 Coil Contributors

Auralis 使用 Coil 负责本地专辑封面的异步解码、尺寸采样、Compose 展示以及内存/磁盘缓存。
项目未引入 Coil 的网络组件，应用仍不申请 `INTERNET` 权限。SAF 音频内嵌封面与同目录
`cover` / `COVER` 的来源选择由 Auralis 按 `METADATA.md` 实现。

## TinyPinyin

- 上游项目：<https://github.com/tyzlmjj/TinyPinyin>
- 使用组件：`me.majiajie:tinypinyin:2.0.3`
- 原始项目：<https://github.com/promeG/TinyPinyin>
- 上游许可证：Apache License 2.0
- 传递依赖：`org.ahocorasick:ahocorasick:0.6.3`（Apache License 2.0）

Auralis 使用 TinyPinyin 在内存中为 Album Artist 生成拉丁字母排序键，使中文名能够按拼音与
拉丁字母名称混合排序。转换结果不写入数据库，也不改变元数据和界面中的艺术家原文。

## Material Color Utilities

- 上游项目：<https://github.com/material-foundation/material-color-utilities>
- 固定 commit：`5b3618b16fdc3825e21d5679bafd144662088ea1`
- 使用内容：Celebi、Score、HCT 及必要依赖，共 15 个 Java 源文件
- 上游许可证：Apache License 2.0
- 版权：Google LLC，保留各文件原始版权声明

Auralis 使用官方算法从专辑封面提取代表色并调整深浅主题下的轨道颜色。源码位于
`app/src/main/java/com/bobo/auralis/mobile/thirdparty/materialcolorutilities`，许可证全文随源码保存。
调整 package/import 命名空间，并将 `QuantizerWu` 的 switch 改为等价 Java 11 语法，未修改上游算法；中性色回退和主题 Tone 范围由应用层负责。
结果只保存在进程内存中，不写入曲库数据库，也不改变封面文件。

## Noto Serif SC

- 上游仓库：<https://github.com/google/fonts/tree/main/ofl/notoserifsc>
- 固定版本：Google Fonts commit `2e61f4355afd22b801791b0df176065082423b87`
- 使用内容：Noto Serif SC Black，仅保留“资料库”三个字所需字形的字体子集
- 上游许可证：SIL Open Font License 1.1
- 版权：Copyright 2012 Google Inc. All Rights Reserved.

Auralis 将该字体子集离线打包，仅用于资料库页面标题，不产生运行时网络请求。许可证全文随应用源码保存在
`app/src/main/res/raw/ofl_noto_serif_sc.txt`。
