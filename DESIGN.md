---
name: 日常账本 · 经典卡片
description: 正式 Android 1.7.0 的白黑蓝经典卡片设计系统
colors:
  primary: "#235ee8"
  primary-dark: "#1745b5"
  ink: "#17212f"
  muted: "#637084"
  line: "#e7ebf1"
  bg: "#f4f6fb"
  surface: "#ffffff"
  soft: "#edf3ff"
  focus: "#719af2"
  card-border: "#e9edf4"
  input-border: "#ccd4df"
  summary-caption: "#e1eaff"
  secondary-ink: "#2554b6"
  secondary-hover: "#dce8ff"
  day-empty: "#f5f7fa"
  day-medium: "#e4edff"
  day-high: "#d8e5ff"
  day-hover: "#cddfff"
  day-amount: "#34568d"
  chart-bar: "#bfd1f6"
  wechat: "#147e64"
  wechat-soft: "#eaf7f1"
  error: "#b64143"
  q0-bg: "#fff2f1"
  q0-line: "#f2dedd"
  q0-ink: "#a13f43"
  q1-bg: "#eef3ff"
  q1-line: "#dbe5f9"
  q1-ink: "#245bce"
  q2-bg: "#fff7e8"
  q2-line: "#f0e4c9"
  q2-ink: "#8a640d"
  q3-bg: "#f3f5f8"
  q3-line: "#e4e8ee"
  q3-ink: "#627187"
  completed-ink: "#7b8697"
  completed-check: "#8b98ac"
  drop-target: "#dae7ff"
  toast: "#24344c"
typography:
  body:
    fontFamily: 'system-ui, "Segoe UI", "Microsoft YaHei", sans-serif'
    fontSize: "16px"
  headline:
    fontSize: "24px"
    fontWeight: 650
    lineHeight: 1.35
  title:
    fontSize: "17px"
    fontWeight: 650
  summary:
    fontSize: "40px"
    fontWeight: 650
    lineHeight: 1.25
    letterSpacing: "-.025em"
  summary-mobile:
    fontSize: "38px"
    fontWeight: 650
    lineHeight: 1.25
    letterSpacing: "-.025em"
  detail-total:
    fontSize: "34px"
    fontWeight: 650
    lineHeight: 1.25
  button:
    fontSize: "14px"
    fontWeight: 600
  todo:
    fontSize: "14px"
    lineHeight: 1.65
  caption:
    fontSize: "12px"
  nav-label:
    fontSize: "11px"
  quadrant-mark:
    fontSize: "23px"
    fontWeight: 600
    lineHeight: 1.2
rounded:
  pill: "6px"
  quadrant-cell: "7px"
  day: "8px"
  segmented: "9px"
  control: "10px"
  nav-indicator: "12px"
  quadrant: "15px"
  card: "17px"
  summary: "18px"
  dialog: "21px"
spacing:
  calendar-gap-mobile: "4px"
  calendar-gap: "5px"
  board-gap: "9px"
  form-gap: "10px"
  field-padding: "11px 12px"
  button-padding: "12px 17px"
  mobile-card-padding: "16px 14px"
  card-gap: "16px"
  mobile-gutter: "16px"
  card-padding: "19px"
  summary-padding: "20px"
  desktop-gutter: "22px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    padding: "{spacing.button-padding}"
  button-primary-hover:
    backgroundColor: "{colors.primary-dark}"
  button-secondary:
    backgroundColor: "{colors.soft}"
    textColor: "{colors.secondary-ink}"
    rounded: "{rounded.control}"
    padding: "{spacing.button-padding}"
  button-secondary-hover:
    backgroundColor: "{colors.secondary-hover}"
  icon-button:
    rounded: "{rounded.control}"
    size: "48px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "{spacing.field-padding}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "{spacing.card-padding}"
  summary-card:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.summary}"
    padding: "{spacing.summary-padding}"
  calendar-day:
    backgroundColor: "{colors.day-empty}"
    rounded: "{rounded.day}"
  pill:
    backgroundColor: "{colors.soft}"
    rounded: "{rounded.pill}"
    padding: "5px 8px"
  bottom-nav:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.muted}"
  dialog:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.dialog}"
    padding: "22px"
---

# Design System: 日常账本

## Overview

**Creative North Star: "经典卡片日常账本"**

这是用户选择的 C 经典卡片方向在正式 Android 应用中的实现：淡灰页面承托白色功能卡片，蓝色总额建立金额重点，深色文字维持清晰层级。紧凑页头与总额把空间让给日历，四象限以淡色分区呈现任务优先级。

本文件从生产代码提取，适用于 `app/src/main/assets` 的本地 WebView 和配套原生表面。前置 YAML 是颜色、字体、圆角、间距及组件的规范层；`.impeccable/design.json` 保存动效、阴影、断点、原生尺寸和可独立渲染的组件样本。别名 `--green`、`--green-dark` 实际指向主蓝色，不是第二套绿色主题。

当前为本地正式 APK 1.7.0，本轮用户要求不上传。MuMu Android 15 的 APK 截图使用独立样例数据，位于 `.tools/v170-review`；它们属于验证证据。真我 GT7 Pro、支付软件现场 OCR、已固定桌面快捷图标的真实刷新尚未验证。本文件描述源码事实，不将这些能力标为通过验证。

**Key Characteristics:**

- 白色功能表面、淡灰页面、蓝色金额重点
- 系统字体、等宽数字、捆绑 Lucide 线性图标
- 圆角卡片和方形日期、直角柱状图
- 状态过渡保持滚动位置与任务身份
- 保留真实本地账本接口及日期核对规则

## Colors

蓝色承担主要行动、选中和总额；中性色承担内容层级。其他颜色仅有明确的支付方式、任务优先级或告警语义。

### Primary

主蓝 `primary` 用于总额卡、主要按钮、当前导航、当日边框和选中柱子；`primary-dark` 是主要按钮悬停色。`soft` 用于次按钮、选中导航底块、普通有支出日期与轻提示。日历的 `day-medium`、`day-high` 是金额热度阶梯，不是独立品牌色。

### Neutral

`bg` 同时用于网页底色和原生 WebView 底色；`surface` 用于卡片、弹层、页头与导航。`ink` 用于主文本，`muted` 用于次文本，`line` 用于分割。卡片细边使用 `card-border`，输入边框使用 `input-border`。`toast` 是深色短反馈表面。

### Semantic

微信使用 `wechat` 与 `wechat-soft`，支付宝复用主蓝。四象限以 `q0` 红、`q1` 蓝、`q2` 黄、`q3` 灰组成背景、边线、文字三元组；顺序分别是重要且紧急、重要不紧急、紧急不重要、不紧急不重要。缺日期和疑似重复提示使用暖黄表面；删除与表单错误使用 `error`。完成态同时使用灰色和删除线。

**The 语义色 Rule.** 绿色只标识微信，红黄灰只承担明确状态；新增普通操作沿用主蓝。

## Typography

**Body Font:** YAML 中的系统字体栈。Android 原生主题采用 `sans`，桌面图标文字采用 `sans-serif-medium`；无新增网络字体或字体下载。

层级取自实际样式：页标题使用 `headline`，模块标题使用 `title`，总额使用 `summary`，日明细总额使用 `detail-total`，按钮使用 `button`，待办正文使用 `todo`。手机模块标题降至 16px，设置模块为 15px。总额在手机为 `summary-mobile`，360px 及以下为 35px，700px 及以上为 44px。根级数字采用 `tabular-nums`。

日历日期和金额分别占一行。日期在手机为 13px，窄屏为 12px；金额由 `fitDayAmounts()` 依据可用宽度测量，以上限 12px 自适应缩小，不换行。年统计金额旋转 -60°，窄屏标签降至 9px；这些是十二个月同时可见的现有密度策略。

WebView 根据系统 `fontScale` 设置文本缩放，范围为 85–200%。保持真实缩放行为；不能把较小的日历金额尺寸当作全局正文尺寸。

## Layout

页面是最大宽度 900px 的单列容器；宽屏左右留白取 `desktop-gutter`，底部预留 112px。700px 及以下采用 `mobile-gutter`，底部预留 103px；360px 及以下左右为 12px。页头粘在顶部，宽屏高度 82px，手机 76px；手机隐藏品牌小块，保留当前页名和加号。两个 700px 媒体查询在边界同时命中，后写的手机规则覆盖重复属性。

固定底部导航均分三列，顺序为备忘录、账单花销、设置。按钮高度 54px，底部 padding 为 `max(9px, env(safe-area-inset-bottom))`。Android 容器同时消费系统边缘 inset，延续现有 WebView 安全区域处理。

日历始终七列，周一开始，日期为 `aspect-ratio:1`。间距宽屏 8px、基础 5px、手机 4px、窄屏 3px。统计紧随日历和支付方式小计；年统计始终十二列，间距 3px，不横向滚动，周统计列间距随屏宽收紧。

四象限为两列两行，每区 3×3，共 36 格，格子保持正方形。外区间距基础 9px、窄屏 7px，内格间距基础 5px、宽屏 8px、窄屏 4px。其下任务清单独立纵向滚动，最大高度基础 240px、手机 224px、窄屏 212px，滚动不连带页面。在四象限内编辑、拖放、原生刷新后恢复清单和页面的滚动位置。

弹层最大宽 430px、宽度为视口减 28px、最大高度 86dvh。日明细采用固定标题与总额、独立滚动明细区，内容不把头部带走。

**The 连续位置 Rule.** 同页任务重绘必须保留原顺序、清单滚动和页面滚动；不能用整个列表平移作为页面入场效果。

## Elevation & Depth

系统以色调层级和细边为主，柔和阴影仅提示表面关系。功能卡片阴影为 `0 3px 10px #1c345405`；分段选中块为 `0 2px 4px #1833560b`；底部导航为 `0 -5px 17px #1c355006`。弹层为 `0 20px 80px #172e4733`，遮罩为 `#1c304966`；拖动影子为 `0 10px 20px #162e4e26`。完整词汇在 sidecar。

原生扫描悬浮条使用 4dp elevation、近白背景 `0xfaffffff`、1dp 淡蓝边框 `0xffccdafa`。这些 Android 值不是网页 box-shadow 的换算结果。

## Shapes

卡片使用 `card` 圆角，总额使用 `summary`，弹层使用 `dialog`。日期使用 `day`，象限格使用 `quadrant-cell`，外象限使用 `quadrant`。控件与输入共用 `control`。日历是圆角正方形，统计柱子明确采用零圆角，不把卡片圆角扩散到数据柱形。

图标为捆绑 Lucide ISC 内联 SVG，24×24 viewBox、1.8 描边、圆端点与连接；基础显示为 22px，主要按钮为 18px，图标按钮为 20px。原生扫描条 grip 与 close 使用同源 Vector Drawable；默认启动图案是蓝底白色三柱矢量。

桌面四象限位图为 192×192 像素，外裁切圆角 44px、四区为 81px 方块、区距 6px、外边距 12px，区圆角 6px。单任务字标字号 59px，多任务 36px 小格内字号 30px，溢出徽章字号 18px。尺寸属于位图坐标；不要混为手机 dp。

## Components

### Buttons / Inputs

主次按钮共享 48px 最小高度、`button-padding` 和 `control` 圆角。主按钮白字蓝底，次按钮蓝字浅蓝底。普通按钮按下缩至 0.96，禁用透明度 0.4 并取消缩放。键盘焦点采用 3px `focus` 轮廓、偏移 3px。文本操作和分段按钮当前最小高度为 44px，不宣称所有网页按钮均为 48px。

加号、关闭、月份切换为 48×48px；待办完成按钮及拖动手柄也为 48×48px，完成圆形标记本身保持 20px。日期和象限格沿用上述网格几何，不能为了统一点击尺寸直接改成固定 48px 格子。原生扫描条四个控件均为 48×48dp，总条为 192×48dp。

输入、选择和正文输入框最小高度 48px，白底细边，聚焦边框变主蓝。金额输入保持大字号和下划线，不套用普通输入边框。待办新增只填一句话；编辑时可选单字标识。

### Cards / Status

功能卡片使用浅边、极轻阴影和 `card-gap`。手机内边距取 `mobile-card-padding`；设置卡和待办清单有各自的紧凑内边距。总额卡内保留总额、下方日均和消费笔数/有支出天数。状态 pill 为浅蓝圆角短标签；黄色告警明确说明缺日期或疑似重复。

### Navigation / Segmented

导航在图标后保留文字，选中项为主蓝并设 `aria-current`。浅蓝指示底块平移到所选位置；分段控件的白色底块平移切换清单/四象限和周总/月总，保留按钮文字。

### Calendar / Charts / Detail

日期无支出显示破折号；普通有支出用 `soft`，超过 3000 分用 `day-medium`，超过 6000 分用 `day-high`。今日用白底主蓝边与右上小点，可叠加金额状态。日期展开优先用共享容器 View Transition，缺能力时降级为普通弹层动画。明细头部固定，点弹层外部关闭。

周统计按周一开始且跨月只计所选月；年统计一次显示十二个月，点月份进入该月周统计。图表柱形按金额比例计算，选中柱为主蓝。日均在本月按已过天数、其他月份按整月天数计算。保持原本金额按分保存的规则，不在视觉层改变账本聚合。

### Quadrants / Task Rows

空格用淡色半透明底与虚线，已放事项用白底实线，中心显示单字。完成后灰色删除线、移出象限并显示在底部已完成组；未完成沿原顺序。拖动超过 7px 才启动，显示蓝色单字影子，目标格用主蓝轮廓及 `drop-target` 背景；结束后抑制 400ms 点击，点击空格仍可选择事项。桌面缩略图和原生渲染复用同一四象限颜色顺序，全部清空恢复默认图案。

### Motion / Scanner

交互使用 `--ease`，即 `cubic-bezier(.16,1,.3,1)`：按钮 160ms、分段平移 260ms、导航平移 280ms、页面仅淡入 220ms、已有任务按身份重排 250ms。图表柱子从 0.2 倍纵向比例进入，时长 400ms。弹层进入 240ms、退出 170ms，toast 进入 220ms、退出 160ms；具体起止姿态见 sidecar。支持系统减少动画和原生禁用动画同步，过渡缩至 1ms，共享容器过渡关闭。源码部分保存操作直接调用 `dialog.close()`，不能将统一退出动画描述为每条路径都已实现。

扫描结果保留忽略、编辑、核对三个独立按钮。日期缺失时核对禁用，只有日期没有时间可入账，不能自动补成今天。支付方式自动识别。原生扫描时暂时隐藏悬浮条并禁用扫描按钮，结束后恢复；返回账本入口打开扫描结果，关闭入口停止扫描。

## Do's and Don'ts

### Do:

- **Do** 从生产资产与原生资源同步提取 token，保留白黑蓝经典卡片方向。
- **Do** 保留系统字体、数字对齐、图标文字标签、系统减少动画和文本缩放。
- **Do** 保留七列日期、十二列年统计、36 格四象限和任务滚动位置。
- **Do** 让完成与拖动控件保持 48×48px，原生扫描控件保持 48×48dp。
- **Do** 保留日期核对、真实本地数据接口和账单统计规则。

### Don't:

- **Don't** 使用 emoji、远程图标字体或新增未授权视觉主题。
- **Don't** 将日历与四象限格子改为固定 48px 而破坏手机布局。
- **Don't** 给统计柱子添加圆角，或以整页位移动画重置清单位置。
- **Don't** 把正式 APK 写成模拟数据 demo，或把模拟器样例当作真机、现场 OCR、桌面刷新验证。
- **Don't** 将当前源码未覆盖的动效路径或未验证的设备能力写成已完成事实。
