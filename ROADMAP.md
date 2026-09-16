# Auralis Mobile 第一阶段开发路线

第一阶段以技术可行性和本地曲库基础能力为先。正式产品 UI 与完整播放器功能必须等 Technical Spike 通过后再开始。

## 开发顺序

1. **Technical Spike**
   验证本地音频访问、元数据读取、播放链路、标准 Android / AndroidX API 以及目标设备上的关键技术假设。
2. **SAF 多目录选择和持久授权**
   使用 `ACTION_OPEN_DOCUMENT_TREE` 选择多个曲库目录，并持久化目录访问权限。
3. **递归扫描**
   递归遍历用户授权的曲库目录，发现音频文件并处理扫描过程中的单文件错误。
4. **MP3 / AAC / ALAC / FLAC 播放兼容验证**
   验证四种目标格式在 AndroidX Media3 / ExoPlayer 播放链路中的可用性和异常表现。
5. **元数据解析验证**
   验证支持格式的元数据读取、字段映射、多值分隔、日期、专辑身份和缺失字段处理。
6. **Room 建库与曲库持久化对账（Phase 3A / 3B Complete）**
   建立 Auralis 自己的本地数据库，区分逻辑曲目（Track）与物理源（TrackSource），实现元数据抽取缓存（Hit/Stale/Miss）、分批事务写入、多源选优与对账恢复。
7. **Albums / Tracks 临时调试界面**
   提供仅用于验证建库和解析结果的临时 Albums / Tracks 界面，不作为正式产品 UI。
8. **Media3 播放**
   接入 Media3 / ExoPlayer，验证从曲库条目加载并播放本地音频。
9. **后台播放与通知栏控制**
   接入 `MediaLibraryService` / `MediaSession`，验证后台播放和通知栏基本媒体控制。
10. **扫描诊断日志**
    完善扫描、解析、访问和播放错误的诊断信息，并支持导出文本或 JSON 日志。
11. **Technical Spike 通过后，开始正式产品 UI 和完整播放器功能**
    只有在前述技术验证通过、关键风险可控后，才进入正式产品 UI、播放页和完整播放器体验的开发。

## 阶段门槛

Technical Spike 是第一阶段的前置门槛。它未通过前，不开始正式产品 UI，不扩展完整播放器功能，也不以临时调试界面替代产品设计。
