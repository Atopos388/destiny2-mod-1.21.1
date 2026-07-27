package atopos.destiny2.client;

import java.util.Map;

/** Display-only translations for LDLib2 strings that are not language keys. */
public final class LDLibChineseText {
    private static final Map<String, String> TEXT = Map.ofEntries(
            Map.entry("basic", "基础"), Map.entry("container", "容器"),
            Map.entry("utils", "工具"), Map.entry("misc", "其他"),
            Map.entry("inventory", "物品栏"), Map.entry("template", "模板"),
            Map.entry("element", "元素"), Map.entry("toggle", "开关"),
            Map.entry("selector", "选择器"), Map.entry("text-area", "多行文本"),
            Map.entry("switch", "切换开关"), Map.entry("label", "标签"),
            Map.entry("text-field", "单行文本"), Map.entry("tag-field", "标签输入框"),
            Map.entry("progress-bar", "进度条"), Map.entry("text", "文本"),
            Map.entry("button", "按钮"), Map.entry("search-component", "搜索组件"),
            Map.entry("scroller", "滚动条"), Map.entry("scroller-view", "滚动视图"),
            Map.entry("scroller-horizontal", "水平滚动条"), Map.entry("scroller-vertical", "垂直滚动条"),
            Map.entry("split-view", "分割视图"), Map.entry("split-view-horizontal", "水平分割视图"),
            Map.entry("split-view-vertical", "垂直分割视图"), Map.entry("tab", "选项卡"),
            Map.entry("tab-view", "选项卡视图"), Map.entry("tree-list", "树状列表"),
            Map.entry("graph-view", "图表视图"), Map.entry("color-selector", "颜色选择器"),
            Map.entry("item-slot", "物品槽"), Map.entry("fluid-slot", "流体槽"),
            Map.entry("inventory-slots", "物品栏槽位"), Map.entry("scene", "场景"),
            Map.entry("dialog", "对话框"), Map.entry("menu", "菜单"),
            Map.entry("inspector", "检查器"), Map.entry("code-editor", "代码编辑器"),
            Map.entry("ui-template", "UI 模板"), Map.entry("template-element", "模板元素"),
            Map.entry("Save", "保存"), Map.entry("save", "保存"),
            Map.entry("Copy", "复制"), Map.entry("copy", "复制"),
            Map.entry("Paste", "粘贴"), Map.entry("paste", "粘贴"),
            Map.entry("Delete", "删除"), Map.entry("delete", "删除"),
            Map.entry("New", "新建"), Map.entry("new", "新建"),
            Map.entry("Open", "打开"), Map.entry("open", "打开"),
            Map.entry("Close", "关闭"), Map.entry("close", "关闭"),
            Map.entry("Add", "添加"), Map.entry("add", "添加"),
            Map.entry("Remove", "移除"), Map.entry("remove", "移除"),
            Map.entry("Search", "搜索"), Map.entry("search", "搜索"),
            Map.entry("Debugger", "调试器"), Map.entry("debugger", "调试器"),
            Map.entry("inline", "内联"), Map.entry("computed", "计算后"),
            Map.entry("local css", "本地样式"), Map.entry("local lss", "本地样式")
    );

    private LDLibChineseText() { }

    public static String translate(String original) {
        return TEXT.getOrDefault(original, original);
    }
}
