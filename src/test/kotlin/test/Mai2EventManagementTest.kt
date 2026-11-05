package test

import icu.samnyan.aqua.net.utils.ApiException
import icu.samnyan.aqua.sega.general.dao.PropertyEntryRepository
import icu.samnyan.aqua.sega.general.model.PropertyEntry
import icu.samnyan.aqua.sega.maimai2.Mai2EventManagement
import icu.samnyan.aqua.sega.maimai2.Mai2EventOverride
import icu.samnyan.aqua.sega.maimai2.model.Mai2GameEvent
import icu.samnyan.aqua.sega.util.BasicMapper
import icu.samnyan.aqua.sega.util.GameDataService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito.*

class Mai2EventManagementTest : StringSpec({
    "overrides take precedence and disabled events are omitted" {
        val repo = mock(PropertyEntryRepository::class.java)
        val data = mock(GameDataService::class.java)
        val original = Mai2GameEvent().apply { id = 123 }
        `when`(data.mai2Events).thenReturn(listOf(original))
        val property = PropertyEntry("maimai2_event_overrides", "[]")
        `when`(repo.findByPropertyKey("maimai2_event_overrides")).thenReturn(property)
        val service = Mai2EventManagement(repo, BasicMapper(), data)
        val entry = Mai2EventOverride(123, 0, "2026-11-04 04:00:00", "2026-12-01 04:00:00", true)
        service.save(entry)
        service.events().single { it.id == 123L }.startDate shouldBe entry.startDate
        service.save(entry.copy(enabled = false))
        service.events().any { it.id == 123L } shouldBe false
        service.managedEvents().single { it.id == 123L }.enabled shouldBe false
        service.delete(123)
        service.events().any { it.id == 123L } shouldBe false
        service.managedEvents().any { it.id == 123L } shouldBe false
        service.save(entry)
        service.events().any { it.id == 123L } shouldBe true
    }

    "invalid dates are rejected before writing" {
        val repo = mock(PropertyEntryRepository::class.java)
        val service = Mai2EventManagement(repo, BasicMapper(), mock(GameDataService::class.java))
        shouldThrow<ApiException> { service.save(Mai2EventOverride(10, 0, "bad", "2026-12-01 04:00:00")) }
        verifyNoInteractions(repo)
    }
})
