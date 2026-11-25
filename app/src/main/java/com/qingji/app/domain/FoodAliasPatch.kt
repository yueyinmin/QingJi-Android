package com.qingji.app.domain

import android.content.Context
import com.qingji.app.data.local.FoodCatalogDatabase
import org.json.JSONObject

/** 版本化只读别名补丁。只提供召回与排序，不写回公共食物库。 */
data class FoodAliasPatch(
    val schemaVersion: Int,
    val appliesToCatalogVersion: String,
    val aliases: List<FoodAliasEntry>
)

data class FoodAliasEntry(
    val alias: String,
    val normalizedAlias: String,
    val candidateFoodIds: List<Long>,
    val preferredFoodId: Long?,
    val source: String,
    val note: String
)

object FoodAliasPatchLoader {
    const val ASSET_PATH = "food_alias_patch/food_alias_patch_v1.json"

    /**
     * 版本不匹配时直接失败：旧补丁不能配新库，调用方必须禁用而不是硬猜。
     */
    fun load(
        context: Context,
        expectedCatalogVersion: String = FoodCatalogDatabase.CATALOG_VERSION
    ): Result<FoodAliasPatch> = runCatching {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val schema = root.optInt("schemaVersion", -1)
        require(schema == 1) { "别名补丁 schemaVersion 不支持：$schema" }

        val version = root.optString("appliesToCatalogVersion")
        require(version == expectedCatalogVersion) {
            "别名补丁版本不匹配：补丁=$version，食物库=$expectedCatalogVersion"
        }

        val array = root.optJSONArray("aliases")
        require(array != null && array.length() > 0) { "别名补丁为空" }
        val aliases = (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val alias = obj.optString("alias").trim()
            if (alias.isBlank()) return@mapNotNull null
            val ids = obj.optJSONArray("candidateFoodIds")
            val candidates = (0 until (ids?.length() ?: 0)).mapNotNull { index ->
                ids?.optLong(index)
            }
            FoodAliasEntry(
                alias = alias,
                normalizedAlias = obj.optString("normalizedAlias").takeIf { it.isNotBlank() } ?: alias,
                candidateFoodIds = candidates,
                preferredFoodId = obj.takeIf { it.has("preferredFoodId") && !it.isNull("preferredFoodId") }
                    ?.optLong("preferredFoodId"),
                source = obj.optString("source"),
                note = obj.optString("note")
            )
        }
        FoodAliasPatch(schema, version, aliases)
    }
}
