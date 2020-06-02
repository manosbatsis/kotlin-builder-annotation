package com.thinkinglogic.example

import com.thinkinglogic.builder.annotation.Builder
import com.thinkinglogic.builder.annotation.BuilderOf
import com.thinkinglogic.builder.annotation.NullableType
import java.time.LocalDate
import java.util.HashSet
import java.util.TreeMap


@BuilderOf(targetClass = CollectionsDataClass::class, suffix = "BuilderOf")
class ForCollectionsDataClass

@Builder
data class CollectionsDataClass(
        val listOfStrings: List<String>,
        @NullableType val listOfNullableStrings: List<String?>,
        val setOfLongs: Set<Long>,
        @NullableType val setOfNullableLongs: Set<Long?>,
        val hashSet: HashSet<Long>,
        val collectionOfDates: Collection<LocalDate>,
        @NullableType val mapOfStringToNullableDates: Map<String, LocalDate?>,
        val treeMap: TreeMap<String, LocalDate>
)
