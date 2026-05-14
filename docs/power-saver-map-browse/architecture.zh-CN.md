# Power Saver Map Browse Architecture

## 目标

为 `PowerSaver Map Browse Mode` 建立一套可扩展的地图架构抽象，避免继续在 `MapInnerFragment` / `MapViewController` 中堆模式分支，为未来地图模式增加、相机策略调整、聚合策略变化打下基础。

## 当前代码约束

### Android

- [MapInnerFragment.java](/Users/whitetang/Desktop/Code/easydelivery_Android/app/src/main/java/com/hf/easydelivery/map/MapInnerFragment.java)
  已经承担大量 orchestration。
- [CameraFollowController.java](/Users/whitetang/Desktop/Code/easydelivery_Android/app/src/main/java/com/hf/easydelivery/map/CameraFollowController.java)
  已负责相机随动和多种 gating。
- [MyClusterRenderer.java](/Users/whitetang/Desktop/Code/easydelivery_Android/app/src/main/java/com/hf/easydelivery/map/MyClusterRenderer.java)
  当前聚合规则较硬，`zoom < 18` 即聚合。

### iOS

- [MapViewController.swift](/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapViewController.swift)
  也承担了较重的地图总控职责。
- [CameraFollowController.swift](/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/CameraFollowController.swift)
  已封装相机随动逻辑。
- 当前 cluster 行为通过 `GMUDefaultClusterRenderer` 配置完成，规则也偏局部。

## 设计原则

1. 不推翻现有地图主流程。
2. 不把新需求继续硬塞进控制器和 renderer。
3. 用明确的模式、策略、工厂和协调器分层。
4. Android 和 iOS 共用同一套产品模型，不追求代码共享，但追求概念共享。

## 核心抽象

### 1. `MapDisplayMode`

职责：

- 定义当前地图展示语义。

建议枚举：

- `FOLLOW`
- `OVERVIEW`
- `POWER_SAVER_BROWSE`

作用：

- 后续所有相机、包裹筛选、聚合、marker 样式都基于该模式决策。

### 2. `MapDisplayModeResolver`

职责：

- 根据当前上下文决定当前地图应该使用哪种 `MapDisplayMode`。

输入：

- perf/profile
- movement state
- navigation mode
- user interaction state
- auto-follow pause state
- proximity/focus state

输出：

- `MapDisplayMode`

设计模式：

- `Strategy + Policy`

### 3. `CameraBehavior`

职责：

- 定义不同展示模式下相机如何工作。

接口建议：

- `buildCamera(...)`
- `shouldAutoMove(...)`
- `shouldRecentre(...)`

实现：

- `FollowCameraBehavior`
- `OverviewCameraBehavior`
- `PowerSaverBrowseCameraBehavior`

设计模式：

- `Strategy`

### 4. `ParcelPresentationPolicy`

职责：

- 决定当前模式下哪些包裹应该真正绘制在地图上。

输入：

- 全量待投包裹
- driver location
- current focus
- visible region
- display mode

输出：

- 需要展示的 parcel 列表

实现：

- `FollowParcelPresentationPolicy`
- `OverviewParcelPresentationPolicy`
- `PowerSaverBrowseParcelPresentationPolicy`

设计模式：

- `Strategy`

### 5. `ClusterPolicy`

职责：

- 决定当前模式下是否聚合、聚合门槛、最大显示数量。

接口建议：

- `isClusterEnabled()`
- `minimumClusterSize()`
- `maxVisibleMarkers()`

实现：

- `DefaultClusterPolicy`
- `PowerSaverBrowseClusterPolicy`

### 6. `MarkerStylePolicy`

职责：

- 决定当前模式下 marker 的展示样式。

输出：

- marker size
- 文本长度
- focus 高亮
- special parcel style

实现：

- `DefaultMarkerStylePolicy`
- `PowerSaverBrowseMarkerStylePolicy`

### 7. `MapExperienceCoordinator`

职责：

- 对外提供地图体验编排入口。

内部协调：

- `MapDisplayModeResolver`
- `CameraBehaviorFactory`
- `ParcelPresentationPolicyFactory`
- `ClusterPolicyFactory`
- `MarkerStylePolicyFactory`

页面控制器只做：

1. 收集上下文
2. 调 coordinator
3. 应用结果

设计模式：

- `Facade`

## 推荐第一阶段落地范围

第一阶段不建议一次性把所有相机逻辑都完全策略化。

### 第一阶段必须新增

- `MapDisplayMode`
- `MapDisplayModeResolver`
- `PowerSaverBrowseParcelPresentationPolicy`
- `PowerSaverBrowseClusterPolicy`
- `MapExperienceCoordinator`

### 第一阶段可暂时保留在现有类中

- 现有 follow 相机主逻辑
- 现有 overview 相机主逻辑

### 第一阶段对现有类的要求

- `MapInnerFragment` / `MapViewController`
  只负责上下文组装和结果应用。
- `MyClusterRenderer` / iOS cluster renderer
  不直接承载产品规则，而是读取 `ClusterPolicy` 结果。

## Android 建议类设计

- `MapDisplayMode.java`
- `MapDisplayModeResolver.java`
- `MapExperienceCoordinator.java`
- `ParcelPresentationPolicy.java`
- `PowerSaverBrowseParcelPresentationPolicy.java`
- `ClusterPolicy.java`
- `PowerSaverBrowseClusterPolicy.java`

现有类改动点：

- `MapInnerFragment`
  - 从“直接决定显示哪些包裹、是否 cluster”转为调用 coordinator。
- `MyClusterRenderer`
  - 从硬编码 zoom 规则改为读取 cluster policy。
- `CameraFollowController`
  - 第一阶段仅新增 browse mode 所需最小入口，不强行重构全部 follow 行为。

## iOS 建议类设计

- `MapDisplayMode.swift`
- `MapDisplayModeResolver.swift`
- `MapExperienceCoordinator.swift`
- `ParcelPresentationPolicy.swift`
- `PowerSaverBrowseParcelPresentationPolicy.swift`
- `ClusterPolicy.swift`
- `PowerSaverBrowseClusterPolicy.swift`

现有类改动点：

- `MapViewController`
  - 只做上下文采集与应用。
- `CameraFollowController`
  - 保留现有随动主体，第一阶段为 browse mode 提供最小入口。
- cluster renderer 配置逻辑
  - 改为由 policy 控制，而不是 view controller 里直接写死。

## 兼容未来变化的原因

这套抽象能应对未来这些变化：

- 新增地图展示模式
- 调整 PowerSaver 展示范围
- 变更同屏 marker 上限
- 不同国家/城市密度差异化策略
- 新的 marker 样式要求
- 跟随、概览、browse 模式并行演进

## 不建议的做法

- 继续在 `MapInnerFragment` / `MapViewController` 中新增大量 `if powerSaver`
- 在 renderer 里直接写产品规则
- 把 marker 筛选、相机行为、cluster 决策揉在一个类里

## 当前结论

这个需求适合做“中等规模的结构化演进”，不是大重构，也不适合继续局部打补丁。
