# Phase 3A 施工册 06 · 扫描管线接入与 logical graph 投影（Step G / H）

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | Step G 接现有 SAF + Phase 2C metadata pipeline；Step H 实现 active source projection → logical graph |
| 提交边界 | scanner/metadata integration |
| 本册章节 | §43 §44 |
| 依赖 | 00 / 02 / 03 / 04 / 05 册 |
| 供下游 | 07 / 08 册 |
| 完成判据 | §44 pipeline 全链路打通；查询路径只读到由 active source 投影出的 logical graph |

<!-- PHASE3A:BODY -->

---

## 43. Source metadata 与 logical metadata 的职责边界

这层必须写进代码注释和架构文档：

```text
AuralisMetadata
    ↓
SourceMetadata
    ↓
identity calculation
    ↓
TrackSource
    ↓ active-source selection
Logical Track graph
```

不要：

```text
AuralisMetadata → TrackEntity
```

直接跳过 source metadata 层。

否则 duplicate、fallback source 和 source-specific differences 后续都会难以处理。

---

## 44. Scan pipeline 最终结构

正式 pipeline 建议形成：

```text
SAF discovery
    ↓
Candidate audio
    ↓
Raw metadata extraction
    ↓
AuralisMetadata interpretation
    ↓
ObservedTrackSource
    ↓
Identity derivation
    ↓
Reconciliation
    ↓
Room transaction
    ↓
Active library graph
```

这和 Musikr 的 Explore → Extract → Evaluate 思路相似，但 Evaluate 的具体 identity 产品语义完全属于 Auralis。
