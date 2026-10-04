# Android 多语言

用户于 2026-10-04 确定：软件支持多语言，默认跟随系统，主要使用中文。当前 Android 提供完整的简体中文与英语界面，保留平台原生资源选择和语言设置。

## 语言与默认行为

| 资源/配置 | 用途 |
| --- | --- |
| `res/values/strings.xml` | 简体中文默认资源；没有匹配的支持语言时作为最终回退 |
| `res/values-en/strings.xml` | 英语界面，适用于系统英语及英语区域变体 |
| `res/xml/locale_config.xml` | 声明 `zh-Hans`、`en` 两种应用语言，供 Android 13+ 系统设置使用 |
| `androidResources.localeFilters` | 打包中文、英语语言资源和默认资源，避免依赖库引入额外应用语言 |

初次安装没有应用语言覆盖，默认按系统的语言优先列表选择资源。中文系统使用中文，英语系统使用英语；系统语言列表没有匹配语言时回退中文。数字与运算符作为计算语法不翻译，计算器使用英文小数点。关键词包含中文和英语，不影响界面语言。

不在启动时强制设置中文或清除用户选择。Android 13 及以上由系统保存应用语言偏好；更早版本跟随系统语言。当前未实现自定义应用内语言选择器或旧系统的应用语言覆盖。

## 切换语言

- Android 13+：系统 **设置 → 应用 → 沧烁工具箱 → 语言**，或 **系统 → 语言 → 应用语言**；选择“系统默认”、简体中文或 English。菜单位置由设备系统决定。
- Android 8–12：改变设备系统语言后，应用按系统资源规则更新。

App 使用正常的系统配置变化，不拦截 locale 重建。首页监听配置中的语言标签刷新目录；内置工具的显示名称和描述从应用 Resources 读取，编码、排序和运行能力保持固定。计算器错误保存为领域错误类型，在 UI 中解析为当前语言资源；表达式、结果及当前首页入口由现有 ViewModel/SavedStateHandle 管理。

当前服务端目录元数据主语言是中文，API 还没有语言字段或语言协商参数。Android 内置计算器在本机资源中本地化；客户端远程目录合并及服务端目录多语言协议需在后续任务定义，不能用未翻译的远程文本覆盖本地工具的语言资源。

## 扩展与验证范围

增加语言时，新增相应 `values-*` 翻译，补齐可翻译字符串，将其语言标签加入 locale_config 和 localeFilters，并同步内置工具名称、描述与无障碍文案。数值、运算符和双语关键词声明为不可翻译资源。Android Lint 负责现有资源的翻译和格式静态检查。

本地搜索页的文案也提供中英文。分类双语检索别名位于 `search_keywords.xml`，新增语言时同步补充别名和工具关键词；显示名称仍使用当前语言资源。查询、分类和查询状态在语言变化后保留，结果重新检索。规则与验证范围见 [本地搜索](LOCAL_SEARCH.md)。

收藏页、星标无障碍标签及失败提示也提供中英文。Room 只存编码与时间，显示文案从当前注册中心解析，语言切换后重新读取。规则与设备验证范围见 [本地收藏](LOCAL_FAVORITES.md)。

本轮语言配置与资源的构建/Lint 记录见 [计算器说明](CALCULATOR.md#验证记录)。尚未在设备上验证默认语言、系统应用语言切换、配置恢复、大字体或不同系统版本。

## 参考

- [Android 应用语言设置与 LocaleConfig](https://developer.android.com/guide/topics/resources/app-languages)
- [AGP 9.1 ApplicationAndroidResources](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/ApplicationAndroidResources)
