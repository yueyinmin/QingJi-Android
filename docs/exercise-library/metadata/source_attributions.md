# 图片授权与署名清单说明

## Workout Guide 素材

- 视觉素材作者：Bryl Lim（https://bryllim.com）
- 视觉素材许可：CC BY-SA 4.0（https://creativecommons.org/licenses/by-sa/4.0/）
- 轻记所使用的每个动作帧，其原始 URL、slug、作者与许可见 `asset_licenses.csv`。
- 每个 slug 的逐帧上游来源及修改记录以 Workout Guide `packages/workout-guide/manifest.json` 为准。

## Everkinetic 上游来源

Workout Guide 的 README/ATTRIBUTION.md 说明，全库 302 个动作中有 76 个动作的第一帧是 Everkinetic CC BY-SA 4.0 素材的改编版本，修改包括透明画布栅格化、单色重着色和矢量描摹。只有 manifest 中对应帧明确包含 `source` 字段时，才应追加 Everkinetic 署名；不可把 Everkinetic 署名错误地附加到其余所有动作或第 2/3 帧。

- 上游项目：https://github.com/everkinetic/data
- 逐帧来源与修改记录：https://github.com/bryllim/workout-guide/blob/main/packages/workout-guide/manifest.json
- Workout Guide 素材许可说明：https://github.com/bryllim/workout-guide/blob/main/LICENSE-ASSETS

## 轻记分发时的署名建议

在应用内“关于 / 开源许可”列出 Bryl Lim、Workout Guide、CC BY-SA 4.0 许可链接与来源链接；对 manifest 标记为 Everkinetic 衍生的第一帧，再列出 Everkinetic 及其来源链接，并说明上游已记录的改编方式。若轻记对 SVG 做了额外改色、裁切或矢量修改，必须在 `asset_licenses.csv` 的 `changes_made_by_qingji` 中逐项记录，并按 CC BY-SA 4.0 分享改编素材。

注意：Workout Guide 的代码/文档是 MIT，视觉素材是 CC BY-SA 4.0；二者不可混用许可。
