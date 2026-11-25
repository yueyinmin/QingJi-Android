package com.qingji.app.ai

import com.qingji.app.data.remote.ai.AiToolDefinition

/** 只读工具注册表。写入工具在阶段 E 提案/权限验收前不得注册。 */
class ToolRegistry {
    private val tools = LinkedHashMap<String, AiTool>()

    fun register(tool: AiTool) {
        require(tool.name.isNotBlank()) { "工具名不能为空" }
        tools[tool.name] = tool
    }

    fun find(name: String): AiTool? = tools[name]

    fun all(): List<AiTool> = tools.values.toList()

    fun definitions(): List<AiToolDefinition> = tools.values.map {
        AiToolDefinition(
            name = it.name,
            description = it.description,
            parametersJson = it.parametersJson
        )
    }
}
