package com.thinkinglogic.example

import com.thinkinglogic.builder.annotation.Builder
import com.thinkinglogic.builder.annotation.BuilderOf


@BuilderOf(targetClass = DataClassWithAdditionalFields::class, suffix = "BuilderOf")
class ForDataClassWithAdditionalFields

@Builder
data class DataClassWithAdditionalFields(
        val constructorString: String,
        private val privateString: String
) {
    val nonConstructorString = constructorString + "foo"

}
