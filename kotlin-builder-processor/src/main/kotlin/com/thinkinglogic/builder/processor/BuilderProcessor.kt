package com.thinkinglogic.builder.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.thinkinglogic.builder.annotation.Builder
import com.thinkinglogic.builder.annotation.BuilderOf
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter.constructorsIn
import javax.lang.model.util.ElementFilter.fieldsIn

/**
 * Kapt processor for the @Builder annotation.
 * Constructs a Builder for the annotated class.
 */
@SupportedAnnotationTypes(
        "com.thinkinglogic.builder.annotation.Builder",
        "com.thinkinglogic.builder.annotation.BuilderOf")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(BuilderProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class BuilderProcessor : AbstractProcessor(), BuilderExtensions {

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        const val CHECK_REQUIRED_FIELDS_FUNCTION_NAME = "checkRequiredFields"
        const val TARGET_CLASS = "targetClass"
        const val SUFFIX_KEY = "suffix"
        const val USE_CONSTR_KEY = "useConstructors"
        val MUTABLE_COLLECTIONS = mapOf(
                List::class.asClassName() to ClassName("kotlin.collections", "MutableList"),
                Set::class.asClassName() to ClassName("kotlin.collections", "MutableSet"),
                Collection::class.asClassName() to ClassName("kotlin.collections", "MutableCollection"),
                Map::class.asClassName() to ClassName("kotlin.collections", "MutableMap"),
                Iterator::class.asClassName() to ClassName("kotlin.collections", "MutableIterator"))
    }

    data class BuilderInfo(
            val targetClass: TypeElement,
            val fields: List<VariableElement>,
            val sourceRoot: File,
            val fieldMixins: List<VariableElement> = emptyList(),
            val suffix: String = Builder::class.java.simpleName)

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(Builder::class.java)
        val annotatedOfElements = roundEnv.getElementsAnnotatedWith(BuilderOf::class.java)
        if ((annotatedElements + annotatedOfElements).isEmpty()) {
            processingEnv.noteMessage {
                "No classes annotated with @${Builder::class.java.simpleName} " +
                        "or @${BuilderOf::class.java.simpleName} in this round ($roundEnv)" }
            return false
        }

        val generatedSourcesRoot = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
            processingEnv.errorMessage { "Can't find the target directory for generated Kotlin files." }
            return false
        }

        processingEnv.noteMessage { "Generating Builders for ${annotatedElements.size} classes in $generatedSourcesRoot" }

        val sourceRootFile = File(generatedSourcesRoot)
        sourceRootFile.mkdir()

        annotatedElements.forEach { annotatedElement ->
            when (annotatedElement.kind) {
                ElementKind.CLASS -> writeBuilderForClass(annotatedElement as TypeElement, sourceRootFile)
                ElementKind.CONSTRUCTOR -> writeBuilderForConstructor(annotatedElement as ExecutableElement, sourceRootFile)
                else -> annotatedElement.errorMessage { "Invalid element type, expected a class or constructor" }
            }
        }
        annotatedOfElements.forEach { annotatedElement ->
            val annotation = annotatedElement.getAnnotationMirror(BuilderOf::class.java)
            val annotationValues = processingEnvironment.elementUtils.getElementValuesWithDefaults(annotation)
            val targetClass = annotation.getAnnotationValue(TARGET_CLASS)
            val classElement: TypeElement = processingEnv.typeUtils
                    .asElement(targetClass.value as TypeMirror).asType().asTypeElement()
            val suffix = annotation.findAnnotationValue(SUFFIX_KEY)?.value?.toString()
                   ?: Builder::class.java.simpleName
            val useConstructors = annotation.findAnnotationValue(
                    USE_CONSTR_KEY)?.value as Boolean? ?: false
            val fields = if(useConstructors) {
                val constructor: ExecutableElement =
                        constructorsIn(processingEnvironment.elementUtils
                                .getAllMembers(classElement)).firstOrNull()
                            ?: throw IllegalArgumentException("No constructor found the target class")
                constructor.parameters
            }
            else classElement.fieldsForBuilder()

            writeBuilder(BuilderInfo(
                    targetClass = classElement,
                    fields = fields,
                    sourceRoot = sourceRootFile,
                    fieldMixins = emptyList(),
                    suffix = suffix
            ))
        }

        return false
    }


    /** Creates a 'build()' function that will invoke a constructor for [returnType], passing [fields] as arguments and returning the new instance. */
    fun createBuildFunction(builderInfo: BuilderInfo): FunSpec {
        val code = StringBuilder("${BuilderProcessor.CHECK_REQUIRED_FIELDS_FUNCTION_NAME}()")
        code.appendln().append("return·${builderInfo.targetClass.simpleName}(")
        val iterator = builderInfo.fields.listIterator()
        while (iterator.hasNext()) {
            val field = iterator.next()
            code.appendln().append("    ${field.simpleName}·=·${field.simpleName}")
            if (!field.isNullable()) {
                code.append("!!")
            }
            if (iterator.hasNext()) {
                code.append(",")
            }
        }
        code.appendln().append(")").appendln()

        return FunSpec.builder("build")
                .returns(builderInfo.targetClass.asClassName())
                .addCode(code.toString())
                .build()
    }

    /** Creates a function that will invoke [check] to confirm that each required field is populated. */
    fun createCheckRequiredFieldsFunction(builderInfo: BuilderInfo): FunSpec {
        val code = StringBuilder()
        builderInfo.fields.filterNot { it.isNullable() }
                .forEach { field ->
                    code.append("    check(${field.simpleName}·!=·null, {\"${field.simpleName}·must·not·be·null\"})").appendln()
                }

        return FunSpec.builder(BuilderProcessor.CHECK_REQUIRED_FIELDS_FUNCTION_NAME)
                .addCode(code.toString())
                .addModifiers(KModifier.PRIVATE)
                .build()
    }

    /** Invokes [writeBuilder] to create a builder for the given [classElement]. */
    private fun writeBuilderForClass(classElement: TypeElement, sourceRootFile: File) {
        writeBuilder(BuilderInfo(
                classElement,
                classElement.fieldsForBuilder(),
                sourceRootFile))
    }

    /** Invokes [writeBuilder] to create a builder for the given [constructor]. */
    private fun writeBuilderForConstructor(constructor: ExecutableElement, sourceRootFile: File) {
        writeBuilder(BuilderInfo(
                constructor.enclosingElement as TypeElement,
                constructor.parameters,
                sourceRootFile))
    }

    /** Writes the source code to create a builder for [classToBuild] within the [sourceRoot] directory. */
    private fun writeBuilder(builderInfo: BuilderInfo) {

        val packageName = processingEnv.elementUtils.getPackageOf(builderInfo.targetClass).toString()
        val builderClassName = "${builderInfo.targetClass.simpleName}${builderInfo.suffix}"

        processingEnv.noteMessage { "Writing $packageName.$builderClassName" }

        val builderSpec = TypeSpec.classBuilder(builderClassName)
        val builderClass = ClassName(packageName, builderClassName)

        builderInfo.fields.forEach { field ->
            processingEnv.noteMessage { "Adding field: $field" }
            builderSpec.addProperty(field.asProperty())
            builderSpec.addFunction(field.asSetterFunctionReturning(builderClass))
        }

        builderSpec.primaryConstructor(FunSpec.constructorBuilder().build())
        builderSpec.addFunction(createConstructor(builderInfo))
        builderSpec.addFunction(createBuildFunction(builderInfo))
        builderSpec.addFunction(createCheckRequiredFieldsFunction(builderInfo))

        FileSpec.builder(packageName, builderClassName)
                .addType(builderSpec.build())
                .build()
                .writeTo(builderInfo.sourceRoot)
    }

    /** Returns all fields in this type that also appear as a constructor parameter. */
    private fun TypeElement.fieldsForBuilder(): List<VariableElement> {
        val allMembers = processingEnv.elementUtils.getAllMembers(this)
        val fields = fieldsIn(allMembers)
        val constructors = constructorsIn(allMembers)
        val constructorParamNames = constructors
                .flatMap { it.parameters }
                .map { it.simpleName.toString() }
                .toSet()
        return fields.filter { constructorParamNames.contains(it.simpleName.toString()) }
    }

    /** Creates a constructor for [classType] that accepts an instance of the class to build, from which default values are obtained. */
    private fun createConstructor(builderInfo: BuilderInfo): FunSpec {
        val source = "source"
        val sourceParameter = ParameterSpec.builder(source, builderInfo.targetClass.asKotlinTypeName()).build()
        val getterFieldNames = builderInfo.targetClass.getterFieldNames()
        val code = StringBuilder()
        builderInfo.fields.forEach { field ->
            if (getterFieldNames.contains(field.simpleName.toString())) {
                code.append("    this.${field.simpleName}·=·$source.${field.simpleName}")
                        .appendln()
            }
        }
        return FunSpec.constructorBuilder()
                .addParameter(sourceParameter)
                .callThisConstructor()
                .addCode(code.toString())
                .build()
    }

    override val processingEnvironment: ProcessingEnvironment
        get() = processingEnv


}
