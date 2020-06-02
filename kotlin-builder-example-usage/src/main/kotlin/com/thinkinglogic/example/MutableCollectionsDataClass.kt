package com.thinkinglogic.example

import com.thinkinglogic.builder.annotation.Builder
import com.thinkinglogic.builder.annotation.BuilderOf
import com.thinkinglogic.builder.annotation.Mutable
import com.thinkinglogic.builder.annotation.NullableType
import java.time.LocalDate


@BuilderOf(targetClass = MutableCollectionsDataClass::class, suffix = "BuilderOf")
class ForMutableCollectionsDataClass

@Builder
data class MutableCollectionsDataClass(
        @Mutable val listOfStrings: MutableList<String>,
        @Mutable val listOfAny: MutableList<Any>,
        @Mutable val nullableSetOfLongs: MutableSet<Long>?,
        @Mutable val collectionOfLongs: MutableCollection<Long>,
        @Mutable @NullableType val setOfNullableLongs: MutableSet<Long?>,
        @Mutable @NullableType val mapOfStringToNullableDates: MutableMap<String, LocalDate?>
)
