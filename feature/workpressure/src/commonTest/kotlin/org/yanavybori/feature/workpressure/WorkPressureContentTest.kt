package org.yanavybori.feature.workpressure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkPressureContentTest {
    @Test
    fun complaint_templates_are_complete_and_unique() {
        assertTrue(workPressureTemplates.size >= 4)
        assertEquals(workPressureTemplates.size, workPressureTemplates.map { it.id }.toSet().size)
        workPressureTemplates.forEach { template ->
            assertTrue(template.recipient.isNotBlank())
            assertTrue(template.beforeSending.isNotEmpty())
            assertTrue("[" in template.body && "]" in template.body)
            assertTrue("Приложения" in template.body)
        }
    }

    @Test
    fun law_cards_include_core_election_and_labor_rules() {
        val citations = workPressureLaws.joinToString(" ") { it.citation }
        assertTrue("67-ФЗ" in citations)
        assertTrue("141" in citations)
        assertTrue("Трудового кодекса" in citations)
        workPressureLaws.forEach { assertTrue(it.url.startsWith("https://")) }
    }

    @Test
    fun official_channels_are_linked() {
        assertTrue(workPressureContacts.size >= 5)
        assertEquals(workPressureContacts.size, workPressureContacts.map { it.url }.toSet().size)
        workPressureContacts.forEach { assertTrue(it.url.startsWith("http")) }
    }

    @Test
    fun ovd_info_source_is_explicit_and_https() {
        assertTrue(OVD_INFO_ELECTION_GUIDE_URL.startsWith("https://ovdinfo.legal/"))
    }
}
