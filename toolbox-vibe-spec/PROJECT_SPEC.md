# 沧烁工具箱：完整项目设计文档

> 文档用途：作为 Vibe Coding / AI Coding Agent 的长期项目规范（Single Source of Truth）。
>
> 本文档定义产品目标、功能范围、技术架构、数据模型、接口、Android 实现边界、后端实现边界、管理后台、部署、安全、测试和分阶段开发计划。

---

## 0. 项目基本信息

**项目名称**：沧烁工具箱

**项目形态**：Android App + REST API + MySQL + Redis + MinIO/对象存储 + Web 管理后台

**目标**：打造一个“搜索优先、本地优先、云端增强、可持续增加工具”的 Android 全能工具平台。

**核心产品公式**：

`搜索 → 找到工具 → 输入 → 执行 → 复制/保存/分享 → 历史/收藏 → 云同步`

### 0.1 核心原则

1. 本地工具优先：能在设备本地完成的操作默认不上传用户数据。
2. 云端能力可选：AI、OCR、在线查询、云处理等需要服务器能力的工具通过 API 提供。
3. 搜索优先：用户不必记住工具属于哪个分类。
4. 工具标准化：所有工具都遵循统一 ToolDefinition/ToolRegistry 协议。
5. 模块化：添加工具不能迫使开发者重写首页、收藏、历史、搜索等公共代码。
6. 后端可控：工具目录、开关、推荐位、排序、版本和参数可以由后台配置。
7. 安全最小化：按需申请权限，不申请无关权限；敏感操作默认本地完成。
8. 可测试：每个功能都有明确的输入、输出和验收标准。
9. AI 编码友好：任何新功能都必须先说明需求、文件范围、接口契约、数据库变化和验收标准。

---

# 1. 产品定位

沧烁工具箱不是“把很多页面堆在一起”的工具集合，而是一个统一的工具平台。

用户可能不知道“时间戳转换”这个名字，但他知道自己想做什么：

- “把这个日期转时间戳”
- “压缩图片”
- “扫描二维码”
- “JSON 排版”
- “算折扣”
- “看手机参数”
- “测一下网络延迟”

因此首页应围绕自然语言关键词、工具名称、别名和关键词检索来组织。

---

# 2. 目标用户

## 2.1 普通用户

需要快速完成日常小任务，例如计算、换算、二维码、图片处理、日期、计时等。

## 2.2 学生

常用计算、单位换算、文本处理、图片/PDF、随机数、时间工具等。

## 2.3 开发者与技术用户

JSON、编码/解码、Hash、UUID、正则、颜色、时间戳、网络诊断、设备信息等。

## 2.4 进阶用户

OCR、文件处理、批量图片、PDF、云端工具、AI 增强等。

---

# 3. 产品范围

## 3.1 MVP 必须包含

### 首页
- 搜索框
- 常用工具
- 最近使用
- 收藏工具
- 分类入口
- 工具推荐位
- 网络不可用时仍能打开本地工具

### 用户
- 匿名使用
- 注册/登录
- JWT 登录态
- Refresh Token
- 用户资料
- 多设备登录记录
- 云同步开关

### 工具
- 计算器
- 科学计算器
- 单位转换
- 百分比/折扣
- 日期/时间差
- Unix 时间戳
- UUID
- Base64
- URL Encode/Decode
- JSON 格式化/压缩
- 文本处理
- MD5/SHA-256
- 二维码扫描
- 二维码生成
- 图片压缩
- 图片缩放
- 图片格式转换
- 设备信息
- 存储信息
- 电池信息
- 指南针（设备支持时）
- 水平仪（传感器支持时）
- 秒表
- 倒计时
- 随机数
- Ping
- HTTP 状态查询
- 当前网络信息

### 云端
- 工具目录同步
- 推荐工具
- 公告
- 用户收藏同步
- 最近使用同步
- 用户设置同步

### 管理后台
- 管理员登录
- 工具管理
- 分类管理
- 推荐位管理
- 用户管理
- 公告管理
- 反馈管理
- 系统配置
- 操作日志

## 3.2 V1 后续扩展

- OCR
- 图片拼接
- 图片水印
- EXIF 查看/清除
- 图片转 PDF
- PDF 合并/拆分/旋转
- 本地扫描
- AI OCR
- AI 图片增强
- AI 抠图
- 云端翻译
- IP/DNS 查询
- DNS over HTTPS 查询
- 网站可用性检测
- 网络速度测试
- 文件 Hash
- 批量文件处理
- 桌面 Widget
- Quick Settings Tile
- App Shortcuts

## 3.3 暂不做

- 社交社区
- 复杂即时通讯
- 在线支付体系
- 动态执行未经审核的远程代码
- 直接下载并执行不受信任的 Android 插件 APK
- 与攻击、入侵、绕过认证相关的功能

---

# 4. 功能分类

建议使用以下稳定分类编码：

| code | 名称 | MVP |
|---|---|---|
| CALC | 计算 | ✅ |
| CONVERT | 转换 | ✅ |
| TEXT | 文本 | ✅ |
| DEV | 开发者 | ✅ |
| QR | 二维码/条码 | ✅ |
| IMAGE | 图片 | ✅ |
| PDF | PDF/文档 | V1 |
| DEVICE | 设备 | ✅ |
| SENSOR | 传感器 | ✅ |
| NETWORK | 网络 | ✅ |
| LIFE | 生活 | ✅ |
| AI | AI | V1 |
| OTHER | 其他 | ✅ |

---

# 5. 工具标准模型

所有工具必须具有以下元数据：

```text
code              唯一工具编码
name              工具名称
description       简短说明
categoryCode      分类
icon              图标资源名
keywords          搜索关键词
mode              LOCAL / SERVER / HYBRID
requiresLogin     是否登录后才能使用
requiredPermissions 所需 Android 权限（客户端声明，不由目录 API 下发）
status            ENABLED / DISABLED / MAINTENANCE
version           工具版本
sortOrder         排序
isFeatured        是否推荐
```

## 5.1 Android ToolDefinition

```kotlin
interface ToolDefinition {
    val metadata: ToolMetadata
    val code: String get() = metadata.code
    val name: String get() = metadata.name
    val description: String get() = metadata.description
    val category: ToolCategory get() = metadata.category
    val keywords: List<String> get() = metadata.keywords
    val mode: ToolMode get() = metadata.mode
    val requiredPermissions: List<String>
    val requiresLogin: Boolean get() = metadata.requiresLogin

    fun isAvailable(context: Context): Boolean

    @Composable
    fun Screen()
}
```

完整接口还从同一份 `metadata` 暴露图标、状态、版本、排序和推荐标记。`isAvailable` 检查设备能力；目录状态、登录及权限检查由工具打开流程分别处理。当前领域模型、字段映射和客户端执行边界见 [工具模型说明](../docs/TOOL_MODEL.md)。

## 5.2 ToolRegistry

```kotlin
object ToolRegistry {
    val tools: List<ToolDefinition> = listOf(
        CalculatorTool,
        ScientificCalculatorTool,
        UnitConverterTool,
        TimestampTool,
        JsonFormatterTool,
        Base64Tool,
        UrlCodecTool,
        HashTool,
        UuidTool,
        TextTool,
        QrScannerTool,
        QrGeneratorTool,
        ImageCompressTool,
        DeviceInfoTool,
        CompassTool,
        TimerTool,
        PingTool
    )
}
```

## 5.3 后端 ToolDefinition

服务端保存工具目录及配置；客户端保存具体执行实现。

服务端不能下发可执行代码，只下发数据和配置，例如：名称、描述、分类、开关、排序、推荐状态、关键词、版本、云端 API 能力等。

---

# 6. 系统架构

```text
┌──────────────────────────────────────────────┐
│                  Android App                  │
│ Kotlin + Compose + MVVM/Clean-ish            │
│                                               │
│ UI / ViewModel / UseCase / Repository         │
│                    │                          │
│          Local Tool Engine                    │
│                    │                          │
│        Room / DataStore / Sensors             │
└───────────────────┬───────────────────────────┘
                    │ HTTPS + JSON
                    ▼
┌──────────────────────────────────────────────┐
│               Spring Boot API                 │
│ Auth / Tool / User / Sync / File / Admin API │
│                    │                          │
│ Service / Domain / Repository                 │
└─────────┬──────────────┬──────────────┬───────┘
          │              │              │
          ▼              ▼              ▼
      MySQL 8.4       Redis 8.x      MinIO/OSS
       主数据            缓存          文件对象
          │
          ▼
┌──────────────────────────────────────────────┐
│                Web Admin                     │
│ Vue 3 + TypeScript + Element Plus             │
└──────────────────────────────────────────────┘
```

---

# 7. 技术栈

## 7.1 Android

- Kotlin
- Jetpack Compose
- Material 3
- Compose BOM
- Navigation Compose
- ViewModel
- Kotlin Coroutines + Flow
- Hilt
- Retrofit
- OkHttp
- Kotlinx Serialization 或 Moshi（二选一，项目内统一）
- Room
- DataStore
- CameraX
- Coil
- WorkManager

Android 16 对应 API 36；项目目标 SDK 使用 36。Compose 采用 BOM 管理组件版本，项目统一放到 `gradle/libs.versions.toml`，避免不同模块各自锁版本。Google 当前文档给出了 Compose BOM 作为统一版本管理方式的建议。

## 7.2 Android 最低版本

MVP 建议 `minSdk = 26`。

原因：减少过旧系统适配成本，同时保留较大的设备覆盖面。若实际目标市场需要更广覆盖，再单独评估 minSdk 下降。

## 7.3 后端

- Java 21 LTS
- Spring Boot 3.5.x（首版优先生态兼容；以后可升级 Spring Boot 4.x）
- Spring Web
- Spring Validation
- Spring Security
- JWT
- MyBatis
- MyBatis-Plus（若项目依赖与 Boot 版本验证通过，否则使用原生 MyBatis）
- Spring Data Redis
- MySQL Driver
- Flyway
- Micrometer
- Springdoc OpenAPI
- Testcontainers
- JUnit 5

Spring 官方当前列出的稳定版本包括 4.1.1、4.0.8、3.5.16 等；本项目首版固定在 3.5.x 线，主要目的是减少第三方依赖在新大版本切换期的兼容变量，而不是追求“最高版本号”。 citeturn479618search0turn479618search5

## 7.4 数据库

- MySQL 8.4 LTS
- InnoDB
- utf8mb4
- UTC 存储时间

MySQL 官方文档当前持续维护 8.4 系列，其 8.4 系列为 LTS 线，本项目以该系列为生产数据库基线。 citeturn479618search8turn479618search12

## 7.5 缓存

- Redis 8.x
- 推荐固定在一个经过验证的稳定小版本
- 用途：缓存、Refresh Token 黑名单/会话、限流、热点工具配置、临时验证码、任务状态

Redis 官方发布说明显示 8.10.0 于 2026 年 7 月进入 GA，因此可作为部署时的候选版本；实际生产版本必须锁定具体镜像 tag，不允许 `latest`。 citeturn479618search4turn479618search16

## 7.6 管理后台

- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia
- Vue Router
- Axios
- ECharts（数据统计需要时）

## 7.7 部署

- Docker / Docker Compose
- Nginx
- Linux
- GitHub Actions CI

---

# 8. Android 工程目录

```text
android/
├── app/
├── core/
│   ├── common/
│   ├── model/
│   ├── ui/
│   ├── navigation/
│   ├── network/
│   ├── database/
│   ├── datastore/
│   ├── permissions/
│   └── tool/
│
├── feature/
│   ├── home/
│   ├── search/
│   ├── favorites/
│   ├── recent/
│   ├── settings/
│   ├── auth/
│   ├── calculator/
│   ├── converter/
│   ├── text/
│   ├── developer/
│   ├── qrcode/
│   ├── image/
│   ├── device/
│   ├── sensor/
│   ├── networktools/
│   └── life/
│
└── gradle/
```

MVP 初期可以保持为多 feature package；当某模块显著增大后再拆成独立 Gradle Module，避免过早模块化。

---

# 9. Android 分层

```text
Composable Screen
       ↓
ViewModel
       ↓
UseCase
       ↓
Repository
   ↙        ↘
Local       Remote
Room       Retrofit
```

## 9.1 Repository 原则

- UI 不得直接访问 Retrofit。
- UI 不得直接操作 Room。
- 工具执行逻辑放到工具模块/UseCase。
- 网络错误转换成统一 AppError。
- 页面不能直接读取 SharedPreferences；统一使用 DataStore。

---

# 10. 首页设计

```text
┌──────────────────────────────┐
│ ToolBox                      │
│ 你要做什么？                 │
│ [ 搜索工具 / 功能 / 关键词 ] │
├──────────────────────────────┤
│ 常用                         │
│ [计算器] [扫码] [转换] [OCR] │
├──────────────────────────────┤
│ 最近使用                     │
│ JSON格式化 / 时间戳 / 图片压缩│
├──────────────────────────────┤
│ 分类                         │
│ 计算 转换 文本 开发 图片 ... │
├──────────────────────────────┤
│ 推荐工具                     │
│ ……                           │
└──────────────────────────────┘

底部：
首页 / 工具 / 收藏 / 我的
```

首页必须做到：

- 首屏不阻塞等待远程工具目录。
- 搜索可以在本地工具数据上立即工作。
- 后端配置异步刷新。
- 首次安装没有网络时仍可以使用基础工具。

---

# 11. 搜索系统

## 11.1 搜索对象

同时搜索：

- 工具名称
- 英文名称
- code
- 分类名称
- keywords
- description

## 11.2 搜索排序

优先级：

1. 完全匹配工具名
2. 工具名开头匹配
3. 关键词匹配
4. 描述匹配
5. 分类匹配
6. 最近使用权重
7. 收藏权重

MVP 不引入远程向量搜索；基础工具数量低于 500 时，本地字符串/模糊匹配足够。

---

# 12. 本地工具能力规范

以下工具必须默认支持离线：

## CALC
- 标准计算器
- 科学计算器
- 百分比
- 折扣
- 分摊
- 日期差

## CONVERT
- 长度
- 面积
- 体积
- 质量
- 温度
- 速度
- 数据容量
- 时间
- 压力
- 功率
- 能量
- 角度

## DEV
- JSON Formatter
- Base64
- URL Encode/Decode
- Unicode
- Hex
- UUID
- MD5
- SHA-1
- SHA-256
- 时间戳
- Regex Tester
- Color Converter

## TEXT
- 字符计数
- 单词计数
- 去除空格
- 去空行
- 行排序
- 行去重
- 大小写
- Trim
- 前后缀批量处理

## DEVICE
- 设备信息
- CPU/ABI
- 内存
- 存储
- 电池
- 屏幕
- 网络基础信息

## SENSOR
- 指南针
- 水平仪
- 加速度
- 陀螺仪
- 磁场
- 光线（设备支持时）

## LIFE
- 秒表
- 倒计时
- 随机数
- 随机字符串
- 日期计算

---

# 13. 图片工具规范

图片处理默认本地完成。

包括：

- 压缩
- 缩放
- 裁剪
- 旋转
- 翻转
- JPG/PNG/WEBP 转换
- 长图拼接
- 图片信息
- EXIF 查看
- EXIF 清除
- 水印

大文件必须流式处理或分块处理，避免把多个原图同时加载进内存造成 OOM。

---

# 14. 二维码工具规范

## 扫描

- CameraX 相机预览
- 识别 QR、常见条码格式
- 支持从相册选择图片识别
- 结果支持复制、分享、重新生成

## 生成

- URL
- 文本
- Wi-Fi
- 联系人
- 电话
- 邮箱
- 短信
- 日历事件

相机权限采用按需申请：只有打开扫描工具时请求 CAMERA。

---

# 15. 网络工具规范

网络工具定位为“网络诊断”，只做合法、用户主动操作的诊断能力。

MVP：

- 当前网络类型
- 当前 Wi-Fi 信息
- DNS 基础信息
- Ping
- HTTP 状态码检查
- 延迟统计
- 连通性检测

必须设置超时、取消、并发上限和服务端限流；禁止将接口设计成任意高频扫描/攻击平台。

---

# 16. 云端工具架构

云端工具统一通过：

```text
RemoteToolClient
      ↓
ToolService
      ↓
Provider / Engine
```

例如 OCR：

```text
Android
  ↓
POST /api/v1/tools/ocr
  ↓
OCR Service
  ↓
Third-party/local provider
  ↓
result
```

任何第三方 API Key 都只能存在服务端，禁止硬编码进 APK。

---

# 17. 用户系统

## 17.1 登录方式

MVP：

- 手机号/验证码 或 邮箱/密码，二选一作为第一登录方式
- JWT Access Token
- Refresh Token

推荐第一版实现邮箱 + 密码，后续增加手机验证码；原因是开发和测试成本更低。

## 17.2 匿名模式

用户不登录仍可：

- 使用本地工具
- 浏览工具列表
- 收藏本地工具（保存本机）
- 保存本地历史

登录后可：

- 云同步收藏
- 云同步最近使用
- 跨设备同步设置
- 使用需要账号的云端能力

---

# 18. Token 设计

Access Token：短生命周期，例如 15 分钟。

Refresh Token：较长生命周期，例如 30 天，可撤销。

服务端必须保存 Refresh Token 的哈希或会话标识，不保存可直接登录的明文长期 Token。

登出：

- 删除客户端 Token
- 服务端撤销 Refresh Session
- 可选加入 Redis 黑名单

---

# 19. 数据同步策略

## 19.1 同步对象

- 收藏
- 最近使用
- 工具排序
- 用户设置
- 云端历史（用户主动开启时）

## 19.2 冲突解决

MVP 采用 `updatedAt + deviceId` 的 Last Write Wins。

收藏特殊处理：

- add 和 remove 都带版本时间
- 服务端使用事件版本或 updatedAt 判断最新状态

## 19.3 同步接口

```text
POST /api/v1/sync/push
GET  /api/v1/sync/pull?cursor=...
```

响应包含 `nextCursor`。

---

# 20. MySQL 数据库

数据库名：`toolbox`

所有表：
- InnoDB
- utf8mb4
- bigint 主键
- created_at / updated_at
- 删除优先软删除时使用 deleted_at

## 20.1 sys_user

```sql
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NULL UNIQUE,
  email VARCHAR(128) NULL UNIQUE,
  password_hash VARCHAR(255) NULL,
  nickname VARCHAR(64) NOT NULL,
  avatar_url VARCHAR(500) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);
```

## 20.2 sys_user_device

字段：
- id
- user_id
- device_id
- device_name
- platform
- app_version
- last_active_at
- refresh_session_hash
- status
- created_at
- updated_at

## 20.3 tool_category

字段：
- id
- code
- name
- icon
- description
- sort_order
- status
- created_at
- updated_at

## 20.4 tool_definition

字段：
- id
- tool_code
- name
- description
- category_id
- icon
- keywords_json
- mode
- version
- status
- sort_order
- is_featured
- requires_login
- config_json
- created_at
- updated_at

## 20.5 user_favorite

联合唯一键：`user_id + tool_code`

## 20.6 user_recent

字段：
- user_id
- tool_code
- last_used_at
- use_count

联合唯一键：`user_id + tool_code`

## 20.7 user_setting

字段：
- user_id
- theme
- language
- grid_columns
- startup_page
- sync_enabled
- config_json

## 20.8 user_history

字段：
- id
- user_id
- tool_code
- input_summary
- result_summary
- content_hash
- device_id
- created_at

敏感/大体积原文不建议默认上传；本地历史优先使用 Room。

## 20.9 banner

字段：
- id
- title
- subtitle
- image_url
- action_type
- action_value
- start_at
- end_at
- sort_order
- status

## 20.10 announcement

字段：
- id
- title
- content
- publish_at
- status

## 20.11 feedback

字段：
- id
- user_id
- type
- content
- contact
- status
- created_at

## 20.12 admin_user

字段：
- id
- username
- password_hash
- nickname
- role_code
- status
- last_login_at
- created_at
- updated_at

## 20.13 admin_operation_log

字段：
- id
- admin_id
- module
- operation
- request_uri
- request_method
- ip
- result
- created_at

## 20.14 api_request_log

生产环境可按需开启；不要记录密码、Authorization、完整文件内容、完整用户敏感输入。

---

# 21. Redis Key 规范

统一前缀：`toolbox:`

```text
toolbox:user:session:{sessionId}
toolbox:refresh:{sessionId}
toolbox:login:rate:{ip}
toolbox:login:rate:{account}
toolbox:tool:list
 toolbox:tool:{code}
toolbox:category:list
toolbox:home:featured
toolbox:banner:active
 toolbox:rate:{userId}:{api}
```

禁止无 TTL 的临时 Key 无限增长。

---

# 22. REST API 规范

统一前缀：`/api/v1`

统一响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "..."
}
```

## 22.1 Auth

```text
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /auth/me
```

## 22.2 Tool

```text
GET /tools
GET /tools/{code}
GET /tools/search?q=...
GET /tools/featured
GET /categories
```

## 22.3 Favorite

```text
GET    /favorites
POST   /favorites/{toolCode}
DELETE /favorites/{toolCode}
```

## 22.4 Recent

```text
GET /recent
POST /recent/{toolCode}
DELETE /recent
```

## 22.5 Settings

```text
GET /settings
PUT /settings
```

## 22.6 Sync

```text
POST /sync/push
GET  /sync/pull
```

## 22.7 Feedback

```text
POST /feedback
GET  /feedback/my
```

## 22.8 Cloud Tools

```text
POST /tools/ocr
POST /tools/image/enhance
POST /tools/pdf/convert
POST /tools/translate
```

所有云端接口必须单独做：
- 超时
- 限流
- 文件大小限制
- MIME 检查
- 权限检查
- 统一错误码

---

# 23. 统一错误码

```text
0      成功
10000  通用错误
10001  参数错误
10002  未登录
10003  Token 无效
10004  Token 过期
10005  无权限
10006  资源不存在
10007  请求过于频繁
10008  服务暂不可用
20001  用户不存在
20002  用户已存在
20003  密码错误
30001  工具不存在
30002  工具已关闭
30003  工具维护中
40001  文件过大
40002  文件类型不支持
40003  文件解析失败
50001  第三方服务错误
```

---

# 24. 文件服务

不要把图片/PDF 二进制直接放 MySQL。

使用：

```text
Android
  ↓ multipart/form-data
Spring Boot
  ↓
MinIO / OSS
  ↓
返回 objectKey / URL
```

数据库只保存：

```text
object_key
file_name
content_type
size
sha256
user_id
created_at
expires_at
```

上传要求：

- 最大文件大小
- MIME 白名单
- 文件扩展名二次检查
- 文件名清洗
- SHA-256
- 病毒扫描（生产环境按需要接入）
- 临时文件自动清理

---

# 25. 管理后台

## 25.1 首页 Dashboard

统计：
- 用户总数
- 今日新增
- DAU/WAU
- 工具使用次数
- 热门工具
- API 调用量
- 错误率
- 云端工具消耗

## 25.2 工具管理

支持：
- 新增
- 编辑
- 上线
- 下线
- 维护
- 分类
- 排序
- 推荐
- 关键词
- 图标
- 版本
- 登录要求
- mode
- 配置 JSON

## 25.3 用户管理

支持：
- 搜索
- 查看
- 禁用/启用
- 登录记录
- 设备
- 最近使用
- 收藏

不得在管理员页面默认展示用户敏感原始工具输入。

## 25.4 公告

- 创建
- 发布
- 下线
- 定时展示

## 25.5 反馈

- 待处理
- 处理中
- 已解决
- 回复

---

# 26. 安全要求

## Android

- 所有 API HTTPS
- Token 不写入普通日志
- 敏感配置不进 Git
- 不内置第三方 API Secret
- 本地敏感数据使用 EncryptedSharedPreferences 或 Jetpack Security 能力（如依赖方案允许）
- WebView 默认禁用不必要能力
- 文件 URI 使用 FileProvider / SAF

## Server

- Spring Security
- BCrypt/Argon2 密码哈希
- JWT 签名密钥使用环境变量/Secret
- CORS 白名单
- Rate Limit
- 登录失败限制
- 参数校验
- SQL 参数化
- 文件上传白名单
- Actuator 仅暴露必要端点
- Swagger 非生产公开或增加认证

## Admin

- 管理员独立 Token
- 角色权限
- 操作日志
- 高风险操作二次确认

---

# 27. 日志规范

统一结构化日志：

```json
{
  "timestamp": "...",
  "level": "INFO",
  "traceId": "...",
  "userId": "...",
  "module": "tool",
  "operation": "getToolList",
  "durationMs": 35
}
```

禁止记录：
- 明文密码
- JWT
- Refresh Token
- API Secret
- 用户上传文件内容
- 用户隐私原文

---

# 28. 可观测性

MVP：
- Actuator health
- Micrometer metrics
- 请求耗时
- 错误率
- 数据库连接池监控
- Redis 连接监控

后续：
- Prometheus
- Grafana
- Loki/ELK
- OpenTelemetry

必须有：

```text
traceId
```

使 Android 报错可以与后端日志对应。

---

# 29. Android 错误状态

每个页面必须明确支持：

```text
Loading
Success
Empty
Error
Offline
PermissionDenied
Unsupported
```

例如指南针不支持：

> 当前设备没有磁力传感器，无法使用指南针。

而不是让页面崩溃。

---

# 30. 网络层

Retrofit API 不直接暴露给 ViewModel。

推荐：

```text
ApiService
  ↓
RemoteDataSource
  ↓
Repository
  ↓
UseCase
  ↓
ViewModel
```

统一处理：

- 401
- 403
- 404
- 429
- 500
- timeout
- offline

401 时仅允许有限次数的 token refresh，避免死循环。

---

# 31. 缓存策略

工具目录：

```text
Network First
↓
成功 → 更新 Room/内存缓存
失败 → 使用本地缓存
```

工具执行：

- LOCAL：不走 API
- SERVER：必须联网
- HYBRID：优先本地，失败后可选服务器

---

# 32. Room 本地数据库

表：

```text
favorite_tool
recent_tool
local_history
cached_tool
sync_queue
```

`sync_queue` 保存：

```text
operationId
entityType
entityId
operation
payload
createdAt
retryCount
status
```

同步必须幂等。

---

# 33. UI 设计规范

Material 3。

主题：
- System
- Light
- Dark
- AMOLED Black（可选）

首页支持：
- Grid
- List
- 2~5 列自适应

工具卡片必须统一：

```text
icon
name
description
favorite
```

所有工具页面优先使用：

```text
标题
输入区域
主要操作
结果区域
复制 / 保存 / 分享
```

不要为每个工具创造一套完全不同的 UI 语言。

---

# 34. 国际化

MVP：
- 简体中文
- English

所有用户可见文本不得硬编码进 Kotlin Composable。

统一使用 `strings.xml` / Compose stringResource。

数据库中的工具名称允许多语言字段或 JSON 国际化对象，第一版可用 `name_zh` + `name_en`，后续再演进为独立 i18n 表。

---

# 35. 会员体系

第一版不强制加入会员。

系统预留：

```text
free
pro
admin
```

工具定义可以配置：

```text
requiresPlan
rateLimit
dailyQuota
```

未来可扩展 AI 工具额度，而不需要修改基础 ToolRegistry。

---

# 36. 推荐系统

MVP 使用规则推荐：

```text
最近使用 + 收藏 + 工具使用次数 + 后台 isFeatured
```

不要在第一版引入机器学习推荐。

可以计算：

`score = recencyWeight + favoriteWeight + useCountWeight + featuredWeight`

这是内部排序算法，不作为对用户做敏感画像的依据。

---

# 37. API 分页

统一：

```json
{
  "page": 1,
  "pageSize": 20
}
```

列表响应：

```json
{
  "records": [],
  "page": 1,
  "pageSize": 20,
  "total": 100
}
```

如果后续数据规模明显增大，再将高频列表升级成 cursor 分页。

---

# 38. 后端包结构

```text
server/
└── src/main/java/com/example/toolbox
    ├── common/
    │   ├── response/
    │   ├── exception/
    │   ├── security/
    │   ├── logging/
    │   └── util/
    ├── auth/
    ├── user/
    ├── tool/
    ├── favorite/
    ├── recent/
    ├── sync/
    ├── file/
    ├── feedback/
    ├── announcement/
    └── admin/
```

每个业务模块：

```text
controller
service
mapper
entity
repository/model
```

复杂模块再增加 `domain`、`dto`、`assembler`。

---

# 39. Admin 工程目录

```text
admin/
├── src/
│   ├── api/
│   ├── views/
│   ├── components/
│   ├── stores/
│   ├── router/
│   ├── layouts/
│   └── utils/
├── package.json
└── vite.config.ts
```

路由：

```text
/login
/dashboard
/tools
/categories
/users
/announcements
/banners
/feedback
/system-config
/logs
```

---

# 40. Docker Compose

开发环境最少启动：

```text
mysql
redis
minio
server
admin
nginx（可选）
```

Android 通过本地开发服务器访问。

禁止提交真实密码；提交：

```text
.env.example
```

实际 `.env` 必须 `.gitignore`。

---

# 41. 环境配置

环境：

```text
local
staging
production
```

配置项示例：

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
JWT_SECRET
MINIO_ENDPOINT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
FILE_MAX_SIZE
CORS_ALLOWED_ORIGINS
```

---

# 42. 数据库迁移

使用 Flyway。

命名：

```text
V1__init.sql
V2__create_tool_tables.sql
V3__create_user_tables.sql
V4__create_feedback.sql
```

禁止手工修改已执行的 migration；修改使用新的 migration。

---

# 43. 测试要求

## Android 单元测试

必须测试：

- Calculator
- Converter
- JSON Formatter
- Base64
- URL Codec
- Hash
- Timestamp
- Search ranking
- Sync conflict

## Android UI Test

至少覆盖：

- 首页
- 搜索
- 打开工具
- 收藏/取消收藏
- 登录
- 错误状态

## Server

至少：

- Auth service
- Tool service
- Favorite API
- Sync API
- Permission check
- Error handler
- Rate limit

集成测试建议使用 Testcontainers 启动 MySQL/Redis。

---

# 44. 质量门禁

任何 Milestone 结束前必须：

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Server：

```text
./mvnw test
./mvnw verify
```

Admin：

```text
npm run typecheck
npm run build
```

不得以“功能代码写完了”作为完成标准，必须以测试/构建通过作为完成标准。

---

# 45. MVP 开发路线图

## Phase 0：基础设施

- [ ] 创建 monorepo
- [ ] Android 项目
- [ ] Spring Boot 项目
- [ ] Admin 项目
- [ ] Docker Compose
- [ ] MySQL/Flyway
- [ ] Redis
- [ ] CI
- [ ] `.env.example`
- [ ] README
- [ ] AGENTS.md

**退出条件：** 三端都可以启动；API healthcheck 通过；数据库 migration 可执行；CI 绿色。

## Phase 1：Walking Skeleton

- [ ] Android 首页
- [ ] Server `/tools`
- [ ] ToolRegistry
- [ ] 本地计算器
- [ ] 搜索
- [ ] 收藏本地
- [ ] 最近使用

**退出条件：** 从首页搜索“计算器”→打开→执行→返回→最近使用显示完整跑通。

## Phase 2：核心工具

- [ ] 单位转换
- [ ] 时间戳
- [ ] UUID
- [ ] Base64
- [ ] URL Codec
- [ ] Hash
- [ ] JSON
- [ ] 文本工具
- [ ] 二维码
- [ ] 图片压缩

**退出条件：** 核心工具离线可用，自动化测试通过。

## Phase 3：账号与同步

- [ ] 注册
- [ ] 登录
- [ ] JWT
- [ ] Refresh Token
- [ ] 用户资料
- [ ] 收藏同步
- [ ] 最近使用同步
- [ ] 设置同步

**退出条件：** 设备 A 收藏后，设备 B 登录同账号可以恢复收藏。

## Phase 4：设备与网络

- [ ] 设备信息
- [ ] 存储
- [ ] 电池
- [ ] 传感器
- [ ] 指南针
- [ ] 水平仪
- [ ] Ping
- [ ] HTTP Check

**退出条件：** 在不支持传感器的设备上优雅降级；网络异常不崩溃。

## Phase 5：后台

- [ ] Admin 登录
- [ ] 工具 CRUD
- [ ] 分类 CRUD
- [ ] 推荐位
- [ ] 用户管理
- [ ] 公告
- [ ] 反馈
- [ ] 操作日志

**退出条件：** 管理员能够关闭一个工具，Android 同步后该工具不再出现在可用列表。

## Phase 6：云端能力

- [ ] MinIO
- [ ] OCR
- [ ] PDF
- [ ] AI 工具接口
- [ ] 文件安全策略
- [ ] 配额
- [ ] 限流

## Phase 7：发布

- [ ] Crash 监控
- [ ] Release CI
- [ ] APK/AAB
- [ ] 隐私政策
- [ ] 权限说明
- [ ] 数据删除机制
- [ ] 生产备份
- [ ] 灰度发布

---

# 46. 具体首批工具清单

建议第一版控制在 35 个左右，而不是一次做 100 个。

```text
01 calculator
02 scientific_calculator
03 percentage
04 discount
05 date_difference
06 unit_converter
07 timestamp
08 uuid
09 base64
10 url_codec
11 unicode
12 hex_codec
13 md5
14 sha256
15 json_formatter
16 json_minifier
17 regex_tester
18 color_converter
19 text_counter
20 text_cleanup
21 line_deduplicate
22 line_sort
23 qr_scanner
24 qr_generator
25 image_compress
26 image_resize
27 image_convert
28 device_info
29 storage_info
30 battery_info
31 screen_info
32 compass
33 level
34 stopwatch
35 countdown
36 random_generator
37 ping
38 http_status
```

---

# 47. 每个工具的开发模板

任何新增工具必须创建：

```text
feature/<tool-name>/
├── <Tool>.kt
├── <Tool>ViewModel.kt（需要状态时）
├── <Tool>UseCase.kt（有业务逻辑时）
├── <Tool>Repository.kt（需要本地/远程数据时）
└── <Tool>Test.kt
```

简单纯函数工具可以只包含：

```text
Screen + pure function + test
```

不要为了看起来“架构完整”给每一个 30 行工具强行创建十几个类。

---

# 48. Git 分支与提交

分支：

```text
main
 develop
feature/*
fix/*
refactor/*
```

提交格式：

```text
feat: add json formatter
fix: handle token refresh loop
refactor: extract tool registry
chore: update dependencies
```

每完成一个小任务就提交，不要一次积累几十个功能再提交。

---

# 49. Vibe Coding / AI Agent 规则

AI Agent 开始任何编码前必须读取：

```text
PROJECT_SPEC.md
ARCHITECTURE.md
ROADMAP.md
AGENTS.md
```

然后：

1. 先定位现有代码。
2. 说明计划修改哪些文件。
3. 如果涉及数据库，先说明 migration。
4. 如果涉及 API，先说明 request/response。
5. 如果涉及 UI，先说明状态和页面流。
6. 实现一个最小增量。
7. 运行测试/编译。
8. 汇报验证结果。
9. 更新 TODO。

禁止：

- 一次重构全项目
- 没看现有代码就创建重复类
- 为了修小 Bug 换核心框架
- 自动升级全部依赖
- 修改数据库却不生成 migration
- API 改字段却不更新客户端
- 只写代码不运行测试

---

# 50. AGENTS.md 建议内容

```text
# 沧烁工具箱 AI Coding Rules

## Before coding
- Read PROJECT_SPEC.md
- Read ARCHITECTURE.md
- Read ROADMAP.md
- Inspect existing code before creating files

## Architecture
- Android: Compose + ViewModel + UseCase + Repository
- Server: Controller + Service + Mapper/Repository
- No UI -> Retrofit direct calls
- No API secrets in Android

## Tool development
- Every tool has a unique code
- Add tool metadata
- Register in ToolRegistry
- Add tests
- Support Error/Empty/Unsupported when applicable

## Backend
- Every schema change requires Flyway migration
- Every API change updates API docs and Android models
- Validate all request parameters
- Do not log tokens/passwords/user raw content

## Security
- HTTPS only in staging/production
- Passwords hashed
- Secrets from environment
- File whitelist and size limit
- Rate limits on auth and expensive APIs

## Quality
- Run tests after each milestone
- Do not claim completion without successful verification
- Keep diffs focused
```

---

# 51. AI Agent 首次启动 Prompt

将下面内容交给 Vibe Coding Agent：

```text
你现在是“沧烁工具箱”项目的主工程师。

请先阅读仓库中的：
1. PROJECT_SPEC.md
2. ARCHITECTURE.md
3. ROADMAP.md
4. AGENTS.md

不要马上大量生成代码。

第一步只完成项目侦察：
- 当前仓库有哪些文件
- Android/Server/Admin 是否已经存在
- 目前哪些模块可以复用
- 缺少哪些基础设施
- 是否存在架构冲突

然后输出：
1. 当前代码结构摘要
2. 与项目规范不一致的地方
3. Phase 0 的实施计划
4. 需要创建/修改的文件清单
5. 预计数据库 migration
6. 需要执行的验证命令

在完成上述分析前，不要重构项目，也不要新增大量业务功能。
```

---

# 52. AI Agent 单功能 Prompt 模板

```text
实现 ROADMAP.md 中的下一个未完成任务。

要求：
1. 先读取 PROJECT_SPEC.md、ARCHITECTURE.md、AGENTS.md、ROADMAP.md。
2. 检查现有实现，避免重复代码。
3. 只实现当前任务，不顺手重构无关模块。
4. 涉及数据库时创建 Flyway migration。
5. 涉及 API 时同时更新接口 DTO、Controller、Service、测试和 Android API model。
6. 涉及 Android 工具时实现 ToolDefinition、注册 ToolRegistry、页面、状态处理和单元测试。
7. 完成后执行相关测试和构建。
8. 最终报告：修改文件、核心实现、测试结果、未完成事项。
```

---

# 53. 验收标准总表

## 产品

- 用户可在 3 次操作内打开常用工具
- 搜索可找到别名工具
- 无网络也可使用本地工具
- 工具执行结果可复制/分享
- 用户可收藏工具
- 登录后可同步收藏

## Android

- 无明显启动崩溃
- 所有页面具备错误状态
- 不支持的传感器不会崩溃
- 相机等权限按需申请
- 大图处理不产生频繁 OOM

## Server

- API 有统一响应结构
- JWT 过期可刷新
- Refresh Token 可撤销
- 参数有校验
- 登录有速率限制
- 数据库有 migration
- 日志不泄露敏感数据

## Admin

- 可以管理工具状态
- 可以管理分类
- 可以管理推荐
- 可以查看用户
- 可以处理反馈
- 所有重要操作可追踪

## DevOps

- Docker Compose 一键启动开发依赖
- CI 自动执行测试和构建
- production 不使用 latest 镜像
- 生产数据库有备份方案

---

# 54. 未来扩展方向

## 工具平台

工具不再只是内置代码，而成为：

```text
Tool Metadata
+
Local Executor
+
Remote Executor
+
Permission Model
+
Quota Model
+
Analytics
```

## AI 助手入口

未来首页可以增加：

```text
“告诉我你想完成什么”
```

例如：

```text
“我有 10 张照片，想压缩成一张长图”
```

AI 不直接执行任意代码，而是进行工具编排：

```text
理解需求
 ↓
选择受信任工具
 ↓
请求用户确认必要操作
 ↓
执行工具链
 ↓
返回结果
```

## Tool Workflow

后续可以支持：

```text
图片
 ↓
压缩
 ↓
加水印
 ↓
转 PDF
 ↓
分享
```

把单一工具升级成工作流平台。

---

# 55. 最终产品形态

最终目标不是“一个有 100 个工具的 App”，而是：

> **一个可搜索、可组合、本地优先、云端增强、可配置、可同步的 Android 工具平台。**

其核心资产是：

```text
ToolRegistry
ToolDefinition
Tool Metadata
Search
Favorite
History
Sync
Cloud API
Admin Console
```

只要这套基础能力稳定，未来增加工具时不需要重新设计整个产品。

---

# 56. 当前版本决策记录

### Decision 001
**Android 使用 Compose。**

理由：统一 UI、声明式页面、适合工具卡片和状态型页面。

### Decision 002
**本地工具和云端工具并存。**

理由：减少网络依赖，同时允许接入 OCR/AI/PDF 等复杂服务。

### Decision 003
**MySQL 保存业务主数据，文件进入 MinIO/OSS。**

理由：数据库不承担大文件存储压力。

### Decision 004
**Redis 用于缓存和临时状态，不作为最终业务数据源。**

### Decision 005
**服务端下发数据和配置，不下发未经审核的可执行代码。**

### Decision 006
**MVP 不做真正的动态 APK 插件市场。**

理由：插件签名、权限、审核、版本兼容和安全会显著增加复杂度。

### Decision 007
**第一版工具数量控制在约 35~40 个。**

理由：优先验证平台能力，而非堆数量。

### Decision 008
**Android 构建版本按现有 Android Studio 的兼容范围固定。**

当前使用 AGP 9.1.0、Gradle 9.3.1、AGP 内置 Kotlin 2.2.10 与同版本 Compose compiler plugin、Compose BOM 2026.06.01。`compileSdk` 和 `targetSdk` 均为 36，`minSdk` 为 26；构建建议使用 Android Studio 自带的 JDK 21。版本在 `android/gradle/libs.versions.toml` 和 Wrapper 配置中统一管理，升级前同时核对 IDE、Gradle、SDK 与库的最低要求。

### Decision 009
**服务端启动壳固定 Java 21、Spring Boot 3.5.16、Maven 3.9.11 和 Springdoc 2.8.17。**

依赖由 Spring Boot BOM 管理，Maven Wrapper 固定并校验 Maven 版本。健康检查提供 `/api/v1/health` 与 `/actuator/health`；API 使用统一响应、错误码和 traceId。机器可读接口文档使用 OpenAPI 3.1，仅在显式启用 `local` profile 时开放。数据库、缓存及 JWT 按各自阶段接入。3.5.x 遵循既有规格，生产上线前另行评估该版本线的支持策略。

---

### Decision 010
**Admin 启动壳固定 Vue 3.5.43、TypeScript 5.9.3、Vite 8.3.2、Element Plus 2.14.7、Pinia 4.0.3、Vue Router 5.3.1 和 Axios 1.20.0。**

Node.js 要求 `^20.19.0 || >=22.12.0`，依赖兼容信息已核对；使用 npm 锁文件和严格 TypeScript 检查。页面请求经过集中 API 模块与 Pinia 状态管理，概览只展示实际健康检查结果。开发代理监听本机地址，生产反向代理在部署任务中配置。管理员认证及业务管理功能遵循 Phase 5。

### Decision 011
**正式环境使用 `tool.zhzgo.cn` 提供官网和管理后台，`toolapi.zhzgo.cn` 提供 API。**

用户于 2026-10-03 确定域名。官网使用 `https://tool.zhzgo.cn/`，后台默认规划为 `https://tool.zhzgo.cn/admin/`，API 基础地址为 `https://toolapi.zhzgo.cn/api/v1`。Android 与正式环境后台使用该 API 地址；部署任务实现 HTTPS、后台子路径与 SPA 回退、API 跨域白名单。配置要求见 [部署说明](../deploy/README.md#正式环境域名约定)。正式站点尚未部署。

### Decision 012
**Compose 使用共用配置加本地/正式覆盖文件，Admin 镜像同时承载 Nginx 网关与基础官网入口。**

本地和正式环境使用独立项目名、镜像名、网络及数据卷。MySQL、Redis、API 按健康检查依次就绪；生产只映射 Nginx 的 80/443。凭据由初始化脚本随机生成并写入被忽略的私有环境文件。固定镜像版本见部署说明；供 Linux 容器执行的 Shell 脚本通过 Git 属性保持 LF。

MinIO 社区仓库已归档，作为开发可选的 `storage` profile；正式对象存储在 Phase 6 评估。2026-10-03 实际拉取发现官方预构建镜像不可用，历史二进制下载返回 `410`，因此开发镜像改从官方 `RELEASE.2025-10-15T17-29-55Z` 源码构建，校验提交 `9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a`，并固定构建/运行基础镜像摘要。依据及版本见部署说明。

Linux 镜像构建及本地五服务编排已通过，全部容器健康，官网/后台/API 路由与浏览器健康概览正常。本地覆盖文件增加 `local-access` 网络，使开发数据库、缓存和对象存储端口发布生效；生产保持内部网络。数据库与 Flyway 接入见 Decision 013，Redis 客户端接入见 Decision 014，对象存储业务遵循 Phase 6；生产 DNS、真实证书及公网部署仍待完成。

### Decision 013
**Phase 0 使用 JDBC/HikariCP 与 Flyway 建立工具目录持久化基础，其他业务表随对应功能新增迁移。**

服务端连接 MySQL `8.4.11`，Connector/J `9.7.0` 与 HikariCP `6.3.3` 沿用 Boot BOM。Flyway core/MySQL 模块统一固定 `11.20.3`；Boot 默认的 `11.7.2` 实际迁移成功但提示 MySQL `8.4` 超出其版本检查范围，新版本启动和现有迁移校验已通过。

`V1` 创建 `tool_category` 与 `tool_definition`，使用 InnoDB、utf8mb4、UTC 时间、唯一编码、索引及数据约束；`V2` 初始化规格中的 13 个分类。工具记录、目录接口和其他业务表在对应路线图任务中实现。已执行的迁移保持原内容，禁止 clean、自动 baseline 与乱序迁移。数据库连接状态加入整体健康检查，响应字段保持原契约。

显式 `local` profile 可从 `server/` 工作目录导入根目录私有 `.env`；Compose 使用注入的数据库环境变量，默认要求 TLS。Docker 与 Windows 本地构建、启动、迁移记录及健康检查已核对；未建立或运行自动化测试。结构、配置和运行记录见 [数据库说明](../docs/DATABASE.md)。

### Decision 014
**Phase 0 使用 Boot 自动配置的 Spring Data Redis/Lettuce，业务缓存随对应功能实现。**

Spring Data Redis `3.5.13` 与 Lettuce `6.6.0.RELEASE` 使用 Boot `3.5.16` BOM；容器沿用 Redis `8.8.3-alpine3.23`。凭据从环境读取，本机 `local` profile 复用私有 `.env` 与开发映射端口。连接及命令超时均为 2 秒，使用共享连接，关闭连接池和 Redis Repository 扫描。

Redis 状态加入现有 Actuator 与业务健康检查，公开响应不包含组件明细。Controller 通过 Service/Repository 使用 Redis，后续业务采用 StringRedisTemplate 与明确 DTO 的 JSON；统一 `toolbox:` Key 前缀，临时值必须通过原子写入设置正数 TTL。目录缓存、登录会话和限流在各自阶段实现，MySQL 保持业务主数据源。

Docker 与 Windows 构建、启动、认证应用连接及健康接口已核对，迁移校验值和分类数据保持不变；未建立或运行自动化测试。配置和记录见 [Redis 说明](../docs/REDIS.md)。

### Decision 015
**Phase 0 使用 GitHub Actions 并行执行三端构建与部署配置校验，再通过一个 CI 汇总门禁。**

工作流使用 `ubuntu-24.04`、JDK 21、现有 Gradle/Maven Wrapper 和 Node `22.23.3`。Android 构建 Debug APK 并执行 Lint，Server 打包，Admin 按锁文件安装并构建正式地址与 `/admin/` 路径，部署任务执行 actionlint、Shell 语法及两套 Compose 配置校验。Action 固定完整提交 SHA，Wrapper 和 actionlint 下载校验 SHA-256，构建产物保留 7 天。

工作流只申请只读仓库权限，不保留 checkout 凭据；配置校验使用公开占位值，当前不需要部署 Secret，也不执行部署。此阶段覆盖构建与静态检查，Server 显式跳过测试；测试及发布门禁随对应任务补充。本地门禁已通过，用户于 2026-10-03 指定 `zhz520/cangshuo-tool` 为远程仓库，初始提交 `08e7dd3` 已推送到 `main`；[首次托管运行 #37128607920](https://github.com/zhz520/cangshuo-tool/actions/runs/37128607920) 五项全部成功，四个构建产物上传成功，Phase 0 的 CI 绿色退出条件已满足。详细配置和记录见 [CI 说明](../docs/CI.md)。

---

# 57. 项目完成定义 Definition of Done

一个功能只有同时满足以下条件才算完成：

```text
[ ] 需求符合 PROJECT_SPEC.md
[ ] 有明确 code / entity / API / screen
[ ] Android/Server/Admin（涉及者）全部联调
[ ] 单元测试通过
[ ] 相关集成/UI测试通过
[ ] 编译成功
[ ] 无明显 lint 错误
[ ] 日志无敏感信息
[ ] API 错误状态完整
[ ] 文档已更新
[ ] ROADMAP 已勾选
[ ] Git commit 已创建
```

---

# 58. 第一阶段最终交付物

仓库完成后至少应出现：

```text
/toolbox
├── android/
├── server/
├── admin/
├── deploy/
│   ├── docker-compose.yml
│   └── nginx.conf
├── docs/
│   ├── PROJECT_SPEC.md
│   ├── ARCHITECTURE.md
│   ├── DATABASE.md
│   ├── API.md
│   └── ROADMAP.md
├── AGENTS.md
├── README.md
├── .env.example
└── .gitignore
```

---

# 59. 项目启动顺序

第一次正式开发时严格按照：

```text
Phase 0
  ↓
数据库 + Redis
  ↓
Spring Boot Health API
  ↓
Android 空壳
  ↓
ToolRegistry
  ↓
Home
  ↓
Search
  ↓
Calculator
  ↓
Favorite
  ↓
Recent
  ↓
Auth
  ↓
Sync
  ↓
更多工具
```

**不要先做 AI、不要先做复杂图片编辑、不要先做会员。**

先把“工具平台的骨架”跑起来。

---

# 60. 项目成功指标（首个公开版本）

产品指标采用可观测指标，不预设商业结果：

- 首屏加载耗时
- 本地工具打开耗时
- 搜索结果命中率
- Crash-free sessions
- API P95 latency
- 登录成功率
- 同步成功率
- 图片处理失败率
- 云端工具错误率
- 日活工具数
- 人均常用工具数

这些指标用于发现体验问题和技术瓶颈，不用于给用户建立不必要的敏感画像。

---

# 61. 参考思路来源

本项目的架构思路可参考以下开源项目类别：

- Toolbox-Android：工具注册、传感器、二维码、计时等基础工具组织方式。
- OneBox：大型工具箱的 feature/core 模块化思路。
- ImageToolbox：图片、OCR、PDF、二维码等媒体工具能力组织方式。
- Unitto：计算器/单位转换类工具的本地优先体验。
- Net Swiss Knife：网络诊断类工具的模块化组织。

注意：实际开发时必须分别检查各项目当前 LICENSE，并只在许可证允许的范围内复用代码、资源或实现；“参考架构/思路”和“直接复制代码”是两回事。

---

# 62. 本文档的维护规则

本文档属于代码仓库的一部分。

发生以下情况必须更新：

- 技术栈变化
- 数据库字段变化
- API 契约变化
- 工具分类变化
- MVP 范围变化
- 安全策略变化
- 新增重大能力

不要让代码长期领先于文档。

最终原则：

> **先写清楚，再让 AI 写；先小步验证，再继续扩展。**
