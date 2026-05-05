package com.example.cctest.routing

import com.example.cctest.feature.personalinfo.data.PersonalInfoRecordResolver
import com.example.cctest.feature.personalinfo.data.PersonalInfoRepository
import com.example.cctest.routing.parser.ParseSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalInfoRecordResolverTest {
    private val resolver = PersonalInfoRecordResolver(PersonalInfoRepository())

    @Test
    fun resolveUniqueName_returnsSingleRecord() {
        val resolution = resolver.resolveFromSlots(ParseSlots(personName = "张雨桐"))
        assertTrue(resolution.isUnique)
        assertEquals("张雨桐", resolution.matchedRecord?.name)
    }

    @Test
    fun resolveFuzzyName_returnsAmbiguousCandidates() {
        val resolution = resolver.resolveFromSlots(ParseSlots(personName = "张"))
        assertFalse(resolution.isUnique)
        assertTrue(resolution.candidates.size >= 2)
    }

    @Test
    fun resolvePosition_returnsMatchingRecord() {
        val resolution = resolver.resolveFromSlots(ParseSlots(listPosition = 12))
        assertTrue(resolution.isUnique)
        assertEquals("record-12", resolution.matchedRecord?.recordId)
    }
}
