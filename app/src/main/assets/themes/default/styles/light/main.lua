name = "白昼"
author = "nirenr"
--输入主颜色或图片
background = 0xffdddddd

--键盘
keyboard = {
    --键盘高度
    height = 240,
    --键盘背景颜色或图片
    background = 0xffdddddd,
    --指定样式全局字体或字体集
    --font="b.ttf"
}

--默认按键样式
key = {
    --按键文字颜色
    text_color = 0xff000000,
    --按键文字大小
    text_size = 22,
    --按键背景颜色或图片
    background = 0xffffffff,
    --按键阴影高度
    elevation = 4,
    --按键圆角半径
    corner_radius = 8,
    --按键背景颜色
    shadow_color = 0xff000000,
    --按键长按超时
    long_click_time = 1000,
    --按键重复执行间隔
    repeat_click_time = 200
    --按键字体
    --font="b.ttf"
    --font={"a.ttf","b.ttf"}
}
--按键四周留白
key.margins = {
    left = 2,
    top = 2,
    right = 2,
    bottom = 3
}
--按键助记
--在按键使用hint默认hint,或者使用hint_up,hint_down,hint_left,hint_right定义四个方向hint
key.hint = {
    show = true,
    --助记文字颜色
    text_color = 0xff444444,
    --助记文字大小
    text_size = 12
}
key.hint.up = {
    show = true,
    --助记文字颜色
    text_color = 0xff444444,
    --助记文字大小
    text_size = 12
}
key.hint.down = {
    show = true,
    --助记文字颜色
    text_color = 0xff444444,
    --助记文字大小
    text_size = 12
}
key.hint.left = {
    show = true,
    --助记文字颜色
    text_color = 0xff444444,
    --助记文字大小
    text_size = 12
}
key.hint.right = {
    show = true,
    --助记文字颜色
    text_color = 0xff444444,
    --助记文字大小
    text_size = 12
}
--按键长按
key.long_click = {
    show = true,
    --长按文字颜色
    text_color = 0xff444444,
    --长按文字大小
    text_size = 12,
    vibration_enabled = true, --震动开关
    --定义位置
    --gravity="top|left",
    --定义偏移
    --offset_x=10,
    --offset_y=10,
}
--按键按下状态
key.pressed = {
    --宽度缩放
    scale_x = 0.9,
    --高度缩放
    scale_y = 0.9,
    --高度改变
    translation_z = 8,
    --水平移动
    translation_x = 0,
    --垂直移动
    translation_y = 0,
    --阴影颜色
    shadow_color = 0xff00ffff,
    --背景按键颜色或图片
    background = 0xff888888,
    text_color = 0xffffffff,
    --助记文字颜色
    hint = {
        text_color = 0xff444444,
    },
    --长按文字颜色
    long_click = {
        text_color = 0xff444444,
    }
}
--按键预览
key.preview = {
    --宽度缩放
    scale_x = 1.2,
    --高度缩放
    scale_y = 1.2,
    text_color = 0xff000000,
    text_size = 22,
    background = 0xffffffff,
    elevation = 16,
    corner_radius = 16,
    --边框颜色
    stroke_color = 0x88dddddd,
    --边框宽度
    stroke_width = 1,
    shadow_color = 0xffff0000
}

--弹出键盘
popup = {
    elevation = 16,
    corner_radius = 8,
    --键盘背景颜色或图片
    background = 0xffdddddd,
    --边框颜色
    stroke_color = 0x88dddddd,
    --边框宽度
    stroke_width = 1,
    shadow_color = 0xff000000
}
popup.key = table.clone(key)
popup.key.text_size = 18
popup.key.width = 10
popup.key.height = 15
popup.key.preview = nil

space = table.clone(key)
space.text_size = 18

--回车键样式，需要在回车键定义style="enter"
enter = table.clone(key)
enter.text_size = 18
enter.background = 0xff1976D2
enter.pressed.background = 0xff1565C0
enter.pressed.text_color = 0xff000000
--禁止预览
enter.preview = nil

enter2 = table.clone(enter)
enter2.corner_radius = 32
enter2.preview = nil

--功能键样式，需要在功能按键定义style="functional"
functional = table.clone(key)
functional.text_size = 18
functional.background = 0xffaaaaaa
functional.pressed.background = 0xff888888
functional.pressed.text_color = 0xffffffff
functional.preview = nil

--符号面板
symbol = {
    background = 0xffdddddd,
    text_size = 22,
    text_color = 0xff000000,
    indicator_color = 0xFF0055FF
}
symbol.text = table.clone(key)
symbol.key = {
    text_color = 0xff000000,
    text_size = 18,
    background = 0xffeeeeee,
    elevation = 2,
    corner_radius = 8,
    shadow_color = 0x800000ff
}
symbol.key.pressed = {
    scale_x = 0.9,
    scale_y = 0.9,
    translation_z = -1,
    translation_x = 0,
    translation_y = 0,
    shadow_color = 0xff00ffff,
    background = 0xffaaaaaa,
}
symbol.tool_bar = {
    gravity = "right", --left,top,right,bottom
    keys = { "hide", "page_up", "page_down", "BackSpace" }
}

--候选栏样式
candidate = {
    height = 48,
    background = 0xffdddddd,
    text_size = 22,
    text_color = 0xff000000,
    elevation = 2,
    shadow_color = 0xff000000
}

candidate.pressed = {
    background = 0x44888888,
    text_color = 0xff000000,
    corner_radius = 0,
}

candidate.comment = {
    text_size = 12,
    text_color = 0xff444444
}
candidate.comment.pressed = {
    text_size = 12,
    text_color = 0xff444444
}

candidate.key = {
    text = "▽",
    text_color = 0xff000000,
    text_size = 18,
    background = 0xffdddddd,
    elevation = 0,
    corner_radius = 8,
    shadow_color = 0x800000ff
}
candidate.key.pressed = {
    scale_x = 0.9,
    scale_y = 0.9,
    translation_z = 2,
    translation_x = 0,
    translation_y = 0,
    shadow_color = 0xff00ffff,
    background = 0xffaaaaaa,
}
--候选面板样式
candidate.expanded = {
    background = 0xffdddddd,
    text_size = 22,
    text_color = 0xff000000,
}
--笔画筛选栏
candidate.expanded.filter_bar = {
    gravity = "bottom", --left,top,right,bottom
    show = true--设置为false隐藏
}

candidate.expanded.tool_bar = {
    gravity = "right", --left,top,right,bottom
    keys = { "hide", "page_up", "page_down", "char_filter", "BackSpace" }
}

candidate.expanded.pressed = {
    background = 0xffffffff,
    ripple_color = 0x40000000,
}
candidate.expanded.comment = {
    text_size = 12,
    text_color = 0xff444444
}

candidate.expanded.key = {
    text_color = 0xff000000,
    text_size = 18,
    background = 0xffeeeeee,
    elevation = 2,
    corner_radius = 8,
    shadow_color = 0x800000ff
}
candidate.expanded.key.pressed = {
    scale_x = 0.9,
    scale_y = 0.9,
    translation_z = -1,
    translation_x = 0,
    translation_y = 0,
    shadow_color = 0xff00ffff,
    background = 0xffaaaaaa,
}

--剪贴板样式
clipboard = table.clone(candidate.expanded)
clipboard.item = table.clone(key)
clipboard.item.text_size = 14
clipboard.item.padding = {
    left = 4,
    top = 4,
    right = 4,
    bottom = 4
}
clipboard.tool_bar = {
    gravity = "right", --left,top,right,bottom
    keys = { "hide", "page_up", "page_down", "undo" }
}

--工具栏样式
toolbar = table.clone(candidate)
--显示方案定义的开关
toolbar.schema_switches = false
toolbar.hide = table.clone(candidate.key)
--支持添加preset_keys按键，也可以直接写事件的表，
--可以指定按键的style
toolbar.keys = { { label = "菜单", send = "Control+grave" }, "Mode_switch", "Keyboard_clipboard", "Mode_small", "Mode_float" }
toolbar.key.text_size = 22
toolbar.key.padding = {
    left = 8,
    top = 0,
    right = 8,
    bottom = 0
}

--提示区样式
preedit = {
    text_size = 18,
    text_color = 0xff222222,
    background = 0xaaffffff,
    inline="none"--嵌入式编辑 input,composition,preedit
}
composition = {
    show = true,
    text_color = 0xff222222,
    background = 0xaaffffff,
    position = "fixed", -- 位置：left|right|left_up|right_up|fixed|bottom_left|bottom_right|top_left|top_right
    min_length = 8, -- 最小词长,超过字数的显示在悬浮窗
    max_length = 10, -- 超过字数则换行
    sticky_lines = 0, -- 固顶行数
    max_entries = -1, -- 最大词条数,-1表示显示全部
    all_phrases = false, -- 所有满足条件的词语都显示在窗口
    border = 2, -- 边框宽度
    max_width = 230, -- 最大宽度，超过则自动换行
    max_height = 400, -- 最大高度
    min_width = 40, -- 最小宽度
    min_height = 0, -- 最小高度
    padding = {
        left = 5,
        top = 5,
        right = 5,
        bottom = 5
    },
    line_spacing = 0, -- 候选词的行间距(px)
    line_spacing_multiplier = 1.2, -- 候选词的行间距(倍数)
    spacing = 1, -- 与预编辑或边缘的距离
    round_corner = 8, -- 窗口圆角
    elevation = 5, -- 阴影
    background = 0xaaaaaaaa, -- 颜色或者图片文件名
    movable = "false", -- 是否可移动窗口，或仅移动一次 true|false|once

    pressed = {
        text_color = 0xff222222,
        background = 0xcccccccc
    },
    window = {
        -- 悬浮窗口组件
        {
            start = "",
            move = "✎ ",
            ["end"] = ""
        },
        {
            start = "",
            composition = "%s",
            ["end"] = "",
            letter_spacing = 0
        },
        {
            start = "\n",
            label = "%s.",
            candidate = "%s",
            comment = " %s",
            ["end"] = "",
            sep = " "
        }
    }
}

--总高度
height = keyboard.height + candidate.height

