# Phase 3A 施工册 07 · Root persistence 迁移（Step I）

> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。
> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义（`\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。
> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，两者冲突时以原文为准。

## 施工导读

| 项 | 内容 |
| --- | --- |
| 施工步骤 | Step I 迁移 root persistence |
| 提交边界 | root persistence migration |
| 本册章节 | §55 |
| 依赖 | 00 / 03 册 |
| 供下游 | 08 册 |
| 完成判据 | §55 三条保证全部满足：root 顺序不丢、SAF 持久权限不重新申请、tree URI 原样迁移；Room 成为 root 的唯一 source of truth |

<!-- PHASE3A:BODY -->

---

## 55. Root persistence 迁移原则

当前 `SafRootStore` 是明确标注的 temporary technical spike SharedPreferences persistence，而且源码已经指出正式阶段应迁移 Room。

真正施工时必须保证：

```text
原有 root 顺序不丢
persisted SAF permissions 不重新申请
tree URI 原样迁移
```

也就是说正式 `LibraryRootEntity` 初次建立时，可以读取旧 `SafRootStore` 顺序一次并写入 Room。

迁移完成后 SharedPreferences 不再作为 source of truth。

是否立刻删除旧 prefs 可以保守处理，初版不必删。
