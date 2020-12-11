package com.gocypher.cybench.utils;

import com.gocypher.cybench.core.utils.JMHUtils;
import com.sun.org.apache.bcel.internal.Repository;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Method;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public class PluginUtils {

    public static String checkReportSaveLocation(String fileName){
        if(!fileName.endsWith("/")){
            fileName = fileName +"/";
        }
        return fileName;
    }
    public static void fingerprintAndHashGeneration(Project project, BenchmarkList benchmarkList, Map<String, String> generatedFingerprints, Map<String, String> manualFingerprints, Map<String, String> classFingerprints){
        SourceSet sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getAt("main");
        FileCollection test = sourceSets.getRuntimeClasspath();
        List<URL> urls = new ArrayList<URL>();
        for(File name : test){
            try {
//                project.getLogger().lifecycle("Dependency Class loader: "+name);
                URL url = name.toURI().toURL();
                urls.add(url);
            }catch (MalformedURLException ex){
                project.getLogger().error("Class not found in the classpath for execution", ex);
            }
        }
        try {
            File testSourceRoot = new File(project.getBuildDir() + PluginConstants.TEST_SOURCE_ROOT);
            URL url = testSourceRoot.toURI().toURL();
            urls.add(url);
        }catch (MalformedURLException ex){
            project.getLogger().error("Class not found in the classpath for execution {} - {}",project.getBuildDir() + PluginConstants.TEST_SOURCE_ROOT, ex);
        }
        project.getLogger().lifecycle("------------------------------------------------------------------------------------");
        Set<BenchmarkListEntry> all = benchmarkList.getAll(new JMHUtils.SilentOutputFormat(), Collections.EMPTY_LIST);
        List<String> benchmarkNames = all.stream().map(BenchmarkListEntry::getUserClassQName).collect(Collectors.toList());
        URL[] urlsArray = urls.toArray(new URL[0]);
        try(URLClassLoader cl = new URLClassLoader(urlsArray)){
            for (String benchmarkClass : benchmarkNames) {
                Class<?> cls = cl.loadClass(benchmarkClass);
                JavaClass javaClass = Repository.lookupClass(cls);
                Class benchmarkAnnotationClass = cl.loadClass("org.openjdk.jmh.annotations.Benchmark");
                List<String> benchmarkMethods = new ArrayList<>();
                for (java.lang.reflect.Method method1 : cls.getMethods()) {
                    if (method1.getAnnotation(benchmarkAnnotationClass) != null) {
                        String name = method1.getName();
                        benchmarkMethods.add(name);
                    }
                }
                for (Method method : javaClass.getMethods()) {
                    try {
                        if (benchmarkMethods.contains(method.getName())) {
                            String hash = hashByteArray(concatArrays(method.getName().getBytes(), method.getSignature().getBytes() ,  method.getCode().getCode()));
                            generatedFingerprints.put(cls.getName() + "." +method.getName(), hash);
                        }
                    } catch (Exception e) {
                        project.getLogger().error("Failed to compute hash for method {} in class {}", method.getName(),cls, e);
                    }
                }
                String classHash = computeClassHash(cls, project);
                java.lang.reflect.Method[] methods = cls.getMethods();
                for (java.lang.reflect.Method method : methods) {
                    if (method.getAnnotation(benchmarkAnnotationClass) != null) {
                        Class benchmarkAnnotationTagClass = cl.loadClass("com.gocypher.cybench.core.annotation.BenchmarkTag");
                        Annotation annotation = method.getAnnotation(benchmarkAnnotationTagClass);
                        if (annotation != null) {
                            String tag = null;
                            if(annotation.toString().contains("com.gocypher.cybench.core.annotation.BenchmarkTag")) {
                                String result = StringUtils.substringBetween(annotation.toString(), "(", ")");
                                tag = result.replace("tag=", "");
                            }
                            manualFingerprints.put(cls.getName() + "." + method.getName(), tag);
                        }
                    }
                    classFingerprints.put(cls.getName() + "." + method.getName(), classHash);
                }
            }
        } catch(Exception exc){
            project.getLogger().error("Class not found in the classpath for execution", exc);
        }
    }
    public static byte[] concatArrays(byte[] ... bytes) {
        String collect = Arrays.stream(bytes).map(String::new).collect(Collectors.joining());
        return collect.getBytes();
    }
    public static String hashByteArray(byte[] classBytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.reset();
        byte[] digested = md.digest(classBytes);
        StringBuffer sb = new StringBuffer();
        for (byte b : digested) {
            sb.append(Integer.toHexString(0xff & b));
        }
        return sb.toString();
    }




    public static Map<String, Object> customUserDefinedProperties(String customPropertiesStr) {
        Map<String, Object> customUserProperties = new HashMap<>();
        if (customPropertiesStr != null && !customPropertiesStr.isEmpty()){
            String [] pairs = customPropertiesStr.split(";") ;
            for (String pair:pairs){
                String [] kv = pair.split("=");
                if (kv.length == 2){
                    customUserProperties.put(kv[0],kv[1]) ;
                }
            }
        }
        return customUserProperties;
    }

    public static byte[] getObjectBytes(Object obj){
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutputStream out;
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  new byte[]{};
    }

    public static String computeClassHash(Class<?> clazz, Project project) {
        if (clazz != null) {
            try {
                byte[] classBytes = getObjectBytes(clazz);
                String classMD5Hash = hashByteArray(classBytes);
                return classMD5Hash;
            } catch (Exception e) {
                project.getLogger().lifecycle("Failed to compute hash for class {}", clazz, e);
            }
        }
        return null;
    }

    public static void generateMethodFingerprints(Class<?> benchmarkClass, Map<String, String> manualFingerprints, Map<String, String> classFingerprints) {

    }

    public static void UpdateFieldViaReflection(Object target, String fieldName, Class<?> classObject, Object value) {
        try {
            Field listField = classObject.getDeclaredField(fieldName);
            updateFileAccess(listField);
            listField.set(target, value);
        } catch (Exception e) {
            throw new GradleException(
                    "Error : unable to set '" + fieldName + "' on " + classObject.getSimpleName(), e);
        }
    }
    public static void updateFileAccess(Field listField) throws NoSuchFieldException, IllegalAccessException {
        listField.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(listField, listField.getModifiers() & ~Modifier.FINAL);
    }


}