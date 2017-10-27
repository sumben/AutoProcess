package com.auto.process.compiler;

import com.auto.process.annotation.AnnotationConst;
import com.auto.process.annotation.ExclusiveEvent;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Created by Jack on 10/20/17.
 */

@SupportedAnnotationTypes("com.auto.process.annotation.ExclusiveEvent")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class AnnotationProcess extends AbstractProcessor {
    private Elements mElementUtils;
    private Filer mJavaFiler;
    private Messager mJavaMessager;
    private HashMap<String, TypeElement> mTypeElements;
    private HashMap<String, LinkedList<VariableInfo>> mVariableInfos;

    /*@Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotationTypes = new LinkedHashSet<>();
        annotationTypes.add(ExclusiveEvent.class.getCanonicalName());
        return annotationTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }*/

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mJavaMessager = processingEnv.getMessager();
        mJavaFiler = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();

        mTypeElements = new HashMap<>();
        mVariableInfos = new HashMap<>();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set,
                           RoundEnvironment roundEnv) {
        mJavaMessager.printMessage(Diagnostic.Kind.NOTE, "begin annotation process...");
        mTypeElements.clear();
        mVariableInfos.clear();
        findAnnotationInfo(roundEnv);
        generateJavaFile();
        //generateJavaFile2(set,roundEnv);
        return true;
    }

    private void findAnnotationInfo(RoundEnvironment roundEnv) {
        Set<? extends Element> elements
                = roundEnv.getElementsAnnotatedWith(ExclusiveEvent.class);
        for (Element ele : elements) {
            if (ele.getKind() != ElementKind.FIELD) continue;
            int viewId = ele.getAnnotation(ExclusiveEvent.class).value();
            VariableElement vElement = (VariableElement) ele;
            TypeElement tElement = (TypeElement) vElement.getEnclosingElement();
            String fullClzName = tElement.getQualifiedName().toString();
            LinkedList<VariableInfo> vInfoList = mVariableInfos.get(fullClzName);
            if (vInfoList == null) {
                vInfoList = new LinkedList<>();
                mVariableInfos.put(fullClzName, vInfoList);
                mTypeElements.put(fullClzName, tElement);
            }
            VariableInfo vInfo = new VariableInfo();
            vInfo.viewId = viewId;
            vInfo.variableElement = vElement;
            vInfoList.add(vInfo);
        }
    }

    private void generateJavaFile() {
        try {
            for (String fullClzName : mTypeElements.keySet()) {
                TypeElement tElement = mTypeElements.get(fullClzName);
                MethodSpec.Builder constructor
                        = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ParameterSpec.builder(
                                TypeName.get(tElement.asType()), "activity").build());
                LinkedList<VariableInfo> infoList = mVariableInfos.get(fullClzName);
                for (VariableInfo vInfo : infoList) {
                    VariableElement vElement = vInfo.variableElement;
                    String fieldName = vElement.getSimpleName().toString();
                    String fieldFullType = vElement.asType().toString();
                    constructor.addStatement("activity.$L=($L)activity.findViewById($L)", fieldName, fieldFullType, vInfo.viewId);
                }

                String packageFullName = mElementUtils.getPackageOf(tElement)
                        .getQualifiedName().toString();

                TypeSpec typeSpec = TypeSpec.classBuilder(getClassName(tElement, packageFullName) + AnnotationConst.POSTFIX)
                        .addModifiers(Modifier.PUBLIC)
                        .addMethod(constructor.build())
                        .build();

                JavaFile javaFile = JavaFile.builder(packageFullName, typeSpec).build();
                javaFile.writeTo(mJavaFiler);
                //javaFile.writeTo(System.out);
            }
        } catch (Exception ex) {
            mJavaMessager.printMessage(Diagnostic.Kind.ERROR, "exception occur:" + ex.getMessage());
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

    private void generateJavaFile2(Set<? extends TypeElement> set,
                                   RoundEnvironment roundEnv) {
        StringBuilder builder = new StringBuilder()
                .append("package com.stablekernel.annotationprocessor.generated;\n")
                .append("public class GeneratedClass {") // open class
                .append("\t\tpublic String getMessage() {\n") // open method
                .append("\treturn ");


        // for each javax.lang.model.element.Element annotated with the CustomAnnotation
        for (Element element : roundEnv.getElementsAnnotatedWith(ExclusiveEvent.class)) {
            String objectType = element.getSimpleName().toString();


            // this is appending to the return statement
            builder.append(objectType).append(" says hello!\n");
        }


        builder.append(";\n") // end return
                .append("\t}\n") // close method
                .append("}\n"); // close class


        try { // write the file
            JavaFileObject source = mJavaFiler.createSourceFile("com.annotation.compiler.GeneratedClass");
            Writer writer = source.openWriter();
            writer.write(builder.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            // Note: calling e.printStackTrace() will print IO errors
            // that occur from the file already existing after its first run, this is normal
        }

    }
}
