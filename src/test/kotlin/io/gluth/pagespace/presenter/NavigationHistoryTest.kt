package io.gluth.pagespace.presenter

import io.gluth.pagespace.domain.Page
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationHistoryTest {

    private val physics = Page("mock:physics", "Physics")
    private val math = Page("mock:math", "Mathematics")
    private val quantum = Page("mock:quantum", "Quantum Mechanics")

    @Test
    fun emptyHistoryCannotGoBack() {
        val history = NavigationHistory()
        assertFalse(history.canGoBack())
        assertNull(history.back())
    }

    @Test
    fun pushAddsPages() {
        val history = NavigationHistory()
        history.push(physics)
        history.push(math)

        assertEquals(listOf(physics, math), history.pages())
        assertEquals(1, history.currentIndex())
    }

    @Test
    fun backReturnsPreviousPage() {
        val history = NavigationHistory()
        history.push(physics)
        history.push(math)
        history.push(quantum)

        val backPage = history.back()
        assertEquals(math, backPage)
        assertEquals(1, history.currentIndex())
        assertTrue(history.isNavigatingBack())
    }

    @Test
    fun backFromFirstPageReturnsNull() {
        val history = NavigationHistory()
        history.push(physics)

        assertNull(history.back())
        assertFalse(history.canGoBack())
    }

    @Test
    fun pushAfterBackTruncatesForwardHistory() {
        val history = NavigationHistory()
        history.push(physics)
        history.push(math)
        history.push(quantum)

        history.back() // now at math, index=1, navigatingBack=true
        history.push(math) // no-op push from navigateTo during back (navigatingBack=true, clears it)

        // History still has all 3 entries; forward not truncated yet
        assertEquals(listOf(physics, math, quantum), history.pages())
        assertEquals(1, history.currentIndex())

        // Now a fresh forward navigation should truncate
        history.push(physics)
        assertEquals(listOf(physics, math, physics), history.pages())
        assertEquals(2, history.currentIndex())
    }

    @Test
    fun navigatingBackFlagClearedByPush() {
        val history = NavigationHistory()
        history.push(physics)
        history.push(math)

        history.back()
        assertTrue(history.isNavigatingBack())

        // When navigating back, the navigateTo flow calls push with navigatingBack=true
        // push should skip adding and clear the flag
        history.push(physics) // this is the "back" page push — but navigatingBack is true, so it's a no-op for adding
        assertFalse(history.isNavigatingBack())
    }

    @Test
    fun canGoBackWithMultiplePages() {
        val history = NavigationHistory()
        history.push(physics)
        assertFalse(history.canGoBack())

        history.push(math)
        assertTrue(history.canGoBack())
    }

    @Test
    fun multipleBacksWalkThroughHistory() {
        val history = NavigationHistory()
        history.push(physics)
        history.push(math)
        history.push(quantum)

        assertEquals(quantum, history.pages()[history.currentIndex()])

        val b1 = history.back()
        assertEquals(math, b1)
        history.push(math) // simulate navigateTo push (no-op because navigatingBack)

        val b2 = history.back()
        assertEquals(physics, b2)
        history.push(physics) // simulate navigateTo push (no-op)

        assertNull(history.back())
        assertEquals(3, history.pages().size) // history preserved
    }
}
