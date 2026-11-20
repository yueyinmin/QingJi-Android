# 第六批检查报告：备份兼容与测试

- 备份 JSON 导出/解析新增 8 个字段：englishName、slug、exerciseType、isStretch、assetPath、attribution；旧备份缺字段一律给默认值。
- 恢复逻辑不再 `clearAll()` 动作表：只合并用户自建动作（同名加「（自建N）」变式）与内置动作收藏标记；训练 5 表仍按备份整包恢复。
- 新增/更新测试：Room 11→12 迁移；302 条种子与导入幂等；收藏/自建/同名冲突；本地素材映射；旧备份恢复不清空新库。
- 版本升到 1.3.0 / versionCode 122，更新日志已同步。
- 待验收：云端 Debug/Release 编译 + 单测；`schemas/12.json` 由云端生成后回写；真机安装与动画验收。
