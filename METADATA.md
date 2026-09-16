# Auralis Mobile 元数据规则

本文档定义 Auralis Mobile v1 对本地音频元数据的读取、解析、展示和检索规则。

## 1. 支持格式与读写边界

v1 支持读取以下音频格式：

- MP3
- AAC
- ALAC
- FLAC

Auralis Mobile 只读取音频文件中的元数据，不允许修改或写回音频文件。包括用户编辑、自动修复、格式化或补全在内的任何操作，都不得改变原始音频文件及其内嵌元数据。

## 2. 多值字段解析

所有多值字段只使用英文分号 `;` 作为分隔符。

- `A; B; C` 必须解析为三个独立值：`A`、`B`、`C`。
- 逗号、斜杠、`&`、`feat.` 等其他符号绝不作为分隔符。
- 分隔符两侧的常规空白应被清理，但字段值内部的标点和空格必须保留。

因此，以下内容都应作为单值处理：

- `Tyler, the Creator` 是单值 Artist。
- `R&B/Soul` 是单值 Genre。
- `Hip-hop/Rap` 是单值 Genre。

同一字段中只有英文分号会产生多个值。解析规则不能根据常见音乐命名习惯进行猜测或拆分。

补充规则（Phase 2C 实现）：
- 若底层容器本身表达物理多值（例如 ID3v2.4 多文本帧、MP4 多个文本原子、Xiph 多个同名键），保持物理多值并对每个字符串分别按英文分号 `;` 分割展平。
- 连续分号（如 `;;A;;;B;;`）或分割后全为空白的项会被忽略，不生成空实体。
- 保持原始出现顺序，不进行字母大小写去重，不合并不同项。

## 3. Artist 与 Album Artist

Artist 与 Album Artist 是两个严格区分的字段，不得互相替代或合并：

- Artist 表示具体曲目的表演者或参与者。
- Album Artist 表示整张专辑的主要艺术家归属。
- 多 Artist 和多 Album Artist 均按本规则解析为独立值。
- 多 Artist / 多 Genre 中的任意单值都可以独立被搜索和浏览。

缺失 Album Artist 时，界面必须显示“未知 Album Artist”，禁止自动降级使用 Artist 作为 Album Artist。其他缺失字段同样显示对应的“未知”值，不允许根据其他字段推测补全。

补充规则（Phase 2C 实现）：
- 在结构化元数据解释层，缺失的 Artist 或 Album Artist 均表达为 `emptyList()`，不注入假值。
- 严禁以下降级与推测行为：
  - 禁止在缺失 Album Artist 时用 Artist 补全；
  - 禁止在缺失 Artist 时用 Album Artist 补全；
  - 禁止在缺失 Artist 时用 Composer 补全；
  - 禁止根据 Compilation 标志自动生成 "Various Artists"。

## 4. 日期与年份

- Date 格式固定为 `YYYY-MM-DD`。
- 界面显示完整日期，不显示为只有年份的替代形式。
- 如有需要，Year 可以由 Date 的前四位派生。
- 不要求单独维护 Year 字段。

补充规则（Phase 2C 实现）：
- 优先读取各格式标准日期字段：MP4 为 `©day`，Xiph 为 `DATE`，ID3v2 为 `TDRC`。
- 仅当字段包含合法 `YYYY-MM-DD`（或带 ISO-8601 时间的合法前缀）时才确认为有效 Date。
- 不优先读取 `ORIGINALDATE` / `TDOR` 替代正式发行 Date。
- 单独年份（如 `TYER` 或 `YEAR = "2024"`）或缺少月/日的信息保持为缺失（`null`），禁止将年份自动升级为 Date。
- 解析器不自行猜测或修复不规范日期。

## 5. 专辑与曲目身份

### 专辑身份

专辑身份至少由以下三个字段共同区分：

- Album
- Album Artist
- Date

因此：

- 同名 Album 但不同 Album Artist，必须视为不同专辑。
- 同 Album / Album Artist 但不同 Date，必须视为不同发行版本，也必须视为不同专辑身份。

缺失字段仍须以其对应的未知值参与展示和身份区分，不能通过其他字段自动猜测。

### 曲目身份与排序

Disc Number 与 Track Number 共同参与曲目区分和专辑内排序。多碟专辑中，即使 Track Number 相同，只要 Disc Number 不同，也必须视为不同曲目位置。

补充规则（Phase 2C 实现）：
- Track Number 与 Disc Number 属于结构化数字（如 `"1/12"` 对应序号 1、总数 12；`"3"` 对应序号 3、总数缺失），不遵循文本多值规则。
- 支持斜杠合并写法（`TRCK` / `TPOS` / `trkn` / `disk`），并支持 Xiph 独立的 `TOTALTRACKS` / `TRACKTOTAL` / `TRACKC` 与 `TOTALDISCS` / `DISCTOTAL` / `DISCC` 组合。
- 无法合法解析为正整数时置为 `null`，不进行猜测，不抛出异常。
- ID3 数字 `TCON`、括号代码 `(17)`、Winamp 扩展流派表与 MP4 `gnre` 属于格式标准枚举，在解析前先行映射还原为文字标准流派，再遵循 Auralis 英文分号 `;` 分割规则；还原后的内部符号（如 `Rock & Roll`、`Pop/Funk`）不作为多值分隔符。

## 6. Artwork 与歌词

### Artwork 优先级

封面来源按以下优先级读取：

1. 内嵌 Artwork
2. 音频文件同目录下的 `cover` 文件
3. 音频文件同目录下的 `COVER` 文件

当存在更高优先级且可用的来源时，不使用低优先级来源覆盖它。

### 歌词优先级

歌词来源按以下优先级读取：

1. 内嵌歌词
2. 音频文件同目录下的 `.lrc` 文件

当存在可用的内嵌歌词时，不使用同目录 `.lrc` 覆盖它。

## 7. 搜索与浏览范围

搜索范围固定为以下字段：

- Title
- Artist
- Album Artist
- Album
- Genre

多值字段中的每个独立值都可以单独命中搜索并用于浏览筛选。

不搜索歌词内容。歌词只能作为播放页或相关音乐内容展示的一部分，不参与曲库搜索索引。

## 8. 曲库身份键与投影规则（Phase 3B 冻结）

### 8.1 文本规范化（IdentityTextNormalizer）
- 仅执行 `trim()` 与 Unicode NFC 标准化。
- 不做小写转换（不 lower-case），不做模糊音或别名归并（严格保留大小写语义）。

### 8.2 专辑键（AlbumKeyV1）
- 输入维度：`albumTitle` + `albumArtists`（规范化唯一有序集）+ `date`。
- 生成 SHA-256 结构哈希。无专辑名称的文件不生成 `AlbumKey`，不创建共享 `AlbumEntity`（`albumId = null`）。

### 8.3 曲目键（TrackKeyV1 与 TrackKeyStrength）
- 输入维度：`AlbumKey`（或无专辑标识）+ `title` + `trackArtists`（规范化唯一有序集）+ `discNumber` + `trackNumber`。
- **STRONG**：具备完整 `AlbumKey`、`title`、`discNumber`、`trackNumber` 与至少一个 `Artist`。
- **WEAK**：缺少上述任一关键身份维度。Weak Key 仅作为单个物理文件回退索引，**绝不允许跨物理源自动合并为同一逻辑曲目**。

### 8.4 逻辑元数据投影（Active Source Controls Logical Metadata）
- 当同一个逻辑曲目存在多个物理源时，逻辑曲目（`TrackEntity`、`TrackArtistCrossRef`、`TrackGenreCrossRef`）的展示元数据完全由当前选优胜出的 **Active Source** 决定。
- 物理源之间的非身份元数据（如不同的 Genre 或细微标签差异）**不做 union 混合**，遵循 **Active source wins** 原则。

