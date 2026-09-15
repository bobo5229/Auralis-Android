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

当前仓库尚未包含顶层 `LICENSE` 文件。在对外分发（发布 APK、上传应用商店或公开构建产物）
之前，必须补充 GPL-3.0 许可证并确认整个项目的许可策略。

上述许可证全文见：<https://www.gnu.org/licenses/gpl-3.0.html>
