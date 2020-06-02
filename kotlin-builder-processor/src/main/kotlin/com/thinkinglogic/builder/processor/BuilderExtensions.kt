package com.thinkinglogic.builder.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.thinkinglogic.builder.annotation.DefaultValue
import com.thinkinglogic.builder.annotation.Mutable
import com.thinkinglogic.builder.annotation.NullableType
import org.jetbrains.annotations.NotNull
import java.util.ArrayList
import java.util.NoSuchElementException
import java.util.stream.Collectors
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.NOTE

interface BuilderExtensions {

    val processingEnvironment: ProcessingEnvironment


    /** Get the given annotation value as a [AnnotationValue] if it exists, throw an error otherwise */
    fun AnnotationMirror.getAnnotationValue(name: String): AnnotationValue =
            findAnnotationValue(name) ?: throw IllegalStateException("Annotation value not found for string '$name'")

    /** Get the given annotation value as a [AnnotationValue] if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValue(name: String): AnnotationValue? =
            processingEnvironment.elementUtils.getElementValuesWithDefaults(this).keys
                    .filter { k -> k.simpleName.toString() == name }
                    .mapNotNull { k -> elementValues[k] }
                    .firstOrNull()

    /** Get the mirror of the single annotation instance matching the given [annotationClass] for this element. */
    fun Element.getAnnotationMirror(annotationClass: Class<out Annotation>): AnnotationMirror =
            findAnnotationMirror(annotationClass)
                    ?: throw IllegalStateException("Annotation value not found for class ${annotationClass.name}")

    /** Get the mirror of the single annotation instance matching the given [annotationClass] for this element. */
    fun Element.findAnnotationMirror(annotationClass: Class<out Annotation>): AnnotationMirror? =
            findAnnotationMirrors(annotationClass).firstOrNull()

    /**
     * Get the mirrors of the annotation instances matching the given [annotationClass] for this element.
     * Mostly useful with [Repeatable] annotations.
     */
    fun Element.findAnnotationMirrors(annotationClass: Class<out Annotation>): List<AnnotationMirror> {
        val annotationClassName = annotationClass.name
        return this.annotationMirrors
                .filter { mirror -> mirror != null && mirror.annotationType.toString().equals(annotationClassName) }
    }

    /** Returns a set of the names of fields with getters (actually the names of getter methods with 'get' removed and decapitalised). */
    fun TypeElement.getterFieldNames(): Set<String> {
        val allMembers = processingEnvironment.elementUtils.getAllMembers(this)
        return ElementFilter.methodsIn(allMembers)
                .filter { it.simpleName.startsWith("get") && it.parameters.isEmpty() }
                .map { it.simpleName.toString().substringAfter("get").decapitalize() }
                .toSet()
    }

    /** Creates a property for the field identified by this element. */
    fun Element.asProperty(): PropertySpec =
            PropertySpec.builder(simpleName.toString(), asKotlinTypeName().copy(nullable = true), KModifier.PRIVATE)
                    .mutable()
                    .initializer(defaultValue())
                    .build()

    /** Returns the correct default value for this element - the value of any [DefaultValue] annotation, or "null". */
    fun Element.defaultValue(): String {
        return if (hasAnnotation(DefaultValue::class.java)) {
            val default = this.findAnnotation(DefaultValue::class.java).value
            // make sure that strings are wrapped in quotes
            return if (asType().toString() == "java.lang.String" && !default.startsWith("\"")) {
                "\"$default\""
            } else {
                default
            }
        } else {
            "null"
        }
    }

    /** Creates a function that sets the property identified by this element, and returns the [builder]. */
    fun Element.asSetterFunctionReturning(builder: ClassName): FunSpec {
        val fieldType = asKotlinTypeName()
        val parameterClass = if (isNullable()) {
            fieldType.copy(nullable = true)
        } else {
            fieldType
        }
        return FunSpec.builder(simpleName.toString())
                .addParameter(ParameterSpec.builder("value", parameterClass).build())
                .returns(builder)
                .addCode("return apply·{ this.$simpleName·=·value }\n")
                .build()
    }

    /**
     * Converts this element to a [TypeName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent,
     * also converting the TypeName according to any [NullableType] and [Mutable] annotations.
     */
    fun Element.asKotlinTypeName(): TypeName {
        var typeName = asType().asKotlinTypeName()
        if (typeName is ParameterizedTypeName) {
            if (hasAnnotation(NullableType::class.java)
                    && assert(typeName.typeArguments.isNotEmpty(), "NullableType annotation should not be applied to a property without type arguments!")) {
                typeName = typeName.withNullableType()
            }
            if (hasAnnotation(Mutable::class.java)
                    && assert(BuilderProcessor.MUTABLE_COLLECTIONS.containsKey(typeName.rawType), "Mutable annotation should not be applied to non-mutable collections!")) {
                typeName = typeName.asMutableCollection()
            }
        }
        return typeName
    }

    /**
     * Converts this type to one containing nullable elements.
     *
     * For instance `List<String>` is converted to `List<String?>`, `Map<String, String>` to `Map<String, String?>`).
     * @throws NoSuchElementException if [this.typeArguments] is empty.
     */
    fun ParameterizedTypeName.withNullableType(): ParameterizedTypeName {
        val lastType = this.typeArguments.last().copy(nullable = true)
        val typeArguments = ArrayList<TypeName>()
        typeArguments.addAll(this.typeArguments.dropLast(1))
        typeArguments.add(lastType)
        return this.rawType.parameterizedBy(*typeArguments.toTypedArray())
    }

    /**
     * Converts this type to its mutable equivalent.
     *
     * For instance `List<String>` is converted to `MutableList<String>`.
     * @throws NullPointerException if [this.rawType] cannot be mapped to a mutable collection
     */
    fun ParameterizedTypeName.asMutableCollection(): ParameterizedTypeName {
        val mutable = BuilderProcessor.MUTABLE_COLLECTIONS[rawType]!!
                .parameterizedBy(*this.typeArguments.toTypedArray())
                .copy(annotations = this.annotations)
        return if (isNullable) {
            mutable.copy(nullable = true)
        } else {
            mutable
        }
    }

    /** Converts this TypeMirror to a [TypeName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent. */
    fun TypeMirror.asKotlinTypeName(): TypeName {
        return when (this) {
            is PrimitiveType -> processingEnvironment.typeUtils.boxedClass(this as PrimitiveType?).asKotlinClassName()
            is ArrayType -> {
                val arrayClass = ClassName("kotlin", "Array")
                return arrayClass.parameterizedBy(this.componentType.asKotlinTypeName())
            }
            is DeclaredType -> {
                val typeName = this.asTypeElement().asKotlinClassName()
                if (!this.typeArguments.isEmpty()) {
                    val kotlinTypeArguments = typeArguments.stream()
                            .map { it.asKotlinTypeName() }
                            .collect(Collectors.toList())
                            .toTypedArray()
                    return typeName.parameterizedBy(*kotlinTypeArguments)
                }
                return typeName
            }
            else -> this.asTypeElement().asKotlinClassName()
        }
    }

    /** Converts this element to a [ClassName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent. */
    fun TypeElement.asKotlinClassName(): ClassName {
        val className = asClassName()
        return try {
            // ensure that java.lang.* and java.util.* etc classes are converted to their kotlin equivalents
            Class.forName(className.canonicalName).kotlin.asClassName()
        } catch (e: ClassNotFoundException) {
            // probably part of the same source tree as the annotated class
            className
        }
    }

    /** Returns the [TypeElement] represented by this [TypeMirror]. */
    fun TypeMirror.asTypeElement() = processingEnvironment.typeUtils.asElement(this) as TypeElement

    /** Returns true as long as this [Element] is not a [PrimitiveType] and does not have the [NotNull] annotation. */
    fun Element.isNullable(): Boolean {
        if (this.asType() is PrimitiveType) {
            return false
        }
        return !hasAnnotation(NotNull::class.java)
    }

    /**
     * Returns true if this element has the specified [annotation], or if the parent class has a matching constructor parameter with the annotation.
     * (This is necessary because builder annotations can be applied to both fields and constructor parameters - and constructor parameters take precedence.
     * Rather than require clients to specify, for instance, `@field:NullableType`, this method also checks for annotations of constructor parameters
     * when this element is a field).
     */
    fun Element.hasAnnotation(annotation: Class<*>): Boolean {
        return hasAnnotationDirectly(annotation) || hasAnnotationViaConstructorParameter(annotation)
    }

    /** Return true if this element has the specified [annotation]. */
    fun Element.hasAnnotationDirectly(annotation: Class<*>): Boolean {
        return this.annotationMirrors
                .map { it.annotationType.toString() }
                .toSet()
                .contains(annotation.name)
    }

    /** Return true if there is a constructor parameter with the same name as this element that has the specified [annotation]. */
    fun Element.hasAnnotationViaConstructorParameter(annotation: Class<*>): Boolean {
        val parameterAnnotations = getConstructorParameter()?.annotationMirrors ?: listOf()
        return parameterAnnotations
                .map { it.annotationType.toString() }
                .toSet()
                .contains(annotation.name)
    }

    /** Returns the first constructor parameter with the same name as this element, if any such exists. */
    fun Element.getConstructorParameter(): VariableElement? {
        val enclosingElement = this.enclosingElement
        return if (enclosingElement is TypeElement) {
            val allMembers = processingEnvironment.elementUtils.getAllMembers(enclosingElement)
            ElementFilter.constructorsIn(allMembers)
                    .flatMap { it.parameters }
                    .firstOrNull { it.simpleName == this.simpleName }
        } else {
            null
        }
    }

    /**
     * Returns the given annotation, retrieved from this element directly, or from the corresponding constructor parameter.
     *
     * @throws NullPointerException if no such annotation can be found - use [hasAnnotation] before calling this method.
     */
    fun <A : Annotation> Element.findAnnotation(annotation: Class<A>): A {
        return if (hasAnnotationDirectly(annotation)) {
            getAnnotation(annotation)
        } else {
            getConstructorParameter()!!.getAnnotation(annotation)
        }
    }

    /** Returns the given [assertion], logging an error message if it is not true. */
    fun Element.assert(assertion: Boolean, message: String): Boolean {
        if (!assertion) {
            this.errorMessage { message }
        }
        return assertion
    }

    /** Prints an error message using this element as a position hint. */
    fun Element.errorMessage(message: () -> String) {
        processingEnvironment.messager.printMessage(ERROR, message(), this)
    }


    fun ProcessingEnvironment.errorMessage(message: () -> String) {
        this.messager.printMessage(ERROR, message())
    }

    fun ProcessingEnvironment.noteMessage(message: () -> String) {
        this.messager.printMessage(NOTE, message())
    }

}