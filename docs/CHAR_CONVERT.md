# 特殊字符转换

`char_convert`，TEXT/LOCAL，排序 85，V49；Compose → ViewModel → UseCase → Repository，完全离线，无新权限。可组合半/全角、Unicode 大小写、逐行空白合并、移除零宽字符和换行统一，结果复制/回填、清空和状态恢复。

源码查阅：2026-10-12，[niwaringo/moji](https://github.com/niwaringo/moji)（MIT）的 `src/core.convert.js` 与 `src/core.map.js`；采用 FF01–FF5E ↔ 21–7E 的范围偏移及全角空格处理，Kotlin 独立实现。未移植日语假名转换；转全角保留普通空格，转半角把 U+3000 转为空格。

边界：UI 输入最多 200,000 UTF-16 单元，过长保留原值并提示；大小写使用 Locale.ROOT，处理位于后台协程。零宽清理含 ZWJ/ZWNJ，可能改变连字或组合 emoji，需要这些内容时应关闭该选项；不冒充简繁转换或完整 Unicode 正规化。

实际测试覆盖全/半角、中文和 emoji 保留、大小写、逐行空白、零宽/BOM 与 CRLF/CR 统一；本批 Android 全量 350/350 测试、assembleDebug、lintDebug 通过（0 错误/81 警告）；线上目录验收在部署后更新。真机输入法、大字号与无障碍尚未验证。
