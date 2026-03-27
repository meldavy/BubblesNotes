package com.mel.bubblenotes.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ContentDriftDetector.
 */
class ContentDriftDetectorTest {

    private val detector = ContentDriftDetector(changeThreshold = 5)

    @Test
    fun `should detect no drift when content is identical`() {
        val result = detector.detectDrift("Hello World", "Hello World")

        assertFalse(result.hasSignificantChange)
        assertEquals(0, result.charactersChanged)
        assertEquals(0.0, result.changePercentage, 0.001)
    }

    @Test
    fun `should detect checkbox toggle as insignificant change`() {
        // Checkbox toggle changes 1 character (space to 'x' or vice versa)
        val oldContent = "- [ ] todo item"
        val newContent = "- [x] todo item"

        val result = detector.detectDrift(oldContent, newContent)

        assertFalse("Checkbox toggle should not trigger AI task", result.hasSignificantChange)
        assertEquals(1, result.charactersChanged)
    }

    @Test
    fun `should detect multiple checkbox toggles as insignificant change`() {
        // Multiple checkbox toggles (3 characters changed: space->x for 3 items)
        val oldContent = "- [ ] item 1\n- [ ] item 2\n- [ ] item 3"
        val newContent = "- [x] item 1\n- [x] item 2\n- [ ] item 3"

        val result = detector.detectDrift(oldContent, newContent)

        // 2 checkboxes toggled from unchecked to checked = 2 characters changed
        assertFalse("Multiple checkbox toggles should not trigger AI task", result.hasSignificantChange)
        assertEquals(2, result.charactersChanged)
    }

    @Test
    fun `should detect significant content change`() {
        val oldContent = "This is a short note"
        val newContent = "This is a significantly longer note with much more content added to it"

        val result = detector.detectDrift(oldContent, newContent)

        assertTrue("Significant content change should trigger AI task", result.hasSignificantChange)
        assertTrue(result.charactersChanged >= 5)
    }

    @Test
    fun `should detect empty old content as significant change`() {
        val result = detector.detectDrift("", "This is new content")

        assertTrue(result.hasSignificantChange)
        assertEquals(19, result.charactersChanged)
    }

    @Test
    fun `should detect empty new content as significant change`() {
        val result = detector.detectDrift("This is old content", "")

        assertTrue(result.hasSignificantChange)
        assertEquals(19, result.charactersChanged)
    }

    @Test
    fun `should detect small text edit as insignificant change`() {
        val oldContent = "This is a test note with some content"
        val newContent = "This is a test note with some conten"  // Removed 1 character

        val result = detector.detectDrift(oldContent, newContent)

        // Only 1 character deleted, should be below threshold
        assertFalse(result.hasSignificantChange)
        assertEquals(1, result.charactersChanged)
    }

    @Test
    fun `should detect medium text edit as significant change`() {
        val oldContent = "This is a test note"
        val newContent = "This is a completely different note with different content"

        val result = detector.detectDrift(oldContent, newContent)

        assertTrue(result.hasSignificantChange)
        assertTrue(result.charactersChanged >= 5)
    }

    @Test
    fun `hasSignificantChange should return correct boolean`() {
        assertTrue(detector.hasSignificantChange("short", "this is much longer content"))
        assertFalse(detector.hasSignificantChange("- [ ] task", "- [x] task"))
    }

    @Test
    fun `should calculate correct change percentage`() {
        val result = detector.detectDrift("abc", "abcd")

        // 1 character added out of 4 = 25% change
        assertEquals(0.25, result.changePercentage, 0.01)
    }

    @Test
    fun `should handle unicode characters correctly`() {
        val oldContent = "Hello World"
        val newContent = "Hello World!"

        val result = detector.detectDrift(oldContent, newContent)

        assertFalse(result.hasSignificantChange)
        assertEquals(1, result.charactersChanged)
    }

    @Test
    fun `should handle markdown formatting changes`() {
        val oldContent = "# Heading\n\n- [ ] task"
        val newContent = "# Heading\n\n- [x] task"

        val result = detector.detectDrift(oldContent, newContent)

        assertFalse("Markdown checkbox toggle should not trigger AI task", result.hasSignificantChange)
        assertEquals(1, result.charactersChanged)
    }

    @Test
    fun `should handle large content with small change`() {
        val oldContent = "A".repeat(1000)
        val newContent = "A".repeat(999) + "B"  // Only 1 character changed

        val result = detector.detectDrift(oldContent, newContent)

        assertFalse(result.hasSignificantChange)
        assertEquals(1, result.charactersChanged)
    }
}
