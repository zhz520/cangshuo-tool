# 数字转中文

`number_chinese`，TEXT/LOCAL，排序 80，V49；Compose → ViewModel → UseCase → Repository，完全离线，无权限。支持小写、大写和人民币金额，阿拉伯数字/中文双向及自动判断，十位口语选项、复制、清空和状态恢复。

源码查阅：2026-10-12，[cnwhy/nzh](https://github.com/cnwhy/nzh/tree/71f536cd4ef1cb37bc68cdc3a45b3ea61fcc7153)（BSD-2-Clause）的 `src/index.js`、`utils.js` 与中文大小写语言表；采用四位分组、零合并、十位与元/角/分规则。Kotlin 独立实现，逆向按分节与规范格式验证；不引入 JS 引擎或外部依赖。

边界：60 个 UTF-16 单元、24 位整数、一般小数最多 8 位，金额最多 2 位且不自动舍入；BigInteger 避免逆向溢出，最大组单位到「万亿亿」。支持常用繁体大写、两/〇和逐字数字；重复单位与非法负号拒绝，不接受科学计数法，不提供财税有效性证明。

实际测试覆盖中文基础分组、小数负数、大写/金额、逆向、自动方向、非法输入及 24 位大整数往返；本批 Android 全量 350/350 测试、assembleDebug、lintDebug 通过（0 错误/81 警告）；线上目录验收在部署后更新。真机输入法、长文本布局及无障碍尚未验证。
