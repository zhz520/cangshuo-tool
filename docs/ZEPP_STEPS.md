# 微信运动步数（Zepp Life 网页工具）

代码 `zepp_steps`，分类 LIFE，模式 WEB，排序 510，地址 `/tools/zepp_steps/`。V50 只登记元数据，不创建账号密码或第三方令牌表。App 复用现有远程 WEB 目录合并、来源白名单和 WebView，后台复用工具详情、状态开关与分类控制。

## 源码查阅与适配

实际查阅日期：2026-10-12；不是官方开放 API，协议改变后需重新验证。

| 实际阅读的源码 | 采用的思路与差异 |
| --- | --- |
| [TonyJiangWJ/mimotion util/zepp_helper.py](https://github.com/TonyJiangWJ/mimotion/blob/master/util/zepp_helper.py)，blob `be3e984f3cc1d2826032f2da154d852e56926513`；[util/aes_help.py](https://github.com/TonyJiangWJ/mimotion/blob/master/util/aes_help.py)，blob `3c152d33cee1f2e6c58bc9c738c2b73a30715c08`，Apache-2.0 | 阅读加密注册登录、客户端令牌交换、绑定设备选择及 band_data 提交。使用 Zepp Life 的 `com.xiaomi.hm.health`，独立以 Java HttpClient/JSON 重写；不复制上游凭据日志、定时任务、令牌续期或 URL 令牌请求。AES/CBC 协议材料只由服务端环境配置。 |
| [FTech6/Zepp_Sport_Robot main.py](https://github.com/FTech6/Zepp_Sport_Robot/blob/main/main.py) 的 `change_steps` | 查阅每日数据结构、1,440 分钟的零样本与 `stp.ttl`。独立构建 JSON，分钟样本为 4,320 零字节的 Base64，只提供总步数与时区，省去睡眠、心率、距离和卡路里。未确认复制许可，因此只作协议研究，不复制代码。最小载荷是否被真实账号接受仍需实测。 |
| [miloce/Zepp-Life-Steps 的登录接口](https://github.com/miloce/Zepp-Life-Steps)（登录源码 blob `89045df20bb36f677ad0c66812f981bb78c964eb`） | 比较重定向与 JSON 登录返回；其 Zepp App 标识与 Zepp Life 不同，未采用该 App 标识，也未复制无明确许可证的实现。 |

## 功能与边界

- Zepp Life 邮箱或中国大陆手机号（可加 `+86`）；邮箱域名规范为小写，密码保留原空格和特殊字符。暂不支持其他区号、小米账号或微信账号直接登录。
- 提交北京时间当天总步数，整数 1–50,000；8,000/10,000/15,000/20,000/30,000 预设、显示密码、授权复选框、清空账号、复制脱敏回执、中英文、系统深浅色、移动安全区和减少动画偏好。
- 先检查服务可用性，再开启提交。四阶段固定 HTTPS 主机：加密账号登录 → 令牌交换 → 查询绑定设备 → 上传当天数据。优先使用返回的手环/手表设备，无设备时使用兼容设备标识并在结果中提示，不自动绑定或解绑。
- 30 秒服务端总预算、5 秒连接预算、65,536 字节上游响应上限、4,096 字节请求上限、最多 4 个并发请求。步数必须是 JSON 整数，授权必须是布尔值 `true`，不接受字符串或小数的隐式转换。
- 每账号 60 秒冷却；Redis 中仅保存进程随机盐加 SHA-256 后的限流键和计数，当前单实例部署有效。随机盐随重启改变，多副本不共享账号冷却键；外层复用现有 IP 限流。Redis 不可用时沿用已有内存回退。
- 无后台自动任务、随机刷步或自动重试；没有历史补刷、微信排行榜查询和验证码绕过。停止等待只取消浏览器等待，不保证服务器未写入。
- 成功仅表示 Zepp 接口返回 `message=success`（有 `code` 时必须为整数 0）。回执永远 `wechatVerified=false`，不声称微信已同步；上传阶段超时、5xx 或损坏 JSON 显示「结果待确认」，先查第三方记录再重试。

## 数据与运维

网页使用本站脚本/样式和同源 API，无 CDN、第三方脚本或 JavaScript 桥；不写入 localStorage/sessionStorage/cookie。每次提交结束清空密码，离开页面清空账号密码；浏览器自身密码管理器行为不由本站保证。请求和第三方令牌只在当前进程内存短暂存在，不写入数据库或日志；Java 字符串不能保证立即擦除，不能宣称密码已从全部内存安全清零。

页面只在 HTTPS 或本机调试地址允许提交。第三方返回正文不透传，日志仅沿用现有 HTTP 状态/时长/计数。凭据请求不自动重定向、没有 Cookie 容器、不在出站 URL 放入密码或 Token。API 返回 `Cache-Control: no-store`；注册阶段只解析 Location，不跟随它。

部署环境配置 `ZEPP_ENABLED`、`ZEPP_LOGIN_KEY`、`ZEPP_LOGIN_IV`；后二者须各为 16 UTF-8 字节，从查阅的协议来源配置，不能提交到 Git 或客户端。缺配置时 capabilities 返回 `available=false`，提交返回 60006/503。后台「工具管理 → 微信运动步数」可切维护/关闭，服务端同样阻止执行；关闭所在分类也有效。

工具打开量沿用 App 的 `/tools/{code}/usage`，HTTP 请求/失败参与已有后台流量统计。独立浏览器打开不伪造 App 工具打开事件；没有额外保存账号或成功提交明细。

## 实际验证

2026-10-12：Server Maven 全量 **170/170** 测试通过，打包成功；其中 Zepp **17** 项覆盖四阶段请求、密码特殊字符、AES 往返、北京时间跨日、设备优先级、严格 JSON、响应预算、限流无重试、上传超时分类、输入校验、请求预算、脱敏、后台开关、并发上限及异常释放。Windows 采用已有 Byte Buddy agent 和 `forkCount=0` 避免沙箱进程附加限制，生产 Docker 不使用该测试选项。

`node --test scripts/check_web_zepp.cjs` **5/5** 通过，校验账号/密码/整数/授权边界、能力契约、日历日期、精确步数回执和固定错误消息；`node --check`、`git diff --check` 通过。部署、浏览器和隔离数据库检查结果在执行后补充。

**未提供真实 Zepp Life 测试账号，因此加密登录、最小 band_data 载荷被真实账号接受及微信运动实际同步仍未验证。** 自动测试使用可控的第三方响应，不能替代这项验收。
