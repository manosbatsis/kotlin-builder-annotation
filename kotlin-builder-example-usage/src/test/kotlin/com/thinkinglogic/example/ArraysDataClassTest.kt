package com.thinkinglogic.example

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class ArraysDataClassTest {

    // given
    val expected = ArraysDataClass(
            arrayOfDates = arrayOf(LocalDate.now(), LocalDate.MIN),
            arrayOfLongs = arrayOf(1L),
            arrayOfStrings = arrayOf("one", "two"),
            arrayOfNullableStrings = arrayOf("foo", null),
            arrayOfListOfStrings = arrayOf()
    )
    @Test
    fun `builder should create object with correct properties`() {

        // when
        val actual = ArraysDataClassBuilder()
                .arrayOfDates(expected.arrayOfDates)
                .arrayOfLongs(expected.arrayOfLongs)
                .arrayOfStrings(expected.arrayOfStrings)
                .arrayOfNullableStrings(expected.arrayOfNullableStrings)
                .arrayOfListOfStrings(expected.arrayOfListOfStrings)
                .build()

        // then
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `builder mixin should create object with correct properties`() {


        // when
        val actual = ArraysDataClassBuilderOf()
                .arrayOfDates(expected.arrayOfDates)
                .arrayOfLongs(expected.arrayOfLongs)
                .arrayOfStrings(expected.arrayOfStrings)
                .arrayOfNullableStrings(expected.arrayOfNullableStrings)
                .arrayOfListOfStrings(expected.arrayOfListOfStrings)
                .build()

        // then
        assertThat(actual).isEqualTo(expected)
    }

}