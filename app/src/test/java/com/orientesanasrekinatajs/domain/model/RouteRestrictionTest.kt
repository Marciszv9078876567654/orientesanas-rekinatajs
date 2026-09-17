package com.orientesanasrekinatajs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRestrictionTest {
    @Test fun blacklistedControlsExcludeConnectionEndpointsAndOtherRules() {
        val rules = listOf(
            RouteRestriction(RouteRestrictionType.BLACKLIST_CONTROL, "a"),
            RouteRestriction(RouteRestrictionType.BLACKLIST_CONTROL, "a"),
            RouteRestriction(RouteRestrictionType.BLACKLIST_CONNECTION, "b", "c"),
            RouteRestriction(RouteRestrictionType.MANDATORY_CONTROL, "d"),
        )
        assertEquals(setOf("a"), blacklistedControlIds(rules))
    }

    @Test fun mandatoryPointsIncludeBothConnectionEndpointsAndDirectControls() {
        val rules = listOf(
            RouteRestriction(RouteRestrictionType.MANDATORY_CONTROL, "a"),
            RouteRestriction(RouteRestrictionType.MANDATORY_CONNECTION, "a", "b"),
            RouteRestriction(RouteRestrictionType.MANDATORY_CONNECTION, "c", "b"),
            RouteRestriction(RouteRestrictionType.BLACKLIST_CONTROL, "d"),
        )
        assertEquals(setOf("a", "b", "c"), mandatoryPointIds(rules))
    }

    @Test fun connectionSetsAreUndirectedDistinctAndSeparatedByType() {
        val rules = listOf(
            RouteRestriction(RouteRestrictionType.BLACKLIST_CONNECTION, "b", "a"),
            RouteRestriction(RouteRestrictionType.BLACKLIST_CONNECTION, "a", "b"),
            RouteRestriction(RouteRestrictionType.MANDATORY_CONNECTION, "d", "c"),
            RouteRestriction(RouteRestrictionType.MANDATORY_CONNECTION, "c", "d"),
            RouteRestriction(RouteRestrictionType.MANDATORY_CONTROL, "e"),
        )
        assertEquals(setOf("a" to "b"), blacklistedConnectionKeys(rules))
        assertEquals(setOf("c" to "d"), mandatoryConnectionKeys(rules))
    }

    @Test fun noRestrictionsProduceEmptySets() {
        assertEquals(emptySet<String>(), mandatoryPointIds(emptyList()))
        assertEquals(emptySet<String>(), blacklistedControlIds(emptyList()))
        assertEquals(emptySet<Pair<String, String>>(), mandatoryConnectionKeys(emptyList()))
        assertEquals(emptySet<Pair<String, String>>(), blacklistedConnectionKeys(emptyList()))
    }
}
