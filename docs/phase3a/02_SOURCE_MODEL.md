# Phase 3A 施工册 02 · Domain source / observation / state model（Step C）

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | Step C 定义 domain observation/state models：ObservedTrackSource / SourceMetadata / states |
| 提交边界 | §72 未单列；建议并入 Room schema 之前的一次提交 |
| 本册章节 | §12 §13 §22 §23 §24 §45 |
| 依赖 | 00 / 01 册 |
| 供下游 | 03 / 04 / 05 册 |
| 完成判据 | ObservedTrackSource、SourceMetadata 与三类 state 以纯 domain model 冻结，不含 Room 依赖 |

<!-- PHASE3A:BODY -->

---

## 12. Physical source 模型

正式命名建议统一使用：

```text
TrackSource
```

不要叫 `PhysicalFile`，因为 Android SAF 的 source 不一定对应传统绝对路径文件。

`TrackSourceEntity` 表示“当前或曾经被 Auralis 发现过的一个物理音频 source”。

基本结构：

```text
TrackSourceEntity
-----------------
sourceId
trackId

provider
documentId

rootId
uri
relativePath
fileName

format
mimeType
sizeBytes
modifiedMs

durationMs
bitrateKbps
sampleRateHz

availabilityState
parseState
playabilityState

lastSeenScanId
lastSeenAt

failureStage
failureCode
failureMessage
```

其中建立：

```text
UNIQUE(provider, documentId)
```

或者至少 unique index。

当前 Auralis 已经明确把 `(provider, documentId)` 作为“同一个物理 document 被多个 tree root 发现时仍相同”的稳定 key，这个设计直接进入正式 source 模型即可。

---

## 13. TrackSource metadata snapshot

这里必须比前一版草案进一步明确：**每一个 TrackSource 必须保存自己最后一次成功解析得到的 metadata snapshot。**

否则有两个 duplicate source：

```text
A/song.flac
B/song.m4a
```

当前 A 为 active，B 为备用。

如果之后 A 删除，需要把 B 提升为 active。此时如果数据库只保存 Track 的 metadata，而没有 B 自己的 metadata，就无法正确切换 Artist / Genre / Album 等信息，除非再次强制读取文件。

因此应增加：

```text
SourceMetadataEntity
--------------------
sourceId PK/FK

title
albumTitle
date

trackNumber
trackTotal
discNumber
discTotal

artistsSerialized
albumArtistsSerialized
genresSerialized

durationMs
bitrateKbps
sampleRateHz
mimeType

trackKeyVersion
trackKeyHash
trackKeyStrength

albumKeyVersion
albumKeyHash nullable
```

这里 `artistsSerialized / albumArtistsSerialized / genresSerialized` 允许用 deterministic JSON array 或专用 Room TypeConverter 存成单列，因为这一层属于“source metadata snapshot/cache”，不是用户查询 graph。

真正用于：

```text
按 Artist 浏览
按 Genre 搜索
Album graph
```

的是 logical 层的正规 relation tables。

这样可以避免为 source snapshot 再创建三套 many-to-many 表，控制复杂度。

`rawMetadata` 不进入正式 database。当前 `AuralisMetadata` 中虽然有 `rawMetadata`，它主要用于 debug / extraction diagnostics，不需要为了 Phase 3 长期持久化整个原始 tag map。

Artwork bytes 也不能塞 Room。

---

## 22. Source availability state

建议正式冻结为：

```text
AVAILABLE
MISSING
UNREACHABLE
```

语义分别为：

`AVAILABLE`：本轮可确认 source 存在。

`MISSING`：Auralis 成功扫描了原 source 所属可观测范围，并明确确认该 document 已不再存在。

`UNREACHABLE`：无法可靠确认它还在不在，例如 root permission revoked、provider error、目录 query failure。

`UNREACHABLE` 绝不能自动转成 `MISSING`。

当前 `SafScanEvent.DirectoryUnavailable` 已经专门为这一行为留出了信号，所以正式 reconciliation 需要直接消费该事件，而不是把“本轮没看到”简单理解成“文件删除”。

---

## 23. Parse state

建议：

```text
NOT_PARSED
PARSED
FAILED
```

成功 Phase 2C 后为 `PARSED`。

失败保留：

```text
failureStage
failureCode
failureMessage
```

Parse failed source 不参与 semantic TrackKey matching，但仍然作为 physical TrackSource 保存。

这满足原先产品规则中“不可解析文件不能静默丢弃”。

---

## 24. Playability state

建议：

```text
UNKNOWN
PLAYABLE
UNPLAYABLE
```

扫描成功和 metadata parse 成功不能自动声称：

```text
PLAYABLE
```

除非已经由播放链路确认。

因此刚扫描成功：

```text
UNKNOWN
```

允许参与 active source candidate。

以后 Media3 成功打开过：

```text
PLAYABLE
```

如果明确失败：

```text
UNPLAYABLE
```

并重新执行 active source selection。

Phase 3 数据模型只需要支持这一状态；不需要为了 Phase 3 专门提前播放每个文件。

---

## 45. ObservedTrackSource

扫描与数据库之间不要直接传 Room entity。

增加一个纯 domain model：

```kotlin
data class ObservedTrackSource(
    val documentKey: SafDocumentKey,
    val root: RootReference,
    val uri: Uri,
    val relativePath: SafPath,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val format: AudioFormat,
    val metadata: AuralisMetadata,
)
```

然后 Identity layer 产生：

```kotlin
ResolvedObservation(
    observation,
    albumIdentity,
    albumKey,
    trackIdentity,
    trackKey,
    trackKeyStrength
)
```

这样：

```text
scanner
metadata engine
identity
Room
```

彼此没有混在一起。
