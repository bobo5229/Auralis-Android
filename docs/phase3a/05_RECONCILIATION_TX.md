# Phase 3A 施工册 05 · Room-backed reconciliation transaction（Step F）

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | Step F 实现 Room-backed reconciliation transaction |
| 提交边界 | reconciliation core + tests |
| 本册章节 | §40 §41 §42 §46 §47 §61 |
| 依赖 | 00 / 03 / 04 册 |
| 供下游 | 06 / 07 册 |
| 完成判据 | §40 的 active metadata 更新完整落在一个 transaction 内，§61 数据库不变量测试全部通过 |

<!-- PHASE3A:BODY -->

---

## 40. Active metadata 更新策略

每当以下任一事件发生：

```text
source added
source missing
source unreachable
source parse state changed
source playability changed
root priority changed
```

都运行：

```text
selectActiveSource(trackId)
```

如果 winner 没变：

```text
不必重建 logical metadata
```

如果 winner 改变：

```text
1. update TrackActiveSource
2. copy winner SourceMetadata scalar values into Track
3. resolve/create Album
4. rebuild TrackArtist relation
5. rebuild TrackGenre relation
```

全部必须处于同一个 Room transaction。

---

## 41. Album lifecycle

AlbumEntity 不需要因为当前没有 active Track 就立刻删除。

建议 Phase 3 初版采取：

```text
保留 orphan Album/Artist/Genre
```

不要在每轮 reconciliation 过程中实时 garbage collect。

原因是：

```text
逻辑更简单
恢复 Track 时 ID 可以稳定
不会在复杂 transaction 中反复删除/重建 graph entity
```

数据库体积非常有限。

未来可以做显式 maintenance GC。

Artist 与 Genre 同理。

---

## 42. Artist / Genre stable ID

一个 Artist 第一次出现时：

```text
lookup by ArtistKey
```

存在：

```text
reuse ArtistId
```

不存在：

```text
create random ArtistId
```

Genre 同理。

因此 ArtistId 不直接等于 artist name hash。

以后 identity algorithm 改版仍有迁移空间。

---

## 46. Reconciliation 输入必须是一轮 scan session

不要让每扫描到一个文件就立刻做“旧文件没看到 = missing”。

需要 `ScanSession` 概念：

```text
scanId
startedAt
roots attempted
roots completed
unavailable scopes
observations
```

扫描过程中可以逐个 parse，但：

```text
missing reconciliation
```

只能在该 root/scoped scan 完成后执行。

这是避免扫描中途把整库标记 missing 的关键。

---

## 47. Reconciliation transaction 边界

TagLib extraction、文件 IO、SAF query 全部在数据库 transaction 外。

Room transaction 只处理：

```text
existing DB state
+
already prepared ResolvedObservation
+
scan completion state
```

事务中绝不能：

```text
open ContentResolver
read audio
run TagLib
decode artwork
probe Media3
```

否则数据库锁会被文件 IO 长时间占用。

---

## 61. Database integration tests

Room instrumentation test 重点不是测试 getter，而是验证 invariants：

```text
唯一 document key
唯一 AlbumKey
唯一 ArtistKey
唯一 GenreKey

junction FK integrity

delete TrackSource
→ SourceMetadata cascade

delete Track
→ source / logical cross refs clean

transaction rollback
→ 不出现 half-updated graph
```

尤其要测试 active source change transaction：

```text
A active → A missing → B active
```

完成后数据库必须同时满足：

```text
TrackActiveSource = B
Track metadata = B
TrackArtist = B artists
TrackGenre = B genres
Album = B album
```

不能出现其中一半还是 A。
