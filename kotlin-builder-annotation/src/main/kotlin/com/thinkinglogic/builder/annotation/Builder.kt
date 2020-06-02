package com.thinkinglogic.builder.annotation

import kotlin.reflect.KClass

/**
 * A lightweight replacement for Lombok's @Builder annotation, decorating a class with @Builder will cause a
 * {AnnotatedClassName}Builder class to be generated.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
annotation class Builder

/**
 * Use this annotation to mark a collection or array as being allowed to contain null values,
 * As knowledge of the nullability is otherwise lost during annotation processing.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class NullableType

/**
 * Use this annotation to mark a MutableList, MutableSet, MutableCollection, MutableMap, or MutableIterator,
 * as knowledge of their mutability is otherwise lost during annotation processing.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class Mutable

/**
 * Use this annotation to provide a default value for the builder,
 * as knowledge of default values is otherwise lost during annotation processing.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class DefaultValue (val value: String = "")

/**
 * An alternative to [Builder] that allows specifying the target class
 * instead of decorating it directly.
 * Used to generate builders for classes of project dependencies,
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
annotation class BuilderOf(
        val targetClass: KClass<*>,
        val suffix: String = "Builder",
        val useConstructors: Boolean = false
)

