\# Phase 3A：Auralis 曲库数据模型与 Identity 详细技术方案



本方案是 Phase 3A 的最终设计规格。目标是把后续数据库与 reconciliation 实现所需要的语义全部冻结下来，使 Agent 在下一阶段只按确定规则施工，不再自行决定 Track identity、Album identity、duplicate、删除恢复或数据库关系。本阶段本身仍不进入 Room 代码施工。



当前仓库已经具备这套设计需要的两个上游输入：`AuralisMetadata` 已提供 Title、Artist、Album Artist、Album、Genre、Date、Track/Disc、时长和音频属性；SAF 层已经提供 `(provider, documentId)` 形式的 `SafDocumentKey`。因此 Phase 3 不需要改变 Phase 2C 的 metadata 语义，也不需要重新设计扫描器。 当前 `SafRootStore` 又天然保存了 root 添加顺序，并明确把该顺序预留为未来 duplicate candidate priority，因此这条已有产品规则可以直接迁移进正式曲库层。



\---



\## 1. 总体模型



Auralis 曲库必须明确区分三个概念：



```text

Logical identity

&#x20;   Track

&#x20;     │

&#x20;     ├── Album

&#x20;     ├── Artists

&#x20;     └── Genres

&#x20;     │

&#x20;     1

&#x20;     │

&#x20;     N

Physical identity

&#x20;   TrackSource

&#x20;     │

&#x20;     ├── SAF document

&#x20;     ├── URI / relative path

&#x20;     ├── audio properties

&#x20;     └── source-specific metadata snapshot

```



最终原则为：



```text

TrackId ≠ TrackKey ≠ SafDocumentKey ≠ Path

```



四者含义严格不同。



`TrackId` 是 Auralis 内部长期稳定的 logical library entry ID；`TrackKey` 是用 metadata 计算出的 semantic reconciliation key；`SafDocumentKey` 是物理 SAF document 的 continuity key；`Path/URI` 只是当前 location。



不能把其中任何两者合并成一个概念。



\---



\# 2. ID 类型与版本策略



所有数据库实体自己的主键都使用 Auralis 生成的 UUID。建议在 Kotlin domain 层使用 value class，数据库中最终保存为 TEXT UUID。



概念上定义：



```kotlin

TrackId(UUID)

TrackSourceId(UUID)

AlbumId(UUID)

ArtistId(UUID)

GenreId(UUID)

LibraryRootId(UUID)

```



Identity key 使用独立字符串类型：



```kotlin

TrackKey(version = 1, hash = ...)

AlbumKey(version = 1, hash = ...)

ArtistKey(version = 1, value = ...)

GenreKey(version = 1, value = ...)

```



数据库主键永远不能直接使用 TrackKey 或 AlbumKey。



原因是 Identity 算法未来可能出现 `V2`。如果 `TrackKeyV1` 同时承担数据库 PK，那么 identity 规则升级会迫使整个曲库及歌单、播放历史等引用一起迁移。采用稳定 UUID 后，未来只需重新计算 key，不需要改变 `TrackId`。



建议数据库直接显式保存：



```text

trackKeyVersion = 1

trackKeyHash

albumKeyVersion = 1

albumKeyHash

```



而不是假定当前算法永远不会变化。



\---



\# 3. Identity canonicalization



所有参与 identity 的文本必须先经过统一 canonicalization。这套规则应该成为独立纯 Kotlin 模块，而不是散落在 DAO 或 scanner 中。



`IdentityTextNormalizerV1` 精确执行：



```text

1\. 如果原始值为 null → null

2\. trim 前后 Unicode whitespace

3\. Unicode normalize NFC

4\. trim 后如果为空字符串 → null

5\. 保留其它所有字符原样

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



\---



\# 4. Multi-value canonicalization



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

\["A", "B"]

\["B", "A"]

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



\---



\# 5. Album identity



\## 5.1 AlbumKeyV1



Album 只有在 `album title != null` 时才创建正式 `AlbumEntity`。



精确定义：



```text

AlbumIdentityV1 {

&#x20;   albumTitle

&#x20;   albumArtists

&#x20;   date

}

```



即：



```text

AlbumKeyV1 =

SHA-256(

&#x20;   versionMarker,

&#x20;   albumTitle,

&#x20;   sortedDistinct(albumArtists),

&#x20;   date

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



\---



\## 5.2 Missing Album Artist



Album Artist 缺失时：



```text

albumArtists = \[]

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



\---



\## 5.3 Missing Date



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



\---



\## 5.4 Missing Album title



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



\---



\## 5.5 Deluxe / Remaster / Edition



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



\---



\# 6. Artist 数据模型



Artist 与 Album Artist 共用一个 `ArtistEntity`，但关系完全独立。



结构：



```text

ArtistEntity

\------------

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



\---



\# 7. Genre 数据模型



Genre 单独成为实体：



```text

GenreEntity

\-----------

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



\---



\# 8. Track identity



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



\---



\## 8.1 TrackKeyV1 精确定义



建议正式冻结为：



```text

TrackIdentityV1 {

&#x20;   albumIdentity

&#x20;   title

&#x20;   trackArtists

&#x20;   discNumber

&#x20;   trackNumber

}

```



即：



```text

TrackKeyV1 =

SHA-256(

&#x20;   versionMarker,

&#x20;   AlbumIdentityV1,

&#x20;   title,

&#x20;   sortedDistinct(trackArtists),

&#x20;   discNumber,

&#x20;   trackNumber

)

```



AlbumIdentity 必须包含：



```text

Album

Album Artists

Date

```



而不是只存 AlbumKey hash；算法序列化最好直接嵌入 canonical Album identity material，这样 TrackKey spec 本身完整可描述。



\---



\# 9. TrackKey 中明确不包含的字段



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



\---



\# 10. TrackKey strength



TrackKey 要分：



```text

STRONG

WEAK

```



因为“能够算 hash”并不代表“具有足够信息进行自动 merge”。



\## Strong 条件



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

&#x20;   title != null,

&#x20;   artists.isNotEmpty(),

&#x20;   trackNumber != null

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



\---



\# 11. TrackKey 序列化协议



不要通过：



```text

"$album|$artist|$date|..."

```



直接拼字符串。



应该设计固定 deterministic encoder，例如：



```text

AURALIS\_TRACK\_V1

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

\["A", "BC"] 与 \["AB", "C"] 不会碰撞

字段边界明确

算法完全 deterministic

```



最终 SHA-256 保存为 lowercase hex 即可。



AlbumKey 使用相同 encoder infrastructure。



\---



\# 12. Physical source 模型



正式命名建议统一使用：



```text

TrackSource

```



不要叫 `PhysicalFile`，因为 Android SAF 的 source 不一定对应传统绝对路径文件。



`TrackSourceEntity` 表示“当前或曾经被 Auralis 发现过的一个物理音频 source”。



基本结构：



```text

TrackSourceEntity

\-----------------

sourceId

trackId



provider

documentId



rootId

uri

relativePath

fileName



format

mimeType

sizeBytes

modifiedMs



durationMs

bitrateKbps

sampleRateHz



availabilityState

parseState

playabilityState



lastSeenScanId

lastSeenAt



failureStage

failureCode

failureMessage

```



其中建立：



```text

UNIQUE(provider, documentId)

```



或者至少 unique index。



当前 Auralis 已经明确把 `(provider, documentId)` 作为“同一个物理 document 被多个 tree root 发现时仍相同”的稳定 key，这个设计直接进入正式 source 模型即可。



\---



\# 13. TrackSource metadata snapshot



这里必须比前一版草案进一步明确：\*\*每一个 TrackSource 必须保存自己最后一次成功解析得到的 metadata snapshot。\*\*



否则有两个 duplicate source：



```text

A/song.flac

B/song.m4a

```



当前 A 为 active，B 为备用。



如果之后 A 删除，需要把 B 提升为 active。此时如果数据库只保存 Track 的 metadata，而没有 B 自己的 metadata，就无法正确切换 Artist / Genre / Album 等信息，除非再次强制读取文件。



因此应增加：



```text

SourceMetadataEntity

\--------------------

sourceId PK/FK



title

albumTitle

date



trackNumber

trackTotal

discNumber

discTotal



artistsSerialized

albumArtistsSerialized

genresSerialized



durationMs

bitrateKbps

sampleRateHz

mimeType



trackKeyVersion

trackKeyHash

trackKeyStrength



albumKeyVersion

albumKeyHash nullable

```



这里 `artistsSerialized / albumArtistsSerialized / genresSerialized` 允许用 deterministic JSON array 或专用 Room TypeConverter 存成单列，因为这一层属于“source metadata snapshot/cache”，不是用户查询 graph。



真正用于：



```text

按 Artist 浏览

按 Genre 搜索

Album graph

```



的是 logical 层的正规 relation tables。



这样可以避免为 source snapshot 再创建三套 many-to-many 表，控制复杂度。



`rawMetadata` 不进入正式 database。当前 `AuralisMetadata` 中虽然有 `rawMetadata`，它主要用于 debug / extraction diagnostics，不需要为了 Phase 3 长期持久化整个原始 tag map。



Artwork bytes 也不能塞 Room。



\---



\# 14. Logical Track metadata



`TrackEntity` 保存 active Track 当前的逻辑 metadata snapshot：



```text

TrackEntity

\-----------

trackId



trackKeyVersion

trackKeyHash

trackKeyStrength



albumId nullable



title nullable

date nullable

trackNumber nullable

trackTotal nullable

discNumber nullable

discTotal nullable



createdAt

updatedAt

```



Artist / Genre 不重复塞 TEXT，而使用关系表。



Logical Track metadata 的唯一来源是：



```text

当前 active TrackSource 的 SourceMetadata

```



当 active source 改变时，TrackEntity 及其 Artist/Genre/Album relations 同步切换到新 source 的 metadata。



当 Track 没有 active source 时，则保留最后一次 logical metadata，不清空。



这保证：



```text

权限暂时失效

文件暂时丢失

删除后的 tombstone

```



仍然知道原来是哪首歌。



\---



\# 15. Track Artist junction



建议：



```text

TrackArtistCrossRef

\-------------------

trackId

artistId

position

```



主键推荐：



```text

PRIMARY KEY(trackId, position)

```



另建：



```text

INDEX(artistId)

```



而不是：



```text

PRIMARY KEY(trackId, artistId)

```



因为我们需要保留 credit order。



由于 Phase 2C 已经保证 list 内的值来自明确多值规则，Phase 3 在写 relation 前可以按 exact normalized identity 去重；如果一个文件真的出现：



```text

A; A

```



logical relation 只保留一次 `A`。



\---



\# 16. Album Artist junction



结构同样为：



```text

AlbumArtistCrossRef

\-------------------

albumId

artistId

position

```



Album Artist relationship 完全来自 Album identity/source metadata。



它不能从 Track Artist 推导。



当一个 Album 下不同 source 对 Album Artist tag 不一致时，它们原则上应该已经生成不同 AlbumKey，因此不会被错误聚合。



\---



\# 17. Track Genre junction



```text

TrackGenreCrossRef

\------------------

trackId

genreId

position

```



仍保留 source 中的 Genre 顺序。



Genre relation 随 active source 更新。



\---



\# 18. AlbumEntity



建议：



```text

AlbumEntity

\-----------

albumId

albumKeyVersion

albumKeyHash UNIQUE



title

date



createdAt

updatedAt

```



Album Artist 通过 junction，不在 AlbumEntity 中再保存拼接字符串。



不要存：



```text

coverPath

representativeTrackId

trackCount

duration

```



作为 authoritative identity data。



这些属于可派生或未来 cache/view 数据。



尤其 Artwork cache 后面必须以 `AlbumId` 或明确 artwork identity 为基础，绝不能用 Album title。



\---



\# 19. ArtistEntity / GenreEntity



建议：



```text

ArtistEntity

\------------

artistId

artistKeyVersion

artistKey UNIQUE

displayName

```



```text

GenreEntity

\-----------

genreId

genreKeyVersion

genreKey UNIQUE

displayName

```



v1 identity 都来自 canonicalized name。



当前不增加：



```text

sortName

alias

MusicBrainzId

externalId

normalizedSearchName

```



这些未来需要时再增加。



\---



\# 20. LibraryRootEntity



当前 debug `SafRootStore` 使用有序 SharedPreferences list；代码已经明确说明这个顺序就是 future duplicate priority。 正式数据库设计应保留这一语义。



建议：



```text

LibraryRootEntity

\-----------------

rootId

treeUri UNIQUE

displayPath

priority



availabilityState

lastSuccessfulScanAt

lastAttemptedScanAt



createdAt

```



其中：



```text

priority

```



代表添加顺序。



不要每次扫描按当前 list index 临时计算优先级，否则删除一个 root 后其余 root 的 identity 数据会全部变化。



可以设计为单调递增：



```text

0

1

2

3

...

```



用户删除 root 2 后：



```text

0

1

3

```



即可。



如果未来支持手动调整 root priority，再明确修改该字段。



\---



\# 21. Root 删除和 root temporarily unavailable 必须区分



这是 database layer 必须提前建模的区别。



`LibraryRootEntity.availabilityState` 建议：



```text

AVAILABLE

UNAVAILABLE

```



“用户从 Auralis 中删除 root”不等于 `UNAVAILABLE`，而是 root configuration 被明确移除。



两种行为不同：



```text

provider / permission temporarily unavailable

→ 保留 root

→ 保留其 source

→ 不判定 source missing

```



```text

user explicitly removes root

→ root 不再参与 active library

→ 该 root 独占 source 可转为 detached/missing

→ 若 physical document 同时被其它 configured root 覆盖，则仍由其它 observation 保持 source active

```



后续实现必须先处理 overlap，再决定 source 是否真的离开 library。



\---



\# 22. Source availability state



建议正式冻结为：



```text

AVAILABLE

MISSING

UNREACHABLE

```



语义分别为：



`AVAILABLE`：本轮可确认 source 存在。



`MISSING`：Auralis 成功扫描了原 source 所属可观测范围，并明确确认该 document 已不再存在。



`UNREACHABLE`：无法可靠确认它还在不在，例如 root permission revoked、provider error、目录 query failure。



`UNREACHABLE` 绝不能自动转成 `MISSING`。



当前 `SafScanEvent.DirectoryUnavailable` 已经专门为这一行为留出了信号，所以正式 reconciliation 需要直接消费该事件，而不是把“本轮没看到”简单理解成“文件删除”。



\---



\# 23. Parse state



建议：



```text

NOT\_PARSED

PARSED

FAILED

```



成功 Phase 2C 后为 `PARSED`。



失败保留：



```text

failureStage

failureCode

failureMessage

```



Parse failed source 不参与 semantic TrackKey matching，但仍然作为 physical TrackSource 保存。



这满足原先产品规则中“不可解析文件不能静默丢弃”。



\---



\# 24. Playability state



建议：



```text

UNKNOWN

PLAYABLE

UNPLAYABLE

```



扫描成功和 metadata parse 成功不能自动声称：



```text

PLAYABLE

```



除非已经由播放链路确认。



因此刚扫描成功：



```text

UNKNOWN

```



允许参与 active source candidate。



以后 Media3 成功打开过：



```text

PLAYABLE

```



如果明确失败：



```text

UNPLAYABLE

```



并重新执行 active source selection。



Phase 3 数据模型只需要支持这一状态；不需要为了 Phase 3 专门提前播放每个文件。



\---



\# 25. Active source



Track 与 active TrackSource 不建议直接在 `TrackEntity` 加：



```text

activeSourceId

```



因为这会形成：



```text

TrackSource.trackId → Track

Track.activeSourceId → TrackSource

```



双向 FK。



更清楚的结构是：



```text

TrackActiveSourceEntity

\-----------------------

trackId PK

sourceId UNIQUE

```



必须保证：



```text

source.trackId == trackId

```



这个条件 SQLite FK 本身不好表达，因此 selection/write service 需要在 transaction 内验证。



如果 Track 当前没有 usable source：



```text

TrackActiveSource row 不存在

```



\---



\# 26. Duplicate candidate eligibility



Source 能成为 active candidate 必须满足：



```text

availabilityState == AVAILABLE

parseState == PARSED

playabilityState != UNPLAYABLE

trackId == targetTrackId

```



其中 `UNKNOWN` 暂时可以参与，因为不能为所有文件做 eager playback probe。



\---



\# 27. Duplicate source ranking



符合资格后，严格按以下 comparator：



```text

1\. playability

&#x20;  PLAYABLE before UNKNOWN



2\. root priority

&#x20;  smaller first



3\. relativePath

&#x20;  deterministic lexical ascending



4\. sourceId

&#x20;  deterministic final tie breaker

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



\---



\# 28. Physical continuity matching



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



\---



\# 29. Path matching 的角色



如果 `(provider, documentId)` 没有命中，可以允许一个非常保守的辅助 location match：



```text

same root

\+

same relativePath

```



但这一规则不能直接证明 logical Track identity。



它的主要用途是处理某些 SAF provider documentId 行为不稳定、但路径没有变化的情况。



如果 path match source 但 metadata 与原 Track 的 Album identity 已改变，后续仍必须按 Album hard boundary 重新判断。



不能因为路径相同就无条件保留 TrackId。



\---



\# 30. Album hard boundary



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



\---



\# 31. 同 source metadata 修改但 Album 不变



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



\---



\# 32. 新 physical source 的 semantic matching



如果：



```text

document key miss

path auxiliary miss

```



说明这是新 physical source。



此时：



```text

if TrackKeyStrength == STRONG:

&#x20;   search active + tombstone Tracks by TrackKey

else:

&#x20;   do not semantic auto-match

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



\---



\# 33. Strong TrackKey 多重命中



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



\---



\# 34. Duplicate Track 不主动做全库 collapse



Phase 3B reconciliation 不应该每次启动就执行：



```text

GROUP BY trackKey

然后自动把所有已有 Track 合并

```



TrackKey 主要用于“新 source 找已有 Track”。



已经存在的两个 Track 即使后来 key 变成相同，也不在 v1 自动 collapse。



这样可以防止 metadata edit 意外将两个历史 Track 合并，导致歌单、播放历史等未来引用被破坏。



如果以后要做 Track merge，应设计显式 migration/merge algorithm。



\---



\# 35. 重编码



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



\---



\# 36. rename / move



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



\---



\# 37. 真正删除文件



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



\---



\# 38. Tombstone



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



\---



\# 39. 重新出现



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



\---



\# 40. Active metadata 更新策略



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

1\. update TrackActiveSource

2\. copy winner SourceMetadata scalar values into Track

3\. resolve/create Album

4\. rebuild TrackArtist relation

5\. rebuild TrackGenre relation

```



全部必须处于同一个 Room transaction。



\---



\# 41. Album lifecycle



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



\---



\# 42. Artist / Genre stable ID



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



\---



\# 43. Source metadata 与 logical metadata 的职责边界



这层必须写进代码注释和架构文档：



```text

AuralisMetadata

&#x20;   ↓

SourceMetadata

&#x20;   ↓

identity calculation

&#x20;   ↓

TrackSource

&#x20;   ↓ active-source selection

Logical Track graph

```



不要：



```text

AuralisMetadata → TrackEntity

```



直接跳过 source metadata 层。



否则 duplicate、fallback source 和 source-specific differences 后续都会难以处理。



\---



\# 44. Scan pipeline 最终结构



正式 pipeline 建议形成：



```text

SAF discovery

&#x20;   ↓

Candidate audio

&#x20;   ↓

Raw metadata extraction

&#x20;   ↓

AuralisMetadata interpretation

&#x20;   ↓

ObservedTrackSource

&#x20;   ↓

Identity derivation

&#x20;   ↓

Reconciliation

&#x20;   ↓

Room transaction

&#x20;   ↓

Active library graph

```



这和 Musikr 的 Explore → Extract → Evaluate 思路相似，但 Evaluate 的具体 identity 产品语义完全属于 Auralis。



\---



\# 45. ObservedTrackSource



扫描与数据库之间不要直接传 Room entity。



增加一个纯 domain model：



```kotlin

data class ObservedTrackSource(

&#x20;   val documentKey: SafDocumentKey,

&#x20;   val root: RootReference,

&#x20;   val uri: Uri,

&#x20;   val relativePath: SafPath,

&#x20;   val fileName: String,

&#x20;   val sizeBytes: Long,

&#x20;   val modifiedMs: Long,

&#x20;   val format: AudioFormat,

&#x20;   val metadata: AuralisMetadata,

)

```



然后 Identity layer 产生：



```kotlin

ResolvedObservation(

&#x20;   observation,

&#x20;   albumIdentity,

&#x20;   albumKey,

&#x20;   trackIdentity,

&#x20;   trackKey,

&#x20;   trackKeyStrength

)

```



这样：



```text

scanner

metadata engine

identity

Room

```



彼此没有混在一起。



\---



\# 46. Reconciliation 输入必须是一轮 scan session



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



\---



\# 47. Reconciliation transaction 边界



TagLib extraction、文件 IO、SAF query 全部在数据库 transaction 外。



Room transaction 只处理：



```text

existing DB state

\+

already prepared ResolvedObservation

\+

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



\---



\# 48. 推荐 Room schema



完整草图：



```text

LibraryRootEntity

TrackEntity

TrackSourceEntity

SourceMetadataEntity

TrackActiveSourceEntity



AlbumEntity

ArtistEntity

GenreEntity



TrackArtistCrossRef

AlbumArtistCrossRef

TrackGenreCrossRef

```



关系：



```text

LibraryRoot 1 ─── N TrackSource



Track 1 ─── N TrackSource

Track 1 ─── 0..1 TrackActiveSource



Track N ─── 0..1 Album



Track N ─── M Artist

Album N ─── M Artist

Track N ─── M Genre



TrackSource 1 ─── 0..1 SourceMetadata

```



\---



\# 49. Foreign key 删除策略



建议 conservative 使用：



```text

TrackSource.trackId → Track

ON DELETE CASCADE

```



但正常 library reconciliation \*\*不删除 Track\*\*。



`SourceMetadata.sourceId → TrackSource ON DELETE CASCADE`



`TrackActiveSource.trackId → Track ON DELETE CASCADE`



`TrackActiveSource.sourceId → TrackSource ON DELETE CASCADE`



junction：



```text

ON DELETE CASCADE

```



Album 在 TrackEntity 中建议：



```text

ON DELETE SET NULL

```



不过正常流程同样不会主动删除 Album。



\---



\# 50. 关键索引



至少设计：



```text

Track(trackKeyVersion, trackKeyHash)

Track(albumId)



TrackSource(provider, documentId) UNIQUE

TrackSource(trackId)

TrackSource(rootId)

TrackSource(lastSeenScanId)



SourceMetadata(trackKeyVersion, trackKeyHash)

SourceMetadata(albumKeyVersion, albumKeyHash)



Album(albumKeyVersion, albumKeyHash) UNIQUE



Artist(artistKeyVersion, artistKey) UNIQUE

Genre(genreKeyVersion, genreKey) UNIQUE



TrackArtist(artistId)

AlbumArtist(artistId)

TrackGenre(genreId)

```



不应为了 Phase 3A 提前建 FTS。



\---



\# 51. UUID 与时间



数据库内部时间统一使用：



```text

Long epoch milliseconds

```



例如：



```text

createdAt

updatedAt

lastSeenAt

scanStartedAt

```



Date metadata 继续保存：



```text

YYYY-MM-DD

```



不能把音乐发行 Date 与数据库 timestamp 混用。



\---



\# 52. Room TypeConverter 边界



只给真正 value-type 数据使用 converter，例如：



```text

UUID ↔ String

Uri ↔ String

enum ↔ String

List<String> source snapshot ↔ JSON

```



不要使用 TypeConverter 把：



```text

Artist list

Genre list

Album relationship

```



塞进 TrackEntity 单列。



查询 graph 的字段必须正规化。



\---



\# 53. Artwork 边界



Phase 3 schema 现在只需允许 source metadata 表达：



```text

hasEmbeddedArtwork

```



是否最终保存 artwork cache identity，可以留给 artwork phase。



不要：



```text

把 ByteArray 放 Room

```



当前 `AuralisMetadata` 本身能够携带 artwork data，这只是 extraction pipeline 对象，不代表数据库应该照抄。



\---



\# 54. Lyrics 边界



类似地：



```text

embeddedLyrics

```



当前已经在 metadata model 中，但 Phase 3 identity/reconciliation 不依赖 lyrics。



Phase 3 database 可以暂时不持久化完整 lyrics，等歌词功能阶段再决定缓存方式。



Lyrics 绝不能参与 TrackKey。



\---



\# 55. Root persistence 迁移原则



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



\---



\# 56. 文件/包结构建议



目前仓库只有 `metadata / saf / scan` 三块正式 library package，而且还没有 Room 依赖。 当前 `app/build.gradle.kts` 也尚未引入 Room，因此数据库层确实还没有被提前实现。



后续建议结构：



```text

library/

&#x20;   metadata/

&#x20;       existing Phase 2 code



&#x20;   saf/

&#x20;       existing Phase 1 code



&#x20;   scan/

&#x20;       existing scan code



&#x20;   identity/

&#x20;       IdentityTextNormalizer.kt

&#x20;       AlbumIdentity.kt

&#x20;       AlbumKey.kt

&#x20;       TrackIdentity.kt

&#x20;       TrackKey.kt

&#x20;       IdentityHasher.kt



&#x20;   model/

&#x20;       ObservedTrackSource.kt

&#x20;       SourceStates.kt



&#x20;   db/

&#x20;       AuralisDatabase.kt



&#x20;       entity/

&#x20;           LibraryRootEntity.kt

&#x20;           TrackEntity.kt

&#x20;           TrackSourceEntity.kt

&#x20;           SourceMetadataEntity.kt

&#x20;           TrackActiveSourceEntity.kt

&#x20;           AlbumEntity.kt

&#x20;           ArtistEntity.kt

&#x20;           GenreEntity.kt

&#x20;           CrossRefs.kt



&#x20;       dao/

&#x20;           LibraryRootDao.kt

&#x20;           TrackDao.kt

&#x20;           SourceDao.kt

&#x20;           GraphDao.kt



&#x20;   reconcile/

&#x20;       LibraryReconciler.kt

&#x20;       SourceMatcher.kt

&#x20;       ActiveSourceSelector.kt

&#x20;       LogicalMetadataProjector.kt

```



这里没有必要现在增加多 module Gradle 工程。



单 `app` module 足够。



\---



\# 57. Pure Kotlin 与 Android/Room 边界



必须尽量让以下代码成为纯 Kotlin：



```text

identity normalization

AlbumKey

TrackKey

Strength calculation

active-source comparator

semantic matching decision

```



它们不依赖：



```text

Context

ContentResolver

Room

Uri（如果不必要）

```



这样大部分 Phase 3 核心规则可以用 JVM test 验收，不依赖 Find X9。



Room 只负责 persistence。



SAF 只负责 observation。



\---



\# 58. Identity 单元测试矩阵



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

\["AB", "C"]

!=

\["A", "BC"]

```



以及：



```text

NULL != ""

```



\---



\# 59. Reconciliation 单元测试矩阵



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



\---



\# 60. Duplicate selection 测试



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



\---



\# 61. Database integration tests



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



\---



\# 62. 真机验收场景



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



\---



\# 63. 关于“Title 修改但 TrackKey 变化”的重要验收



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



\---



\# 64. 关于 Album 修改的相反验收



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



\---



\# 65. Artwork/cache identity 预留



虽然 Phase 3 不实现 artwork cache，但需要提前规定：



以后 Album artwork cache key 可以引用：



```text

AlbumId

```



不能引用：



```text

albumTitle

```



Track artwork 可以引用：



```text

TrackId

或 SourceId

```



具体来源策略以后决定。



这样 Phase 3 的 stable identity 能自然支持后续 artwork。



\---



\# 66. Playlist identity 预留



虽然本阶段不实现 playlist，但现在就明确：



以后：



```text

PlaylistEntry

```



必须引用：



```text

TrackId

```



而不是：



```text

SourceId

URI

Path

TrackKey

```



这样 duplicate winner 切换、rename、move、reencode 都不会让歌单失效。



这也是为什么 Track tombstone 必须保留。



\---



\# 67. 播放层预留



以后 Media3 播放一个 Track 时逻辑应是：



```text

TrackId

→ TrackActiveSource

→ TrackSource.uri

→ Media3

```



播放层不应该自己重新做 duplicate resolution。



如果当前 source 播放失败：



```text

mark source UNPLAYABLE

→ rerun active source selection

→ optionally retry next source

```



这是后续 phase 的职责，但 Phase 3 数据模型必须支持。



\---



\# 68. 不采用音频 fingerprint



Phase 3 明确不引入：



```text

Chromaprint

AcoustID

PCM fingerprint

audio content hash

whole-file SHA

```



Whole-file hash 也不适合作 logical identity，因为 metadata 修改或重编码都会改变文件 bytes。



现阶段：



```text

SafDocumentKey

\+

Strong TrackKey

\+

tombstone history

```



已经足以覆盖确定的产品需求。



只有未来需求变为“路径、documentId、metadata、编码全都变化后仍必须认出相同录音”，再评估 fingerprint。



\---



\# 69. 明确不复用 Auxio/Musikr 的部分



继续复用其成熟机制思想：



```text

SAF exploration

raw metadata extraction

pipeline layering

metadata cache 思路

graph construction 思路

metadata-based hash 思路

```



但不得把其以下语义带入 Auralis：



```text

Album Artist fallback Artist

Artist fallback Album Artist

missing Album fallback directory

Album identity 不含 Date

URI 直接作为长期 song identity

MusicBrainz 优先取代 Auralis identity

Auxio unified Artist 产品语义

```



Auralis graph 从 Phase 3 开始必须完全由本方案定义。



\---



\# 70. Phase 3A 应形成的正式设计文档



虽然本轮不施工，但最终仓库施工时建议把设计固化成：



```text

docs/LIBRARY\_MODEL.md

```



其内容至少包括：



```text

Track vs TrackSource

TrackId / TrackKey / SafDocumentKey / Path 区别

TrackKeyV1 spec

AlbumKeyV1 spec

Strong / Weak

Artist / Genre graph

source states

duplicate ranking

reconciliation order

tombstone behavior

Room ER graph

```



同时更新：



```text

ARCHITECTURE.md

METADATA.md

```



只写摘要和链接，不重复整份规范。



`LIBRARY\_MODEL.md` 应成为未来改变 identity 时的唯一 specification。



\---



\# 71. 后续施工顺序



真正执行时建议严格按依赖顺序施工，而不是一次把整个 database 和 scan 集成一起写完：



```text

Step A

冻结 identity domain：

Normalizer / canonical collection / AlbumKey / TrackKey / Strength。



Step B

完整 JVM tests，identity 规则全部通过。



Step C

定义 domain observation/state models：

ObservedTrackSource / SourceMetadata / states。



Step D

引入 Room，建立 schema、FK、index、DAO 和 migration baseline。



Step E

实现 pure reconciliation decision layer。



Step F

实现 Room-backed reconciliation transaction。



Step G

接现有 SAF + Phase 2C metadata pipeline。



Step H

实现 active source projection → logical graph。



Step I

迁移 root persistence。



Step J

加入 debug inspection UI。



Step K

JVM / Room / Find X9 三层验收。

```



不能反过来先建 Room 表再边做边决定 identity。



\---



\# 72. 阶段提交边界



后续施工最好拆成数个独立 commit，使问题容易定位：



```text

identity model + tests



Room schema + DAO



reconciliation core + tests



scanner/metadata integration



root persistence migration



debug/acceptance tooling



docs

```



TagLib/JNI 不应该因为 Phase 3 被修改。



Phase 2C `MetadataInterpreter` 也原则上不应修改，除非发现它缺少 Phase 3 已经确定必须存在的 semantic output；目前 `AuralisMetadata` 已具备主要 identity 字段，因此预期无需改 Phase 2C。



\---



\# 73. Phase 3A 最终不变量



Phase 3 后所有代码都必须满足以下不变量：



```text

TrackId 是用户层长期 identity。



TrackKey 是可变化、可版本化的 semantic reconciliation key。



SafDocumentKey 是 physical continuity key。



Path/URI 从来不是 logical identity。



Album identity = Album + AlbumArtist set + Date。



Album Artist 永不 fallback Artist。



Artist 永不 fallback Composer 或 Album Artist。



缺失 Album 不创建 shared Unknown Album entity。



Multi Artist identity 与顺序无关，但显示顺序保留。



Genre 不参与 Track identity。



codec/bitrate/size/duration/path 不参与 Track identity。



一个 Track 可以拥有多个 TrackSource。



一个 Track 同时最多一个 active source。



duplicate loser 不删除。



active logical metadata 始终来自 active source。



same physical source + same Album → metadata edit 保持 TrackId。



Album identity 改变 → 必须跨 logical Track。



new physical source 只有 Strong TrackKey 才能自动 semantic merge。



Weak TrackKey 不做跨文件 merge。



semantic match 多重命中时不自动猜测。



successful scan 才能确认 MISSING。



unavailable directory 永远不能推导为 deleted。



没有 active source 的 Track 保留 tombstone。



重新出现可以重新连接旧 TrackId。



未来 playlist 引用 TrackId，而不是 source/path/key。



v1 不引入 audio fingerprint。

```



这组不变量就是 Phase 3A 的最终技术契约。只要后续数据库和 reconciliation 实现逐条满足这些条件，就不会把“文件位置”“物理文件”“metadata identity”和“用户眼中的曲目”重新混成同一个概念。

