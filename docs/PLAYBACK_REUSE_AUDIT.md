# 播放层复用审计

## 结论

Auralis 播放层不从零实现，也不整体移植其他播放器。基础服务与会话直接采用
AndroidX Media3 官方模式；Auxio 用于补充本地音乐播放器的工程经验。Auralis 只实现
自身数据库身份到 Media3 `MediaItem` 的必要适配。

## 固定上游

| 上游 | 固定版本 | 用途 |
| --- | --- | --- |
| AndroidX Media3 | `1.11.1` | `ExoPlayer`、`MediaLibraryService`、`MediaLibrarySession`、`MediaController` |
| AndroidX Media demo | tag `1.11.1`，commit `8c6678b657ede1e7883fc164ef73ed483c7796c3` | Service / Session 生命周期与异步 MediaItem 解析模式 |
| Auxio | `c05cebc52fe2393bf3a988068e4060fd6dba408f` | 本地播放、SAF URI、音频焦点和错误处理经验 |

## 复用边界

直接采用官方 Media3 能力：

- `MediaLibraryService` 承载播放器生命周期；
- `MediaLibrarySession` 暴露系统媒体会话；
- `ExoPlayer` 负责解码、音频焦点和播放状态；
- `MediaController` 作为 UI 唯一控制入口；
- Media3 自动生成媒体通知，不自建通知状态机。

Auralis 自行保留的薄适配：

- `TrackId → TrackActiveSource → TrackSource.uri`；
- Room 元数据到 `MediaMetadata` 的投影；
- 播放成功/失败回写 `PlayabilityState` 并触发 active source 重选；
- 不允许 Media3 或上游播放器改变 Auralis 的 Track / Album identity 规则。

## 不直接移植 Auxio 播放层的原因

固定提交中的 Auxio 仍以 `MediaBrowserServiceCompat` 和自有 playback fragment 为核心，
同时依赖定制 Media3、Hilt、完整 Musikr 图谱和 Unix 构建脚本。整体移植会引入与 Auralis
产品语义无关的队列、状态恢复、Android Auto、FFmpeg 和依赖注入体系。Auralis v1 也明确
不恢复上次播放状态，因此只参考其成熟经验，不复制这些产品行为。

## 许可

- AndroidX Media3 示例为 Apache License 2.0。
- Auxio / Musikr 为 GPL-3.0-or-later；既有派生关系与固定提交继续记录在
  `THIRD_PARTY_NOTICES.md`。
- Auralis 整体按 GPL-3.0-or-later 分发，并在仓库根目录保留完整许可证文本。
