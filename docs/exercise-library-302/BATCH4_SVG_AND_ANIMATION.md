# 第四批检查报告：本地 SVG 与三帧动画

- `ExerciseMediaMapping` 改为从 302 条种子生成映射：UUID → slug/assetPath/attribution，全部本地 `exercises/<category>/<slug>/frame-N.svg`。
- 列表卡片只加载首帧；详情页 `produceState` 驱动 1→2→3→2，每帧 420ms，离开页面自动取消。
- 某帧加载失败先退回首帧；首帧仍失败才走默认图标，不让单张坏图拖垮列表。
- 静态校验：302 条映射路径唯一、无 http 开头；76 条 Everkinetic 署名未泛化；状态不是「已审核」。
- 未做：Android 真机渲染（本机安全软件仍阻挡构建，需云端出包后验收）。
