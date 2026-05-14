# Power Saver Map Browse Delivery Plan

## 目标

以低风险、可验证的方式交付 `PowerSaver Map Browse Mode`，避免一次性大改引入地图回归。

## 迭代原则

1. 先把模式边界建立起来，再优化体验细节。
2. 先保证 Android / iOS 产品模型一致，再细调局部实现。
3. 每一阶段都必须可单独验证和可回退。

## Iteration 1

### 目标

建立 `PowerSaver Map Browse Mode` 的最小可用版本。

### 范围

- 新增 `MapDisplayMode`
- 新增 `MapDisplayModeResolver`
- 新增 `MapExperienceCoordinator`
- 新增 `PowerSaverBrowseParcelPresentationPolicy`
- 新增 `PowerSaverBrowseClusterPolicy`
- PowerSaver 下切换到 browse mode
- 默认更大范围地图视野
- 关闭默认聚合
- 同屏 marker 上限控制

### 不做

- 新 marker 视觉体系
- 精细 focus 高亮
- 更复杂的密集区域自适应

### 验收

- PowerSaver 下展示模式明显区别于 realtime
- 同屏可直接看到一批包裹号
- 密集区域不会完全不可读

## Iteration 2

### 目标

优化 `PowerSaver Browse` 的可读性和认知效率。

### 范围

- 新增 `MarkerStylePolicy`
- browse mode marker 更小、更轻
- 当前 focus 包裹高亮
- 大件标记在 browse mode 保持可辨识
- 优化同屏 marker 排序优先级

### 验收

- 司机能更快识别当前最相关包裹
- marker 不再像 follow 模式一样过重

## Iteration 3

### 目标

优化低频位置更新下的相机稳定性和浏览体验。

### 范围

- 新增 `PowerSaverBrowseCameraBehavior`
- 更保守的低频 recenter
- 手势后恢复逻辑优化
- 位置更新迟滞场景下减少镜头干扰

### 验收

- PowerSaver 下地图不会频繁跳动
- 司机拖图后，系统不会强抢镜头

## 风险控制

### 风险 1：地图体验回归影响 realtime

控制：

- browse mode 逻辑仅挂在 `PowerSaver`
- follow 逻辑尽量不动
- 使用 display mode 隔离

### 风险 2：密集区域完全失控

控制：

- 同屏 marker 上限
- 明确 parcel presentation policy
- 分阶段推进

### 风险 3：两端行为逐渐分叉

控制：

- 文档先行
- 统一抽象名和职责边界
- 每阶段都做两端一致性检查

## 建议交付顺序

1. 文档和抽象设计定稿
2. Android Iteration 1
3. iOS Iteration 1
4. 两端对齐测试
5. 再进入 Iteration 2

## 回滚策略

- 任一阶段若效果不佳，可回滚到仅保留 `display mode` 壳结构，不启用 browse mode 入口。
- 不应让 PowerSaver browse 逻辑与 follow 核心逻辑深度缠绕，否则回滚成本会明显上升。
