package test

import icu.samnyan.aqua.net.Feature
import icu.samnyan.aqua.net.utils.ApiException
import icu.samnyan.aqua.sega.general.dao.PropertyEntryRepository
import icu.samnyan.aqua.sega.general.model.PropertyEntry
import icu.samnyan.aqua.net.FeatureSwitches
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class FeatureSwitchesTest : StringSpec({
    "every feature is on when nothing was switched off, except those that are off by default" {
        val switches = testSwitches()
        Feature.entries.forEach { switches.enabled(it) shouldBe it.defaultOn }
        switches.all()["nameReview"] shouldBe false
        switches.all().filterKeys { it != "nameReview" }.values.all { it } shouldBe true
    }

    "a switched off feature is off and the others stay on" {
        val switches = testSwitches(Feature.EXPORT, Feature.CLAIM)
        switches.enabled(Feature.EXPORT) shouldBe false
        switches.enabled(Feature.CLAIM) shouldBe false
        switches.enabled(Feature.RENAME) shouldBe true
        switches.enabled(Feature.CUSTOM_AVATAR) shouldBe true
    }

    "require refuses with 403 and the reason" {
        val switches = testSwitches(Feature.RENAME)
        val error = shouldThrow<ApiException> { switches.require(Feature.RENAME) }
        error.code shouldBe 403
        error.message shouldBe "管理员已关闭修改名字"
        switches.require(Feature.CLAIM) // still allowed
    }

    "setting a feature saves the list and takes effect at once" {
        val store = mock(PropertyEntryRepository::class.java)
        val switches = FeatureSwitches(store)
        switches.set(Feature.CLAIM, false)
        switches.enabled(Feature.CLAIM) shouldBe false
        val saved = org.mockito.ArgumentCaptor.forClass(PropertyEntry::class.java)
        verify(store).saveAndFlush(saved.capture() ?: PropertyEntry("", ""))
        saved.value.propertyValue shouldBe "claim"

        switches.set(Feature.CLAIM, true)
        switches.enabled(Feature.CLAIM) shouldBe true
    }

    "the keys are unique" {
        Feature.entries.map { it.key }.toSet().size shouldBe Feature.entries.size
    }
})
