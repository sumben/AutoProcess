package com.auto.process.api;

import com.auto.process.annotation.AnnotationConst;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by Jack on 10/27/17.
 */

public class ViewProcessHelper{

    public static void bind(Object host){
        String clzFullName = host.getClass().getName()+AnnotationConst.CLASS_POSTFIX;
        try {
            Class<?> clz = Class.forName(clzFullName);
            Constructor<?> constructor = clz.getConstructor(host.getClass());
            constructor.newInstance(host);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    public void unbind(Object host) {

    }

    public static void hook(Object host){

    }
}
