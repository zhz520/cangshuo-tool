# PDF 工作台

`pdf_studio` / PDF / WEB，官网路径 `/tools/pdf_studio/`。Android 使用共享 WebView 与系统文档选择器，网页在专用 Worker 内处理文件；文件不上传到服务器。不引入 Android PDF 依赖，不需要账号或新的运行时权限。V34 只登记目录元数据；不存在云端 PDF 转换 API。

## 源码参考（2026-10-06）

| 来源 | 实际采用与适配 |
| --- | --- |
| [pdf-lib PDFDocument.ts](https://github.com/Hopding/pdf-lib/blob/v1.17.1/src/api/PDFDocument.ts) 的 load/copyPages/embedJpg/embedPng/save | 加载、按页复制、图片嵌入及序列化；新建输出文档，明确不保留文档级表单、书签、附件与签名。读取加密标记后拒绝处理，不尝试绕过密码。先查阅 master 在线源码，再对下载的 1.17.1 源码核对。 |
| [PDFPage.ts](https://github.com/Hopding/pdf-lib/blob/v1.17.1/src/api/PDFPage.ts) 的 getRotation/setRotation/drawImage | 在原页面旋转角度上累加并归一化，图片按比例居中；旋转保留全部页面，范围仅决定旋转目标。 |
| [JpegEmbedder.ts](https://github.com/Hopding/pdf-lib/blob/v1.17.1/src/core/embedders/JpegEmbedder.ts)、[png.ts](https://github.com/Hopding/pdf-lib/blob/v1.17.1/src/utils/png.ts) | 参考 JPEG SOF 尺寸和 PNG 解码的内存展开；嵌入前读取头部，检查像素预算并拒绝 APNG。 |

依赖固定 `pdf-lib@1.17.1`，使用 npm 包内 UMD 成品，MIT 授权放在 vendor。脚本 SHA-256 为 `0f9a5cad07941f0826586c94e089d89b918c46e5c17cf2d5a3c6f666e3bc694f`，回归检查核对该值；只从本站加载，不使用 CDN。UI 和边界代码为本项目实现，未复制参考仓库页面。

## 功能与边界

- 合并：选择 2–10 个 PDF，可上移、下移、删除文件，按展示顺序合并。
- 提取/拆分：单个 PDF，填写 `1-3,5,2`；按填写顺序生成一份 PDF，支持重复页码，空范围表示全部。不生成 ZIP 或每页独立文件。
- 旋转：单个 PDF，顺时针 90/180/270°；保留全部页，选中页在原角度上旋转。
- 图片转 PDF：静态 JPEG/PNG，一图一页，A4 24pt 留白或图片尺寸（96 DPI）；保持宽高比。EXIF 方向不自动应用，动画、WebP/HEIC 不支持。
- 最多 10 个文件，单个 8,000,000 字节，总输入 20,000,000 字节，PDF 总源页数/输出页数 ≤200，单图 ≤12,000,000 像素、边长 ≤12000，输出 ≤12,000,000 字节。
- 文件大小在读取前及 Worker 内复验，PNG/JPEG 尺寸在解码前检查。PDF 内嵌流/复杂对象仍由解析库处理，字节限制不等同于严格堆内存上限；大文件应减少页面与输入。
- 后台 Worker 30 秒超时，取消/清空/离开页面时终止 Worker，epoch 隔离旧结果。输入或选项变化立即丢弃旧输出，失败后可重试，不显示原始异常。
- 文件名按纯文本显示。中英 UI，系统英语自动选择 English，可手动切换。结果显示页数与大小，保存实际 PDF 字节。
- 加密文件拒绝；不保留数字签名有效性、交互表单、书签、附件，可能保留页面注释；不提供 OCR、PDF 渲染、密码恢复或文档安全净化。

## Android 与安全

唯一 code 在 ToolRegistry 注册，中英名称/说明与 V34 对齐。文件选择仅在 `pdf_studio` 的白名单来源开放，使用 ACTION_OPEN_DOCUMENT，不启用 JavaScript 接口或直接 file/content URL 导航；content URI 数量限制为 10，拒绝其他 scheme，退出页面回收回调。选择器结果由 WebView 的文件表单读取，实际 provider 行为留待发布设备验收。

PDF 导出走现有分层的 Decode UseCase → ViewModel → Repository → ACTION_CREATE_DOCUMENT。Base64 读取预算单独设为 PDF 12 MB；检查 `%PDF-` 与尾部 `%%EOF`（仅传输格式检查，不是安全扫描），原二维码仍使用 4 MB 上限。保存失败/取消尝试删除未完成文档，provider 可能不支持删除。网页 CSP 禁止联网请求、外部脚本、object 与表单提交，既有域名/HTTPS/错误/返回策略复用。

## 实际验证

`node scripts/check_web_pdf.cjs`：35/35 通过，覆盖真实生成/加载 PDF 的合并顺序、提取顺序/重复、相对旋转/角度归一、PNG 转 PDF、加密拒绝、损坏文件、页码/数量/字节/像素/APNG 预算、取消检查及依赖校验。Android 新增 3 项 PDF 导出测试；离线 `testDebugUnitTest assembleDebug lintDebug` 在 3 分 2 秒内成功。V34 运行记录另行补入。

本地 Server 与 Admin 镜像构建成功（Server 打包跳过测试）；本地栈重新启动并健康，`GET /tools` 返回 pdf_studio/WEB/PDF，工具页与 engine/worker/app/style/vendor 资源均返回 HTTP 200。V34 已由应用启动执行；未执行浏览器交互或 Android provider/文件选择/保存/进程生命周期实机验收，按用户要求延期到发布；未部署正式域名。
