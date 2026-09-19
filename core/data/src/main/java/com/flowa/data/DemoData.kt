package com.flowa.data

import com.flowa.domain.*

internal object DemoData {
    fun create(): AppState {
        val samples = listOf(
            Triple("下午茶时忽然想到：主页的键盘应该在打开的一瞬间就就绪，想法不能在解锁动画里溜走。", listOf("灵感"), 0),
            Triple("产品评审纪要\n- 演示模式只切数据集和配置组，真实记录完全不动\n- 历史页要看得出标签、搜索和统计\n- 下周一对一下截图顺序：主页、历史、标签、工作流、助手", listOf("工作"), 0),
            Triple("晚上买菜\n- 番茄\n- 鸡蛋\n- 青菜\n- 豆腐\n- 记得带上帆布袋", listOf("生活", "待办"), 1),
            Triple("整理书架时翻到一本旧笔记本。字很乱，但那几页比后来工整的文档更像自己。", listOf("生活", "灵感"), 1),
            Triple("读书摘录：速记工具不该要求你先分类、先起标题、先决定这则想法值不值得留下。分类是事后的整理，不是入场券。真正稀缺的是那两分钟的完整注意力——如果写下之前还要做选择，想法就已经在菜单里冷却了。好的工具先接住，再慢慢长出结构。", listOf("读书"), 2),
            Triple("跟设计对了一轮标签筛选。多选、排除、无标签这三项同时出现时，筛选条才像真的在用。", listOf("工作"), 3),
            Triple("跑步到江边时冒出来的一句：记录不是为了更忙，是为了回头时还能认出来。", listOf("生活", "灵感"), 4),
            Triple("钥匙放在门口抽屉第二层。", emptyList(), 4),
            Triple("让助手把会议纪要压成三段：结论、未决、下一步。", listOf("工作"), 6),
            Triple("摘录：先写下来，意义会在重读时自己长出来。", listOf("读书"), 10),
            Triple("周末沿着河堤走到旧桥再折返，大约四十分钟。下次带上耳机，只听风。", listOf("生活"), 11),
            Triple("下雨了，窗没关。", emptyList(), 35)
        )
        return AppState(demoMode = true, draft = Draft("下周一演示，截图顺序先排一下：主页、历史、标签", listOf("工作")),
            notes = samples.mapIndexed { index, (text, tags, day) -> Note(text = text, tags = tags, createdAt = System.currentTimeMillis() - day * 86400000L - index * 60000) },
            workflows = listOf(Workflow(name = "保存记录", icon = "save", steps = listOf(Step(type = StepType.SAVE))), Workflow(name = "复制并保存", icon = "copy", steps = listOf(Step(type = StepType.COPY), Step(type = StepType.SAVE))), Workflow(name = "润色", icon = "ai", steps = listOf(Step(type = StepType.AI, prompt = "整理成简洁清晰的文字，保留原意。"), Step(type = StepType.SAVE)))),
            skills = listOf(Skill("会议纪要", "将会议纪要整理为结论、未决事项、下一步三个部分。")), memory = "回复简洁，使用简体中文。",
            sessions = listOf(ChatSession(title = "回顾最近的灵感", messages = listOf(Message(role = "user", text = "帮我回顾最近的灵感"), Message(role = "assistant", text = "## 最近的想法\n- 让输入更直接，打开应用即可记录。\n- 先接住想法，再通过标签和工作流整理。\n- 记录是为了在回头时认出自己。")))))
    }
}
