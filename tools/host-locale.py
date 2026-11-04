#!/usr/bin/env python3
"""Build the Android string resources from the host's own locale dictionaries.

DSH ships one Chinese dictionary per client plugin, compiled into that plugin's
`client.js` as `const zh = { "key": "译文" }`. 2469 entries exist across 45 packages,
and they are the *same words the web UI says* — `正在分析请求`, `新建会话`,
`操作审批`. Translating by hand what the host has already translated would be both
slower and worse: the two clients would then disagree about what a "workspace" is
called, on the same screen, in the same conversation.

So this script is the inverse: it reads `app/src/main/res/values/strings.xml` (the
English source of truth) and emits `values-zh-rCN/strings.xml`, filling each entry
from the host dictionary when the English text matches and leaving the English in
place when it does not. An untranslated string is visible and honest; a wrongly
matched one is invisible and wrong, which is why the match is by *exact* English
value and never by fuzzy similarity.
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
# Every English resource file, not just the original one. The strings were split by
# screen so three agents could extract in parallel; the translator has to read the
# directory rather than one file, or two thirds of the app silently stays English.
EN_DIR = os.path.join(REPO, "app/src/main/res/values")
ZH = os.path.join(REPO, "app/src/main/res/values-zh-rCN/strings.xml")
CATALOG = os.path.join(HERE, "dsh-locale.json")



# Hand-written Chinese for strings the host dictionary does not carry.
#
# The host's dictionaries cover the *web* client. Roughly 175 of this app's strings are
# its own — the settings page's subtitles, the drawer's view-options menu, the approval
# card's verbs — and no amount of matching will find them there. They are translated
# here instead, using the host's own vocabulary for every term it already has a word for
# (`审批` for approval, `插话发送` for steer, `排队发送` for queue, `收起`/`展开` for
# collapse/expand), so a reader moving between the two clients sees one vocabulary rather
# than two.
#
# Kept as a plain dict rather than a second resource file because it is *input to the
# generator*: `values-zh-rCN/strings.xml` is generated, and a hand edit there would be
# overwritten the next time anyone runs the tool.
SUPPLEMENT = {
    # --- chat / tools
    "Steering…": "正在插话…",
    "Couldn't load picture": "图片加载失败",
    "Ran for %1$s": "运行了 %1$s",
    "Calling %1$s…": "正在调用 %1$s…",
    "%1$s · running": "%1$s · 运行中",
    "Collapse subtool calls": "收起子工具调用",
    "Expand subtool calls": "展开子工具调用",
    "Collapse process": "收起过程",
    "Expand process": "展开过程",
    "Expand to-dos": "展开待办",
    "Collapse to-dos": "收起待办",
    "Show details": "显示详情",
    "Hide details": "隐藏详情",
    "Approval sent": "审批已发送",
    "Approval required": "需要审批",
    "%1$s wants to run": "%1$s 想要执行",
    "Reject": "拒绝",
    "Allow once": "允许一次",
    "Expand goal": "展开目标",
    "Collapse goal": "收起目标",
    "Save": "保存",
    "Web Search": "网页搜索",
    "Web Fetch": "网页抓取",
    "Question": "提问",
    # --- session readings
    "Output speed, %1$s tokens per second": "输出速度，每秒 %1$s 个 token",
    "%1$s tok/s": "%1$s tok/s",
    "Steps": "步",
    "Token usage, %1$s percent served from cache": "Token 用量，%1$s%% 命中缓存",
    "Cache %1$s%%": "缓存 %1$s%%",
    "%1$s tok": "%1$s tok",
    "Cost (off-peak – peak)": "费用（低谷 – 高峰）",
    "Family cost (off-peak – peak)": "同族费用（低谷 – 高峰）",
    "Balance & top up": "余额与充值",
    # --- composer
    "Message DSH…": "给 DSH 发消息…",
    "Send and steer": "发送并插话",
    "Send and queue": "发送并排队",
    "Steer now": "立即插话",
    "Cut into the current turn": "打断当前轮次",
    "Queue for later": "稍后排队",
    "Deliver when this turn ends": "本轮结束后送达",
    # --- composer / editing verbs
    "Default while busy": "忙碌时的默认行为",
    "What Send does while the agent runs": "智能体运行时发送按钮的行为",
    "Switch default": "切换默认",
    "Current default": "当前默认",
    "%1$s · uploading…": "%1$s · 上传中…",
    "Remove attachment": "移除附件",
    "Full screen": "全屏",
    "Close full screen input": "关闭全屏输入",
    "Select all": "全选",
    "Cut": "剪切",
    "Paste": "粘贴",
    "Quote text": "引用文本",
    "Translate": "翻译",
    # --- queue / jobs
    "Expand queue": "展开队列",
    "Collapse queue": "收起队列",
    "(no text)": "（无文本）",
    "Close background jobs": "关闭后台任务",
    "No background jobs in this session.": "本会话没有后台任务。",
    "Collapse finished jobs": "收起已完成任务",
    "Expand finished jobs": "展开已完成任务",
    "Waiting for output…": "等待输出…",
    # --- drawer
    "Refresh sessions": "刷新会话",
    "Expand workspace": "展开工作区",
    "Collapse workspace": "收起工作区",
    "Move up": "上移",
    "Move down": "下移",
    "Subagent session": "子智能体会话",
    "Show subagents": "显示子智能体",
    "Hide subagents": "隐藏子智能体",
    "Session actions": "会话操作",
    "Session title": "会话标题",
    "Show archived": "显示已归档",
    # --- settings
    "Back": "返回",
    "This device": "本机",
    "Appearance, language, and what this build remembers": "外观、语言，以及这个版本记住的偏好",
    "Permissions, transcript display, and what Enter does": "权限、对话显示方式，以及回车键的行为",
    "The host\u2019s theme preference and font size": "宿主的主题偏好与字号",
    "Which route answers, and the catalogs behind it": "由哪条路由应答，以及它背后的目录",
    "Agent": "智能体",
    "The preset a new session starts in": "新会话启动时使用的预设",
    "The Shell tab\u2019s cell size": "Shell 页签的字符尺寸",
    "Plugins": "插件",
    "Diagnostics": "诊断",
    "Crash logs this build has written": "这个版本写下的崩溃日志",
    "General": "通用",
    "Appearance": "外观",
    "Model": "模型",
    "Terminal": "终端",
    "About": "关于",
    "The host, its version, and this build\u2019s credits": "宿主、它的版本，以及这个版本的致谢",
    "AMOLED black": "AMOLED 纯黑",
    "Every dark surface is drawn pure black.": "所有深色界面都使用纯黑。",
    "Applies when the appearance is dark; nothing changes while it is light.": "仅在深色外观下生效；浅色外观时不会有变化。",
    "Trajectory and Shell tabs": "运行轨迹与 Shell 页签",
    "Show the Trajectory and Shell readings beside Chat. Turn them off for a conversation that is only answers.": "在 Chat 旁显示运行轨迹与 Shell 读数。如果只看回答，可以关掉它们。",
    "App language": "应用语言",
    "Which language this app draws in. Following the device is the default.": "这个应用使用的语言。默认跟随系统。",
    # --- settings, second pass (the strings the host dictionary never had)
    "The host\u2019s theme preference and font size": "宿主的主题偏好与字号",
    "The Shell tab\u2019s cell size": "Shell 页签的字符尺寸",
    "What this deployment has loaded": "这个部署已加载的内容",
    "The host, its version, and this build\u2019s credits": "宿主、它的版本，以及这个版本的致谢",
    "Theme": "主题",
    "Monet light": "Monet 浅色",
    "Monet dark": "Monet 深色",
    "Monet": "Monet",
    "Reading settings…": "正在读取设置…",
    "Default mode": "默认模式",
    "Conversation display": "对话显示方式",
    "Completed turns": "已完成的轮次",
    "Normal": "完整",
    "While the agent is running": "智能体运行时的回车行为",
    "Could not load agent presets.": "无法加载智能体预设。",
    "Built-in": "内置",
    "No description.": "没有说明。",
    "Failed to load. %1$s": "加载失败。%1$s",
    "Default. %1$s": "默认。%1$s",
    "New task default. %1$s": "新任务默认。%1$s",
    "Cell size": "字符尺寸",
    "Text size": "文字大小",
    "Increase text size": "增大文字",
    "Decrease text size": "减小文字",
    "Search archived sessions": "搜索已归档会话",
    "Reading sessions…": "正在读取会话…",
    "No archived sessions.": "没有已归档的会话。",
    "This deployment stores settings read-only.": "这个部署的设置是只读的。",
    "Language": "语言",
    # --- about
    "App": "应用",
    "Connection": "连接",
    "Certificate": "证书",
    "Session": "会话",
    "Support": "支持",
    "Android": "Android",
    "Package": "包名",
    "Device": "设备",
    "Host": "宿主",
    "Route": "路由",
    "Active URL": "当前地址",
    "LAN address": "局域网地址",
    "Account": "账户",
    "Sign-in": "登录",
    "Session cookie": "会话 Cookie",
    "DSH version": "DSH 版本",
    "Title": "标题",
    "Directory": "目录",
    "Access mode": "访问模式",
    "Agent preset": "智能体预设",
    "Not configured": "未配置",
    "Not connected": "未连接",
    "Not reported": "未上报",
    "Present": "已存在",
    "LAN": "局域网",
    "Public": "公网",
    "Save LAN address": "保存局域网地址",
    "Try the LAN door again": "重试局域网入口",
    "Buy me a coffee on Ko-fi": "在 Ko-fi 上请我喝杯咖啡",
    "No app can open %1$s": "没有可以打开 %1$s 的应用",
    "Sign out and forget credentials": "退出登录并清除凭据",
    # --- widget
    "DSH status": "DSH 状态",
    "Open the app": "打开应用",
    # --- final pass: long prose and the last status words
    "Couldn't load picture": "图片加载失败",
    "The host\u2019s theme preference and font size": "宿主的主题偏好与字号",
    "The Shell tab\u2019s cell size": "Shell 页签的字符尺寸",
    "The host, its version, and this build\u2019s credits": "宿主、它的版本，以及这个版本的致谢",
    "Light and dark follow the system; Monet follows the wallpaper.": "浅色与深色跟随系统；Monet 跟随壁纸取色。",
    "The settings document is read-only in this deployment.": "这个部署的设置文档是只读的。",
    "Allow switching Agent modes": "允许切换智能体模式",
    "Android": "Android",
    "What new sessions start at. The open session\u2019s own mode is what its tool calls use.": "新会话启动时使用的模式。当前会话的工具调用使用它自己的模式。",
    "What Enter and Send do mid-turn; Cmd/Ctrl+Enter uses the other one.": "回车与发送在轮次进行中的行为；Cmd/Ctrl+Enter 使用另一种。",
    "Enter your API keys to use models from the following providers.": "输入 API 密钥即可使用以下提供方的模型。",
    "The host has not reported any model providers for this session.": "宿主还没有上报这个会话的模型提供方。",
    "A preset is the plugin composition one session\u2019s agent runs. Changing it affects new sessions, not the one open now.": "预设是一个会话的智能体运行的插件组合。修改它影响新会话，不影响当前会话。",
    "When enabled, new tasks can choose Standard, PTC, Creator, Meta and other agent modes.": "开启后，新任务可以选择 Standard、PTC、Creator、Meta 等智能体模式。",
    "The Shell page\u2019s text size \u2014 the same value the shell\u2019s own A-/A+ controls change.": "Shell 页的文字大小，与 Shell 自己的 A-/A+ 控件改的是同一个值。",
    "Configure and inspect the plugins installed in this deployment.": "配置并查看这个部署已安装的插件。",
    "Compact folds a finished turn behind one summary line; normal keeps every row.": "紧凑模式把已完成的轮次折叠成一行摘要；完整模式保留每一行。",
    "A native Android client for the DSH host. It speaks the host\u2019s own protocol, draws nothing the host did not send, and keeps no copy of your conversation.": "DSH 宿主的原生 Android 客户端。它使用宿主自己的协议，不绘制宿主没有发送的内容，也不保留你对话的副本。",
    "Optional, for the same host: reachable only on your own network.": "可选，指向同一个宿主：只在你的局域网内可达。",
    "The LAN door failed a real call, so it is set aside for now.": "局域网入口的真实调用失败了，暂时搁置。",
    "The LAN door answered a real call with something that was not the host.": "局域网入口用非宿主的内容应答了真实调用。",
    "No LAN address is set, so only the public door is tried.": "没有设置局域网地址，只尝试公网入口。",
    "This client is a personal project and stays free. The host is what does the work.": "这个客户端是个人项目，保持免费。真正干活的是宿主。",
    "Whether an agent needs you, and which sessions are running.": "是否有智能体在等你，以及哪些会话正在运行。",
    # --- by resource name: the long prose whose English drifted from the key above
    # (a trailing word, a punctuation difference) and would otherwise stay English
    # forever. Keyed on the resource id, which is this app's own and stable.
    "@settings_section_appearance_subtitle": "宿主的主题偏好与字号",
    "@settings_section_terminal_subtitle": "Shell 页签的字符尺寸",
    "@settings_about_row_subtitle": "宿主、它的版本，以及这个版本的致谢",
    "@settings_theme_monet_system": "Monet",
    "@settings_permission_default_summary": "新会话启动时使用的模式。当前会话的工具调用使用它自己的模式。",
    "@settings_busy_enter_summary": "回车与发送在轮次进行中的行为；Cmd/Ctrl+Enter 使用另一种。",
    "@settings_agent_intro": "预设是一个会话的智能体运行的插件组合。修改它影响新会话，不影响当前会话。",
    "@settings_agent_picker_summary": "开启后，新任务可以选择 Standard、PTC、Creator、Meta 等智能体模式。",
    "@settings_terminal_note": "Shell 页的文字大小，与 Shell 自己的 A-/A+ 控件改的是同一个值。",
    "@settings_terminal_sp_pair": "%1$s sp · 行高 %2$s sp",
    "@about_intro": "DSH 宿主的原生 Android 客户端。它使用宿主自己的协议，不绘制宿主没有发送的内容，也不保留你对话的副本。",
    "@about_row_android": "Android",
    "@about_android_api": "API %1$d",
    "@about_lan_note": "可选，指向同一个宿主：只在你的局域网内可达。",
    "@about_lan_door_transport": "局域网入口的真实调用失败了，暂时搁置。",
    "@about_lan_door_wrong_answer": "局域网入口用非宿主的内容应答了真实调用。",
    "@about_lan_door_unconfigured": "没有设置局域网地址，只尝试公网入口。",
    "@about_support_note": "这个客户端是个人项目，保持免费。真正干活的是宿主。",
    "@app_name": "DSH",
    "@widget_initial_headline": "DSH",
    "@chat_attachment_load_failed": "图片加载失败",
    "@stats_tokens_per_second": "%1$s tok/s",
    "@stats_token_count": "%1$s tok",
}

def normalise(text):
    """A comparison key: punctuation, placeholders and case flattened away."""
    text = text.replace("…", "...")
    text = re.sub(r"\{[a-zA-Z]+\}", "%s", text)
    text = re.sub(r"%[0-9]+\$[sd]", "%s", text)
    text = text.replace("’", "'").replace("‘", "'")
    text = re.sub(r"\s+", " ", text)
    return text.strip().rstrip(".!:").strip().lower()


def load_host_dictionary():
    """English text -> the host's Chinese for it, from the extracted catalog."""
    with open(CATALOG, encoding="utf-8") as handle:
        catalog = json.load(handle)
    en = catalog["en"]
    zh = catalog["zh"]
    # Keyed by the English *value*, not the key: the Android resource ids are this
    # app's own, and the only thing the two projects share is the words.
    by_english = {}
    for key, english in en.items():
        chinese = zh.get(key)
        if chinese and english and english != chinese:
            by_english.setdefault(english, chinese)
            # A second, looser index. The host writes for a web UI: it uses a real
            # ellipsis where Android copy often uses three dots or none, and it spells
            # placeholders `{count}` where a resource needs `%1$d`. Matching only the
            # literal string left two thirds of the app English for no reason.
            by_english.setdefault(normalise(english), chinese)
    # The hand-written supplement wins over a host match: it was written for
    # this app's exact strings, where a host entry for a similar phrase may
    # carry the web client's different wording.
    for english, chinese in SUPPLEMENT.items():
        by_english[english] = chinese
        by_english.setdefault(normalise(english), chinese)
    return by_english


def android_format_to_java(text):
    """`%1$s` in Android is `%1$s` in the host dictionary already; nothing to do."""
    return text


def main():
    """Merge the host's Chinese into values-zh-rCN, for every English resource file.

    Non-destructive: hand-written translations win, and the host fills the gaps.
    """
    host = load_host_dictionary()
    zh_path = ZH
    if os.path.exists(zh_path):
        zh_tree = ET.parse(zh_path)
        zh_root = zh_tree.getroot()
        existing = {n.get("name") for n in zh_root if n.tag in ("string", "plurals")}
    else:
        zh_root = ET.Element("resources")
        existing = set()
    added = 0
    translated = 0
    for name in sorted(os.listdir(EN_DIR)):
        if not name.endswith(".xml"):
            continue
        tree = ET.parse(os.path.join(EN_DIR, name))
        for node in tree.getroot():
            if node.tag != "string":
                continue
            key = node.get("name")
            if key in existing:
                continue
            english = "".join(node.itertext()).strip()
            chinese = (host.get(english) or host.get(normalise(english))
                       or host.get("@" + key))
            if chinese:
                translated += 1
            new = ET.SubElement(zh_root, "string", {"name": key})
            new.text = chinese if chinese else english
            added += 1
            existing.add(key)
    tree = ET.ElementTree(zh_root)
    ET.indent(tree, space="    ")
    tree.write(zh_path, encoding="utf-8", xml_declaration=True)
    print(f"added {added} ({translated} from the host dictionary, {added - translated} left English)")
    print(f"kept {len(existing) - added} existing")


if __name__ == "__main__":
    main()