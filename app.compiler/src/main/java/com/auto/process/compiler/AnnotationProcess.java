package com.auto.process.compiler;

import com.auto.process.annotation.ActivityModule;
import com.auto.process.annotation.AnnotationConst;
import com.auto.process.annotation.Exclusive;
import com.auto.process.annotation.ViewJsonBean;
import com.auto.process.annotation.ViewModule;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.google.gson.Gson;
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
        findAndParseTarget(roundEnv);
        //generateJavaFile();
        generateGsonFile();
        mOutputMessager.printMessage(Diagnostic.Kind.NOTE,
                "finish compiler time annotation process.");
        return true;
    }

    private void findAndParseTarget(RoundEnvironment roundEnv) {
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

    private MethodSpec createGetViewMethod(){
        MethodSpec.Builder builder
                = MethodSpec.methodBuilder("getViewById")
                .addParameter(ParameterSpec.builder(
                        TypeName.OBJECT,"viewHost").build())
                .addParameter(int.class,"viewId",Modifier.FINAL)
                .returns(ClassName.get("android.view","View"))
                .addModifiers(Modifier.PUBLIC,Modifier.FINAL)
                .addStatement("$T view = null",
                        ClassName.get("android.view","View"))
                .beginControlFlow("if(viewHost instanceof $T)"
                        ,ClassName.get("android.app","Activity"))
                .addStatement("Activity activity = (Activity)viewHost")
                .addStatement("view = (View)activity.findViewById($L)","viewId")
                .nextControlFlow("else if(viewHost instanceof View)")
                .addStatement("View root = (View)viewHost")
                .addStatement("view = root.findViewById($L)","viewId")
                .endControlFlow()
                .addStatement("return view");
        return builder.build();
    }

    private MethodSpec createCacheFileMethod(){
        MethodSpec.Builder builder
                = MethodSpec.methodBuilder("getCacheFileDir")
                     .addParameter(TypeName.OBJECT,"viewHost")
                     .returns(String.class)
                     .addModifiers(Modifier.PUBLIC,Modifier.FINAL)
                     .addStatement("$T cacheFileDir = null",String.class)
                     .beginControlFlow("if(viewHost instanceof $T)",
                             ClassName.get("android.app","Activity"))
                     .addStatement("Activity activity = (Activity)viewHost")
                     .addStatement("cacheFileDir = activity.getCacheDir().getPath()")
                     .nextControlFlow("else if(viewHost instanceof $T)",
                             ClassName.get("android.view","View"))
                     .addStatement("View root = (View)viewHost")
                     .addStatement("cacheFileDir = root.getContext().getCacheDir().getPath()")
                     .endControlFlow()
                     .addStatement("return cacheFileDir");
        return builder.build();
    }

    private MethodSpec createWriteFileMethod(){
        MethodSpec.Builder builder
                = MethodSpec.methodBuilder("writeFile")
                            .addParameter(String.class,"fileFullName")
                            .addParameter(String.class,"json")
                            .addModifiers(Modifier.PRIVATE)
                            .addStatement("$T fileWriter = null",
                                    ClassName.get("java.io","FileWriter"))
                            .beginControlFlow("try")
                            .addStatement("\tfileWriter = new $L(fileFullName)","FileWriter")
                            .addStatement("\tfileWriter.write(json)")
                            .addStatement("\tfileWriter.flush()")
                            .nextControlFlow("catch($T e)",
                                    ClassName.get("java.io","FileNotFoundException"))
                            .addStatement("\te.printStackTrace()")
                            .nextControlFlow(" catch ($T e) ",
                                    ClassName.get("java.io","IOException"))
                            .addStatement("\te.printStackTrace()")
                            .addCode("} finally {\n"+
                                        "\t\ttry{\n"
                                           +"\t\t\tfileWriter.close();\n"
                                           +"\t\t}catch(IOException e){\n"
                                           +"\t\t\te.printStackTrace();\n"
                                           +"\t\t}\n")
                            .endControlFlow();
        return builder.build();
    }

    private MethodSpec.Builder createAccessorBuilder(){
        MethodSpec.Builder builder
                = MethodSpec.methodBuilder(AnnotationConst.FIELD_ACCESSOR)
                            .addModifiers(Modifier.PUBLIC,Modifier.FINAL)
                            .addParameter(TypeName.OBJECT,"viewHost");
        return builder;
    }

    private TypeSpec.Builder createClassBuilder(String qualifiedName){
        return TypeSpec.classBuilder(
                qualifiedName + AnnotationConst.CLASS_POSTFIX)
                .addModifiers(Modifier.PUBLIC);
    }

    private void generateGsonFile(){
        try {
            MethodSpec.Builder constructorBuilder = null;
            TypeSpec.Builder typeBuilder = null;
            String packageName = null;
            Gson gson = new Gson();
            LinkedList<ViewJsonBean> jsonBeanList = new LinkedList<>();
            FieldSpec jsonField = null;
            for (String aClassName : metaDataMap.keySet()) {
                MetaData metaData = metaDataMap.get(aClassName);
                TypeElement tElement = metaData.typeElement;
                LinkedList<FieldResource> frList = metaData.fields;
                if(constructorBuilder==null){
                    constructorBuilder = MethodSpec.constructorBuilder()
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(ParameterSpec.builder(
                                    TypeName.get(tElement.asType()), "viewHost").build())
                            .addStatement("$T cacheFileDir = $N(viewHost)",String.class,"getCacheFileDir");
                }
                ViewJsonBean jsonBean = null;
                for (FieldResource fr : frList) {
                    VariableElement vElement = fr.vEle;
                    String fieldName = vElement.getSimpleName().toString();
                    String fieldType = vElement.asType().toString();
                    jsonBean = new ViewJsonBean();
                    jsonBean.id = fr.resId;
                    jsonBean.viewName = fieldName;
                    jsonBean.typeName = fieldName;
                    jsonBeanList.add(jsonBean);
                }
                if(packageName==null){
                    packageName = mElementUtils.getPackageOf(tElement)
                            .getQualifiedName().toString();
                }

                String json = gson.toJson(jsonBeanList);
                String fileFullName
                        = getClassName(tElement, packageName)+AnnotationConst.JSON_POSTFIX;
                constructorBuilder.addStatement("$T fullDir = cacheFileDir+$S+$S",
                        String.class,"/",fileFullName);
                if(jsonField==null){
                   jsonField = FieldSpec.builder(String.class,"jsonFileData")
                                        .addModifiers(Modifier.PRIVATE,Modifier.FINAL)
                                        .initializer("$S",json)
                                        .build();
                }
                constructorBuilder.addStatement("$N($L,$L)","writeFile","fullDir","jsonFileData");
                constructorBuilder.addStatement("$T.d($S,$L)",
                        ClassName.get("android.util","Log"),"fullDir","fullDir");
                if(typeBuilder==null){
                    typeBuilder = createClassBuilder(getClassName(tElement, packageName));
                }
                if(typeBuilder!=null) {
                    if(jsonField!=null){
                        typeBuilder.addField(jsonField);
                    }
                    if (constructorBuilder != null) {
                        typeBuilder.addMethod(constructorBuilder.build());
                    }
                    typeBuilder.addMethod(createCacheFileMethod());
                    typeBuilder.addMethod(createWriteFileMethod());
                    JavaFile javaFile
                            = JavaFile.builder(packageName, typeBuilder.build())
                            .build();
                    javaFile.writeTo(mWriter);
                }
            }
        }catch (Exception ex) {
            ex.printStackTrace();
            mOutputMessager.printMessage(Diagnostic.Kind.ERROR,
                    "exception occur:" + ex.getMessage());
        }
    }

    private void generateJavaFile() {
        try {
            ArrayList<FieldSpec> fieldSpecs = new ArrayList<>(10);
            TypeSpec.Builder typeBuilder = null;
            String packageName = null;
            MethodSpec.Builder constructorBuilder = null;
            MethodSpec.Builder accessorBuilder = null;
            for (String aClassName : metaDataMap.keySet()) {
                MetaData metaData = metaDataMap.get(aClassName);
                TypeElement tElement = metaData.typeElement;
                if(constructorBuilder==null){
                    constructorBuilder = MethodSpec.constructorBuilder()
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(ParameterSpec.builder(
                                    TypeName.get(tElement.asType()), "viewHost").build());
                }

                LinkedList<FieldResource> frList = metaData.fields;
                for (FieldResource fr : frList) {
                    VariableElement vElement = fr.vEle;
                    String fieldName = vElement.getSimpleName().toString();
                    String fieldType = vElement.asType().toString();
                    constructorBuilder.addStatement("viewHost.$L=($L)$N(viewHost,$L)"
                                            ,fieldName,fieldType,"getViewById",fr.resId);

                    FieldSpec.Builder fieldBuilder
                            = FieldSpec.builder(ClassName.get(vElement.asType()),fieldName)
                                       .addModifiers(Modifier.PUBLIC)
                                       .initializer("null");
                    fieldSpecs.add(fieldBuilder.build());
                    if(accessorBuilder==null){
                        accessorBuilder = createAccessorBuilder();
                    }
                    accessorBuilder.addStatement("$L = ($L)$N(viewHost,$L)"
                            ,fieldName,fieldType,"getViewById",fr.resId);
                }
                if(packageName==null){
                    packageName = mElementUtils.getPackageOf(tElement)
                            .getQualifiedName().toString();
                }
                if(typeBuilder==null){
                    typeBuilder = createClassBuilder(getClassName(tElement, packageName));
                }
                for (FieldSpec fieldSpec : fieldSpecs){
                    typeBuilder.addField(fieldSpec);
                }
                if(typeBuilder!=null){
                    if(constructorBuilder!=null){
                        typeBuilder.addMethod(constructorBuilder.build());
                    }
                    typeBuilder.addMethod(createGetViewMethod());
                    if(accessorBuilder!=null){
                        typeBuilder.addMethod(accessorBuilder.build());
                    }
                    JavaFile javaFile
                            = JavaFile.builder(packageName, typeBuilder.build())
                            .build();
                    javaFile.writeTo(mWriter);
                }
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

    private class MetaData{
        TypeElement typeElement;
        LinkedList<FieldResource> fields;
    }

    private class FieldResource{
        int resId;
        VariableElement vEle;
    }
}
