package com.thinkinglogic.example

import com.thinkinglogic.builder.annotation.Builder
import com.thinkinglogic.builder.annotation.BuilderOf
import com.thinkinglogic.builder.annotation.DefaultValue


@BuilderOf(targetClass = DataClassWithLongPropertyNames::class, suffix = "BuilderOf")
class FoDataClassWithLongPropertyNames

@Builder
data class DataClassWithLongPropertyNames(
        @DefaultValue("myDefault") val stringWithAVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongNameThatWouldCauseLineWrappingInTheGeneratedFile: String = "myDefault",
        val nullableString: String?
) {

    /**
     * @return a Builder initialised with fields from this object.
     */
    fun toBuilder() = DataClassWithLongPropertyNamesBuilder(this)

    companion object {
        @JvmStatic
        fun builder() = DataClassWithLongPropertyNamesBuilder()
    }
}