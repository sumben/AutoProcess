package com.auto.process.compiler;

import com.auto.process.annotation.ActivityModule;
import com.auto.process.annotation.AnnotationConst;
import com.auto.process.annotation.Exclusive;
import com.auto.process.annotation.ViewModule;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/**
 * Created by Jack on 10/20/17.
 */

@AutoService(Processor.class)
public class AnnotationProcess extends AbstractProcessor {
    private Elements mElementUtils;
    private Filer mWriter;
    private Messager mOutputMessager;
    private HashMap<String,MetaData> metaDataMap;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotationTypes = new LinkedHashSet<>();
        annotationTypes.add(Exclusive.class.getCanonicalName());
        annotationTypes.add(ActivityModule.class.getCanonicalName());
        annotationTypes.add(ViewModule.class.getCanonicalName());
        return annotationTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mOutputMessager = processingEnv.getMessager();
        mWriter = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();

        metaDataMap = new HashMap<>(10);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set,
                           RoundEnvironment roundEnv) {
        mOutputMessager.printMessage(Diagnostic.Kind.NOTE,
                "start compiler time annotation process...");
        metaDataMap.clear();
        findAndParserTarget(roundEnv);
        generateJavaFile();
        return true;
    }

    private void findAndParserTarget(RoundEnvironment roundEnv) {
        Set<? extends Element> elementSet
                = roundEnv.getElementsAnnotatedWith(Exclusive.class);
        for (Element element : elementSet) {
            if(!SuperficialValidation.validateElement(element)
                    ||element.getKind() != ElementKind.FIELD){
                continue;
            }
            int viewId = element.getAnnotation(Exclusive.class).value();
            VariableElement vElement = (VariableElement) element;
            TypeElement tElement = (TypeElement) vElement.getEnclosingElement();
            String aClassName = tElement.getQualifiedName().toString();
            MetaData metaData = metaDataMap.get(aClassName);
            if(metaData==null){
                metaData = new MetaData();
                metaData.typeElement = tElement;
            }

            LinkedList<FieldResource> frList = metaData.fields;
            if(frList==null){
                frList = new LinkedList<>();
            }

            FieldResource fr = new FieldResource();
            fr.resId = viewId;
            fr.vEle = vElement;

            frList.add(fr);
            metaData.fields = frList;
            metaDataMap.put(aClassName,metaData);
        }
    }

    private void generateJavaFile() {
        try {
            ArrayList<FieldSpec> fieldSpecs = new ArrayList<>(10);
            TypeSpec.Builder typeBuilder = null;
            String packageName = null;
            MethodSpec.Builder constructorBuilder = null;
            for (String aClassName : metaDataMap.keySet()) {
                MetaData metaData = metaDataMap.get(aClassName);
                TypeElement tElement = metaData.typeElement;
                if(constructorBuilder==null){
                    constructorBuilder = MethodSpec.constructorBuilder()
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(ParameterSpec.builder(
                                    TypeName.get(tElement.asType()), "activity").build());
                }
                MethodSpec.Builder accessor
                        = MethodSpec.methodBuilder(AnnotationConst.FIELD_ACCESSOR);
                LinkedList<FieldResource> frList = metaData.fields;
                for (FieldResource fr : frList) {
                    VariableElement vElement = fr.vEle;
                    String fieldName = vElement.getSimpleName().toString();
                    String fieldType = vElement.asType().toString();
                    constructorBuilder.addStatement("activity.$L=($L)activity.findViewById($L)", fieldName, fieldType, fr.resId);

                    FieldSpec.Builder fieldBuilder
                            = FieldSpec.builder(ClassName.get(vElement.asType()),fieldName)
                                       .addModifiers(Modifier.PUBLIC);
                    fieldSpecs.add(fieldBuilder.build());
                }
                if(packageName==null){
                    packageName = mElementUtils.getPackageOf(tElement)
                            .getQualifiedName().toString();
                }
                if(typeBuilder==null){
                    typeBuilder = TypeSpec.classBuilder(
                            getClassName(tElement, packageName) + AnnotationConst.POSTFIX)
                            .addModifiers(Modifier.PUBLIC);
                }
            }
            for (FieldSpec fieldSpec : fieldSpecs){
                typeBuilder.addField(fieldSpec);
            }
            if(typeBuilder!=null){
                typeBuilder.addMethod(constructorBuilder.build());
                JavaFile javaFile = JavaFile.builder(packageName, typeBuilder.build()).build();
                javaFile.writeTo(mWriter);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            mOutputMessager.printMessage(Diagnostic.Kind.ERROR,
                    "exception occur:" + ex.getMessage());
        }

    }

    private static String getClassName(TypeElement type, String packageName) {
        int packageLen = packageName.length() + 1;
        return type.getQualifiedName().toString().substring(packageLen).replace('.', '$');
    }

    private class VariableInfo {
        int viewId;
        VariableElement variableElement;
    }

    private class MetaData{
        TypeElement typeElement;
        LinkedList<FieldResource> fields;
    }

    private class FieldResource{
        int resId;
        VariableElement vEle;
    }
}
