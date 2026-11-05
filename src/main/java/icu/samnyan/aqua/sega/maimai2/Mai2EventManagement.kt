package icu.samnyan.aqua.sega.maimai2

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.sega.general.dao.PropertyEntryRepository
import icu.samnyan.aqua.sega.general.model.PropertyEntry
import icu.samnyan.aqua.sega.maimai2.model.Mai2GameEvent
import icu.samnyan.aqua.sega.util.BasicMapper
import icu.samnyan.aqua.sega.util.GameDataService
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Mai2EventOverride(
    val id: Long = 0,
    val type: Int = 0,
    val startDate: String = "",
    val endDate: String = "",
    val enabled: Boolean = true,
)

@Service
class Mai2EventManagement(val properties: PropertyEntryRepository, val mapper: BasicMapper, val gameData: GameDataService) {
    private val key = "maimai2_event_overrides"
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")

    fun overrides(): List<Mai2EventOverride> = properties.findByPropertyKey(key)?.let {
        mapper.read<List<Mai2EventOverride>>(it.propertyValue)
    } ?: emptyList()

    fun events(): List<Mai2GameEvent> {
        val base = gameData.mai2Events.associateBy { it.id }.toMutableMap()
        base.putIfAbsent(261016011, Mai2GameEvent().apply {
            id = 261016011; startDate = "2026-11-04 04:00:00"; endDate = "2026-12-01 04:00:00"
        })
        overrides().forEach { entry ->
            if (!entry.enabled) base.remove(entry.id)
            else base[entry.id] = Mai2GameEvent().apply {
                id = entry.id; type = entry.type; startDate = entry.startDate; endDate = entry.endDate
            }
        }
        return base.values.toList()
    }

    fun managedEvents(): List<Mai2EventOverride> {
        val entries = events().map { Mai2EventOverride(it.id, it.type, it.startDate ?: "", it.endDate ?: "") }.associateBy { it.id }.toMutableMap()
        overrides().forEach { entries[it.id] = it }
        return entries.values.sortedByDescending { it.id }
    }

    @Synchronized
    fun save(entry: Mai2EventOverride) {
        if (entry.id <= 0 || entry.id > Int.MAX_VALUE || entry.type < 0) 400 - "无效的活动 ID 或类型"
        val start = try { LocalDateTime.parse(entry.startDate, dateFormat) } catch (_: Exception) { 400 - "开始时间格式应为 yyyy-MM-dd HH:mm:ss" }
        val end = try { LocalDateTime.parse(entry.endDate, dateFormat) } catch (_: Exception) { 400 - "结束时间格式应为 yyyy-MM-dd HH:mm:ss" }
        if (!start.isBefore(end)) 400 - "结束时间必须晚于开始时间"
        val existing = properties.findByPropertyKey(key) ?: PropertyEntry(key, "[]")
        val entries = mapper.read<List<Mai2EventOverride>>(existing.propertyValue).filterNot { it.id == entry.id } + entry
        existing.propertyValue = mapper.write(entries)
        properties.saveAndFlush(existing)
    }
}

@RestController
@API("/api/v2/game/mai2/event")
class Mai2EventAdminApi(val jwt: JWT, val events: Mai2EventManagement) {
    private fun admin(token: Str) = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    @API("admin/list")
    fun list(@RP token: Str): List<Mai2EventOverride> { admin(token); return events.managedEvents() }

    @API(value = ["admin/save"], method = [RequestMethod.POST])
    fun save(@RP token: Str, @RB event: Mai2EventOverride): Map<String, Boolean> {
        admin(token); events.save(event); return mapOf("success" to true)
    }
}
