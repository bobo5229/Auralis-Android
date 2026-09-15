# Phase 3A 施工册 01 · Identity domain（Step A / B）

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | Step A 冻结 identity domain：Normalizer / canonical collection / AlbumKey / TrackKey / Strength；Step B 完整 JVM tests |
| 提交边界 | identity model + tests |
| 本册章节 | §3 §4 §5 §6 §7 §8 §9 §10 §11 §58 |
| 依赖 | 00 册 |
| 供下游 | 02 / 03 / 04 册 |
| 完成判据 | §3–§11 规则实现完成，§58 identity 测试矩阵全部通过 |

<!-- PHASE3A:BODY -->

---

## 3. Identity canonicalization

所有参与 identity 的文本必须先经过统一 canonicalization。这套规则应该成为独立纯 Kotlin 模块，而不是散落在 DAO 或 scanner 中。

`IdentityTextNormalizerV1` 精确执行：

```text
1. 如果原始值为 null → null
2. trim 前后 Unicode whitespace
3. Unicode normalize NFC
4. trim 后如果为空字符串 → null
5. 保留其它所有字符原样
```

明确不执行：

```text
lowercase
case folding
内部连续空格压缩
标点删除
accent removal
全半角替换
语言转换
artist alias
模糊匹配
```

因此 `Taylor Swift` 与 `taylor swift` 在 identity 层不是同一文本；`AC/DC` 也不会被改写。

这是有意采用 conservative identity：宁可偶尔产生两个 entry，也不要因为激进 normalization 把不同作品错误合并。

---

## 4. Multi-value canonicalization

Artist、Album Artist、Genre 都来自 Phase 2C 已经解析好的 `List<String>`，Phase 3 不允许再做任何 split。

Identity 中的多值字段采用：

```text
normalize each value
→ drop null/empty
→ exact deduplicate
→ sort lexicographically by normalized value
```

也就是说 identity 使用集合语义。

例如：

```text
["A", "B"]
["B", "A"]
```

参与 identity 时完全相同。

但是 source metadata 和最终展示关系必须同时保存原始有效顺序：

```text
A; B
```

仍按 `A → B` 显示。

因此必须明确区分：

```text
identity representation = sorted set
display representation = ordered list
```

不允许 identity hash 的排序结果反过来改变 UI Artist credit 顺序。

---

## 5. Album identity

### 5.1 AlbumKeyV1

Album 只有在 `album title != null` 时才创建正式 `AlbumEntity`。

精确定义：

```text
AlbumIdentityV1 {
    albumTitle
    albumArtists
    date
}
```

即：

```text
AlbumKeyV1 =
SHA-256(
    versionMarker,
    albumTitle,
    sortedDistinct(albumArtists),
    date
)
```

Date 使用 Phase 2C 已经得到的 `YYYY-MM-DD` 或 NULL。

因此：

```text
Album A
Album Artist = X
Date = 2025-01-01
```

与：

```text
Album A
Album Artist = X
Date = 2026-01-01
```

必须是两个不同 release。

Album title 绝不能单独决定 identity。

---

### 5.2 Missing Album Artist

Album Artist 缺失时：

```text
albumArtists = []
```

空集合本身进入 AlbumKey。

绝不 fallback：

```text
Album Artist ← Artist
```

也不创建一个真实的：

```text
ArtistEntity("Unknown Album Artist")
```

Unknown 只是 UI presentation。

---

### 5.3 Missing Date

Date 缺失时：

```text
date = NULL
```

NULL 作为合法 identity component。

因此：

```text
Album / Artist / NULL
```

与：

```text
Album / Artist / 2025-01-01
```

是两个 AlbumEntity。

如果用户后来真的修改文件，把 Date 从 NULL 补成日期，那么这已经是 Album identity 变化。

---

### 5.4 Missing Album title

这是特殊情况。

当：

```text
metadata.album == null
```

时，不创建：

```text
AlbumEntity("Unknown Album")
```

而是：

```text
Track.albumId = null
```

UI 再将 NULL 显示为“未知 Album”。

否则所有没有 Album tag 的文件都会聚合到同一个虚假的 Unknown Album。

这同时意味着缺少 Album 的 track 无法产生 Strong TrackKey，后面不会进行跨物理文件 semantic duplicate merge。

---

### 5.5 Deluxe / Remaster / Edition

v1 不增加任何 Edition 推理。

如果 metadata 是：

```text
Random Access Memories
Random Access Memories (10th Anniversary Edition)
```

Album title 本身不同，因此不同 Album。

如果 title 相同但 Date 不同，也不同。

如果：

```text
Album
Album Artist
Date
```

三者全部完全相同，那么 v1 就认为它们属于同一个 release。

不能用以下信息偷偷区分 release：

```text
目录名
封面
bitrate
codec
文件格式
track count
disc total
文件大小
采样率
```

以后如果真实曲库证明需要更细区分，再明确增加 Catalog Number / Edition / Release ID，而不是当前阶段预留猜测逻辑。

---

## 6. Artist 数据模型

Artist 与 Album Artist 共用一个 `ArtistEntity`，但关系完全独立。

结构：

```text
ArtistEntity
------------
artistId
artistKeyVersion
artistKey
displayName
```

`ArtistKeyV1` 可以直接来自 normalized artist name。

例如：

```text
ArtistKeyV1("Charli xcx")
```

Artist 本身不因为它出现在 Track Artist 或 Album Artist 中而创建两个 entity。

但关系分别为：

```text
TrackArtistCrossRef
AlbumArtistCrossRef
```

所以：

```text
Track → Artist
```

与：

```text
Album → AlbumArtist
```

永远不会发生 fallback。

这比创建 `ArtistEntity` 和 `AlbumArtistEntity` 两张几乎完全相同的表更干净，同时不会违背 Auralis 的语义规则。

---

## 7. Genre 数据模型

Genre 单独成为实体：

```text
GenreEntity
-----------
genreId
genreKeyVersion
genreKey
displayName
```

Track 与 Genre 使用：

```text
TrackGenreCrossRef
```

关系是 many-to-many。

Genre：

```text
不参与 TrackKey
不参与 AlbumKey
不决定 duplicate
```

因此修改 Genre 不会把歌曲变成新的 Track。

Artist → Genre、Album → Genre 在 v1 不另存 relation；需要时从 Track graph 推导。

---

## 8. Track identity

Track 是用户看到的长期 logical library entry。

Track 自身使用随机 UUID：

```text
TrackId
```

TrackKey 只用于：

```text
新 source 与已有 logical Track 的 reconciliation
duplicate source grouping
丢失后重新关联
```

---

### 8.1 TrackKeyV1 精确定义

建议正式冻结为：

```text
TrackIdentityV1 {
    albumIdentity
    title
    trackArtists
    discNumber
    trackNumber
}
```

即：

```text
TrackKeyV1 =
SHA-256(
    versionMarker,
    AlbumIdentityV1,
    title,
    sortedDistinct(trackArtists),
    discNumber,
    trackNumber
)
```

AlbumIdentity 必须包含：

```text
Album
Album Artists
Date
```

而不是只存 AlbumKey hash；算法序列化最好直接嵌入 canonical Album identity material，这样 TrackKey spec 本身完整可描述。

---

## 9. TrackKey 中明确不包含的字段

下面这些字段不得参与 TrackKey：

| 字段 | 原因 |
|---|---|
| Genre | 用户编辑 Genre 不应成为新曲目 |
| bitrate | 重编码会改变 |
| sample rate | 重编码会改变 |
| MIME / codec | FLAC → ALAC 需要保持关联 |
| duration | encoder/container 可能有轻微变化 |
| file size | 必然随编码改变 |
| path | move / rename 必须保持 identity |
| URI | physical locator |
| documentId | physical identity，不是 logical identity |
| artwork | 封面变化不能改变 Track |
| lyrics | 歌词变化不能改变 Track |
| trackTotal | 编号总数不是曲目自身 identity |
| discTotal | 同上 |
| file name | metadata identity 不依赖文件名 |

Composer 当前不进入 Auralis Track identity。

---

## 10. TrackKey strength

TrackKey 要分：

```text
STRONG
WEAK
```

因为“能够算 hash”并不代表“具有足够信息进行自动 merge”。

### Strong 条件

必须首先：

```text
Album title != null
```

然后三项证据中至少存在两项：

```text
Title
Track Artist
Track Number
```

即：

```text
album != null
AND
countPresent(
    title != null,
    artists.isNotEmpty(),
    trackNumber != null
) >= 2
```

Disc Number 不单独贡献 strong score，只作为进一步 differentiation。

例子：

| Metadata | Strength |
|---|---|
| Album + Title + Artist | STRONG |
| Album + Title + Track Number | STRONG |
| Album + Artist + Track Number | STRONG |
| Album + Title | WEAK |
| Album + Artist | WEAK |
| Album + Track Number | WEAK |
| Title + Artist，但 Album 缺失 | WEAK |
| 全部主要字段缺失 | WEAK |

Weak key 可以计算和保存，但不得跨 physical source 自动 merge。

---

## 11. TrackKey 序列化协议

不要通过：

```text
"$album|$artist|$date|..."
```

直接拼字符串。

应该设计固定 deterministic encoder，例如：

```text
AURALIS_TRACK_V1
FIELD album.title LENGTH 5 VALUE "Album"
FIELD album.artists COUNT 2
VALUE LENGTH 1 "A"
VALUE LENGTH 1 "B"
FIELD album.date VALUE "2025-01-01"
FIELD title VALUE "Track"
...
```

工程实现可以是 length-prefixed bytes，也可以是统一的 digest update helper。

核心要求只有四个：

```text
NULL 与 "" 不相同
["A", "BC"] 与 ["AB", "C"] 不会碰撞
字段边界明确
算法完全 deterministic
```

最终 SHA-256 保存为 lowercase hex 即可。

AlbumKey 使用相同 encoder infrastructure。

---

## 58. Identity 单元测试矩阵

Identity 层至少覆盖下面这些固定场景：

| 输入变化 | TrackKey |
|---|---|
| path 改变 | 相同 |
| URI 改变 | 相同 |
| codec 改变 | 相同 |
| bitrate 改变 | 相同 |
| Genre 改变 | 相同 |
| artwork 改变 | 相同 |
| Artist 顺序 A;B → B;A | 相同 |
| Album Artist 顺序 A;B → B;A | 相同 AlbumKey |
| NFC 等价 Unicode | 相同 |
| Title 改变 | 不同 |
| Track Artist 改变 | 不同 |
| Track Number 改变 | 不同 |
| Disc Number 改变 | 不同 |
| Album title 改变 | 不同 |
| Album Artist 改变 | 不同 |
| Date 改变 | 不同 |
| Album Artist NULL → Artist fallback | 明确禁止 |
| Date NULL → date | AlbumKey 不同 |

还要单测 hash serialization 的边界碰撞：

```text
["AB", "C"]
!=
["A", "BC"]
```

以及：

```text
NULL != ""
```
