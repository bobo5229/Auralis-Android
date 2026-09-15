# Phase 3A 施工册 04 · Pure reconciliation decision layer（Step E）

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | Step E 实现 pure reconciliation decision layer |
| 提交边界 | reconciliation core + tests |
| 本册章节 | §26 §27 §28 §29 §30 §31 §32 §33 §34 §35 §36 §37 §38 §39 §59 §60 |
| 依赖 | 00 / 01 / 02 册 |
| 供下游 | 05 / 06 册 |
| 完成判据 | §28–§39 决策规则实现完成，§59 / §60 测试矩阵全部通过，且不依赖 Room |

<!-- PHASE3A:BODY -->

---

## 26. Duplicate candidate eligibility

Source 能成为 active candidate 必须满足：

```text
availabilityState == AVAILABLE
parseState == PARSED
playabilityState != UNPLAYABLE
trackId == targetTrackId
```

其中 `UNKNOWN` 暂时可以参与，因为不能为所有文件做 eager playback probe。

---

## 27. Duplicate source ranking

符合资格后，严格按以下 comparator：

```text
1. playability
   PLAYABLE before UNKNOWN

2. root priority
   smaller first

3. relativePath
   deterministic lexical ascending

4. sourceId
   deterministic final tie breaker
```

这里对“排除不可播放后优先更早 root”的现有产品规则做一个实现层细化：

```text
PLAYABLE > UNKNOWN > UNPLAYABLE(excluded)
```

不能出现：

```text
FLAC > MP3
higher bitrate > lower bitrate
lossless > lossy
larger file > smaller file
```

格式与音质完全不参与 source selection。

---

## 28. Physical continuity matching

每个新 observation 进入 reconciliation 后，第一步永远是 physical matching。

优先：

```text
(provider, documentId)
```

如果命中已有 TrackSource：

```text
这是已有 physical source
```

而不是新文件。

随后更新：

```text
uri
root
relativePath
fileName
size
modifiedMs
availability
metadata snapshot
```

这一匹配优先于 TrackKey。

因此一个文件只是 rename 或 move，而 provider 保持相同 documentId 时，TrackSourceId 与 TrackId 都保持。

---

## 29. Path matching 的角色

如果 `(provider, documentId)` 没有命中，可以允许一个非常保守的辅助 location match：

```text
same root
+
same relativePath
```

但这一规则不能直接证明 logical Track identity。

它的主要用途是处理某些 SAF provider documentId 行为不稳定、但路径没有变化的情况。

如果 path match source 但 metadata 与原 Track 的 Album identity 已改变，后续仍必须按 Album hard boundary 重新判断。

不能因为路径相同就无条件保留 TrackId。

---

## 30. Album hard boundary

这是 reconciliation 中最重要的产品约束之一：

```text
AlbumKey changed
→ cannot remain same Logical Track
```

即使：

```text
same SafDocumentKey
same TrackSourceId
```

如果成功解析后的 source 从：

```text
Album A / Artist X / Date 2025
```

变成：

```text
Album B / Artist X / Date 2025
```

则必须：

```text
detach source from old Track
attach/create different Track
```

旧 Track 如果因此失去所有 active source，则进入 inactive/tombstone 状态。

这是唯一一个强于 physical continuity 的 semantic boundary。

---

## 31. 同 source metadata 修改但 Album 不变

如果：

```text
same physical source
same AlbumKey
```

那么以下修改都保持原 TrackId：

```text
Title
Track Artist
Track Number
Disc Number
Genre
```

TrackKey 可以变化。

这时候做：

```text
Track.trackKey = newKey
logical metadata = new source metadata
relations = update
TrackId unchanged
```

这正是为什么 TrackId 与 TrackKey 必须分开。

---

## 32. 新 physical source 的 semantic matching

如果：

```text
document key miss
path auxiliary miss
```

说明这是新 physical source。

此时：

```text
if TrackKeyStrength == STRONG:
    search active + tombstone Tracks by TrackKey
else:
    do not semantic auto-match
```

Strong TrackKey 唯一命中：

```text
attach source to existing Track
```

没有命中：

```text
create new TrackId
```

Weak：

```text
always create new Track
```

除非前面已经由 physical continuity 命中。

---

## 33. Strong TrackKey 多重命中

这必须预先定义，否则 Agent 会自行猜。

如果一个新 source 的 Strong TrackKey 同时命中多个 existing Track：

```text
do NOT arbitrarily choose one
```

因为这说明此前已经存在两个 logical entry 拥有同一 semantic identity，可能源于：

```text
历史 weak→strong metadata 更新
早期数据库版本
人为保留的独立 entry
identity collision
```

v1 策略：

```text
treat semantic match as ambiguous
create a new Track
record reconciliation diagnostic
```

不要自动 merge 多个已有 Track。

自动 merge 是不可逆行为，false positive 成本远高于多一个 duplicate。

---

## 34. Duplicate Track 不主动做全库 collapse

Phase 3B reconciliation 不应该每次启动就执行：

```text
GROUP BY trackKey
然后自动把所有已有 Track 合并
```

TrackKey 主要用于“新 source 找已有 Track”。

已经存在的两个 Track 即使后来 key 变成相同，也不在 v1 自动 collapse。

这样可以防止 metadata edit 意外将两个历史 Track 合并，导致歌单、播放历史等未来引用被破坏。

如果以后要做 Track merge，应设计显式 migration/merge algorithm。

---

## 35. 重编码

典型情况：

旧：

```text
song.flac
```

删除，出现：

```text
song.m4a
```

document key 大概率变化。

如果新文件核心 metadata 相同：

```text
Strong TrackKey equal
```

则：

```text
new TrackSourceId
→ existing TrackId
```

旧 FLAC TrackSource：

```text
MISSING
```

新 M4A：

```text
AVAILABLE
```

active source 切换到新文件。

因此格式完全不进入 TrackKey。

---

## 36. rename / move

优先路径：

```text
SafDocumentKey unchanged
→ same sourceId
→ same TrackId
→ update relativePath/uri
```

如果 provider 因 move 产生新 documentId：

```text
old source eventually MISSING
new observation → Strong TrackKey match
→ same TrackId
```

此时 Auralis 可以拥有：

```text
old missing source
new available source
```

都连接一个 Track。

这是预期行为。

---

## 37. 真正删除文件

只有一次成功、完整、可观测的 scan 能确认删除。

例如 root 成功扫描：

```text
previous source S
not observed in completed scan
its parent scope had no error
```

才允许：

```text
S.availability = MISSING
```

之后重新运行 active source selector。

如果 Track 无其它 eligible source：

```text
remove TrackActiveSource
Track becomes inactive
```

但：

```text
do not delete TrackEntity
do not delete metadata
do not delete TrackKey
```

因为以后重新出现时要恢复 TrackId。

---

## 38. Tombstone

这里可以不单独增加：

```text
trackState = TOMBSTONE
```

因为 Track 是否 active 可以由：

```text
TrackActiveSource 是否存在
```

直接推导。

所以：

```text
active Track = has TrackActiveSource
inactive/tombstone Track = no TrackActiveSource
```

避免保存两个可能冲突的状态。

未来 UI 默认只查询 active Track。

---

## 39. 重新出现

如果 missing source 后来重新出现：

第一优先：

```text
same provider + documentId
```

则直接恢复 source。

否则：

```text
Strong TrackKey
```

可以命中 inactive/tombstone Track。

匹配成功：

```text
new/returned source → old TrackId
```

然后重新选择 active source。

因此歌单以后只要引用 `TrackId`，用户删除文件再重新放回来，就有机会自动恢复。

---

## 59. Reconciliation 单元测试矩阵

纯逻辑 reconciliation 至少覆盖：

```text
same document key + same Album
→ preserve sourceId + TrackId

same document key + title changed + same Album
→ preserve TrackId

same document key + Artist changed + same Album
→ preserve TrackId

same document key + AlbumKey changed
→ detach old Track + new/different Track

new document key + Strong TrackKey match
→ attach existing Track

new document key + Weak TrackKey match
→ create new Track

new document key + Strong TrackKey no match
→ create new Track

Strong TrackKey matches >1 existing Track
→ ambiguous; do not merge

FLAC missing + ALAC appears + same Strong TrackKey
→ same TrackId

root unavailable
→ source UNREACHABLE, not MISSING

successful root scan no longer sees source
→ MISSING

MISSING source reappears same document key
→ restore source

MISSING source reappears different document key but same Strong key
→ same TrackId
```

---

## 60. Duplicate selection 测试

固定测试：

```text
PLAYABLE vs UNKNOWN
→ PLAYABLE

UNKNOWN root 0 vs UNKNOWN root 1
→ root 0

same root:
a/song.m4a
b/song.flac
→ a/song.m4a

root earlier has MP3
root later has FLAC
→ MP3

root earlier 128 kbps
root later 320 kbps
→ earlier root

candidate UNPLAYABLE
→ excluded
```

还要验证 comparator 无论 observation 输入顺序如何都产生同一个 winner。
