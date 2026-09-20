# Auralis Mobile 第一阶段开发路线

第一阶段以技术可行性和本地曲库基础能力为先。正式产品 UI 与完整播放器功能必须等 Technical Spike 通过后再开始。

## 开发顺序

1. **Technical Spike（Complete）**
   本地音频访问、元数据读取、播放链路、标准 Android / AndroidX API 以及目标设备上的关键技术假设均已验证通过。
2. **SAF 多目录选择和持久授权（Complete）**
   已使用 `ACTION_OPEN_DOCUMENT_TREE` 选择多个曲库目录并持久化目录访问权限，Find X9 真机验收通过。
3. **递归扫描（Complete）**
   已实现授权目录递归扫描和单文件错误隔离，Find X9 真机验收通过。
4. **MP3 / AAC / ALAC / FLAC 播放兼容验证（Complete）**
   四种目标格式已在 Find X9 上通过 AndroidX Media3 / ExoPlayer 播放链路真机验收。
5. **元数据解析验证（Complete）**
   支持格式的元数据读取、字段映射、多值分隔、日期、专辑身份和缺失字段处理均已通过自动测试与真机验收。
6. **Room 建库与曲库持久化对账（Phase 3A / 3B Complete）**
   建立 Auralis 自己的本地数据库，区分逻辑曲目（Track）与物理源（TrackSource），实现元数据抽取缓存（Hit/Stale/Miss）、分批事务写入、多源选优与对账恢复。
7. **Albums / Tracks 临时调试界面（Complete）**
   提供仅用于验证建库和解析结果的临时 Albums / Tracks 界面，不作为正式产品 UI。已在 Find X9 上完成真机验收。
8. **Media3 播放（Complete）**
   已接入 Media3 / ExoPlayer，并在 Find X9 上通过从曲库条目加载和播放本地音频的真机验收。
9. **后台播放与通知栏控制（Complete）**
   已接入 `MediaLibraryService` / `MediaSession`，并在 Find X9 上通过后台播放和通知栏基本媒体控制的真机验收。
10. **扫描诊断日志（Complete）**
    已汇总扫描、解析、访问和播放错误，并支持导出文本或 JSON 日志；Find X9 真机验收通过。
11. **Technical Spike 通过后，开始正式产品 UI 和完整播放器功能**
    前述技术验证已经通过；正式 UI 的信息架构、复用边界和分阶段实施计划见 `docs/PRODUCT_UI_PLAN.md`。

## 阶段门槛

Technical Spike 已通过，正式产品 UI 与完整播放器功能可以开始开发。临时调试界面仅保留为开发验收工具，不作为产品界面继续扩展。
