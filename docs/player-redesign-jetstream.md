# PlayerScreen 重设计参考 — JetStream (Figma)

> 来源: Figma 文件 `DZnTSj2qB7VQVAhUil2RIT` "📺 JetStream - Streaming App (Community)"
> 拉取时间: 2026-09-08，通过 Figma REST API (Framelink figma-developer-mcp token 同款)
> 节点: player 帧 = `685:1449`（960×540）。注意：用户原始链接 `node-id=76-671` 指向的是 "Dark" 色板组，不是 player 帧本身。

## 色板（Dark 主题 token，Material 3 风格）

| Token | Hex | 用途 |
|---|---|---|
| Background | `#1A1C1E` | 页面/播放器底 |
| On Background | `#E3E2E6` | 背景上的文字 |
| Surface | `#121316` | 卡片/浮层底 |
| On Surface | `#C7C6CA` | 主文字/图标（标题、副标题） |
| Surface Variant | `#43474E` | 圆形按钮底、次要容器 |
| On Surface Variant | `#C4C6CF` | 次要图标/文字 |
| Outline | `#8E9099` | 描边 |
| Primary | `#A8C8FF` | 主强调色（浅蓝） |
| On Primary | `#003062` | 主色上的文字 |
| Primary Container | `#00468A` | 主色容器 |
| On Primary Container | `#D6E3FF` | 主色容器文字 |
| Secondary | `#BDC7DC` | 次要强调 |
| Tertiary | `#DCBCE1` | 第三强调 |
| Error | `#FFB4AB` | 错误色 |
| White | `#FFFFFF` | 时间、进度条、字幕文字 |

## Player 屏幕结构（960×540, 16:9）

```
┌──────────────────────────────────────────────┐
│  Poster (全屏海报/视频帧)                      │
│  Scrim (底部渐变遮罩: 黑→黑)                    │
│                                                │
│  [可选] Closed Captions 黑底#000 白字 16px      │
│                                                │
│  Vikram Rana          (⊙)(⊙)(⊙)(⊙)  ← 标题 + 4个圆钮│
│  [Label] 2011 • Action/Fantasy                  │
│  1:08:24  ────────●────────  1:54:00           │
│                              Info              │
└──────────────────────────────────────────────┘
```

### 具体元素

1. **CinematicBackground**: 全屏海报图 + 底部 Scrim（垂直渐变 `#000 → #000`）
2. **Info 区**（左侧，x=58 起）:
   - 标题 Title：`Inter 400, 28px, #C7C6CA`
   - 副标题 Subtitle 行：Tag 徽章（底 `#E5E1E6`，圆角，内 icon `#C9C5D0` + 黑字 label `#000`，Google Sans Text 500 14px）+ 文本 `2011 • Action/Fantasy`（Inter 400 16px `#C7C6CA`）
3. **Actions 区**（右侧）: 4 个 `IconButton`，40×40 圆形，底 `#43474E`(Surface Variant)，图标 20×20 `#C7C6CA`(On Surface)。图标由 component instance 决定（默认 CC/音频/倍速/全屏之类，可换）
4. **Seeker**（底部横条，16px 高）:
   - 当前时间 `1:08:24`（Inter 500 12px `#FFFFFF`）
   - 进度条：轨道 bar `#FFFFFF` + 已播 progress `#FFFFFF` + handle 圆点 16×16 `#938F99`
   - 总时长 `1:54:00`（Inter 400 12px `#FFFFFF`）
5. **More/Info** 提示：icon + "Info" 文字（Inter 500 12px `#C4C6CF`）

## 字体

- 标题/副标题/时间：`Inter`（400 / 500）
- 徽章 label / 字幕：`Google Sans Text`（400 / 500）
- 字号梯度：标题 28 → 副标题 16 → tag 14 → 时间 12

## 与当前 PlayerScreen.kt 的差距

| 维度 | 当前 tvgram | JetStream |
|---|---|---|
| 底 | 纯黑 `Color.Black` | `Background #1A1C1E` + 海报 + Scrim |
| 布局 | 进度条 + 时间/倍速行 + 按钮行（平铺） | 左：标题+副标题+tag；右：4 圆钮；下：细 seeker |
| 按钮 | 48dp 圆形，白透明，focus 60% 白 | 40dp 圆形，`#43474E` 底，focus 用色板 |
| 进度条 | 白透明条，focus 变高 | 细白条 + `#938F99` 圆点 handle |
| 时间 | 右侧 `pos/dur` 一体 | 两端：当前左、总时长右 |
| 标题 | 无（纯视频） | 大标题 + 年份•类型（TG 场景 → caption/发送者） |
| 字幕 | 无 | 黑底白字 |

## 保留不动的（TV 场景必需）

- D-pad 焦点系统 / Back 优先 / 自动隐藏
- 旋转（TG 竖屏视频刚需）、倍速、信息抽屉
- 流式播放、首帧门控、错误重试、播完即删

## 改法（建议）

**阶段 A — 视觉重主题（低风险）**：把 `PlayerScreen.kt` 里硬编码的 `Color.White`/`Color.Black` 换成上面的 token 常量（新增 `PlayerTokens` 对象）；按钮改 `#43474E` 底 + `#C7C6CA` 图标；进度条改细条 + 圆点 handle + 两端时间。

**阶段 B — 布局对齐（中）**：控制器改成 JetStream 三段式——底部左标题块（用 TG 消息 caption / 发送者名）、右侧圆形按钮排、下方细 seeker。保留全部现有交互回调。

**阶段 C — 可选增强**：海报+Scrim（用消息缩略图当背景）、字幕样式、Info 提示条。
