# 文本相似性

`text_similarity`，TEXT/LOCAL，排序 90，V49；Compose → ViewModel → UseCase → Repository，完全离线，无权限。同时展示编辑距离、归一相似度、字符集合 Jaccard、最长连续公共子串和公共前缀，支持忽略大小写/空白、交换、清空、复制摘要和状态恢复。

源码查阅：2026-10-12，[sindresorhus/leven index.js](https://github.com/sindresorhus/leven/blob/0c8c7f459c3bf29fcf4a0c679f7cced3c6c28a53/index.js)（MIT），采用短串优先、裁剪共同前后缀和单行动态规划；Kotlin 独立实现，另外三项指标为独立算法。不使用在线语言模型或传输用户文本。

边界：每段最多 3,000 UTF-16 单元，忽略大小写使用 Locale.ROOT；后台协程执行，最长子串 O(nm) 时间/O(m) 空间，编辑距离 O(nm) 时间/O(min(n,m)) 空间。指标按 UTF-16 单元计算，emoji 不按用户感知字素计数；相似度用于字符串比较，不能称作语义相似度、查重或抄袭结论。

实际测试覆盖经典距离、完全相同、集合交集/百分比、前缀/子串、忽略大小写空白及中文；本批 Android 全量 350/350 测试、assembleDebug、lintDebug 通过（0 错误/81 警告）；随 Zepp V50 候选一并通过隔离验收（268/268），并在生产只读 HTTPS 验收 59/59 中确认目录一致性。真机长文本响应、大字号与无障碍尚未验证。
