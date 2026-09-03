package com.betterstreamflix.compose.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCategoryScrollIndexTest {

    @Test
    fun heroOnly_offsetsCategoryByOne() {
        assertEquals(
            1,
            homeCategoryScrollIndex(
                categoryIndex = 0,
                hasStatusBanner = false,
                hasParentalBanner = false,
            ),
        )
        assertEquals(
            3,
            homeCategoryScrollIndex(
                categoryIndex = 2,
                hasStatusBanner = false,
                hasParentalBanner = false,
            ),
        )
    }

    @Test
    fun statusAndParentalBanners_shiftCategoryIndex() {
        assertEquals(
            2,
            homeCategoryScrollIndex(
                categoryIndex = 0,
                hasStatusBanner = true,
                hasParentalBanner = false,
            ),
        )
        assertEquals(
            3,
            homeCategoryScrollIndex(
                categoryIndex = 0,
                hasStatusBanner = true,
                hasParentalBanner = true,
            ),
        )
        assertEquals(
            5,
            homeCategoryScrollIndex(
                categoryIndex = 2,
                hasStatusBanner = true,
                hasParentalBanner = true,
            ),
        )
    }

    @Test
    fun missingCategory_returnsNegative() {
        assertEquals(
            -1,
            homeCategoryScrollIndex(
                categoryIndex = -1,
                hasStatusBanner = true,
                hasParentalBanner = true,
            ),
        )
    }
}
