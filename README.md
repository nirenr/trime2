# 中文兔

**一款基于 Rime 输入法引擎、支持 Lua 高度定制和可视化编辑的 Android 输入法。**

开发者：[nirenr](https://github.com/nirenr)

[项目仓库](https://github.com/nirenr/trime2) · [版本下载](https://github.com/nirenr/trime2/releases) · [Rime 输入法引擎](https://github.com/rime/librime)

中文兔将 Rime 的输入方案与 Android 键盘的外观、布局和交互相结合。输入规则由 Rime 管理，主题、配色及键盘布局则由 Lua 配置控制。既可以直接使用内置配置，也可以通过可视化编辑器打造自己的输入法，无需从头编写主题代码。

## 软件特性

- **Rime 输入引擎**：支持 Rime 输入方案、词典、用户词典、简繁转换及方案定制，可按需安装拼音、双拼、五笔等兼容方案。
- **独立方案组**：按目录管理不同的 Rime 配置，方便组织多套输入方案。
- **Lua 主题系统**：主题、配色和键盘布局相互独立，支持动态选择键盘、预设按键、脚本以及图片、字体、声音资源。
- **灵活的键盘布局**：支持行布局、弹性布局、绝对坐标布局及符号面板；可配置单击、长按、四方向滑动、组合事件及弹出键盘。
- **丰富的外观定制**：按键背景、圆角、描边、阴影、字体、助记、按下状态、悬浮提示、工具栏、候选栏、剪贴板及 Composition 悬浮窗口均可按实际组件支持的属性配置。
- **四大可视化编辑器**：主题、配色、键盘和 Rime 方案均可在应用内编辑；配色与键盘支持即时预览。
- **独立补丁保存**：Lua 可视化编辑默认保存到 `.custom.lua`，保留原始配置；支持编辑补丁、清除补丁及主动合并到源码。
- **内置代码编辑器**：支持 Lua、YAML 语法高亮、行号、查找、撤销、重做；YAML 支持空白符显示和 Rime 常用属性补全。
- **AI 与语音识别**：支持配置自定义 AI 和 ASR 服务，包括模型、API 地址、鉴权和提示词。
- **现代 Android 界面**：卡片式首页、矢量图标、深色模式及高版本 Android 边到边显示适配；支持异步文件解析与资源加载。

## 快速开始

1. 从 [Releases](https://github.com/nirenr/trime2/releases) 下载适合设备的安装包（如已有发布版本）。
2. 在 Android 系统输入法设置中启用中文兔，并将其选为当前输入法。应用内部名称可能随版本变化。
3. 在应用首页选择方案组、输入方案、键盘主题、颜色样式和默认键盘。
4. 安装或修改 Rime 方案后，点击「部署」，在测试输入框中检查输入效果。
5. 需要个性化配置时，进入相应的编辑器，修改并保存；必要时重新选择主题或配色以刷新配置。

## 配置目录

用户数据目录为 Android 公共文档目录下的 `rime`，通常是 `/storage/emulated/0/Documents/rime/`。实际路径以设备为准。

```text
Documents/rime/
├── schemas/                         # Rime 方案组
│   └── default/
│       ├── default.yaml
│       ├── default.custom.yaml
│       ├── example.schema.yaml
│       └── example.dict.yaml
├── themes/                          # 主题目录
│   └── my_theme/
│       ├── main.lua                 # 主题入口
│       ├── main.custom.lua          # 可选：主题补丁
│       ├── styles/
│       │   └── light/
│       │       ├── main.lua         # 配色入口
│       │       └── main.custom.lua  # 可选：配色补丁
│       ├── keyboards/
│       │   ├── qwerty26.lua
│       │   └── qwerty26.custom.lua  # 可选：键盘补丁
│       ├── images/                  # 主题图片、图标
│       ├── fonts/                   # 字体文件
│       ├── sounds/                  # 按键声音
│       └── scripts/                 # 主题脚本
├── images/                          # 公共图片
├── fonts/                           # 公共字体
└── scripts/                         # 公共脚本
```

以上是配置结构示例，并非所有文件和目录都会自动创建。内置默认主题位于源码的 [`assets/themes/default/`](assets/themes/default/)（标准 Android 工程中对应 `app/src/main/assets/themes/default/`）。建议复制完整默认主题到用户主题目录，改名后再编辑，不直接改动内置资源。

## 主题配置

每个主题使用一个独立目录，包含 `main.lua`、`styles/` 和 `keyboards/`。主题负责定义默认配色、键盘选择逻辑、预设按键及服务配置，不负责定义 Rime 的编码和词典。

### 基本信息与动态键盘

`themes/my_theme/main.lua` 示例：

```lua
name = "我的主题"
author = "nirenr"
style = "light"
keyboard = "qwerty26"

function get_keyboard(id, alphabet)
    if id == "" then
        return keyboard
    end
    if string.find(alphabet, "%d") then
        return "qwerty36"
    end
    if string.find(alphabet, "/") then
        return "qwerty27"
    end
    return keyboard
end
```

| 属性 | 说明 |
| --- | --- |
| `name` | 主题显示名称 |
| `author` | 作者 |
| `style` | 默认配色目录名，不含路径 |
| `keyboard` | 默认键盘文件名，不含 `.lua` |
| `get_keyboard(id, alphabet)` | 根据方案 ID 和编码字符集选择键盘；用户手动指定的键盘优先 |
| `action_labels` | 输入框不同动作对应的回车键标签 |
| `preset_keys` | 可被键盘及工具栏引用的预设事件 |
| `ai_config` / `asr_config` | 自定义 AI / 语音识别服务 |

示例依赖主题内已有 `qwerty26.lua`、`qwerty27.lua`、`qwerty36.lua` 和 `styles/light/main.lua`，因此建议从完整默认主题副本开始修改。

### 预设按键（Preset keys）

预设按键集中定义事件，再由不同键盘引用。内置预设可作为回退，同名主题定义优先。

```lua
preset_keys = preset_keys or {}
preset_keys.CopyText = {
    label = "复制",
    send = "Control+c",
    description = "复制选中文本",
    functional = true,
}
preset_keys.MyBackSpace = {
    label = "⌫",
    send = "BackSpace",
    repeatable = true,
}
```

常用字段：`label`（标签）、`description`（描述）、`send`（按键事件）、`text`（事件文本）、`commit`（直接上屏）、`command` / `option`（命令及参数）、`toggle` / `states`（状态切换及标签）、`repeatable`（重复触发）、`sticky`（粘滞状态）、`functional`（功能键）。具体事件及命令以内置 `main.lua` 和 `Event.java` 为准。

在键盘中使用 `{ click = "CopyText" }` 即可引用该事件，无需复制整个预设定义。主题编辑器支持搜索、新增、修改和删除预设按键；删除前应检查键盘和工具栏中是否仍有引用。

### AI 与 ASR 服务

可在主题编辑器中填写，也可以通过 Lua 配置：

```lua
ai_config = {
    base_url = "",       -- 服务 API 地址
    model = "",          -- 模型名称
    key = "",            -- API Key
    system_prompt = "请润色以下文本，保留原意。",
    extra_params = { stream = false },
}

asr_config = {
    base_url = "",
    model = "",
    key = "",
    prompt = "",
    system_prompt = "请将语音准确转写为中文。",
    extra_params = { stream = false },
}
```

请根据服务商文档填写真实地址和模型。当前内置客户端不支持流式输出；ASR 自定义服务需要支持 `input_audio` 多模态输入。`extra_params` 可以使用 JSON 字符串或 Lua table。API Key 可能以明文保存在源码、补丁及备份中，请勿将包含密钥的主题上传至公开仓库。使用外部 AI 或语音服务时，相关文本或音频可能发送至所配置的服务端。

## 配色配置

配色文件位于 `themes/<主题>/styles/<配色>/main.lua`。每个配色独立成目录，可包含专属图片及字体。

建议复制内置 `light` 配色作为基础，在其原有定义中修改以下内容，而不是用简化示例替换完整配色文件。配色文件可以读取主题中定义的全局变量；应避免修改主题共享的全局表。

### 基础外观

```lua
name = "清蓝"
author = "nirenr"
background = 0xffe6edf7

keyboard = {
    height = 240,
    background = 0xffe6edf7,
}

key = {
    background = 0xffffffff,
    text_color = 0xff263449,
    text_size = 22,
    corner_radius = 8,
    elevation = 2,
    shadow_color = 0xff000000,
    margins = { left = 2, top = 2, right = 2, bottom = 3 },
    pressed = {
        background = 0xffb9cdec,
        text_color = 0xff263449,
        scale_x = 0.95,
        scale_y = 0.95,
    },
}
```

颜色使用 Android ARGB 格式 `0xAARRGGBB`，其中 `AA` 表示透明度（`00` 完全透明，`FF` 完全不透明）。例如 `0xffffffff` 为白色，`0xff000000` 为黑色，`0x80000000` 为半透明黑色。

常见样式属性包括 `background`、`text_color`、`text_size`、`font`、`corner_radius`、`stroke_color`、`stroke_width`、`elevation`、`shadow_color`、`padding` 和 `margins`。不同组件实际读取的属性并不完全相同，不应将普通按键的全部属性套用到候选文字或工具栏按钮。

### 长按文字、助记与弹出提示

```lua
key.long_click = {
    show = true,
    text_color = 0xff64748b,
    text_size = 10,
    pressed = { text_color = 0xff315da8 },
}
key.hint = {
    show = true,
    text_color = 0xff64748b,
    text_size = 12,
    pressed = { text_color = 0xff315da8 },
    up = { show = true },
    down = { show = true },
    left = { show = true },
    right = { show = true },
}
key.preview = {
    width = 64,
    height = 64,
    background = 0xffffffff,
    text_color = 0xff263449,
    text_size = 28,
    corner_radius = 12,
    elevation = 8,
}
```

助记内容来自键盘中的 `hint`、`hint_long`、`hint_up`、`hint_down`、`hint_left`、`hint_right` 及对应事件。配色决定其显示状态、大小、颜色和位置。长按文字、单助记和四方向助记的按下颜色均可通过各自的 `pressed.text_color` 配置；助记本身是文字或图片标签，不具有独立按键背景、圆角等外观属性。

### 样式复用与独立覆盖

```lua
-- 共享同一个表，修改其中一个也会影响另一个
shared_key = key

-- 使用工程内置的 table.clone 创建样式副本
functional = table.clone(key)
functional.background = 0xffcdd9eb

enter = table.clone(key)
enter.background = 0xff315da8
enter.text_color = 0xffffffff
```

`table.clone` 是工程内置 Lua 扩展，不应假设其对任意嵌套对象都进行深复制。样式解析还可能通过 Lua 元表继承缺失字段。配色编辑器可查看属性来源、修改共享来源或创建独立覆盖；对复杂动态 Lua 配置，建议直接编辑源码并验证实际效果。

### 候选栏、工具栏与悬浮窗口

以下是可在完整配色基础上调整的片段：

```lua
candidate.height = 48
candidate.background = 0xffe6edf7
candidate.text_color = 0xff263449
candidate.text_size = 20
candidate.pressed.background = 0xffb9cdec
candidate.comment.text_size = 12
candidate.comment.text_color = 0xff64748b

-- 工具栏按钮可引用 preset_keys 或直接定义事件表
toolbar.keys = {
    { label = "菜单", send = "Control+grave" },
    "Mode_switch", "Keyboard_clipboard",
}
toolbar.hide.text = "bunny.png" -- 需存在对应图片

composition.show = true
composition.max_width = 230
composition.round_corner = 8
composition.background = 0xeeffffff
height = keyboard.height + candidate.height
```

| 配色分组 | 控制范围 |
| --- | --- |
| `keyboard` | 键盘背景及高度 |
| `key`、`functional`、`space`、`enter`、`enter2` | 普通按键及特殊按键样式 |
| `popup` | 弹出键盘 |
| `toolbar`、`toolbar.key`、`toolbar.hide` | 工具栏、普通按钮及收起按钮 |
| `candidate`、`candidate.comment`、`candidate.key` | 横向候选栏、注释及更多候选按钮 |
| `candidate.expanded` | 展开候选面板 |
| `preedit` | 预编辑区 |
| `composition` | 独立悬浮窗口，包含编码、候选、序号等组件 |
| `symbol`、`clipboard` | 符号及剪贴板面板 |

完整字段及组合方式请参考内置 `styles/light/main.lua` 和对应 Java 渲染源码。横向候选栏与 Composition 是不同组件，应分别配置。

### 图片、字体与配色引用

- 背景可使用颜色或图片文件名，例如 `background = "keyboard.png"`。
- 按键标签可使用对应 PNG 图片名称，支持的单色图标可按文字颜色着色；找不到图标时回退为文字。
- `font = "myfont.ttf"` 指定字体，支持的样式也可使用字体列表。
- 图片、字体优先查找当前配色目录，其次查找当前主题的 `images/` 或 `fonts/`，最后查找公共数据目录中的对应资源。
- 可使用 `require "styles.light"` 引用其他配色入口，或 `require "styles.light.a"` 引用该配色中的 `a.lua`；具体用法参考内置配色目录说明。

## 键盘布局配置

键盘文件存放于 `themes/<主题>/keyboards/`，例如 `qwerty26.lua`。按键的事件和外观分别通过 `click` 等事件属性及 `style` 引用配置。

### 行布局（RowKeyboardView）

宽高使用键盘总宽高的百分比。行和按键均不使用 `grow`。

```lua
name = "示例行键盘"
key_width = 25
key_height = 50
rows = {
    {
        height = 50,
        keys = {
            { click = "a", long_click = "1" },
            { click = "b", swipe_up = "copy", hint_up = "复制" },
            { click = "c", hint = "注" },
            { click = "BackSpace", style = "functional" },
        },
    },
    {
        height = 50,
        keys = {
            { click = "Shift_L", style = "functional" },
            { click = "space", width = 50 },
            { click = "Return", style = "enter" },
        },
    },
}
```

行可设置 `width`（默认按键宽度）、`height` 和 `style`（行容器背景样式）。按键宽高优先使用自身配置，其次行配置，再使用键盘默认宽高。各行及各按键的百分比应合理分配，避免超出画布。

### 弹性布局（FlexboxKeyboardView）

使用 `flex_box`，支持嵌套行列、固定尺寸（dp）及 `grow` 权重。

```lua
name = "示例弹性键盘"
flex_box = {
    direction = "row",
    { grow = 1, direction = "column", keys = {
        { click = "copy" }, { click = "paste" },
    } },
    { grow = 2, direction = "column", keys = {
        { click = "space" }, { click = "Return" },
    } },
}
```

### 绝对布局（AbsKeyboardView）

使用 `keys`，`x`、`y`、`width` 和 `height` 均为百分比。

```lua
name = "方向键盘"
key_width = 25
key_height = 50
keys = {
    { click = "Left",  x = 0,  y = 50 },
    { click = "Up",    x = 25, y = 0 },
    { click = "Down",  x = 25, y = 50 },
    { click = "Right", x = 50, y = 50 },
    { click = "Return", x = 75, y = 0, height = 100 },
}
```

此外，`key_maps` 用于符号键盘，可参考内置 `symbols_ext.lua`；复杂符号布局可通过源码模式编辑。

## 主题、配色与键盘开发参考（详细）

本章以当前工程的 Java 渲染器和内置默认主题为依据。示例中的 Lua 配置属于中文兔，不是 Rime 原生 YAML 字段。建议复制默认主题后逐项修改；下文的片段不是要求用不完整的配置替换原文件。

### 1. Lua 配置约定

| 写法 | 含义 |
|---|---|
| `name = "清蓝"` | 定义全局配置项 |
| `key = { text_size = 22 }` | 定义具名样式表 |
| `key.text_size = 24` | 修改已有表的字段 |
| `{ "a", "b" }` | 数组，Lua 下标从 1 开始 |
| `true` / `false` | 布尔值，不要加引号 |
| `nil` | 删除字段；有继承时可能继续取得父级值 |
| `-- 注释` | 单行注释 |
| `["end"] = ""` | 使用 Lua 保留字作为表键时的写法 |

颜色使用 `0xAARRGGBB`，不是 CSS 的 `#RRGGBB` 字符串。普通按键字号、宽高样式、边距、阴影及位移通常使用 dp，许多读取方法要求整数；缩放和行距倍数使用小数。键盘布局的宽高单位另见下文。`composition.line_spacing` 直接传给 Android 的 `setLineSpacing()`，单位为 px。不要把所有数值都理解为百分比或 sp。

### 2. 主题入口的完整使用方法

主题目录名是配置 ID，`name` 是面向用户的名称。`style` 对应 `styles/` 下的目录，`keyboard` 对应 `keyboards/` 下不带 `.lua` 的文件名。`get_keyboard(id, alphabet)` 是根据 Rime 方案选择布局的函数，不用于修改方案本身。例如返回 `number` 时，应存在 `keyboards/number.lua` 或相应内置布局。

回车标签由 `action_labels` 定义：

```lua
action_labels = {
    none = "回车", send = "发送", go = "前往",
    done = "完成", search = "搜索",
    previous = "上一个", next = "下一个",
}
preset_keys.Return = { label = "action_labels", send = "Return" }
```

`label = "action_labels"` 是内置回车标签机制；实际显示取决于 Android 输入框的 IME action。一般按钮的 `label` 则为普通文本或图片标签。

#### 2.1 预设事件字段参考

| 字段 | 类型 | 含义 |
|---|---|---|
| `label` | 字符串 | 显示标签，不等于上屏内容 |
| `description` | 字符串 | 描述，用于辅助访问等 |
| `send` | 字符串 | 按键名或组合键，如 `BackSpace`、`Control+c` |
| `text` | 字符串 | 事件文本，由事件处理流程解释 |
| `commit` | 字符串 | 直接提交文字 |
| `command` | 字符串 | 内置命令或支持的脚本名称 |
| `option` | 字符串 | 命令参数 |
| `select` | 字符串 | 切换目标，如键盘 ID |
| `toggle` | 字符串 | Rime 状态开关名称 |
| `states` | 字符串数组 | 状态对应的显示标签 |
| `preview` | 字符串 | 事件的弹出提示文本，不是配色中的 preview 表 |
| `shift_lock` | 字符串 | Shift 锁定方式，内置示例为 `click`、`double`、`long` |
| `sticky` | 布尔 | 粘滞事件标志，默认 false |
| `repeatable` | 布尔 | 按住重复执行，默认 false |
| `functional` | 布尔 | 功能事件标志；Event 表读取默认 true，不等同于配色 `style = "functional"` |
| `index` | 整数 | Event 表支持的索引参数，实际含义取决于事件处理分支 |

以下片段可加入已有主题的 `preset_keys` 表：

```lua
preset_keys.MySymbols = { label = "符号", send = "Eisu_toggle", select = "symbols_ext" }
preset_keys.MyReturn = { label = "返回", send = "Eisu_toggle", select = ".default" }
preset_keys.MyDate = { label = "日期", command = "date", option = "yyyy-MM-dd" }
preset_keys.MyPhrase = { label = "问候", commit = "你好！" }
preset_keys.MyCopy = { label = "复制", send = "Control+c" }
```

常用内置事件包括 `Keyboard_symbols`、`Keyboard_number`、`Keyboard_clipboard`、`Keyboard_editor`、`Keyboard_default`、`Keyboard_switch`、`BackSpace`、`Return`、`space`、`Shift_L`。`.default` 表示返回默认键盘，`.next` 用于键盘切换。内置命令示例包括 `date`、`deploy`、`commit`、`add_phrase`、`filter`、`gpt`、`run`；参数格式应参照内置主题中同类事件，不宜自行假定任意命令都可用。执行 Lua 脚本、网络请求或打开外部应用可能涉及隐私及设备操作，只使用可信主题。

### 3. 配色属性逐项参考

#### 3.1 全局与普通按键

| 属性 | 类型/单位 | 含义 |
|---|---|---|
| `name`、`author` | 字符串 | 配色名称、作者 |
| `background` | ARGB 或资源名 | 输入区域主背景 |
| `height` | dp | 输入界面的总高度，通常为键盘高度加候选栏高度 |
| `keyboard.height` | dp | 键盘主体高度，实际还受用户高度缩放影响 |
| `keyboard.background` | ARGB 或资源名 | 键盘主体背景 |
| `key.background` | ARGB 或资源名 | 普通按键背景 |
| `key.text_color` | ARGB | 主标签颜色 |
| `key.text_size` | dp 整数 | 主标签字号 |
| `key.font` | 字符串或数组 | 字体文件或字体集 |
| `key.corner_radius` | dp | 纯色背景圆角 |
| `key.stroke_color` | ARGB | 纯色背景描边颜色 |
| `key.stroke_width` | dp | 描边宽度，0 表示无描边 |
| `key.elevation` | dp | 阴影高度 |
| `key.shadow_color` | ARGB | 阴影颜色，着色效果依赖 Android 版本 |
| `key.gravity` | 字符串 | 主标签对齐，如 `center`、`top\|left` |
| `key.offset_x`、`key.offset_y` | dp | 标签水平、垂直偏移，正值向右、向下 |
| `key.padding` | 表 | 按键内容内边距 |
| `key.margins` | 表 | 按键外边距，影响背景之间的间隔 |

`padding` 与 `margins` 的字段均为 `left`、`top`、`right`、`bottom`，单位 dp。背景是图片时，不能假设数值背景的圆角、描边处理同样作用到图片。字体、背景和阴影也取决于具体组件是否调用相应读取方法。

```lua
key.gravity = "center"
key.offset_x = 0
key.offset_y = 2
key.padding = { left = 2, top = 2, right = 2, bottom = 2 }
key.margins = { left = 2, top = 2, right = 2, bottom = 3 }
```

#### 3.2 按下状态、动画与遮罩

| 属性（相对 `key.pressed`） | 含义 |
|---|---|
| `background` | 按下背景，可使用颜色或图片 |
| `text_color` | 按下文字颜色 |
| `corner_radius`、`stroke_width`、`stroke_color` | 按下背景为颜色时的形状与边框 |
| `scale_x`、`scale_y` | 宽高缩放倍数，1 为原尺寸，0.9 为缩小 10% |
| `translation_x`、`translation_y` | 按下时水平和垂直移动，dp |
| `translation_z` | 按下时 Z 轴位移，影响叠放和阴影 |
| `shadow_color` | 按下阴影颜色 |
| `mask` | 按下遮罩样式表 |

普通按键按下时不重新设置字号、字体、内外边距，因此不应配置 `pressed.text_size` 等作为普通按键按下效果。遮罩的正确路径为 `key.pressed.mask`，不是旧示例中的 `key.mask`。

```lua
key.pressed.mask = {
    show = true, background = 0x330000ff,
    width = 32, height = 32, corner_radius = 16,
    scale_x = 1.2, scale_y = 1.2,
    translation_x = 0, translation_y = 0, translation_z = 1,
}
```

遮罩宽高为 dp，未指定时使用匹配按键的布局尺寸；`show = false` 禁止创建可见遮罩。非键盘组件是否出现遮罩取决于其交互路径，不能只看是否创建 View。

#### 3.3 六种助记

| 键盘内容字段 | 配色样式路径 | 位置 |
|---|---|---|
| `hint_long` 或长按事件标签 | `key.long_click` | 可配置 gravity |
| `hint` | `key.hint` | 可配置 gravity |
| `hint_up` | `key.hint.up` | 顶部 |
| `hint_down` | `key.hint.down` | 底部 |
| `hint_left` | `key.hint.left` | 左侧 |
| `hint_right` | `key.hint.right` | 右侧 |

六种助记均支持 `show`、`text_color`、`text_size`、`font`、`offset_x`、`offset_y`、`pressed.text_color`。方向助记的位置由代码固定；必须存在相应方向样式表才会创建对应控件，缺少内容时也不会凭空生成标签。方向助记可按对应滑动事件标签回退，位置冲突时存在隐藏其他助记的逻辑。长按和单助记与四方向助记统一由 `mHints` / `mHintStyles` 管理按下颜色。

```lua
key.hint = { show = true, text_size = 10, text_color = 0xff666666, gravity = "bottom" }
key.hint.up = { show = true, offset_y = 1, pressed = { text_color = 0xffff0000 } }
key.hint.down = { show = true }
key.hint.left = { show = true }
key.hint.right = { show = true }
key.long_click = { show = true, gravity = "top|right", text_size = 10,
    pressed = { text_color = 0xff315da8 } }
```

正确的单助记按下颜色为 `key.hint.pressed.text_color`，长按为 `key.long_click.pressed.text_color`，不是 `key.pressed.hint.text_color` 或 `key.pressed.long_click.text_color`。助记是 TextView/ImageSpan，不支持独立背景、圆角、阴影及边框；图片助记按下时不会仅凭 TextView 的颜色变化重新着色。

#### 3.4 Preview 与弹出键盘

`key.preview` 是按键上方的临时提示，`popup` 是多按键选择面板，两者不同。Preview 由 `hasKey("preview")` 判断是否创建，移除实际样式表的该字段可关闭（注意继承与共享关系），不依赖 `preview.show`。

| Preview 属性 | 含义 |
|---|---|
| `width`、`height` | 提示框尺寸，dp |
| `background` | 背景颜色或图片 |
| `text_size`、`text_color` | 提示文字字号与颜色 |
| `corner_radius`、`stroke_width`、`stroke_color` | 纯色背景形状 |
| `elevation`、`shadow_color` | 阴影 |
| `scale_x`、`scale_y` | 提示动画缩放 |

Preview 固定居中布局并悬浮定位，不是普通键盘中的一行；初始化未单独设置 `preview.font` 或 `preview.gravity`。事件中的 `preview` 可指定提示内容。工具栏和候选按钮即使复用 KeyView，也不因此保证具有键盘内的 Preview 行为。

```lua
popup.column_count = 5
popup.background = 0xffeeeeee
popup.corner_radius = 8
popup.elevation = 8
popup.key.text_size = 18
popup.key.width = 10
popup.key.height = 15
```

`popup.key` 是弹出面板按键样式，宽高与普通 KeyView 的样式尺寸不能直接类比；建议以默认配置为起点，并结合实际面板验证。键盘按键 `popup = { "ā", "á", "ǎ", "à" }` 提供选择内容，触发方式还受软件长按弹出设置影响。

#### 3.5 声音、振动和重复触发

| 字段 | 类型 | 含义 |
|---|---|---|
| `long_click_time` | 毫秒 | 长按阈值，KeyStyle 默认 1000 |
| `repeat_click_time` | 毫秒 | 重复事件间隔，默认 200 |
| `sound_enabled` | 布尔 | 开启声音 |
| `sound_effect` | 字符串 | 声音资源文件名 |
| `vibration_enabled` | 布尔 | 开启振动 |
| `vibration_effect` | 二维数组 | 时间序列及振幅序列，振幅限制 0–255 |

```lua
key.vibration_enabled = true
key.vibration_effect = { { 0, 18 }, { 0, 100 } }
key.long_click.vibration_enabled = true
-- key.sound_effect = "click.wav"  -- 需存在资源
-- key.sound_enabled = true
```

声音先查找配色目录，再查找主题 `sounds/`。振动波形的两个数组应等长且时间非负；自定义波形依赖 Android API 26 及设备支持。反馈是否触发还取决于具体事件和全局设置。

#### 3.6 特殊样式与继承

`space`、`enter`、`enter2`、`functional` 是命名样式，不是自动识别所有按键用途的开关。键盘可显式引用 `style = "enter"`。`Key` 未指定 style 时通常使用 click 字符串作为查找名称，找不到则回退到基础 key 样式。

```lua
number_key = table.clone(key)
number_key.background = 0xffedf3fc
number_key.text_size = 20
-- 在键盘中：{ click = "1", style = "number_key" }
```

`functional = key` 表示共享引用，改变任一表都会改变同一对象；`table.clone(key)` 创建副本，不应把它当作任何复杂对象都可无条件深复制的机制；Java `getKeyStyle(name, parent)` 还会为缺失字段设置继承。`pressed` 默认继承自身普通样式、方向助记继承 `hint`，`toolbar.key` 回退到全局 `key`，`toolbar.hide` 回退到 `toolbar.key`，`composition.key` 回退到全局 `key`。独立组件不一定继承路径的上一级；嵌套表也不一定逐字段合并。修改后重新加载，避免样式缓存保留旧值。

### 4. 输入区域及扩展面板

#### 4.1 横向候选栏

| 路径 | 作用 |
|---|---|
| `candidate.height` | 横向候选栏高度（dp） |
| `candidate.background` | 整体背景 |
| `candidate.text_size`、`text_color`、`font` | 候选正文样式 |
| `candidate.elevation`、`shadow_color` | 候选栏阴影 |
| `candidate.pressed.background`、`text_color` | 高亮候选背景和文字 |
| `candidate.comment.text_size`、`text_color`、`font` | 候选注释样式 |
| `candidate.comment.pressed.text_color` | 高亮注释颜色 |
| `candidate.key.text` | 更多候选按钮的文字或 PNG 图标名 |
| `candidate.key` | 更多按钮的普通外观与按下样式 |

候选项并不是普通键盘 KeyView，长按忘词也不是 `long_click` 助记样式；更多候选按钮不加载键盘助记。候选文字与注释的实际布局参考 `CandidateAdapter.java`，不要将 Composition 的窗口样式当成横向候选栏样式。

#### 4.2 工具栏

`toolbar` 控制背景、阴影等容器样式；`toolbar.key` 控制按钮，`toolbar.hide` 控制收起按钮，`toolbar.hide.text` 是标签；`toolbar.schema_switches` 控制是否显示方案开关；`toolbar.keys` 是有序按钮列表。

```lua
toolbar.schema_switches = true
toolbar.keys = {
    "Mode_switch", "Keyboard_clipboard",
    { label = "菜单", send = "Control+grave" },
    { click = "Keyboard_symbols", long_click = "Keyboard_symbols_ext", style = "functional" },
    { name = "ascii_mode", options = { "ascii_mode" }, states = { "中", "英" }, reset = 0 },
}
toolbar.hide.text = "bunny.png"
```

最后一个开关示例展示源码支持的 `options` / `states` / `name` / `reset` 表形式；具体开关应与方案中实际选项匹配。普通事件按钮、方案开关、收起按钮并不都具有完整 Key 的助记能力；完整事件表才可能加载对应助记。`toolbar.hide` 不支持键盘长按文字与方向助记。

#### 4.3 展开候选、符号和剪贴板

| 样式路径 | 含义 |
|---|---|
| `candidate.expanded` | 展开候选面板背景及文字样式 |
| `candidate.expanded.pressed` | 高亮样式，内置示例包含 ripple_color |
| `candidate.expanded.comment` | 展开候选注释 |
| `candidate.expanded.key` | 面板功能按钮 |
| `candidate.expanded.filter_bar.show` | 笔画筛选栏是否显示 |
| `candidate.expanded.filter_bar.gravity` | 筛选栏方位 |
| `candidate.expanded.tool_bar.gravity`、`keys` | 工具栏方位及按钮顺序 |
| `symbol` | 符号面板整体样式 |
| `symbol.text` | 符号内容项样式 |
| `symbol.key` | 符号面板功能按钮 |
| `symbol.tab_bar` | 分类标签栏，含 gravity、indicator_color、height |
| `symbol.tool_bar` | 工具栏，含 gravity、keys、height |
| `clipboard` | 剪贴板整体样式 |
| `clipboard.item` | 剪贴板内容项样式及 padding |
| `clipboard.key` | 剪贴板功能按钮 |
| `clipboard.tab_bar`、`clipboard.tool_bar` | 分类标签与工具栏 |

`tab_bar.gravity` 常用 top/bottom；工具栏常用 left/top/right/bottom。工具栏内置标识 `hide`、`page_up`、`page_down` 与普通预设事件名称不同，由面板代码处理。不能对这些面板按钮套用普通按键全部助记属性。

#### 4.4 预编辑与 Composition

`preedit` 用于预编辑文字区域，可配置 `text_size`、`text_color`、`background`；`inline` 选择嵌入编辑模式，内置示例有 `none`、`input`、`composition`、`preedit`。`composition` 是独立悬浮窗口。

| Composition 属性 | 含义 |
|---|---|
| `show` | 是否显示悬浮预编辑窗口 |
| `text_color`、`text_size` | 编码文字样式 |
| `background` | 窗口背景颜色或资源 |
| `position` | 窗口位置，如 fixed、left、right、left_up、right_up、bottom_left、bottom_right、top_left、top_right |
| `min_length` | 候选进入窗口的最小长度条件 |
| `max_length` | 候选换行长度条件 |
| `sticky_lines` | 固顶行相关布局设置 |
| `max_entries` | 最大候选条目数，-1 表示不限制 |
| `all_phrases` | 是否继续收集满足条件的词语 |
| `use_cursor` | 是否使用候选光标高亮 |
| `min_width`、`max_width`、`min_height`、`max_height` | 窗口尺寸限制，dp |
| `padding` | 四边内边距，dp |
| `line_spacing` | 附加行距，px |
| `line_spacing_multiplier` | 行距倍数 |
| `spacing` | 与预编辑区或边缘的距离 |
| `border`、`round_corner`、`elevation` | 内置窗口边框、圆角、阴影相关配置；具体效果以窗口实现为准 |
| `movable` | 字符串 true/false/once，控制窗口移动 |
| `pressed.text_color`、`pressed.background` | 高亮编码样式 |
| `key` | 窗口功能键文字样式 |
| `key.hint` | 候选序号样式 |
| `key.pressed.hint.text_color` | 高亮候选序号颜色（特殊路径） |
| `window` | 有序窗口组件表 |

窗口的候选正文和注释还复用全局 `candidate`、`candidate.pressed`、`candidate.comment`、`candidate.comment.pressed`。`composition.key.pressed.hint.text_color` 是 Composition 的专用读取方式，不能推广到普通键盘助记。

```lua
composition.window = {
    { start = "", move = "✎ ", ["end"] = "" },
    { start = "", composition = "%s", ["end"] = "", letter_spacing = 0 },
    { start = "\n", label = "%s.", candidate = "%s", comment = " %s", sep = " ", ["end"] = "" },
}
```

组件 `start` / `end` 是前后文本，`sep` 是条目分隔符，`composition` / `label` / `candidate` / `comment` 使用格式字符串，`move` 是拖动标记。源码还处理 `click`、`when`、`align` 等组件字段，复杂条件建议参考 `Composition.java` 中对应分支。整体配色预览只展示一个 Composition 候选是编辑器策略，并不会修改真正窗口的 `max_entries`。

### 5. 键盘自定义深入说明

#### 5.1 按键字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `click` | 字符串或事件表 | 单击 |
| `long_click` | 同上 | 长按 |
| `swipe_up` / `swipe_down` | 同上 | 上滑 / 下滑 |
| `swipe_left` / `swipe_right` | 同上 | 左滑 / 右滑 |
| `combo` | 同上 | 组合事件 |
| `label` | 字符串 | 主标签覆盖 |
| `description` | 字符串 | 按键描述 |
| `hint` / `hint_long` | 字符串 | 单助记 / 长按助记 |
| `hint_up/down/left/right` | 字符串 | 对应方向助记（完整字段名分别为 hint_up 等） |
| `style` | 字符串 | 配色中的样式名称 |
| `ascii` | 字符串或表 | ASCII 模式事件，含 click 的表可定义独立 ASCII 按键 |
| `composing` | 字符串 | 组合输入状态下的事件 |
| `has_menu` | 字符串 | 存在候选菜单时的事件 |
| `paging` | 字符串 | 翻页状态下的事件 |
| `send_bindings` | 布尔 | 是否按绑定事件逻辑处理；未设置且无状态事件时为 false |
| `swipe_repeatable` | 布尔 | 滑动重复触发标志 |
| `popup` | 字符串数组 | 弹出键盘内容 |
| `commit` | 字符串 | 在未指定 click 时可作为直接上屏事件定义 |

布局字段 `width`、`height`、`grow`、`x`、`y` 的含义必须按布局区分。

```lua
{
    click = "a", label = "A", ascii = "A",
    long_click = "1", hint_long = "1",
    swipe_up = "MyCopy", hint_up = "复制",
    swipe_down = { commit = "@" }, hint_down = "@",
    swipe_left = "Left", hint_left = "←",
    swipe_right = "Right", hint_right = "→",
    style = "number_key",
}
```

这是单个按键表，需放在对应布局数组中；`MyCopy`、`number_key` 需先在主题、配色中定义。标签和助记只负责显示，实际执行由事件决定。字符串事件可引用预设名、按键名或输入文本，复杂事件建议明确写成表。可视化编辑器支持从预设中选择或直接输入事件。

#### 5.2 三种通用布局及符号布局的差别

| 字段 | Row | Flexbox | Abs |
|---|---|---|---|
| 根配置 | rows | flex_box | keys |
| 尺寸单位 | 百分比 | dp | 百分比 |
| 行/容器 | 行表 keys | 嵌套行列或 keys | 无 |
| `key_width` / `key_height` | 支持 | 不读取 | 支持 |
| `width` / `height` | 行及按键 | 子容器及按键 | 按键 |
| `grow` | 不支持 | 支持 | 不支持 |
| `direction` | 不支持 | row/column | 不支持 |
| `x` / `y` | 不支持 | 不支持 | 支持 |
| `style` | 键盘、行、按键 | 键盘、容器、按键 | 键盘、按键 |

加载器按 `rows` → `flex_box` → `keys` → `key_maps` 的顺序判断布局，建议每个文件只定义一种。Row/Abs 的 `layout` 虽被读取，但当前按键坐标计算没有使用它。

**Row**：默认行高为总高度除以行数，默认键宽为总宽度除以首行按键数；`key_height`、`key_width` 可覆盖。每个按键优先自身 width/height，再行 width/height，再全局默认。行高控制下一行 Y 坐标；单键高度变化不自动改变下一行起点。行 `style` 只绘制行背景，不覆盖行内按键的样式。各行宽度合计可以不足 100%，超出则可能被裁切；无自动权重压缩。

```lua
name = "混合宽度"
key_width = 25
key_height = 50
rows = {
    { style = "number_row", width = 20, keys = {
        { click = "1" }, { click = "2" }, { click = "3" },
        { click = "BackSpace", width = 40, style = "functional" },
    } },
    { keys = {
        { click = "Keyboard_default" }, { click = "space", width = 50 },
        { click = "Return", style = "enter" },
    } },
}
```

**Flexbox**：默认 direction=row，默认 grow=1。父容器为 row 时，正数 width 固定主轴宽度并取消 grow；为 column 时，正数 height 固定主轴高度并取消 grow。另一轴不指定正数尺寸时填满。固定尺寸的项目不参与收缩，容器默认不换行。根 `flex_box` 的 width/height/grow 不通过子项 LayoutParams 生效；可配置 direction 和 style。

```lua
name = "数字编辑区"
flex_box = {
    direction = "row",
    { width = 60, direction = "column", keys = {
        { click = "Left" }, { click = "Right" },
    } },
    { grow = 2, direction = "column",
        { direction = "row", keys = { { click = "1" }, { click = "2" }, { click = "3" } } },
        { direction = "row", keys = { { click = "4" }, { click = "5" }, { click = "6" } } },
    },
    { grow = 1, direction = "column", keys = {
        { click = "BackSpace", style = "functional" }, { click = "Return", style = "enter" },
    } },
}
```

**Abs**：左上角为 (0,0)，右下角为 (100,100)。默认 x/y=0、key_width=20、key_height=10。实际像素坐标为总宽或总高乘以百分比再除以 100。重叠是允许的，数组顺序影响叠放，删除或插入不自动移动其他按键。注意不要让 x+width 或 y+height 超出预期画布。

**分类符号布局**：

```lua
name = "常用符号"
key_maps = {
    { name = "中文", keys = { "，", "。", "？", "！", "……" } },
    { name = "数学", keys = { "+", "−", "×", "÷", "=" } },
}
```

该格式由 `SymbolsKeyboardView` 创建分类标签及符号列表，样式来自 `symbol` 分组。目前通过源码编辑，不使用 Row/Flexbox/Abs 的按键结构编辑功能。

### 6. 资源、调试与源码索引

背景和 PNG 标签首先查找当前配色目录，其次主题 `images/`，最后公共 `images/`；字体采用对应的 `fonts/` 查找顺序。图标标签可写 `bunny` 或 `bunny.png`，`getSpan()` 会尝试加载 PNG 并依据颜色和字号生成 ImageSpan；找不到则显示文字。图片背景与图片标签的处理方式不同。请勿把 Android 应用首页的 VectorDrawable XML 当作 Lua 主题 PNG 标签直接使用。

修改配置后先保存补丁、重新加载主题或配色，再用实际键盘验证。若不生效，检查文件名与配置 ID、补丁是否有效、组件是否读取该属性、样式是否共享、方向助记是否定义、资源路径和事件名是否正确。Lua 配置通常不需要 Rime 重新部署；修改 Rime YAML 或词典才需要部署。不要把 API Key 或私人词典提交到公开仓库。

| 源码（相对 `java/com/osfans/trime/`） | 用途 |
|---|---|
| `theme/ThemeManager.java` | 主题、配色、键盘选择与服务配置 |
| `theme/Style.java`、`theme/KeyStyle.java` | 样式读取、单位转换、继承、图标与反馈 |
| `Key.java`、`Event.java` | 按键、状态、事件字段 |
| `keyboard/KeyView.java` | 主标签、mHints、按下状态、遮罩和 Preview |
| `keyboard/RowKeyboardView.java` | 行布局与行样式 |
| `keyboard/FlexboxKeyboardView.java` | 弹性布局 |
| `keyboard/AbsKeyboardView.java` | 绝对坐标布局 |
| `keyboard/SymbolsKeyboardView.java` | 分类符号布局 |
| `candidate/ToolbarView.java`、`candidate/CandidateView.java`、`candidate/CandidateAdapter.java` | 工具栏与横向候选栏 |
| `Composition.java` | 悬浮窗口组件及样式 |
| `editor/PaletteProperties.java`、`editor/PaletteCapabilities.java` | 可视化属性目录及过滤规则 |

内置示例位于 `assets/themes/default/main.lua`、`assets/themes/default/styles/light/main.lua` 及 `assets/themes/default/keyboards/`。标准 Android 工程通常在 `app/src/main/` 下包含上述目录。以实际渲染器为最终依据：内置配置中也可能残留历史字段，编辑器的模拟预览不能证明任意字段都在运行时生效。

## 内置编辑器

| 编辑器 | 功能 |
| --- | --- |
| 主题编辑器 | 基本信息、默认配色与键盘、AI/ASR、Preset keys 管理及 Lua 源码编辑 |
| 配色编辑器 | 按组件组织属性，HSV 调色板、透明度、数值滑动条、继承和共享关系管理、即时预览 |
| 键盘编辑器 | 三种布局可视化、加载当前配色与助记、行和容器属性、长按插入或删除按键、预设事件选择与手动输入 |
| 方案编辑器 | YAML 结构化编辑、属性中文说明、引擎组件管理、源码高亮、空白符及常用属性补全 |

可视化预览采用模拟渲染，复杂动态 Lua、运行时输入状态和部分布局效果可能与实际输入法有所差异。修改后请在实际输入环境测试。

## 独立补丁与源码合并

主题、配色及键盘的可视化修改默认保存为对应的 `.custom.lua` 文件。例如 `qwerty26.lua` 对应 `qwerty26.custom.lua`，`main.lua` 对应 `main.custom.lua`。

加载时先执行原始配置，再应用对应补丁；保存时重新计算最终差异，避免不断向原始源码追加历史修改。键盘的添加、删除和重排以最终结构为准。

更多菜单提供原始源码编辑、补丁源码编辑、清除补丁及合并补丁到源码。普通保存不会主动合并；合并操作需预览、确认和校验，并备份相关文件。对于无法安全合并的动态 Lua 结构，编辑器可能拒绝合并。原始文件发生外部修改后，旧补丁可能因校验不一致而无法应用，应先备份并核对。

Lua 补丁由中文兔实现，与 Rime 原生 `.custom.yaml` 定制机制不同，不可混用。编辑和保存源码前建议备份整个主题或方案组。

## Rime 输入方案配置

中文兔的主题配置负责显示和交互，Rime 方案负责输入编码、词典、候选转换及选词等逻辑。将所需方案、词典和依赖文件放入对应 `schemas/<方案组>/`，通过管理方案启用，然后重新部署。

推荐使用 Rime 原生用户定制文件，避免直接修改上游方案。例如 `default.custom.yaml`：

```yaml
patch:
  menu/page_size: 7
```

方案专属修改可保存为 `<schema_id>.custom.yaml`。例如对已安装的朙月拼音配置 `luna_pinyin.custom.yaml`：

```yaml
patch:
  translator/enable_completion: true
```

配置是否生效取决于实际方案、组件及 librime 版本。更多字段说明、方案编写方式和补丁语法请参阅 Rime 官方文档。

## Rime 相关资源

| 项目 | 链接 |
| --- | --- |
| Rime GitHub 组织 | <https://github.com/rime> |
| Rime 官网 | <https://rime.im/> |
| librime 核心引擎 | <https://github.com/rime/librime> |
| Rime 社区及文档 | <https://github.com/rime/home> |
| 输入方案设计书 | <https://github.com/rime/home/wiki/RimeWithSchemata> |
| 定制指南 | <https://github.com/rime/home/wiki/CustomizationGuide> |
| 默认配置及基础资源 | <https://github.com/rime/rime-prelude> |
| 朙月拼音 | <https://github.com/rime/rime-luna-pinyin> |
| 双拼方案 | <https://github.com/rime/rime-double-pinyin> |
| 五笔方案 | <https://github.com/rime/rime-wubi> |
| Rime 配方管理工具 plum | <https://github.com/rime/plum> |
| librime-lua 扩展 | <https://github.com/hchunhui/librime-lua> |
| 同文输入法 | <https://github.com/osfans/trime> |

上述为相关上游项目及学习资料，不代表所有插件和方案均已内置或适配当前版本。

## 反馈与贡献

欢迎通过 [Issues](https://github.com/nirenr/trime2/issues) 提交问题、功能建议、主题与配色使用反馈。反馈问题时建议附上 Android 版本、软件版本、复现步骤、相关配置及错误日志，并移除 API Key、私人词典和个人输入内容。

## 致谢

感谢 Rime 社区、同文输入法及 LuaJ、AndroLua、TextWarrior 等相关项目的开发者与贡献者，以及所有参与输入方案、词典、主题和配色制作的用户。

开发者：**[nirenr](https://github.com/nirenr)**
