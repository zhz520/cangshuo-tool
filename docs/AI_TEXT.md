# AI 文本助手

`ai_text` / AI / SERVER，需要用户登录。支持摘要（SUMMARIZE）、同语言改写（REWRITE）和中英翻译（TRANSLATE，targetLanguage=zh/en）。服务端兼容 Chat Completions，一次请求一次文本结果，默认关闭，地址、模型、密钥由部署环境提供。V35 新增每日尝试计数并登记工具，不存输入/输出原文。

## 参考（2026-10-06）

- [OpenAI Chat Completions 请求/响应](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)：实际核对 messages、model、stream、max_completion_tokens、choices.message.content、finish_reason 和 usage。采用非流式单结果协议，默认令牌参数 max_completion_tokens；旧兼容服务可显式配置 max_tokens，不自动降级/重发。
- [Spring AI OpenAiChatModel.java](https://github.com/spring-projects/spring-ai/blob/main/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java)：阅读请求构建、choices/usage/finish_reason 映射及流取消。main 链接可变化；本项目不引入 Spring AI/供应商 SDK，不复用它的提示日志或工具执行循环，使用 Java 21 HttpClient 和现有 Jackson 实现有界文本协议。

## 配置与 API

服务端配置：AI_ENABLED=false，AI_ENDPOINT（完整 HTTPS `/chat/completions` URL）、AI_API_KEY、AI_MODEL、AI_PROVIDER_NAME（界面展示的服务商名称）。还包括 AI_TOKEN_PARAMETER、AI_MAX_OUTPUT_TOKENS=1024（64–4096）、AI_TIMEOUT_SECONDS=30（1–60）、AI_DAILY_ATTEMPTS=20（1–1000）、AI_MAX_CONCURRENT=4（1–32）。启用时校验地址/无凭据/无查询参数、模型和密钥；配置对象日志脱敏。环境模板不包含实际密钥。

`GET /api/v1/tools/ai-text/status`：用户 Bearer JWT，data={enabled,providerName,model,dailyLimit,usedToday,maxInputChars}，不公开密钥或上游地址。

`POST /api/v1/tools/ai-text`：用户 Bearer JWT，请求 `{task,text,targetLanguage}`，返回 data={text,model,truncated,inputTokens,outputTokens}。输出令牌统计允许 null，不伪造为零；finish_reason=length 显式标识 truncated。只接受 stop/length 与单个 assistant 文本；工具调用、空值/拒绝内容等不当作成功。

请求 JSON 读取前限制 65,536 字节；正文 1–8000 UTF-16 字符，可包含换行/tab，拒绝其他控制字符。不允许客户端指定上游 URL、模型、系统提示或密钥。输出最多 16,000 字符，上游响应最多 262,144 字节（读取过程中超限取消）。HTTPS 无重定向，连接 8 秒，整次异步调用含正文读取有超时并取消 future；失败不重试，避免重复计费。

V35 ai_daily_usage 使用 `(user_id, usage_date UTC)` 主键，原子 UPDATE `attempts < limit` 保证多个实例并发不会超额。只有获得本实例并发槽后才扣次数，实际开始上游调用前预占一次；上游失败/超时仍计一次，防止失败循环绕过费用控制。旧计数保留待数据保留/清理策略统一处理；用户删除时级联。并发上限按实例，跨实例每日额度由 MySQL 统一保证。

错误：400/10001 输入；401/10002 登录；413/40001 请求体预算；429/40005 当日尝试用尽；429/10007 并发或上游限流；503/10008 关闭/不可用；502/50001 上游失败、超时、无效响应。既有全站限流仍生效，错误不传递上游正文/凭据/用户输入。

## 验证与当前范围

2026-10-06：Server `mvnw verify` 130/130 测试与打包成功（56 秒），包括配置/任务/关闭/额度/并发释放、响应/截断/usage、预算取消、重定向不重试、整次超时取消、拒绝与工具调用、严格 UTF-8/尾随 JSON。Android `testDebugUnitTest assembleDebug lintDebug` 206/206 通过（最终 47 秒），Lint 0 错误/30 警告；随后调整 AI 英语额度文案以消除其 plurals 提示，最终汇总门禁再核对。新回归覆盖同意、重复调用、取消、账号切换；发现并修正了协程接收者将 cancel 解析为取消账号监听的问题。

`python scripts/check_ai_api.py` 对本地真实服务/MySQL 12/12 通过：匿名 401、默认关闭 503 且不扣次数、输入和正文预算、响应无凭据、目录登记，以及 24 个并行条件 UPDATE 只有 12 次成功。QA 账号和计数已删除，V35 已执行。真实服务商尚未配置，未调用付费 API；本地默认关闭。

Android 工具入口、第三方处理同意、结果复制/分享与取消已接入，App 网络读取/整次上限 70 秒；输入和结果仅存在内存，账号切换清空，取消后上游可能仍处理并计次。显示次数在成功后加一，失败或取消可手动刷新服务状态，不自动重复生成。OCR 按用户要求暂不做，AI 不提供附件/图像/会话历史/自动工具执行；真实供应商、手机界面、剪贴板/分享及生产 TLS 验收未执行。
