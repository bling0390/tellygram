# SearchScreen 参考 — JetStream search 三态 (Figma)

> 来源: Figma `DZnTSj2qB7VQVAhUil2RIT` "📺 JetStream - Streaming App (Community)"
> 节点: search-default `57:848` / search-focused `3:293` / search-active `665:1686`，均 960×540
> 拉取: 2026-09-08，Figma REST API

## 三态流转

| 帧 | 内容 | 触发 |
|---|---|---|
| `search-default` | Nav + 搜索框(placeholder) + **分类 chips** + 内容网格 | 初始/未聚焦 |
| `search-focused` | Nav(搜索图标高亮) + 搜索框(聚焦) + 内容网格（**chips 消失**） | 聚焦搜索框 |
| `search-active` | Nav + 搜索框(已输入 + 光标) + 内容网格 + **D-pad 键盘** + 语音提示 | 输入中 |

结构语义：default 是「分类浏览」入口，focus/active 时 chips 收起、进入真正的「搜内容」态。

## 布局（自上而下）

1. **Nav 栏**（高 32）：logo + 5 个 tab + 搜索图标
   - tabs: `Home / Categories / Movies / Shows / Favorites`
   - 搜索图标: 32×32 方块，default 底 `#FFFFFF`、focus/active 底 `#3E4758`（Secondary Container）
2. **搜索框**（高 60，pad 28/20）：placeholder `Search for movies, shows and more...`
3. **分类 chips**（仅 default）：12 个，3 行 × 4 列，每个 196×110，行/列 gap 20
   - Action / Anime / Black Voices / Comedy / Documentary / Fantasy / Foriegn / Horror / LGBTQ / War & Military / Musicals
4. **内容网格**（滚动）：多个 section，每个 = 标题 + 横向卡片行
   - Trending / Top 10 in the US / Now playing movies / Popular films this week / Science documentaries

## 色（在已应用的 M3 dark 基础上，search 专属）

| 元素 | 值 | 映射 token |
|---|---|---|
| 页面底 | `#1A1C1E` | Background |
| 搜索 placeholder | `#C7C6CA` | On Surface |
| chip/标题/卡片文字 | `#FFFFFF` | —（白字） |
| Nav tab 选中 | `#E3E2E6` | On Background |
| Nav tab 未选中 | `#C4C6CF` | On Surface Variant |
| 搜索图标底(focus/active) | `#3E4758` | Secondary Container |
| 搜索图标底(default) | `#FFFFFF` | — |
| logo 字 | `#E3E2E6` | On Background |

**键盘色**（search-active 专属，非 M3）：
| 元素 | 值 |
|---|---|
| 键盘 body | `#1D2228` |
| 键盘 header | `#191C22` |
| 按键底 | `#3A434E`（圆角 4） |
| 按键 label | `#DADCE0` Roboto 500 16px |
| 激活键底 | `#FFFFFF`，字 `#3C4043` |
| 双字符键小数字 | `#80868B`（未激活）/ `#5F6368`（激活），9px |
| 主键(搜索) | 底 `#3A4B5D`，字 `#FFFFFF`，62×32 |
| 空格键 | 96×32 |
| 语音提示 | 圆底 `#424856` + `Press and hold to speak` `#FFFFFF` Roboto 400 13px |

## 字体

| 元素 | 字体/字重/字号 |
|---|---|
| chip 文字 | Google Sans Text 500 18px |
| section 标题 | Inter 500/400 16px |
| 卡片标题 | Inter 500/400 12px |
| Top10 数字 | Inter 600 57px（= displayLarge） |
| 搜索 placeholder | Inter 500 14px |
| Nav tab | Inter 500 14px |
| logo "jet stream" | Lexend Exa 500 14px |

## 卡片

- 竖版海报卡：153×231（2:3），圆角 4
- Top10 卡：268×150（16:9），左上叠大号 `#1/#2/...` 数字
- section 内 gap 16，卡间距 20

## 与 tvgram 现有 SearchScreen 的差距（关键）

JetStream search = **内容发现页**（分类浏览 + 趋势内容网格 + 文本搜）。tvgram search = **TG 会话名搜索**（文本输入 → 会话结果网格 + D-pad 键盘）。两者结构不同。

映射思路（待定，见讨论）：
- 分类 chips → 按 类型(频道/群/私聊) / 置顶 / 归档 过滤
- 内容网格 section → 按时间/类型分组的媒体缩略图流
- 搜索框 → 现有会话名搜索保留，chips 作为「无输入时的浏览态」

## 已有资产复用

tvgram 现在已有：D-pad 键盘（`DpadKeyboard`，样式可对齐上面键盘色）、搜索框、结果网格。所以改起来是「加 chips 浏览态 + 键盘/alist 换 JetStream 皮肤」，不是从零写。
