PHASE 3B — Library Persistence & Reconciliation

目标
建立 Auralis 第一版可持久化曲库：
- SAF 扫描结果进入 Room
- 区分 logical Track 与 physical TrackSource
- 支持重复文件归并与 active source 选择
- 支持路径变化、documentId 变化后的重连
- 支持已删除 / 暂不可访问文件的正确状态
- 避免每次扫描对全部文件重新做 TagLib extraction
- 保持 Phase 3A 已确认的 identity / metadata 规则不变

==================================================
1. 模块边界
==================================================

保持现有流程：
SAF Scanner
    ↓
CandidateAudio
    ↓
TagLib raw extraction
    ↓
AuralisMetadata

新增：
AuralisMetadata + physical source info
    ↓
ObservedTrackSource
    ↓
Library Reconciliation
    ↓
Room persistent library

不要把 Musikr 整体接入项目。
只参考其：
- Explore / Extract / Evaluate 分层思想
- cache Hit / Stale / Miss
- 并行 extraction
- batch DB write
- sealed pipeline states

不要复用其：
- URI 作为 library identity
- deleteExcludingUris 删除语义
- Artist / AlbumArtist fallback
- Album UID 规则
- logical graph 全量 rebuild 作为 authoritative state

==================================================
2. Room Schema v1
==================================================

实现以下核心表：

LibraryRootEntity
- rootId
- treeUri
- addedOrder
- displayName / displayPath
- availability state
- timestamps

TrackEntity
- trackId UUID PK
- trackKey
- keyStrength
- title
- albumId nullable
- date
- discNumber
- discTotal
- trackNumber
- trackTotal
- createdAt
- updatedAt

TrackSourceEntity
- sourceId UUID PK
- trackId nullable FK
- rootId FK
- provider
- documentId
- uri
- relativePath
- fileName
- format
- mimeType
- size
- modifiedMs
- durationMs
- bitrateKbps
- sampleRateHz
- availabilityState
- parseState
- playbackState
- failureStage
- failureReason
- lastSeenAt

约束 / index：
- index(provider, documentId)
- index(trackId)
- index(rootId)
- index(rootId, relativePath)

AlbumEntity
- albumId UUID PK
- albumKey UNIQUE
- title
- date

ArtistEntity
- artistId UUID PK
- nameKey UNIQUE
- name

GenreEntity
- genreId UUID PK
- nameKey UNIQUE
- name

TrackArtistCrossRef
- trackId
- artistId
- position

AlbumArtistCrossRef
- albumId
- artistId
- position

TrackGenreCrossRef
- trackId
- genreId
- position

TrackActiveSourceEntity
- trackId PK
- sourceId UNIQUE

SourceMetadataEntity
用于保存 physical source 最近一次成功解析出的 normalized metadata，
承担 extraction cache 职责。
至少保存：
- sourceId
- metadata fingerprint / modifiedMs
- normalized title
- album
- artists
- albumArtists
- genres
- date
- track/disc numbers
以及生成 AlbumKey / TrackKey 所需字段。

不创建 Unknown Artist / Unknown Album / Various Artists 伪实体。

==================================================
3. Identity Builder
==================================================

实现独立纯 Kotlin builder：

IdentityTextNormalizer
- trim
- Unicode NFC
- 不 lower-case
- 不做 fuzzy normalization

AlbumKeyV1
输入：
- album title
- Album Artist normalized distinct set
- date

TrackKeyV1
输入：
- album identity
- title
- Track Artist normalized distinct set
- disc number
- track number

hash：
- SHA-256
- version prefix
- 明确 NULL marker
- 长度前缀或其它无歧义序列化

同时实现：
TrackKeyStrength
- STRONG
- WEAK

采用 Phase 3A 已确认规则判断 strong / weak。

TrackId / AlbumId / ArtistId / GenreId：
- 使用随机 UUID
- 不使用 hash 作为 DB primary key

==================================================
4. ObservedTrackSource
==================================================

建立扫描层到 library 层之间的内部模型：

ObservedTrackSource
包含：
- SafDocumentKey
- root identity / root added order
- uri
- relativePath
- file name
- format / mime
- size
- modifiedMs
- extraction result
- AuralisMetadata
- audio properties
- AlbumKey
- TrackKey
- TrackKeyStrength

同时明确 extraction outcome：
- SUCCESS
- FAILED
- UNSUPPORTED

不要用 nullable metadata + boolean 组合表达 pipeline 状态。

==================================================
5. Metadata Extraction Cache
==================================================

参考 Musikr 的 Hit / Stale / Miss。

扫描发现 source 后：

1. 根据 physical source continuity 查现有 TrackSource
2. 检查缓存 metadata 是否仍可复用
3. 若 source 未变化：
   → HIT
   → 不重新打开文件
   → 从 SourceMetadataEntity hydrate ObservedTrackSource

4. 若 source 改变：
   → STALE
   → 重新 TagLib extraction

5. 新 source：
   → MISS
   → 进行 extraction

第一版 source change signal 至少使用：
- modifiedMs
- size

不要只依赖 URI。

不要建立 Musikr 风格独立 URI cache database；
直接让 SourceMetadataEntity 承担 persistent extraction cache。

==================================================
6. Extraction Pipeline
==================================================

扫描与 extraction 分离。

推荐流程：

SAF discovery
    ↓
Source classification
    ├── HIT → hydrate
    └── STALE / MISS → extract
                         ↓
                    normalize metadata
                         ↓
                    create observation

允许并行 TagLib extraction。

参考 Musikr PARALLELISM 模式，
但不要硬编码照搬具体实现。

DB persistence 使用 batch，
不要每解析一首歌就提交完整 transaction。

第一版可使用约 100–500 条 observation 为一个 persistence batch，
实际常量由实现选择。

==================================================
7. Reconciliation
==================================================

所有 authoritative library 状态变化集中进入 reconciliation 层。

对每个 ObservedTrackSource：

A. 优先匹配 physical source
使用：
provider + documentId

若匹配：
- 更新 uri / relativePath / root / file stats
- 恢复 AVAILABLE
- 更新 lastSeenAt

若其已有 Track：
- AlbumKey 未变化
  → 保持 TrackId
  → 更新 TrackKey / metadata / relations
- AlbumKey 变化
  → 从旧 Track 脱离
  → 按新 identity 重新解析 logical Track

B. 未匹配现有 source
若 Strong TrackKey 命中现有 Track：
- attach 新 TrackSource 到该 TrackId

否则：
- 创建新 TrackId
- attach source

Weak TrackKey：
- 不允许跨 physical source 自动合并

==================================================
8. Album / Artist / Genre Graph
==================================================

Album：
- Album title 缺失时，不创建共享 AlbumEntity
- albumId = null
- UI 后续显示 Unknown Album placeholder

Album title 存在：
- 按 AlbumKey 查找或创建 AlbumEntity

Artist：
- Track Artist 与 Album Artist 共用 ArtistEntity
- 但关系表完全分离
- 严禁 fallback

Genre：
- 独立 GenreEntity
- TrackGenreCrossRef
- Genre 不参与 Track identity

relation 更新时：
- 删除该 Track / Album 旧 relation
- 按当前 active source metadata 重建 relation
- position 保存原始 credit 顺序

==================================================
9. Duplicate Source Resolution
==================================================

同一个 logical Track 可以有多个 TrackSource。

eligible source：
- availability = AVAILABLE
- parseState = SUCCESS
- playbackState != UNPLAYABLE

playbackState = UNKNOWN 仍允许被选为 active source。

active source 排序：

1. LibraryRoot.addedOrder 升序
2. relativePath 稳定字典序
3. sourceId 作为最终 deterministic tie-break

禁止：
- bitrate 优先
- lossless 优先
- codec 优先
- size 优先

active source 写入 TrackActiveSourceEntity。

若当前 active source 变成 known UNPLAYABLE：
- 立即重新选择下一 eligible source

==================================================
10. Logical Metadata Projection
==================================================

TrackEntity + relations 表示当前 logical library metadata。

来源：
- 当前 active source 的 SourceMetadataEntity

重复文件之间若 Genre / title 等非 identity metadata 有差异：
- 不做 union
- active source wins

active source 变化时：
- TrackId 不变
- Track logical metadata 与 relations 更新到新 active source

若无 active source：
- 保留最后一次成功 logical metadata
- 用于不可访问 / tombstone 状态显示

==================================================
11. Missing / Unreachable Reconciliation
==================================================

扫描完成后不能简单执行：
“本轮没看到 → 删除”。

只允许在 successful scanned scope 内确认 MISSING。

规则：

成功遍历的目录：
- 之前存在但本轮未出现的 source
  → availability = MISSING

DirectoryUnavailable(root, path)：
- 该 subtree 下已有 source
  → 不得标记 MISSING
  → 标记 / 保持 UNREACHABLE

root 整体不可访问：
- 该 root 所有 source 不删除
- 保留 Track 与 metadata

Track：
- 至少有 eligible source
  → active
- 有 source 但全部 unavailable / failed
  → 保留 Track，进入 unavailable 状态
- 所有 source 均 confirmed MISSING
  → 从 active library 隐藏
  → Track row 保留为 tombstone

==================================================
12. Tombstone / Reconnect
==================================================

不要物理删除 TrackEntity，仅因为其 source 消失。

之后若：
- 新 source 出现
- physical identity 无法连续
- Strong TrackKey 命中 tombstoned Track

则：
- attach 到旧 TrackId
- Track 恢复 active

这样可以满足：
文件删除后重新出现，
仍恢复为原 logical library entry。

==================================================
13. Transaction Boundary
==================================================

不要在 SAF traversal 期间保持长 Room transaction。

推荐：

Discovery / extraction
    ↓
形成 observation batch
    ↓
Room transaction:
    - upsert source
    - update SourceMetadata
    - reconcile Track
    - reconcile Album / Artist / Genre relations
    - recompute affected active source
    ↓
commit

扫描最终完成后再执行：
- missing / unreachable reconciliation
- affected Track active-source recompute

保证：
扫描中途 app crash 时，
上一版 DB 仍然可用，
不会因为半次 scan 把曲库清空。

==================================================
14. DAO / Repository 边界
==================================================

至少拆：

LibraryRootDao
TrackDao
TrackSourceDao
AlbumDao
ArtistDao
GenreDao
SourceMetadataDao
RelationDao

业务层：
LibraryRepository
LibraryReconciler
ActiveSourceSelector
IdentityBuilder
SourceMetadataCache

不要把 reconciliation 逻辑塞进 DAO。

DAO 负责：
- query
- insert/update/delete relation rows
- transaction primitives

Reconciler 负责：
- identity decision
- Track continuity
- duplicate decision
- tombstone logic

==================================================
15. 第一阶段不做的内容
==================================================

Phase 3B 不做：

- 播放器 UI 重构
- MediaLibraryService
- 播放队列
- Playlist
- 搜索 / FTS
- artwork 完整缓存体系
- lyrics
- metadata 写回
- MusicBrainz identity
- audio fingerprint
- fuzzy matching
- alias merging
- Various Artists
- metadata repair
- 每次 scan 预播放验证文件
- bitrate / lossless quality ranking
- 自动目录监听刷新

==================================================
16. Tests
==================================================

Identity tests：
- Album Artist 顺序变化不会改变 AlbumKey
- Artist 顺序变化不会改变 TrackKey
- Date 改变 → AlbumKey 改变
- Album 改变 → TrackKey 改变
- Genre 改变 → TrackKey 不变
- codec / bitrate 改变 → TrackKey 不变
- null 与 empty encoding 不冲突

Reconciliation tests：
- 同 documentId 路径变化 → TrackId 不变
- documentId 变化 + Strong TrackKey 一致 → TrackId 不变
- FLAC → ALAC / AAC 等技术变化但 identity 一致 → TrackId 不变
- Weak TrackKey 不跨 source 自动合并
- AlbumKey 改变 → 新 logical Track
- confirmed deletion → source MISSING
- 全 source MISSING → Track tombstone / hidden
- unavailable root → 不删除 Track
- DirectoryUnavailable subtree → 不标 MISSING
- source reappear → 恢复旧 TrackId

Duplicate tests：
- earlier-added root 优先
- 同 root 时 relativePath 优先
- bitrate 不影响 active source
- known UNPLAYABLE active source 会切换下一 source

Metadata tests：
- Album Artist 不 fallback Artist
- Artist 不 fallback Album Artist
- missing Album 不创建共享 AlbumEntity
- multi genre 正确建 relation
- active source switch 后 logical metadata 跟随 source

Cache tests：
- unchanged source → cache HIT，不调用 TagLib
- modified source → STALE，重新 extraction
- new source → MISS
- extraction failure 被持久化到 TrackSource 状态

==================================================
17. Real-device Acceptance
==================================================

在 Find X9 上完成以下验收：

A. 初次扫描
- 当前 AAC / M4A / MP3 正常入库
- Artist / Album Artist / Genre 数量正确

B. 二次不改文件扫描
- library 结果不变
- 大部分 source 走 metadata cache
- 不重新对全部文件做 TagLib extraction

C. 文件重命名 / 路径移动
- TrackId 保持

D. 复制同一首歌到第二 root
- logical Track 只有一条
- 两条 TrackSource 都保留
- active source 符合 root addedOrder

E. 删除 active source
- 若 duplicate 仍存在，自动切换
- Track 不消失

F. 删除唯一 source
- Track 从 active library 隐藏
- tombstone 保留

G. 文件恢复
- Strong TrackKey 一致时恢复旧 TrackId

H. 临时撤销 / 破坏 root 可访问性
- 曲目不被错误删除
- source 状态变为 unavailable / unreachable

I. 修改 Album Date
- 重新扫描后变为不同 release

==================================================
18. Documentation
==================================================

完成施工后更新：

ARCHITECTURE.md
加入：
- Track / TrackSource layering
- Room authoritative library
- reconciliation lifecycle
- cache role
- unavailable vs missing

METADATA.md
只补充：
- AlbumKey / TrackKey 的最终冻结规则
- active source controls logical metadata

ROADMAP.md
标记：
Phase 3B complete

如实现过程中发现必须修改 Phase 3A 已确认 identity 规则：
禁止 Agent 自行决定；
停止该部分施工并汇报。