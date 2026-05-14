# Power Saver Map Browse Test Plan

## 测试目标

验证 `PowerSaver Map Browse Mode` 在低频定位场景下是否真正提升司机地图可读性，同时不破坏现有 realtime 跟随模式。

## 测试范围

- Android
- iOS
- PowerSaver
- realtime
- 稀疏区域
- 中密度区域
- 高密度区域

## 核心验证维度

### 1. 模式切换正确性

- realtime 下保持现有跟随语义
- PowerSaver 下进入 browse mode
- 两种模式切换后地图行为明显可区分

### 2. 地图可读性

- PowerSaver 下地图视野明显更大
- 司机可直接读到一批包裹号
- 不出现默认 cluster 把包裹全部聚合起来的情况

### 3. 同屏复杂度控制

- 密集区域下 marker 数量受控
- 不会出现一屏大量 marker 完全重叠不可读

### 4. 相机稳定性

- PowerSaver 下位置更新低频时，地图不会像 realtime 一样频繁自动跟随
- 手动拖图后，系统不会过快强抢镜头

### 5. 两端一致性

- Android / iOS 的：
  - 进入条件
  - 默认视野级别
  - 聚合行为
  - 同屏 marker 上限语义
  - 手势优先级
  保持一致

## 场景用例

### Case 1：PowerSaver，普通住宅区，低密度

- 位置更新较慢
- 地图展示 `2km - 3km` 范围
- 司机能直接看见多个包裹号

### Case 2：PowerSaver，城市普通街区，中密度

- 同屏 marker 有上限控制
- 不聚合，但也不乱到不可读

### Case 3：PowerSaver，downtown 高密度

- 不应简单全量展开所有包裹
- 必须验证筛选策略生效

### Case 4：realtime 驾驶跟随

- 行为与当前版本一致
- 不应被 browse mode 新逻辑污染

### Case 5：PowerSaver 下手势拖图

- 用户拖动地图后，地图不应立即跳回自动跟随

### Case 6：PowerSaver 与 realtime 往返切换

- 模式切换后地图行为恢复正确
- 不遗留错误 cluster 状态、错误 marker 样式或错误 zoom

## 验收指标

### 产品指标

- 司机在 PowerSaver 下能更快判断附近包裹分布
- 地图主观可读性明显优于当前 PowerSaver 行为

### 工程指标

- realtime 模式无明显回归
- Android / iOS 一致性通过人工对齐检查

## 回归重点

- 当前地图跟随
- 接近投递焦点逻辑
- cluster 点开列表
- 大件 marker
- 静止 / 驾驶切换
- 用户拖图暂停自动跟随

## 测试建议

- 真机优先
- 低密度与高密度路线都要测
- 建议录屏对比：
  - 当前版本 PowerSaver
  - 新版本 PowerSaver Browse
  - 新版本 realtime
