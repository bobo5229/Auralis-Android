# AndroidLiquidGlass / Backdrop 复用审计

## 结论

Auralis 使用 AndroidLiquidGlass 当前发布名称 Backdrop 的底层效果 API，不复制其示例组件。
悬浮导航、独立搜索按钮和迷你播放器由 Auralis 按自身信息架构组合，背景采样、模糊、
vibrancy 与 lens 效果交给 Backdrop。

## 固定来源

- 上游仓库：<https://github.com/Kyant0/AndroidLiquidGlass>
- 文档：<https://kyant.gitbook.io/backdrop>
- 审计分支：`kmp`
- 审计 commit：`65ab177e90e5c1d8c62e70cf7755841982da65f6`
- Maven 坐标：`io.github.kyant0:backdrop:2.0.0`
- 许可证：Apache License 2.0

## 版本选择

审计时上游最新发布版为 `2.0.1`，其构建使用 Kotlin 2.4.x，与本项目 Kotlin 2.2.10
发生元数据版本不兼容，已通过实际编译确认。`2.0.0` 可以在当前工具链中编译运行，因此固定
使用稳定版 `2.0.0`，不为单个视觉依赖升级整个 Kotlin / Android 构建链。

升级 Backdrop 时必须重新执行：

1. Kotlin 编译与 debug APK 构建；
2. Find X9 上主导航、滚动内容和迷你播放器的实时玻璃效果验收；
3. 深浅背景的文字可读性、点击区域和系统导航栏安全间距检查；
4. 长列表滚动与页面切换的卡顿检查。

## 复用边界

复用内容：

- `rememberLayerBackdrop` / `layerBackdrop` 建立可采样内容层；
- `drawBackdrop` 绘制指定形状的玻璃表面；
- `blur`、`vibrancy` 与 `lens` 组成受控的视觉效果。

Auralis 自有内容：

- 四栏导航与独立搜索按钮的信息架构；
- 当前目的地、播放状态和页面返回栈；
- 迷你播放器的内容、控制和 Media3 状态接入；
- 玻璃区域之外的主题、排版、专辑页与播放页。

不直接复制上游 `LiquidBottomTabs` 等示例，以免把演示数据模型、交互假设和升级风险带入
产品代码。

## 平台与降级

Find X9 / ColorOS 16 是主要视觉验收设备。Backdrop 在支持的平台使用系统图形能力；低版本
Android 无法提供完整效果时，Auralis 仍绘制半透明表面、边界和可读前景，不让玻璃效果成为
导航或播放控制可用性的前提。

## 风格模式

Auralis 保留两套使用同一 Backdrop 渲染链路的参数预设：

- `Liquid Glass`（默认）：低模糊、低表面遮罩、较强折射，开启 depth effect 与克制的 chromatic aberration；
- `磨砂玻璃`：保留首轮真机验收通过的高模糊、高表面遮罩与较弱折射参数。

Demo 阶段通过顶部菜单切换并使用可保存的 Compose 页面状态；正式产品阶段迁移为 DataStore
界面偏好。两种模式共用布局、点击区域与播放状态，不维护两套组件实现。

## QWEA0 实现的定向审计

- 参考仓库：<https://github.com/QWEA0/Liquid-Glass-Android>
- 审计 commit：`73e22530f4f6d1d525d7d76ca83efe07dd816eee`
- 审计版本：`v2.0.10`
- 许可证：MIT

该项目通过圆角矩形 SDF、沿法线折射、分通道色散、双瓣镜面高光、重力传感器光源与
按压形变强化 iOS 风格的液态观感。它面向 Android View/XML，并明确建议 Compose 项目使用
Kyant0/AndroidLiquidGlass，因此 Auralis 不引入其 View 组件或 JitPack 依赖，也未复制其源码。
本次仅将上述光学特征作为参数调校依据，优先使用现有 Backdrop 的 `lens`、`depthEffect` 和
`chromaticAberration` 能力。只有现有 Compose 能力经真机验证仍不足时，才另行评估最小范围
移植，并在发生实际代码派生时补充第三方许可声明。
