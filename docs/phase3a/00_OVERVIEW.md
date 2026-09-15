# Phase 3A 施工册 00 · 总览与全局契约

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | 前置，Step A–K 全部适用 |
| 提交边界 | docs（§72） |
| 本册章节 | §1 §2 §53 §54 §56 §57 §65 §66 §67 §68 §69 §70 §71 §72 §73 |
| 依赖 | —— 册 |
| 供下游 | 01–08 册 |
| 完成判据 | 无独立代码产出；后续任一册与 §73 不变量冲突时以本册为准 |

<!-- PHASE3A:BODY -->

---

## 原文前言

本方案是 Phase 3A 的最终设计规格。目标是把后续数据库与 reconciliation 实现所需要的语义全部冻结下来，使 Agent 在下一阶段只按确定规则施工，不再自行决定 Track identity、Album identity、duplicate、删除恢复或数据库关系。本阶段本身仍不进入 Room 代码施工。

当前仓库已经具备这套设计需要的两个上游输入：`AuralisMetadata` 已提供 Title、Artist、Album Artist、Album、Genre、Date、Track/Disc、时长和音频属性；SAF 层已经提供 `(provider, documentId)` 形式的 `SafDocumentKey`。因此 Phase 3 不需要改变 Phase 2C 的 metadata 语义，也不需要重新设计扫描器。 当前 `SafRootStore` 又天然保存了 root 添加顺序，并明确把该顺序预留为未来 duplicate candidate priority，因此这条已有产品规则可以直接迁移进正式曲库层。

---

## 1. 总体模型

Auralis 曲库必须明确区分三个概念：

```text
Logical identity
    Track
      │
      ├── Album
      ├── Artists
      └── Genres
      │
      1
      │
      N
Physical identity
    TrackSource
      │
      ├── SAF document
      ├── URI / relative path
      ├── audio properties
      └── source-specific metadata snapshot
```

最终原则为：

```text
TrackId ≠ TrackKey ≠ SafDocumentKey ≠ Path
```

四者含义严格不同。

`TrackId` 是 Auralis 内部长期稳定的 logical library entry ID；`TrackKey` 是用 metadata 计算出的 semantic reconciliation key；`SafDocumentKey` 是物理 SAF document 的 continuity key；`Path/URI` 只是当前 location。

不能把其中任何两者合并成一个概念。

---

## 2. ID 类型与版本策略

所有数据库实体自己的主键都使用 Auralis 生成的 UUID。建议在 Kotlin domain 层使用 value class，数据库中最终保存为 TEXT UUID。

概念上定义：

```kotlin
TrackId(UUID)
TrackSourceId(UUID)
AlbumId(UUID)
ArtistId(UUID)
GenreId(UUID)
LibraryRootId(UUID)
```

Identity key 使用独立字符串类型：

```kotlin
TrackKey(version = 1, hash = ...)
AlbumKey(version = 1, hash = ...)
ArtistKey(version = 1, value = ...)
GenreKey(version = 1, value = ...)
```

数据库主键永远不能直接使用 TrackKey 或 AlbumKey。

原因是 Identity 算法未来可能出现 `V2`。如果 `TrackKeyV1` 同时承担数据库 PK，那么 identity 规则升级会迫使整个曲库及歌单、播放历史等引用一起迁移。采用稳定 UUID 后，未来只需重新计算 key，不需要改变 `TrackId`。

建议数据库直接显式保存：

```text
trackKeyVersion = 1
trackKeyHash
albumKeyVersion = 1
albumKeyHash
```

而不是假定当前算法永远不会变化。

---

## 53. Artwork 边界

Phase 3 schema 现在只需允许 source metadata 表达：

```text
hasEmbeddedArtwork
```

是否最终保存 artwork cache identity，可以留给 artwork phase。

不要：

```text
把 ByteArray 放 Room
```

当前 `AuralisMetadata` 本身能够携带 artwork data，这只是 extraction pipeline 对象，不代表数据库应该照抄。

---

## 54. Lyrics 边界

类似地：

```text
embeddedLyrics
```

当前已经在 metadata model 中，但 Phase 3 identity/reconciliation 不依赖 lyrics。

Phase 3 database 可以暂时不持久化完整 lyrics，等歌词功能阶段再决定缓存方式。

Lyrics 绝不能参与 TrackKey。

---

## 56. 文件/包结构建议

目前仓库只有 `metadata / saf / scan` 三块正式 library package，而且还没有 Room 依赖。 当前 `app/build.gradle.kts` 也尚未引入 Room，因此数据库层确实还没有被提前实现。

后续建议结构：

```text
library/
    metadata/
        existing Phase 2 code

    saf/
        existing Phase 1 code

    scan/
        existing scan code

    identity/
        IdentityTextNormalizer.kt
        AlbumIdentity.kt
        AlbumKey.kt
        TrackIdentity.kt
        TrackKey.kt
        IdentityHasher.kt

    model/
        ObservedTrackSource.kt
        SourceStates.kt

    db/
        AuralisDatabase.kt

        entity/
            LibraryRootEntity.kt
            TrackEntity.kt
            TrackSourceEntity.kt
            SourceMetadataEntity.kt
            TrackActiveSourceEntity.kt
            AlbumEntity.kt
            ArtistEntity.kt
            GenreEntity.kt
            CrossRefs.kt

        dao/
            LibraryRootDao.kt
            TrackDao.kt
            SourceDao.kt
            GraphDao.kt

    reconcile/
        LibraryReconciler.kt
        SourceMatcher.kt
        ActiveSourceSelector.kt
        LogicalMetadataProjector.kt
```

这里没有必要现在增加多 module Gradle 工程。

单 `app` module 足够。

---

## 57. Pure Kotlin 与 Android/Room 边界

必须尽量让以下代码成为纯 Kotlin：

```text
identity normalization
AlbumKey
TrackKey
Strength calculation
active-source comparator
semantic matching decision
```

它们不依赖：

```text
Context
ContentResolver
Room
Uri（如果不必要）
```

这样大部分 Phase 3 核心规则可以用 JVM test 验收，不依赖 Find X9。

Room 只负责 persistence。

SAF 只负责 observation。

---

## 65. Artwork/cache identity 预留

虽然 Phase 3 不实现 artwork cache，但需要提前规定：

以后 Album artwork cache key 可以引用：

```text
AlbumId
```

不能引用：

```text
albumTitle
```

Track artwork 可以引用：

```text
TrackId
或 SourceId
```

具体来源策略以后决定。

这样 Phase 3 的 stable identity 能自然支持后续 artwork。

---

## 66. Playlist identity 预留

虽然本阶段不实现 playlist，但现在就明确：

以后：

```text
PlaylistEntry
```

必须引用：

```text
TrackId
```

而不是：

```text
SourceId
URI
Path
TrackKey
```

这样 duplicate winner 切换、rename、move、reencode 都不会让歌单失效。

这也是为什么 Track tombstone 必须保留。

---

## 67. 播放层预留

以后 Media3 播放一个 Track 时逻辑应是：

```text
TrackId
→ TrackActiveSource
→ TrackSource.uri
→ Media3
```

播放层不应该自己重新做 duplicate resolution。

如果当前 source 播放失败：

```text
mark source UNPLAYABLE
→ rerun active source selection
→ optionally retry next source
```

这是后续 phase 的职责，但 Phase 3 数据模型必须支持。

---

## 68. 不采用音频 fingerprint

Phase 3 明确不引入：

```text
Chromaprint
AcoustID
PCM fingerprint
audio content hash
whole-file SHA
```

Whole-file hash 也不适合作 logical identity，因为 metadata 修改或重编码都会改变文件 bytes。

现阶段：

```text
SafDocumentKey
+
Strong TrackKey
+
tombstone history
```

已经足以覆盖确定的产品需求。

只有未来需求变为“路径、documentId、metadata、编码全都变化后仍必须认出相同录音”，再评估 fingerprint。

---

## 69. 明确不复用 Auxio/Musikr 的部分

继续复用其成熟机制思想：

```text
SAF exploration
raw metadata extraction
pipeline layering
metadata cache 思路
graph construction 思路
metadata-based hash 思路
```

但不得把其以下语义带入 Auralis：

```text
Album Artist fallback Artist
Artist fallback Album Artist
missing Album fallback directory
Album identity 不含 Date
URI 直接作为长期 song identity
MusicBrainz 优先取代 Auralis identity
Auxio unified Artist 产品语义
```

Auralis graph 从 Phase 3 开始必须完全由本方案定义。

---

## 70. Phase 3A 应形成的正式设计文档

虽然本轮不施工，但最终仓库施工时建议把设计固化成：

```text
docs/LIBRARY_MODEL.md
```

其内容至少包括：

```text
Track vs TrackSource
TrackId / TrackKey / SafDocumentKey / Path 区别
TrackKeyV1 spec
AlbumKeyV1 spec
Strong / Weak
Artist / Genre graph
source states
duplicate ranking
reconciliation order
tombstone behavior
Room ER graph
```

同时更新：

```text
ARCHITECTURE.md
METADATA.md
```

只写摘要和链接，不重复整份规范。

`LIBRARY_MODEL.md` 应成为未来改变 identity 时的唯一 specification。

---

## 71. 后续施工顺序

真正执行时建议严格按依赖顺序施工，而不是一次把整个 database 和 scan 集成一起写完：

```text
Step A
冻结 identity domain：
Normalizer / canonical collection / AlbumKey / TrackKey / Strength。

Step B
完整 JVM tests，identity 规则全部通过。

Step C
定义 domain observation/state models：
ObservedTrackSource / SourceMetadata / states。

Step D
引入 Room，建立 schema、FK、index、DAO 和 migration baseline。

Step E
实现 pure reconciliation decision layer。

Step F
实现 Room-backed reconciliation transaction。

Step G
接现有 SAF + Phase 2C metadata pipeline。

Step H
实现 active source projection → logical graph。

Step I
迁移 root persistence。

Step J
加入 debug inspection UI。

Step K
JVM / Room / Find X9 三层验收。
```

不能反过来先建 Room 表再边做边决定 identity。

---

## 72. 阶段提交边界

后续施工最好拆成数个独立 commit，使问题容易定位：

```text
identity model + tests

Room schema + DAO

reconciliation core + tests

scanner/metadata integration

root persistence migration

debug/acceptance tooling

docs
```

TagLib/JNI 不应该因为 Phase 3 被修改。

Phase 2C `MetadataInterpreter` 也原则上不应修改，除非发现它缺少 Phase 3 已经确定必须存在的 semantic output；目前 `AuralisMetadata` 已具备主要 identity 字段，因此预期无需改 Phase 2C。

---

## 73. Phase 3A 最终不变量

Phase 3 后所有代码都必须满足以下不变量：

```text
TrackId 是用户层长期 identity。

TrackKey 是可变化、可版本化的 semantic reconciliation key。

SafDocumentKey 是 physical continuity key。

Path/URI 从来不是 logical identity。

Album identity = Album + AlbumArtist set + Date。

Album Artist 永不 fallback Artist。

Artist 永不 fallback Composer 或 Album Artist。

缺失 Album 不创建 shared Unknown Album entity。

Multi Artist identity 与顺序无关，但显示顺序保留。

Genre 不参与 Track identity。

codec/bitrate/size/duration/path 不参与 Track identity。

一个 Track 可以拥有多个 TrackSource。

一个 Track 同时最多一个 active source。

duplicate loser 不删除。

active logical metadata 始终来自 active source。

same physical source + same Album → metadata edit 保持 TrackId。

Album identity 改变 → 必须跨 logical Track。

new physical source 只有 Strong TrackKey 才能自动 semantic merge。

Weak TrackKey 不做跨文件 merge。

semantic match 多重命中时不自动猜测。

successful scan 才能确认 MISSING。

unavailable directory 永远不能推导为 deleted。

没有 active source 的 Track 保留 tombstone。

重新出现可以重新连接旧 TrackId。

未来 playlist 引用 TrackId，而不是 source/path/key。

v1 不引入 audio fingerprint。
```

这组不变量就是 Phase 3A 的最终技术契约。只要后续数据库和 reconciliation 实现逐条满足这些条件，就不会把“文件位置”“物理文件”“metadata identity”和“用户眼中的曲目”重新混成同一个概念。
