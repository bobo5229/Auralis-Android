# Auralis Mobile 元数据解析引擎调研（Technical Spike Phase 2A）

本文记录 Auralis Mobile 元数据解析层的复用方案调研结论与最小移植计划。本阶段只做调研，不实现元数据系统。

## 1. 基线

| 项 | 值 |
| --- | --- |
| 上游仓库 | <https://github.com/OxygenCobalt/Auxio> |
| 参考 commit | `c05cebc52fe2393bf3a988068e4060fd6dba408f` |
| Musikr 模块 | `musikr/`（GPL-3.0-or-later） |
| TagLib 子模块 | `musikr/src/main/cpp/taglib` → <https://github.com/taglib/taglib> |
| TagLib pin commit | `5d63187a8b8d291cbf4f77d1bb973bc5ee91cf03`（TagLib 2.2.1，gitlink；`.gitmodules` 里的 `tag = ee1931b` 与实际 gitlink 不一致，以 gitlink 为准） |
| TagLib 许可 | LGPL-2.1 / MPL-1.1 |

调研方法：把上游以 `--filter=blob:none --sparse` 浅克隆到仓库外的临时目录，逐个读取上述文件与 TagLib pin commit 的实际源码。本文所有关于 TagLib 行为/API 的结论都来自该 commit 的源码，不是推测。

## 2. 结论摘要

1. **推荐复用 Auxio/Musikr 的“文件/URI → 原始 TagLib 元数据”层**，并保留 Auralis 自己的解释规则。
2. 最小依赖：**5 个 Kotlin 文件 + 18 个 C++ 文件 + 1 份自写 CMakeLists + TagLib 子模块**。不带 Room、Music 模型、Cache、播放、UI。
3. Windows 构建可行：**TagLib 作为 git submodule 通过 `add_subdirectory` 参与 AGP 的 `externalNativeBuild`**，不需要 `sh`、WSL 或 Git-Bash。
4. v1 只编译 **arm64-v8a**。
5. MP3 / M4A(AAC) / M4A(ALAC) / FLAC 四种格式的元数据路径均已确认；原始多值结构不会丢失。
6. **无硬阻塞**。有 2 个前置条件（本机未安装 NDK/CMake）和 1 个待实机验证的构建集成风险（见 §9 R2）。

## 3. A. 依赖树分析

### 3.1 必须直接复用

**Kotlin：`musikr/src/main/java/org/oxycblt/musikr/metadata/` 整包（326 行）**

| 上游文件 | 行数 | 移植改动 |
| --- | --- | --- |
| `Metadata.kt` | 61 | 仅改包名。`Metadata(id3v2, xiph, mp4, cover, properties)` 与 `Properties` 原样保留；**按容器分开的三个 map 是本方案的核心价值** |
| `MetadataExtractor.kt` | 56 | 输入类型由 `musikr.fs.File` 改为 Auralis 自己的 `MetadataTarget(uri, fileName)`；`openFileDescriptor(uri, "r")` 数据路径不变 |
| `NativeInputStream.kt` | 93 | 去掉 `musikr.fs.File` 依赖，只持有 `fileName`；其余原样 |
| `NativeTagMap.kt` | 74 | 内联 `correctWhitespace()`（来自 `util/ParseUtil.kt`，只需这一个扩展：`trim().ifBlank { null }`） |
| `TagLibJNI.kt` | 42 | 仅改包名与输入类型；保持 `System.loadLibrary("tagJNI")` |

**C++：`musikr/src/main/cpp/`（18 个文件，约 1150 行）**

`taglib_jni.cpp`、`JInputStream.{h,cpp}`、`JMetadataBuilder.{h,cpp}`、`JTagMap.{h,cpp}`、`JClassRef.{h,cpp}`、`JObjectRef.{h,cpp}`、`JStringRef.{h,cpp}`、`JByteArrayRef.{h,cpp}`、`util.h`

必须做的三类机械改动：

1. JNI 导出符号改名：
   `Java_org_oxycblt_musikr_metadata_TagLibJNI_openNative` → `Java_com_bobo_auralis_mobile_library_metadata_TagLibJNI_openNative`
2. C++ 内硬编码的 Java 类路径全部改包。`JClassRef` / `JObjectRef` 都接受类路径参数，因此这是纯字符串替换：
   - `JInputStream.cpp`：`org/oxycblt/musikr/metadata/NativeInputStream`
   - `JTagMap.cpp`：`org/oxycblt/musikr/metadata/NativeTagMap`
   - `JMetadataBuilder.cpp`：`.../Properties`、`.../Metadata`
   - `taglib_jni.cpp`：`MetadataResult$Success` / `$NoMetadata` / `$NotAudio` / `$ProviderFailed`
3. 头文件 include 由“TagLib 安装后的扁平布局”改为“TagLib 源码树布局”，共 16 处（见 §5.2）。

**构建与第三方**

- `app/src/main/cpp/CMakeLists.txt`：按 §5.1 重写（约 40 行），不是移植。
- TagLib 子模块：`app/src/main/cpp/taglib`，pin `5d63187a8b8d291cbf4f77d1bb973bc5ee91cf03`。已确认 Auxio 用的是上游 TagLib，不需要私有 fork：该 commit 下 `isSupported(IOStream*)`、`MP4::Properties::codec()`、`XiphComment::fieldListMap()` 全部存在。
- R8 keep 规则：把 `musikr/consumer-rules.pro` 的 9 条 `-keep` 按新包名写进 `app/src/main/keepRules/rules.keep`。当前 release 未开混淆，属于预防性措施。

### 3.2 可以裁剪

| 项 | 判断 |
| --- | --- |
| `cpp/build_taglib.sh`、`cpp/android.toolchain.cmake` | 丢弃，由 CMake 直接构建 TagLib 取代 |
| `musikr/build.gradle` | 不移植，配置写进 `app/build.gradle.kts` |
| `taglib_jni.cpp` 的 `parseOpus` / `parseVorbis` / `parseWav` 及对应 `isSupported`、`dispatchAndParse` 分支 | v1 不读 Ogg/Opus/WAV，可删；随后可配 `WITH_RIFF` / `WITH_DSF` / `WITH_MATROSKA` = OFF 缩小 .so。**建议留到 Phase 2C**：Phase 2B 先按 Auxio 的 flag 集合跑通，再裁剪 |
| `setId3v1` | 保留（只有 ID3v1 的老 MP3 靠它才有元数据）。代价见 §9 R4 |
| 多 ABI 的 TagLib 构建 | v1 只编 arm64-v8a |

### 3.3 Auralis 不需要（不进入移植范围）

- `musikr/cache/`（Room、`DBCache`）
- `musikr/covers/`（封面缓存、存储、转码、`ChainedCovers`）
- `musikr/fs/`（Auralis 已有自己的 SAF 层，见 `THIRD_PARTY_NOTICES.md`）
- `musikr/graph/MusicGraph.kt`、`musikr/model/`、`musikr/pipeline/`、`musikr/playlist/`
- `Musikr.kt`、`Music.kt`、`Library.kt`、`Config.kt`、`util/LangUtil.kt`、`fs/track/LocationObserver.kt`
- **`musikr/tag/` 整包**，尤其：
  - `tag/parse/TagParser.kt`：Artist 缺失时回退 Composer，Compilation 自动补 `"Various Artists"` → 与 `METADATA.md` 直接冲突
  - `tag/interpret/Separators.kt`：按逗号/斜杠/`&`/`+` 拆分、支持反斜杠转义、可配置 → 与「只用 `;`」冲突
  - `tag/interpret/Naming.kt`、`tag/format/ID3.kt`、`tag/format/Vorbis.kt`、`tag/Date.kt`、`tag/Disc.kt`

移植后的包结构建议（与 Phase 1 保持一致，全部放在 `:app`）：

```
app/src/main/java/com/bobo/auralis/mobile/library/metadata/   # Kotlin，5 个文件
app/src/main/cpp/                                            # JNI + CMakeLists
app/src/main/cpp/taglib/                                     # git submodule
```

## 4. B. SAF 兼容性分析

### 4.1 Musikr 为什么能处理 `content://`

因为 Musikr 的 `File.uri` 本身就是 SAF URI（`DocumentsContract.buildDocumentUriUsingTree(rootUri, documentId)`）。整条元数据链路**从不请求真实绝对文件路径**，只使用：

- `ContentResolver.openFileDescriptor(uri, "r")`：用 URI 打开，
- 以及文件名（`deviceFile.path.name`，即 SAF 的 `DISPLAY_NAME`），仅用于让 TagLib 按扩展名判断容器类型。

### 4.2 数据路径

```
SafFile(uri, path)                                    ← Auralis Phase 1 已有
  → ContentResolver.openFileDescriptor(uri, "r")      → ParcelFileDescriptor
  → FileInputStream(pfd.fileDescriptor)
  → java.nio.channels.FileChannel                     → read / position / size
  → NativeInputStream
        readBlock(ByteBuffer) / isOpen / seekFromBeginning / seekFromCurrent /
        seekFromEnd / tell / length / name / close
  → JNI: JInputStream : TagLib::IOStream
        readBlock 用 NewDirectByteBuffer 把 C++ TagLib::ByteVector 的内存
        零拷贝包成 direct ByteBuffer，由 Java 侧 FileChannel 直接填充
  → TagLib::FileRef(IOStream *)
        1) detectByResolvers(stream)      无自定义 resolver，跳过
        2) detectByResolvers(stream->name())
        3) detectByExtension(stream)      读 stream->name() 的扩展名
        4) detectByContent(stream)        逐格式 isSupported() 探测
  → JMetadataBuilder → Kotlin Metadata(id3v2, xiph, mp4, cover, properties)
```

关键点：

- `FileChannel` 同时提供 `read`、`position`、`size`，正好覆盖 `TagLib::IOStream` 需要的能力，所以 TagLib 不需要路径。
- 扩展名判断走 `stream->name()`，而 `JInputStream::name()` 返回 `NativeInputStream.name()`，最终是 SAF 的 `DISPLAY_NAME`。因此**只要提供文件名即可**，`SafPath.name` 已经完全满足。
- `MetadataExtractorImpl` 在 `Dispatchers.IO` 上执行，`openFileDescriptor` 返回 null 时归类为 `ProviderFailed`。

### 4.3 Auralis 的最小适配

1. 新增 `data class MetadataTarget(val uri: Uri, val fileName: String)`。
2. `NativeInputStream` 不再接收 `musikr.fs.File`，改为只持有 `fileName`。
3. `MetadataExtractor.extract()` 接收 `MetadataTarget`。

改动约 6 行。`SafFile` 的 `parent` / `root` / `documentKey` 在这一层完全不使用；`SafFile` 只需按 `AudioFormat` 过滤后喂进来。

**结论：SAF 兼容性无阻塞。** 这一层不依赖真实绝对路径，也不依赖 MediaStore。

## 5. C. Windows / Android Studio 构建方案

Auxio 的 `musikr/build.gradle` 用 `sh -c build_taglib.sh` 预编译 TagLib，这是 Windows 上唯一的障碍，且它只是**构建脚本形式**的问题，不是方案问题。

### 5.1 主方案 A（推荐）：单次 CMake，TagLib 走 `add_subdirectory`

不调用 `sh`、`build_taglib.sh`、`android.toolchain.cmake`，不需要 WSL 或 Git-Bash，不需要自定义 Gradle 任务（因此与 `org.gradle.configuration-cache=true` 天然兼容）。

`app/src/main/cpp/CMakeLists.txt` 关键内容：

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("tagJNI")                       # → libtagJNI.so，与 System.loadLibrary("tagJNI") 一致
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(TAGLIB_SRC   "${CMAKE_CURRENT_SOURCE_DIR}/taglib")                  # git submodule
set(TAGLIB_BUILD "${CMAKE_CURRENT_BINARY_DIR}/taglib-build")

# 必须在 add_subdirectory 之前锁定 cache 变量：TagLib 顶层 CMakeLists 读这些 option
foreach(o BUILD_SHARED_LIBS BUILD_BINDINGS BUILD_TESTING BUILD_EXAMPLES)
  set(${o} OFF CACHE BOOL "" FORCE)
endforeach()
set(VISIBILITY_HIDDEN ON CACHE BOOL "" FORCE)
set(WITH_ZLIB   OFF CACHE BOOL "" FORCE)   # 与 Auxio 一致
set(WITH_MP4    ON  CACHE BOOL "" FORCE)   # M4A / AAC / ALAC
set(WITH_VORBIS ON  CACHE BOOL "" FORCE)   # FLAC 归在这个 option 下
# WITH_APE / WITH_ASF / WITH_DSF / WITH_MATROSKA / WITH_MOD /
# WITH_SHORTEN / WITH_TRUEAUDIO = OFF
add_subdirectory("${TAGLIB_SRC}" "${TAGLIB_BUILD}")

add_library(tagJNI SHARED
        taglib_jni.cpp JInputStream.cpp JTagMap.cpp JMetadataBuilder.cpp
        JClassRef.cpp JObjectRef.cpp JStringRef.cpp JByteArrayRef.cpp)

target_include_directories(tagJNI PRIVATE "${TAGLIB_SRC}" "${TAGLIB_BUILD}")

target_link_options(tagJNI PRIVATE "-Wl,--exclude-libs,ALL,-z,max-page-size=16384")

target_link_libraries(tagJNI PRIVATE android log tag)   # TagLib 的 target 名是 tag
```

`app/build.gradle.kts` 增补：

```kotlin
android {
    ndkVersion = "28.2.13676358"                                   // 与 Auxio README 一致
    defaultConfig {
        ndk { abiFilters += "arm64-v8a" }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

以下依据全部从 pin 的 TagLib commit 直接读源码核实：

- `taglib/CMakeLists.txt` 里库的 **target 名是 `tag`**，并且 `target_include_directories(tag INTERFACE ...)` 只导出 `$<INSTALL_INTERFACE:...>`，**没有 `$<BUILD_INTERFACE:...>`** → 走 `add_subdirectory` 时消费端必须自己加 `-I`。
- `taglib_config.h` 由顶层 `configure_file` 生成到 `${CMAKE_CURRENT_BINARY_DIR}`，而 `taglib/CMakeLists.txt` 引用 `${CMAKE_CURRENT_BINARY_DIR}/../taglib_config.h` → 需要 `-I${TAGLIB_BUILD}`（`TAGLIB_BUILD` 即 TagLib 顶层在本项目 build 目录下的位置）。
- 静态链接时 `if(NOT BUILD_SHARED_LIBS) target_compile_definitions(tag PUBLIC TAGLIB_STATIC)` → 链接 `tag` 会自动带上该宏。
- `find_package(utf8cpp QUIET)` 找不到时会 `add_subdirectory("3rdparty/utfcpp")` → 离线可构建，不需要网络。
- 顶层 `cmake_minimum_required(VERSION 3.10...3.31)` 与 CMake 3.22.1 兼容。
- 同一种集成方式已经被 KTagLib 在 Android 上验证过：`add_subdirectory("../../../../taglib/taglib" taglib)` + `target_link_libraries(ktaglib ${log-lib} tag)`。
- TagLib 的 `WITH_*` 选项覆盖关系：`WITH_VORBIS` 同时管 Vorbis / FLAC / Ogg / Opus，`FLAC::File` 的检测分支在 `#ifdef TAGLIB_WITH_VORBIS` 内，所以 FLAC 依赖它。

**前置条件（需人工执行一次）**：本机 SDK（`C:\Users\BoBo\AppData\Local\Android\Sdk`）当前**没有安装 NDK，也没有安装 CMake**。需要在 Android Studio → SDK Manager 安装：

- `NDK 28.2.13676358`（与 Auxio 一致）
- `CMake 3.22.1`（SDK 的 CMake 包自带 ninja）

### 5.2 头文件 include 需要从扁平布局改为源码布局（16 处）

上游用安装后的 `taglib/pkg/<abi>/include`，头文件被压平在 `taglib/` 下；`add_subdirectory` 用的是源码树布局。需要改的 include：

| 上游写法 | 源码树路径 |
| --- | --- |
| `taglib/mpegfile.h` | `taglib/mpeg/mpegfile.h` |
| `taglib/mp4file.h` | `taglib/mp4/mp4file.h` |
| `taglib/mp4properties.h` | `taglib/mp4/mp4properties.h` |
| `taglib/mp4tag.h` | `taglib/mp4/mp4tag.h` |
| `taglib/flacfile.h` | `taglib/flac/flacfile.h` |
| `taglib/vorbisfile.h` | `taglib/ogg/vorbis/vorbisfile.h` |
| `taglib/opusfile.h` | `taglib/ogg/opus/opusfile.h` |
| `taglib/xiphcomment.h` | `taglib/ogg/xiphcomment.h` |
| `taglib/wavfile.h` | `taglib/riff/wav/wavfile.h` |
| `taglib/id3v1tag.h` | `taglib/mpeg/id3v1/id3v1tag.h` |
| `taglib/id3v2tag.h` | `taglib/mpeg/id3v2/id3v2tag.h` |
| `taglib/textidentificationframe.h` | `taglib/mpeg/id3v2/frames/textidentificationframe.h` |
| `taglib/attachedpictureframe.h` | `taglib/mpeg/id3v2/frames/attachedpictureframe.h` |
| `taglib/tpropertymap.h` | `taglib/toolkit/tpropertymap.h` |
| `taglib/tiostream.h` | `taglib/toolkit/tiostream.h` |
| `taglib/tstring.h` | `taglib/toolkit/tstring.h` |

`taglib/fileref.h` 与 `taglib/audioproperties.h` 位置不变。

### 5.3 备选方案 B（若 A 受阻）

保留上游“预编译 TagLib → CMake 导入 `libtag.a` + 扁平 include”的结构，把 `build_taglib.sh` 换成 Kotlin/Gradle 任务：

- 用 `androidComponents.sdkComponents.ndkDirectory` 解析出 NDK 目录；
- 调用 SDK 里的 `cmake.exe`：`-G Ninja`、`-DCMAKE_TOOLCHAIN_FILE=<ndk>/build/cmake/android.toolchain.cmake`、`-DANDROID_ABI=arm64-v8a`、`-DANDROID_PLATFORM=android-28`，其余 flag 与上游一致；
- `cmake --build` 后 `cmake --install --prefix <build>/taglib/<abi>`，得到 `lib/libtag.a` 与扁平 `include/`。

优点：移植的 C++ 与上游完全一致，include 不用改。缺点：多一个 Gradle 任务，以及 NDK/CMake 路径解析。

### 5.4 备选方案 C（最不推荐）

把预编译的 `libtag.a` 与头文件提交进仓库。可复现性差、二进制进 git、taglib 升级要手工重建，仅在 A、B 都不可行时考虑。

## 6. D. ABI

- **v1 只编译 `arm64-v8a`。** 主力设备 OPPO Find X9（Dimensity 9500）是 arm64-v8a；`PRODUCT.md` 只面向 Android 手机。
- **`armeabi-v7a` 不需要**：v1 没有 32 位目标设备。
- **`x86_64` 只对模拟器有价值**：成本是多一遍 TagLib 编译（clean build 每个 ABI 约 2–4 分钟）以及 APK 体积增加约 1.5–2 MB。本机 SDK 里没有 `system-images` 目录，说明当前并不使用模拟器 → v1 不开。
- 将来要用模拟器时，**只需要改 `abiFilters` 一行**，`CMakeLists.txt` 不用动（`${ANDROID_ABI}` 由 AGP 传入）。
- 保留 `-Wl,--exclude-libs,ALL,-z,max-page-size=16384`：前者隐藏静态链接进 `libtagJNI.so` 的 TagLib 符号，后者满足 Android 15+ 的 16 KB 页要求。

## 7. E. 格式支持确认

### 7.1 字段映射矩阵

| Auralis 字段 | MP3（ID3v2；ID3v1 也写进同一 map） | M4A/AAC（MP4） | M4A/ALAC（MP4） | FLAC（Xiph） |
| --- | --- | --- | --- | --- |
| 原始 map | `id3v2` | `mp4` | `mp4` | `xiph` |
| Title | `TIT2` | `©nam` | `©nam` | `TITLE` |
| Artist（多值） | `TPE1`（ID3v2.4 的 NUL 多值 → `List`） | `©ART`（StringList） | 同左 | `ARTIST`（StringList） |
| Album Artist（多值） | `TPE2` | `aART` | 同左 | `ALBUMARTIST` |
| Album | `TALB` | `©alb` | 同左 | `ALBUM` |
| Genre（多值） | `TCON`（**可能是 `17` / `(17)Rock`**） | `©gen` / `gnre`（数字） | 同左 | `GENRE` |
| Date | `TDRC` / `TYER`+`TDAT` / `TYER` | `©day` | 同左 | `DATE` |
| Disc Number | `TPOS` | `disk` → `"N/T"` 字符串 | 同左 | `DISCNUMBER` |
| Track Number | `TRCK` | `trkn` → `"N/T"` 字符串 | 同左 | `TRACKNUMBER` |
| 内嵌 Artwork | `APIC`（优先 FrontCover，否则第一张） | `covr`（优先 PNG/JPEG） | 同左 | FLAC picture（优先 FrontCover） |
| 内嵌 Lyrics | **`USLT` 当前读不到** | `©lyr` 可读 | `©lyr` 可读 | `LYRICS` / `UNSYNCEDLYRICS` 可读 |
| 自定义标签 | `TXXX` → key `TXXX:DESC` | `----` → key `----:DESC` | 同左 | 所有 Xiph 字段，key 大写 |
| mimeType | `audio/mpeg` | `audio/aac` | `audio/alac` | `audio/flac` |

容器识别：`TagLib::FileRef` 先按扩展名（`.mp3`/`.aac` → `MPEG::File`，`.m4a` → `MP4::File`，`.flac` → `FLAC::File`），失败再按内容探测。M4A 通过 `MP4::Properties::codec()` 区分 AAC 与 ALAC，因此 `audio/mpeg` / `audio/aac` / `audio/alac` / `audio/flac` 可以准确区分。

### 7.2 多值结构是否会丢

**原始层不会丢。** 依据：

- `JTagMap` 对 `TagLib::StringList` 提供重载，把整段列表逐个转成 Java `ArrayList<String>` 再交给 Kotlin（`add_id` / `add_custom` / `add_combined` 的 List 版本）。
- `NativeTagMap` 内部是 `MutableMap<String, List<String>>`，按 key 存 List。
- `NativeTagMap` 只做 `correctWhitespace()`（`trim()`，全空白则丢弃该项），**不拆分、不合并、不做分隔符推断**。真正会拆分/合并的是 Auxio 的 `Separators` / `TagParser`，它们不在移植范围内。

**两个必须记录的例外：**

1. MP4 的 `trkn` / `disk` 是 IntPair，`JMetadataBuilder::setMp4` 会把它序列化成 `"N/T"` 单字符串，原始的两段整数结构被压平。Auralis 需要对这两个字段做数字解析——这属于数字字段解析，不是多值分隔符规则。
2. ID3v1 回退会把 `tag.track()` / `tag.year()` 的 `0` 写成 `"0"`，并使用数字 `TCON`。见 §9 R4。

### 7.3 内嵌 Lyrics：现状与补齐方案

现状（已逐行核对 `JMetadataBuilder::setId3v2`）：

- FLAC：`setXiph` 把 `XiphComment::fieldListMap()` 的所有字段导出（key 大写），所以 `LYRICS` / `UNSYNCEDLYRICS` **可读**。
- M4A：`©lyr` 是 StringList item，**可读**。
- MP3：`USLT`（`UnsynchronizedLyricsFrame`）既不是 `TextIdentificationFrame` 也不是 `AttachedPictureFrame`，会落到 `else { continue; }` 分支被**静默丢弃**。`COMM`（`CommentsFrame`）同理。

**这是 Auralis 侧的缺口，不是 TagLib 的限制。** TagLib 在 pin 的 commit 中有 `taglib/mpeg/id3v2/frames/unsynchronizedlyricsframe.h`，提供 `text()` / `description()` / `language()`。补齐只需在 `JMetadataBuilder::setId3v2` 的帧循环里加一个分支（约 10 行）：

```cpp
} else if (auto lyricsFrame =
        dynamic_cast<TagLib::ID3v2::UnsynchronizedLyricsFrame *>(frame)) {
    TagLib::String desc = lyricsFrame->description();
    if (desc.isEmpty())
        id3v2.add_id("USLT", lyricsFrame->text());
    else
        id3v2.add_combined("USLT", desc, lyricsFrame->text());
}
```

`SYLT`（同步歌词）不处理：v1 只需要纯文本歌词。

### 7.4 `.aac`（裸 ADTS）需真机确认

`AudioFormat.AAC` 对应 `.aac` 扩展名，`FileRef` 会把它交给 `MPEG::File`（TagLib 有 `MPEG::Properties::isADTS()`，支持 ADTS 解析）。但 `parseMpeg` 只读取 ID3v1 / ID3v2 标签，裸 ADTS 文件通常不带 ID3v2，因此可能几乎没有可读元数据。需要在 Phase 2B 用真实 `.aac` 文件确认。

## 8. F. 备选方案对比

| 维度 | **Auxio/Musikr TagLib JNI（推荐）** | KTagLib（Android 端 TagLib JNI 封装） | Jaudiotagger（Android fork） |
| --- | --- | --- | --- |
| SAF `content://` 兼容性 | 原生支持：`openFileDescriptor(uri)` → `FileChannel` → 自建 `IOStream`，不需要真实路径 | 原生支持：直接吃 fd（`TagLib::FileStream(fd, true)`）。但 fd 路由下 `FileRef` 拿不到扩展名（`FileStreamPrivate("")`），只能靠内容探测 | **只接受 `AudioFileIO.read(File)`**，内部 `new RandomAccessFile(file, "r")`，因此 SAF 必须先复制到临时路径 |
| 是否保留多值标签 | **完整保留**：按容器分 3 个 map，每个 key 是 `List<String>`，不做拆分/合并 | 使用 TagLib `PropertyMap`，**已被 TagLib 规范化合并**，丢失 `TPE2` / `aART` / `TXXX` / `----` 等原始键名 | `FieldKey` 模型（ARTIST / ALBUM_ARTIST / GENRE / LYRICS / …），多值支持因格式实现而异 |
| MP3/AAC/ALAC/FLAC | 全部支持；M4A 还能区分 AAC 与 ALAC | 取决于其 pin 的 2021 年 TagLib fork，MP4/ALAC 能力较弱 | 全部支持（MP3/FLAC/MP4/Ogg/WAV…），MP4 是其最弱一环 |
| Artwork | 单张内嵌封面（FrontCover 优先，否则第一张） | `getArtwork(fd)` 取最大图 | `Tag.getFirstArtwork()` |
| Lyrics / custom tags | FLAC / M4A 已可读；MP3 需补 `USLT` 分支；自定义标签完整（`TXXX:` / `----:`） | PropertyMap 会丢掉未规范化的键 | `FieldKey.LYRICS`；自定义帧支持有限 |
| Windows 构建复杂度 | NDK + CMake；方案 A 不需要 `sh`/WSL | 库很旧：AGP 7.0 / Gradle 7.1.1 / Kotlin 1.5.21，与当前 AGP 9.3.2 + Gradle 9.5 不兼容 | 纯 JVM 依赖，Windows 最省事 |
| 维护成本 | 上游活跃（Auxio 仍在维护）；移植面 5 Kotlin + 18 C++，需自己维护包名与构建 | 最后提交 2021-08，无更新 | 上游迭代缓慢；Android 兼容依赖第三方 fork，版本停在 2.2.3/3.x |
| 许可 | Auxio/Musikr GPL-3.0+（Auralis 已因此为 GPL-3.0）；TagLib LGPL-2.1/MPL-1.1 | 同样引 TagLib | Apache / LGPL 双许可 |

关于 **AndroidTagLib**：在可验证的来源中找不到以此为名的维护中项目（GitHub 常见命名下均不存在，检索只命中无关的 AndroidTagView）。实际存在的同类方案是 **KTagLib**（`com.github.timusus:KTagLib`，Kotlin 绑定 + JitPack 分发），上表按它对比。如果指的是某个具体仓库，需要其 URL 才能进一步核实。

## 9. 风险与开放问题

| ID | 级别 | 内容 |
| --- | --- | --- |
| R1 | 前置条件 | 本机 SDK 未安装 NDK 与 CMake → 需在 Android Studio SDK Manager 安装 `NDK 28.2.13676358` 与 `CMake 3.22.1` |
| R2 | 中 | 方案 A 的 `add_subdirectory` 集成尚未实机验证过。缓解：§5.1 的每条依据都来自 pin 的 TagLib 源码；若失败则切方案 B |
| R3 | 中 | MP3 内嵌歌词（`USLT`）当前读不到，需补 1 处 C++ 分支（方案已定，见 §7.3） |
| R4 | 低 | ID3v1 回退会注入 `"0"`（`TRCK` / `TYER`）；数字 `TCON` / `gnre` 需要 ID3 genre 表才能显示为名字。**`METADATA.md` 未定义该规则**，Phase 2C 开始前必须决定：①按原文显示（严格“不推断”）②按 ID3 标准表映射（Auxio 的做法）。本阶段不做决定 |
| R5 | 低 | `trkn` / `disk` / `TRACKNUMBER` / `DISCNUMBER` 的 `"N/T"` 形式需要数字解析。`METADATA.md` 的「只用 `;`」针对多值文本字段，不适用于这两类数字字段，Phase 2C 需确认该边界 |
| R6 | 低 | 只提取 1 张内嵌封面，不支持多张内嵌图 / 大小图区分（v1 不需要） |
| R7 | 低 | 非可寻址的 SAF provider（云盘、管道型）会让 `FileChannel.position()` / `size()` 抛异常，最终归类为 `ProviderFailed`。v1 只面向手机内部存储，可接受；解析层需要把它区分为“访问失败” |
| R8 | 低 | `.aac` 裸 ADTS 能否读出标签需真机确认（见 §7.4） |
| R9 | 低 | `correctWhitespace` 会 `trim()` 并丢弃全空白值。与 `METADATA.md`「清理分隔符两侧空白、保留字段内部标点空格」一致，合理 |
| R10 | 低 | `NativeTagMap` 用 `map[id] = ...` 写入，同一 key 出现多次时后者覆盖前者。同一 ID 的多帧（少见）会丢掉前面的值，需记录 |

## 10. G. 结论与 Phase 2B 最小验证

### 10.1 推荐结论

**采用 Auxio/Musikr 的原始元数据提取层（TagLib JNI）。** 理由：

1. 它是唯一同时满足以下四点的方案：原生支持 SAF `content://`、不丢失原始多值结构、覆盖 MP3/AAC/ALAC/FLAC 与内嵌封面、不需要真实绝对路径。
2. 与 Phase 1 已有的 SAF 层天然衔接（`SafFile` → `MetadataTarget` 只需约 6 行适配）。
3. 该层本身不做任何解释，因此 `METADATA.md` 的规则（Artist 与 Album Artist 严格区分、不退化为 Composer、不自动补 Various Artists、只用 `;` 分隔、缺失保持未知）可以完整保留。

### 10.2 最少需要移植的内容

5 个 Kotlin 文件 + 18 个 C++ 文件 + 1 份自写 CMakeLists + TagLib 子模块（pin `5d63187…`）。

### 10.3 绝对不要带进来的内容

`musikr/cache/`、`musikr/covers/`、`musikr/fs/`、`musikr/graph/`、`musikr/model/`、`musikr/pipeline/`、`musikr/playlist/`、`Musikr.kt`、`Music.kt`、`Library.kt`、`Config.kt`，以及 **`musikr/tag/` 整包**（尤其 `TagParser.kt`、`Separators.kt`、`Naming.kt`、`format/ID3.kt`）。

### 10.4 Phase 2B 最小验证顺序

先验证构建链，再验证数据：

1. 安装 NDK `28.2.13676358` 与 CMake `3.22.1`。
2. 加入 TagLib 子模块（pin `5d63187…`），写 `app/src/main/cpp/CMakeLists.txt`，改 `app/build.gradle.kts`（`ndkVersion`、`abiFilters`、`externalNativeBuild`），只编 arm64-v8a。
3. **只验证构建链**：`openNative` 先用占位实现返回固定值，确认 `Gradle → CMake → TagLib → libtagJNI.so → APK` 打通。此步不看任何标签。
4. 补 5 个 Kotlin 文件，把 `openNative` 换成真实实现。
5. debug 页增加只读的“原始元数据 dump”：对每个候选音频显示 `mimeType` / `durationMs` / `bitrateKbps` / `sampleRateHz`，以及 `id3v2` / `xiph` / `mp4` 三个 map（含每个 key 的 List 元素个数）与 cover 字节数；可导出到 `getExternalFilesDir()`。
6. 真机验收：MP3 / M4A(AAC) / M4A(ALAC) / FLAC 各至少 1 个真实文件，确认 mimeType 正确、map 非空、**某个多值字段的 List size ≥ 2**（证明没有被合并）。
7. `gradlew test` 保持通过。不接 Room、不接 Media3、不写正式 UI。

**可判定的通过标准**：clean 状态下一次 `gradlew :app:assembleDebug` 成功；APK 内含 `lib/arm64-v8a/libtagJNI.so`；四种格式的 dump 内容符合 §7.1 的映射矩阵。

### 10.5 本阶段（Phase 2A）不做的事

不写任何元数据实现代码、不接 Room、不接播放、不写正式 UI、不创建 TagLib 子模块、不进入 Phase 2B。
