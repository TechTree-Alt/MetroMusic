package com.luna.music.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The index letters and the search shorthand, for the three alphabets the app files: a Korean name
 * landing under '#' is invisible until somebody with a Korean library opens the jump grid.
 */
class LibraryIndexTest {

    @Test
    fun hangulIsFiledByItsInitialConsonant() {
        assertEquals("ㅂ", LibraryIndex.bucketOf("방탄소년단"))
        assertEquals("ㅇ", LibraryIndex.bucketOf("아이유"))
        assertEquals("ㅎ", LibraryIndex.bucketOf("  혁오"))
        assertEquals("ㄱ", LibraryIndex.bucketOf("ㄱ"))
    }

    @Test
    fun tenseDoublesAreFoldedIntoThePlainConsonant() {
        assertEquals("ㄱ", LibraryIndex.bucketOf("까치"))
        assertEquals("ㄷ", LibraryIndex.bucketOf("뜨거운 감자"))
        assertEquals("ㅂ", LibraryIndex.bucketOf("빠른 노래"))
        assertEquals("ㅅ", LibraryIndex.bucketOf("싸이"))
        assertEquals("ㅈ", LibraryIndex.bucketOf("짜릿해"))
    }

    @Test
    fun decomposedHangulIsFiledTheSame() {
        val nfd = java.text.Normalizer.normalize("방탄", java.text.Normalizer.Form.NFD)
        assertEquals("ㅂ", LibraryIndex.bucketOf(nfd))
    }

    @Test
    fun latinCyrillicAndTheRest() {
        assertEquals("d", LibraryIndex.bucketOf("Daft Punk"))
        assertEquals("е", LibraryIndex.bucketOf("Ёлка"))
        assertEquals("к", LibraryIndex.bucketOf("Кино"))
        assertEquals("#", LibraryIndex.bucketOf("2NE1"))
        assertEquals("#", LibraryIndex.bucketOf(""))
        assertEquals("#", LibraryIndex.bucketOf("ㅏ"))
    }

    @Test
    fun koreanPhonesReadHangulFirst() {
        assertTrue(LibraryIndex.rankOf("ㅎ", koreanFirst = true) < LibraryIndex.rankOf("a", true))
        assertTrue(LibraryIndex.rankOf("z", koreanFirst = false) < LibraryIndex.rankOf("ㄱ", false))
        assertEquals(0, LibraryIndex.rankOf("#", true))
    }

    @Test
    fun theJumpGridShowsOnlyTheAlphabetsInUse() {
        val latin = LibraryIndex.jumpDomain(setOf("a", "q"), koreanFirst = false)
        assertEquals(27, latin.size)

        val both = LibraryIndex.jumpDomain(setOf("a", "ㅂ"), koreanFirst = true)
        assertEquals("#", both.first())
        assertEquals("ㄱ", both[1])
        assertEquals(1 + 14 + 26, both.size)

        assertEquals("ㄱ", LibraryIndex.jumpDomain(emptySet(), koreanFirst = true)[1])
        assertEquals("a", LibraryIndex.jumpDomain(emptySet(), koreanFirst = false)[1])
    }

    @Test
    fun initialConsonantsFindTheNamesTheyBegin() {
        assertTrue(LibraryIndex.matches("방탄소년단", "ㅂㅌㅅㄴㄷ"))
        assertTrue(LibraryIndex.matches("방탄 소년단", "ㅂㅌㅅ"))
        assertTrue(LibraryIndex.matches("방탄소년단", "소년"))
        assertTrue(LibraryIndex.matches("Daft Punk", "punk"))
        assertTrue(LibraryIndex.matches("anything", "  "))
        assertFalse(LibraryIndex.matches("방탄소년단", "ㅇㅇ"))
        assertFalse(LibraryIndex.matches("Daft Punk", "ㄷ"))
    }
}
