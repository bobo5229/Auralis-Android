# Phase 3A 施工册 03 · Room schema、FK、索引与 DAO（Step D）

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | Step D 引入 Room，建立 schema、FK、index、DAO 和 migration baseline |
| 提交边界 | Room schema + DAO |
| 本册章节 | §14 §15 §16 §17 §18 §19 §20 §21 §25 §48 §49 §50 §51 §52 |
| 依赖 | 00 / 01 / 02 册 |
| 供下游 | 05 / 07 册 |
| 完成判据 | §48 全部实体与关系建成，§49 删除策略与 §50 索引生效，DAO 可读写 |

<!-- PHASE3A:BODY -->

---

## 14. Logical Track metadata

`TrackEntity` 保存 active Track 当前的逻辑 metadata snapshot：

```text
TrackEntity
-----------
trackId

trackKeyVersion
trackKeyHash
trackKeyStrength

albumId nullable

title nullable
date nullable
trackNumber nullable
trackTotal nullable
discNumber nullable
discTotal nullable

createdAt
updatedAt
```

Artist / Genre 不重复塞 TEXT，而使用关系表。

Logical Track metadata 的唯一来源是：

```text
当前 active TrackSource 的 SourceMetadata
```

当 active source 改变时，TrackEntity 及其 Artist/Genre/Album relations 同步切换到新 source 的 metadata。

当 Track 没有 active source 时，则保留最后一次 logical metadata，不清空。

这保证：

```text
权限暂时失效
文件暂时丢失
删除后的 tombstone
```

仍然知道原来是哪首歌。

---

## 15. Track Artist junction

建议：

```text
TrackArtistCrossRef
-------------------
trackId
artistId
position
```

主键推荐：

```text
PRIMARY KEY(trackId, position)
```

另建：

```text
INDEX(artistId)
```

而不是：

```text
PRIMARY KEY(trackId, artistId)
```

因为我们需要保留 credit order。

由于 Phase 2C 已经保证 list 内的值来自明确多值规则，Phase 3 在写 relation 前可以按 exact normalized identity 去重；如果一个文件真的出现：

```text
A; A
```

logical relation 只保留一次 `A`。

---

## 16. Album Artist junction

结构同样为：

```text
AlbumArtistCrossRef
-------------------
albumId
artistId
position
```

Album Artist relationship 完全来自 Album identity/source metadata。

它不能从 Track Artist 推导。

当一个 Album 下不同 source 对 Album Artist tag 不一致时，它们原则上应该已经生成不同 AlbumKey，因此不会被错误聚合。

---

## 17. Track Genre junction

```text
TrackGenreCrossRef
------------------
trackId
genreId
position
```

仍保留 source 中的 Genre 顺序。

Genre relation 随 active source 更新。

---

## 18. AlbumEntity

建议：

```text
AlbumEntity
-----------
albumId
albumKeyVersion
albumKeyHash UNIQUE

title
date

createdAt
updatedAt
```

Album Artist 通过 junction，不在 AlbumEntity 中再保存拼接字符串。

不要存：

```text
coverPath
representativeTrackId
trackCount
duration
```

作为 authoritative identity data。

这些属于可派生或未来 cache/view 数据。

尤其 Artwork cache 后面必须以 `AlbumId` 或明确 artwork identity 为基础，绝不能用 Album title。

---

## 19. ArtistEntity / GenreEntity

建议：

```text
ArtistEntity
------------
artistId
artistKeyVersion
artistKey UNIQUE
displayName
```

```text
GenreEntity
-----------
genreId
genreKeyVersion
genreKey UNIQUE
displayName
```

v1 identity 都来自 canonicalized name。

当前不增加：

```text
sortName
alias
MusicBrainzId
externalId
normalizedSearchName
```

这些未来需要时再增加。

---

## 20. LibraryRootEntity

当前 debug `SafRootStore` 使用有序 SharedPreferences list；代码已经明确说明这个顺序就是 future duplicate priority。 正式数据库设计应保留这一语义。

建议：

```text
LibraryRootEntity
-----------------
rootId
treeUri UNIQUE
displayPath
priority

availabilityState
lastSuccessfulScanAt
lastAttemptedScanAt

createdAt
```

其中：

```text
priority
```

代表添加顺序。

不要每次扫描按当前 list index 临时计算优先级，否则删除一个 root 后其余 root 的 identity 数据会全部变化。

可以设计为单调递增：

```text
0
1
2
3
...
```

用户删除 root 2 后：

```text
0
1
3
```

即可。

如果未来支持手动调整 root priority，再明确修改该字段。

---

## 21. Root 删除和 root temporarily unavailable 必须区分

这是 database layer 必须提前建模的区别。

`LibraryRootEntity.availabilityState` 建议：

```text
AVAILABLE
UNAVAILABLE
```

“用户从 Auralis 中删除 root”不等于 `UNAVAILABLE`，而是 root configuration 被明确移除。

两种行为不同：

```text
provider / permission temporarily unavailable
→ 保留 root
→ 保留其 source
→ 不判定 source missing
```

```text
user explicitly removes root
→ root 不再参与 active library
→ 该 root 独占 source 可转为 detached/missing
→ 若 physical document 同时被其它 configured root 覆盖，则仍由其它 observation 保持 source active
```

后续实现必须先处理 overlap，再决定 source 是否真的离开 library。

---

## 25. Active source

Track 与 active TrackSource 不建议直接在 `TrackEntity` 加：

```text
activeSourceId
```

因为这会形成：

```text
TrackSource.trackId → Track
Track.activeSourceId → TrackSource
```

双向 FK。

更清楚的结构是：

```text
TrackActiveSourceEntity
-----------------------
trackId PK
sourceId UNIQUE
```

必须保证：

```text
source.trackId == trackId
```

这个条件 SQLite FK 本身不好表达，因此 selection/write service 需要在 transaction 内验证。

如果 Track 当前没有 usable source：

```text
TrackActiveSource row 不存在
```

---

## 48. 推荐 Room schema

完整草图：

```text
LibraryRootEntity
TrackEntity
TrackSourceEntity
SourceMetadataEntity
TrackActiveSourceEntity

AlbumEntity
ArtistEntity
GenreEntity

TrackArtistCrossRef
AlbumArtistCrossRef
TrackGenreCrossRef
```

关系：

```text
LibraryRoot 1 ─── N TrackSource

Track 1 ─── N TrackSource
Track 1 ─── 0..1 TrackActiveSource

Track N ─── 0..1 Album

Track N ─── M Artist
Album N ─── M Artist
Track N ─── M Genre

TrackSource 1 ─── 0..1 SourceMetadata
```

---

## 49. Foreign key 删除策略

建议 conservative 使用：

```text
TrackSource.trackId → Track
ON DELETE CASCADE
```

但正常 library reconciliation **不删除 Track**。

`SourceMetadata.sourceId → TrackSource ON DELETE CASCADE`

`TrackActiveSource.trackId → Track ON DELETE CASCADE`

`TrackActiveSource.sourceId → TrackSource ON DELETE CASCADE`

junction：

```text
ON DELETE CASCADE
```

Album 在 TrackEntity 中建议：

```text
ON DELETE SET NULL
```

不过正常流程同样不会主动删除 Album。

---

## 50. 关键索引

至少设计：

```text
Track(trackKeyVersion, trackKeyHash)
Track(albumId)

TrackSource(provider, documentId) UNIQUE
TrackSource(trackId)
TrackSource(rootId)
TrackSource(lastSeenScanId)

SourceMetadata(trackKeyVersion, trackKeyHash)
SourceMetadata(albumKeyVersion, albumKeyHash)

Album(albumKeyVersion, albumKeyHash) UNIQUE

Artist(artistKeyVersion, artistKey) UNIQUE
Genre(genreKeyVersion, genreKey) UNIQUE

TrackArtist(artistId)
AlbumArtist(artistId)
TrackGenre(genreId)
```

不应为了 Phase 3A 提前建 FTS。

---

## 51. UUID 与时间

数据库内部时间统一使用：

```text
Long epoch milliseconds
```

例如：

```text
createdAt
updatedAt
lastSeenAt
scanStartedAt
```

Date metadata 继续保存：

```text
YYYY-MM-DD
```

不能把音乐发行 Date 与数据库 timestamp 混用。

---

## 52. Room TypeConverter 边界

只给真正 value-type 数据使用 converter，例如：

```text
UUID ↔ String
Uri ↔ String
enum ↔ String
List<String> source snapshot ↔ JSON
```

不要使用 TypeConverter 把：

```text
Artist list
Genre list
Album relationship
```

塞进 TrackEntity 单列。

查询 graph 的字段必须正规化。
