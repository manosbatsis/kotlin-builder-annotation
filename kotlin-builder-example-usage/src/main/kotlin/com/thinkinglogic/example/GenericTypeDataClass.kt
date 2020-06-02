package com.thinkinglogic.example

import com.thinkinglogic.builder.annotation.Builder
import com.thinkinglogic.builder.annotation.BuilderOf


@BuilderOf(targetClass = GenericTypeDataClass::class, suffix = "BuilderOf")
class ForGenericTypeDataClass

@Builder
data class GenericTypeDataClass(
        val myTypedObject: TypedObject<String>
)

class TypedObject<T>(val value: T)