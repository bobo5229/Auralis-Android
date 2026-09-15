# Phase 3A 施工册 08 · Debug inspection UI 与三层验收（Step J / K）

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | Step J 加入 debug inspection UI；Step K JVM / Room / Find X9 三层验收 |
| 提交边界 | debug/acceptance tooling |
| 本册章节 | §62 §63 §64 |
| 依赖 | 00–07 册 |
| 供下游 | —— 册 |
| 完成判据 | §62 的 debug 字段可显示、真机场景逐条通过；§63 / §64 两个相反验收均成立 |

<!-- PHASE3A:BODY -->

---

## 62. 真机验收场景

Phase 3 后真正需要 Find X9 验证的场景，不是 identity hash 本身，而是 SAF provider 的实际行为。

建议准备一首可复制的 AAC/M4A 测试文件，执行：

```text
首次扫描
→ 记录 TrackId / SourceId / documentId

rename
→ scan
→ TrackId 是否不变

同目录 move 或跨目录 move
→ scan
→ 观察 documentId 是否稳定
→ 无论稳定与否 TrackId 应尽量保持

复制成 duplicate
→ 曲库只有一个 active Track
→ DB 中有两个 source

删除 winner
→ duplicate 自动接管

重新放回
→ source / Track 正确恢复

AAC → MP3 或 AAC → M4A 重编码并保留核心 metadata
→ TrackId 保持

修改 Genre
→ TrackId 保持

修改 Title
→ 同 physical document 下 TrackId 保持

修改 Album
→ 必须成为不同 Track
```

这里需要 debug UI 能显示：

```text
TrackId
TrackKey
TrackKey strength
SourceId
SafDocumentKey
root priority
active source
source states
```

这类 debug 输出只用于 Phase 3 验收，不属于最终产品 UI。

---

## 63. 关于“Title 修改但 TrackKey 变化”的重要验收

这项很容易被错误实现，所以单独冻结。

原来：

```text
TrackId = T1
TrackKey = K1
Title = Old
```

同一个 physical source 把 Title 改为 New：

```text
same SafDocumentKey
same AlbumKey
TrackKey = K2
```

结果必须：

```text
TrackId = T1
```

不是创建 T2。

新的 `K2` 写回 T1。

这证明 TrackKey 只是 reconciliation key，而不是 Track identity 本身。

---

## 64. 关于 Album 修改的相反验收

原来：

```text
source S
Track T1
Album A
```

修改为：

```text
same source S
Album B
```

结果：

```text
source S 可以仍是同一个 TrackSourceId
但不能继续属于 T1
```

应变成：

```text
S.trackId = T2
```

T1 如果没有其它 active source：

```text
inactive/tombstone
```

这是 Album hard boundary 的直接实现。
