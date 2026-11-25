package test

import icu.samnyan.aqua.net.Feature
import icu.samnyan.aqua.net.FeatureSwitches
import icu.samnyan.aqua.sega.general.dao.PropertyEntryRepository
import icu.samnyan.aqua.sega.general.model.PropertyEntry
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Real switches backed by a stubbed store: everything is on except the features listed in [off]. */
fun testSwitches(vararg off: Feature): FeatureSwitches {
    val store = mock(PropertyEntryRepository::class.java)
    if (off.isNotEmpty()) {
        `when`(store.findByPropertyKey("feature_switches")).thenReturn(PropertyEntry("feature_switches", off.joinToString(",") { it.key }))
    }
    return FeatureSwitches(store)
}
