# Base64 编解码器

唯一编码 `base64`，分类 `DEV`，模式 `LOCAL`，排序值 130。`Base64ToolDefinition` 注册到 ToolRegistry，首页、目录、搜索与推荐共用同一份定义；无需账号、网络或 Android 权限。服务端目录登记由 V7 迁移完成，本轮没有修改迁移、接口或依赖。

2026-10-04 本轮修复：解码结果改为“字节优先”。文本层使用严格 UTF-8 校验，非法字节不再静默替换为 `U+FFFD`；解码成功后始终提供无损 Hex 视图与复制；非法 UTF-8 时显示无效字节数和首个无效位置，文本输出、文本复制与互换被禁用，原始字节仍完整保留。

## 实际开源参考

查阅日期：2026-10-04。以下链接为当日取得的分支文件，不是固定提交；没有把项目名称或 README 当作完整业务源码参考。

| 来源与实际阅读范围 | 采用思路 | Android 适配与差异 |
| --- | --- | --- |
| [CyberChef `FromBase64.mjs`](https://github.com/gchq/CyberChef/blob/master/src/core/operations/FromBase64.mjs)（sha `292de1e2d9c256ecf867a6d9c17fe6a9631d320f`）：完整操作类 | 输出类型是 `byteArray` 而不是文本；选项为字母表、移除非法字符、严格模式；文本解释是后续独立步骤 | 采用“字节优先、文本单独解释”的结构；不引入自定义字母表，只保留标准与 URL-Safe 两种 |
| [CyberChef `Base64.mjs`](https://github.com/gchq/CyberChef/blob/master/src/core/lib/Base64.mjs)（sha `aeda98cfc096a169ccc0454926140a16d2c6fbca`）：`toBase64`、`fromBase64`、字母表选项 | 字母表长度校验、移除非字母字符、严格模式的 4n+1 长度与填充位置检查、默认返回原始字节 | 未复制其手写解码循环，使用 `java.util.Base64`；仅参考“解码输出字节、严格校验独立于文本解释”的设计 |
| [IT-Tools `base64-string-converter.vue`](https://github.com/CorentinTh/it-tools/blob/main/src/tools/base64-string-converter/base64-string-converter.vue)（sha `07c4c394d7a5e229a0ef23c471fbb537ad8b10c3`）：完整组件 | 双向卡片、URL-Safe 开关、输入校验与复制交互 | 该组件的校验只检查 Base64 形式，不检查解码后是否为合法 UTF-8；本项目补上严格 UTF-8 层并显示明确错误 |
| [IT-Tools `base64.ts`](https://github.com/CorentinTh/it-tools/blob/main/src/utils/base64.ts)（sha `44e59f41f5c261e54f92e9791ab04fc09dd1e295`）：`base64ToText`、`isValidBase64`、URL-Safe 转换 | 通过解码再编码比对判断 Base64 是否有效；URL-Safe 字符映射与填充处理 | 未采用重编码比对；保留项目原有的去空白与标准/URL-Safe 回退容错，并在文档写明边界 |
| [IT-Tools `base64-file-converter.vue`](https://github.com/CorentinTh/it-tools/blob/main/src/tools/base64-file-converter/base64-file-converter.vue)（sha `a489f9a1de61ab1bbbe5bf80dc822e5b95e07c44`）：完整组件 | Base64 按文件字节处理，支持预览与下载 | 文件模式不在本轮范围；该实现用于确认“字节语义”与“文本预览”应分层，本项目先提供 Hex 无损视图 |

没有复制第三方业务代码，也没有新增第三方许可证资产。

## 产品决策（2026-10-04）

Base64 是任意字节的编码，文本只是其中一种解释。本工具的数据契约如下：

| 情况 | 文本区 | Hex 区 | 复制与互换 |
| --- | --- | --- | --- |
| 输入不是合法 Base64 | 输入错误，不产生结果 | 不可用 | 无 |
| Base64 合法，字节是合法 UTF-8 | 显示文本 | 始终可用，完整无损 | 可复制文本或 Hex，可互换 |
| Base64 合法，字节不是合法 UTF-8 | 显示错误：无效字节数、首个无效位置；不显示 `�` | 始终可用，完整无损 | 只允许复制 Hex，不允许互换 |

- 字节层永远无损；文本层要么无损，要么明确报错，绝不输出替换字符。
- 有损替换不作为默认行为；本轮没有实现“允许替换”开关。若以后加入，必须默认关闭、显示警告，并且不能覆盖 Hex 的完整数据。

## 实现分层

```text
Base64Route / Base64Screen
  → Base64ViewModel
  → EncodeBase64UseCase / DecodeBase64UseCase
  → Base64Repository / LocalBase64Repository
  → java.util.Base64 + RFC 3629 UTF-8 校验 + Hex
```

- `Base64Result.Encoded` 表示编码结果；`Base64Result.Decoded` 携带 `text`（非法 UTF-8 时为 null）、`hex`、`byteCount`、`invalidUtf8Count` 与 `firstInvalidByteOffset`。
- 输入容错：先 `trim`，再去掉所有空白；主解码器失败时回退到相反的字母表（标准与 URL-Safe 互试），与原有行为一致。
- UTF-8 校验：逐字节按 RFC 3629 检查前导字节 `0xC2–0xF4`、继续字节 `0x80–0xBF`，拒绝过长编码、代理区码点与大于 `U+10FFFF` 的值；通过后再由 `CharsetDecoder` 的 `REPORT` 模式生成文本。
- Hex 使用大写连续字符，无分隔符，复制时是完整字节。

## 功能覆盖与规则

### 编码

- 任意文本 → UTF-8 字节 → 标准或 URL-Safe Base64；空输入为等待状态。
- URL-Safe 使用 `-` 与 `_`，标准模式使用 `+` 与 `/`；填充按 `java.util.Base64` 标准输出。

### 解码

- 合法 Base64 先得到字节，再生成 Hex 与 UTF-8 文本；无效 UTF-8 只影响文本区，不影响 Hex。
- 错误信息包含总字节数、无效 UTF-8 字节数和首个无效位置（1 起算，按字节）。
- Hex 预览最多 8000 个字符，超出时显示已显示/总长度提示；复制始终使用完整 Hex。
- 输入上限 100000 个 UTF-16 单元；对应解码字节最多约 75KB、Hex 最多约 150k 字符，处理在本地同步完成。

### 明确不支持

- 不做 Base64 → 文件下载、图片预览或文件保存。
- 不自动剥离 `data:...;base64,` 前缀。
- 不支持 GBK、Latin-1 等其他文本编码，也不自动猜测编码。
- 不支持自定义 Base64 字母表、流式大文件或微秒级性能承诺。

## 界面与交互

- 保留模式切换、URL-Safe 开关、输入卡与输出卡布局；解码模式的输出卡新增 Hex 区域。
- 非法 UTF-8 使用 `errorContainer` 卡片显示错误；文本复制按钮和互换按钮不出现，Hex 复制始终可用。
- Hex 使用等宽字体、可选择文本；浅色/深色主题沿用共享 token。
- 中英文资源完整，共 25 个 `base64_*` key。

## 服务端目录登记

`V7__seed_base64_tool.sql` 保持不变：code `base64`、分类 `DEV`、模式 `LOCAL`、排序 130、`ENABLED`、推荐位；关键词覆盖 Base64、编码、解码、url safe 等。本轮没有新增迁移、API、依赖或权限。

## 验证与验收

2026-10-04 验证记录：

| 检查 | 结果 | 能证明的范围 |
| --- | --- | --- |
| 离线 `:app:assembleDebug :app:lintDebug` | 完整构建 1 分 29 秒；修正文案后重跑 1 分 40 秒；收紧防御分支后最终增量构建 1 分 10 秒，BUILD SUCCESSFUL | 0 错误、24 条警告，数量与修复前一致；`lintReportDebug` 保持 UP-TO-DATE；Base64 源码与新增资源无 Lint 问题 |
| 中英资源 key | `base64_*` 各 25 个，集合一致、无重复、全部被引用 | 英语切换不会因缺 key 回退中文 |
| JBR 直接调用编译后的 Kotlin 类（临时程序已删除，最终代码复验） | 20/20 通过 | `/w==` → text=null、invalid=1、offset=0、hex=`FF`；`aGVsbG8=` → `hello`/`68656C6C6F`；`中文` 编码往返；URL-Safe 编码 `77-977-977-9` 与回退解码 `Hello-_`；过长编码 `C0 80` 计 2 个无效字节；孤立继续字节计 1 个；UTF-8 BOM 判定有效；`!!!!` 为非法输入；空白为空输入 |
| `git diff --check` | 通过 | 已跟踪差异没有空白错误 |

未执行：单元/仪器测试、Android 运行时样例、Preview 渲染、真机或模拟器、英语系统切换、剪贴板与超长输入验收。

建议人工样例（待设备执行）：

1. 输入 `/w==`：文本区显示 UTF-8 错误，字节数 1、无效字节 1、首个位置 1；Hex 显示 `FF`，可复制；没有 `�`，无文本复制与互换按钮。
2. 输入 `aGVsbG8=`：文本显示 `hello`，Hex 显示 `68656C6C6F`，两种复制都可用，可互换。
3. 输入 `5Lit5paH`：文本显示 `中文`，字节数 6。
4. 输入 `77u/`：文本可显示 BOM 字符，判定为合法 UTF-8；Hex 为 `EFBBBF`。
5. 开启 URL-Safe 输入 `77-977-977-9`：解码为三个 `�` 字面字符（合法 UTF-8），无效字节数为 0。
6. 输入 `!!!!`：提示 Base64 非法；输入空白：等待状态。

剩余风险：Hex 预览截断只影响显示，复制使用完整数据；UTF-8 无效字节按字节计数，截断序列会按实际无效字节数统计；超长输入、剪贴板与设备字体缩放仍需验收。
