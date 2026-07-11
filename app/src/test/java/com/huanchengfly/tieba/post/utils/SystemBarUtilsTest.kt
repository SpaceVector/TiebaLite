package com.huanchengfly.tieba.post.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarUtilsTest {
    @Test
    fun detectsHyperOsFromXiaomiBuildVersion() {
        assertTrue(isHyperOs("Xiaomi", "OS3.0.315.0.WPBCNXM"))
        assertFalse(isHyperOs("Xiaomi", "V14.0.31.0.TMBCNXM"))
        assertFalse(isHyperOs("Google", "OS3.0.315.0.WPBCNXM"))
    }
}
