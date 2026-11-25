package icu.samnyan.aqua.net

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.sega.general.dao.PropertyEntryRepository
import icu.samnyan.aqua.sega.general.model.PropertyEntry
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

/** Server-wide switches an admin can flip for every player. A feature is on unless it was switched off. */
enum class Feature(val key: String, val label: String, val offMessage: String) {
    RENAME("rename", "修改名字", "管理员已关闭修改名字"),
    CUSTOM_AVATAR("customAvatar", "上传 / 使用自定义头像", "管理员已关闭自定义头像"),
    CLAIM("claim", "创哥理赔", "管理员已关闭创哥理赔"),
    EXPORT("export", "数据导出", "管理员已关闭数据导出"),
}

@Service
class FeatureSwitches(private val properties: PropertyEntryRepository) {
    private val key = "feature_switches"

    /** Features that are switched off. Kept in memory so the hot paths (every game request) never touch the database. */
    @Volatile private var off: Set<String>? = null

    private fun load(): Set<String> = off ?: synchronized(this) {
        off ?: (properties.findByPropertyKey(key)?.propertyValue
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()).also { off = it }
    }

    fun enabled(feature: Feature) = feature.key !in load()

    /** Throws 403 with the reason when the feature is switched off. */
    fun require(feature: Feature) {
        if (!enabled(feature)) 403 - feature.offMessage
    }

    fun all(): Map<String, Boolean> = Feature.entries.associate { it.key to enabled(it) }

    @Synchronized
    fun set(feature: Feature, enabled: Boolean) {
        val next = load().toMutableSet().also { if (enabled) it.remove(feature.key) else it.add(feature.key) }
        val entry = properties.findByPropertyKey(key) ?: PropertyEntry(key, "")
        entry.propertyValue = next.sorted().joinToString(",")
        properties.saveAndFlush(entry)
        off = next
    }
}

/** What players' pages need to know to hide or disable a feature. */
@RestController
@API("/api/v2/switches")
class FeatureSwitchesApi(private val switches: FeatureSwitches) {
    @API("/list")
    @Doc("Which features are currently allowed for players.", "Feature switches")
    fun list() = switches.all()
}

@RestController
@API("/api/v2/admin/switches")
class FeatureSwitchesAdminApi(private val jwt: JWT, private val switches: FeatureSwitches) {
    private fun admin(token: Str) = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    @API("/list")
    @Doc("All feature switches with their labels.", "Feature switches")
    fun list(@RP token: Str): List<Map<String, Any>> {
        admin(token)
        return Feature.entries.map { mapOf("key" to it.key, "label" to it.label, "enabled" to switches.enabled(it)) }
    }

    @API("/set")
    @Doc("Turn a feature on or off for every player.", "Feature switches")
    fun set(@RP token: Str, @RP key: Str, @RP enabled: Boolean): List<Map<String, Any>> {
        admin(token)
        val feature = Feature.entries.find { it.key == key } ?: (400 - "未知的功能开关")
        switches.set(feature, enabled)
        return Feature.entries.map { mapOf("key" to it.key, "label" to it.label, "enabled" to switches.enabled(it)) }
    }
}
