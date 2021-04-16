package com.gocypher.cybench.utils;

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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;

import com.gocypher.cybench.core.utils.JMHUtils;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.model.BenchmarkReport;
import com.gocypher.cybench.launcher.utils.Constants;

public final class PluginUtils {
    private static final Properties cfg = new Properties();

    private PluginUtils() {
    }

    public static String checkReportSaveLocation(String fileName) {
        if (!fileName.endsWith("/")) {
            fileName = fileName + "/";
        }
        return fileName;
    }

    @SuppressWarnings ({"unchecked", "rawtypes"})
    public static void fingerprintAndHashGeneration(Project project, BenchmarkList benchmarkList,
            Map<String, String> generatedFingerprints, Map<String, String> manualFingerprints,
            Map<String, String> classFingerprints) {
        Set<BenchmarkListEntry> all = benchmarkList.getAll(new JMHUtils.SilentOutputFormat(), Collections.EMPTY_LIST);
        List<String> benchmarkNames = all.stream().map(BenchmarkListEntry::getUserClassQName)
                .collect(Collectors.toList());
        URL[] urlsArray = getUrlsArray(project);
        try (URLClassLoader cl = new URLClassLoader(urlsArray)) {
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
                            String hash = hashByteArray(concatArrays(method.getName().getBytes(),
                                    method.getSignature().getBytes(), method.getCode().getCode()));
                            generatedFingerprints.put(cls.getName() + "." + method.getName(), hash);
                        }
                    } catch (Exception e) {
                        project.getLogger().error("Failed to compute hash for method {} in class {}", method.getName(),
                                cls, e);
                    }
                }
                String classHash = computeClassHash(cls, project);
                java.lang.reflect.Method[] methods = cls.getMethods();
                for (java.lang.reflect.Method method : methods) {
                    if (method.getAnnotation(benchmarkAnnotationClass) != null) {
                        if (cl.findResource(PluginConstants.BENCHMARK_TAG) != null) {
                            Class benchmarkAnnotationTagClass = cl.loadClass(PluginConstants.BENCHMARK_TAG);
                            Annotation annotation = method.getAnnotation(benchmarkAnnotationTagClass);
                            if (annotation != null) {
                                String tag = null;
                                if (annotation.toString().contains(PluginConstants.BENCHMARK_TAG)) {
                                    String result = StringUtils.substringBetween(annotation.toString(), "(", ")");
                                    tag = result.replace("tag=", "");
                                }
                                manualFingerprints.put(cls.getName() + "." + method.getName(), tag);
                            }
                        }
                    }
                    classFingerprints.put(cls.getName() + "." + method.getName(), classHash);
                }
            }
        } catch (Exception exc) {
            project.getLogger().error("Class not found in the classpath for execution", exc);
        }
    }

    public static URL[] getUrlsArray(Project project) {
        SourceSet sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
                .getAt("main");
        FileCollection test = sourceSets.getRuntimeClasspath();
        List<URL> urls = new ArrayList<>();
        for (File name : test) {
            try {
                URL url = name.toURI().toURL();
                urls.add(url);
            } catch (MalformedURLException ex) {
                project.getLogger().error("Class not found in the classpath for execution", ex);
            }
        }
        try {
            File testSourceRoot = new File(project.getBuildDir() + PluginConstants.TEST_SOURCE_ROOT);
            URL url = testSourceRoot.toURI().toURL();
            urls.add(url);
        } catch (MalformedURLException ex) {
            project.getLogger().error("Class not found in the classpath for execution {} - {}",
                    project.getBuildDir() + PluginConstants.TEST_SOURCE_ROOT, ex);
        }
        project.getLogger()
                .lifecycle("------------------------------------------------------------------------------------");
        return urls.toArray(new URL[0]);
    }

    public static byte[] concatArrays(byte[]... bytes) {
        String collect = Arrays.stream(bytes).map(String::new).collect(Collectors.joining());
        return collect.getBytes();
    }

    public static String hashByteArray(byte[] classBytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.reset();
        byte[] digested = md.digest(classBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digested) {
            sb.append(Integer.toHexString(0xff & b));
        }
        return sb.toString();
    }

    public static Map<String, Object> customUserDefinedProperties(String customPropertiesStr) {
        Map<String, Object> customUserProperties = new HashMap<>();
        if (customPropertiesStr != null && !customPropertiesStr.isEmpty()) {
            String[] pairs = customPropertiesStr.split(";");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    customUserProperties.put(kv[0], kv[1]);
                }
            }
        }
        return customUserProperties;
    }

    public static byte[] getObjectBytes(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutputStream out;
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[] {};
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

    public static void UpdateFieldViaReflection(Object target, String fieldName, Class<?> classObject, Object value) {
        try {
            Field listField = classObject.getDeclaredField(fieldName);
            updateFileAccess(listField);
            listField.set(target, value);
        } catch (Exception e) {
            throw new GradleException("Error : unable to set '" + fieldName + "' on " + classObject.getSimpleName(), e);
        }
    }

    public static void updateFileAccess(Field listField) throws NoSuchFieldException, IllegalAccessException {
        listField.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(listField, listField.getModifiers() & ~Modifier.FINAL);
    }

    public static void appendMetadataFromClass(Class<?> aClass, BenchmarkReport benchmarkReport, Project project,
            URLClassLoader cl) {
        try {
            if (cl.findResource(PluginConstants.METADATA_LIST) != null) {
                Class<?> cyBenchMetadataList = cl.loadClass(PluginConstants.METADATA_LIST);
                Class<?> benchmarkMetaData = cl.loadClass(PluginConstants.BENCHMARK_METADATA);
                Annotation[] annotation = aClass.getDeclaredAnnotations();
                for (Annotation ann : annotation) {
                    if (cyBenchMetadataList.equals(ann.annotationType())) {
                        parseCyBenchArrayMetadata(ann.toString(), project, benchmarkReport);
                    }
                    if (benchmarkMetaData.equals(ann.annotationType())) {
                        parseCyBenchMetadata(ann.toString().split(PluginConstants.BENCHMARK_METADATA_NAME, -1), project,
                                benchmarkReport);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void appendMetadataFromMethod(Optional<java.lang.reflect.Method> benchmarkMethod,
            BenchmarkReport benchmarkReport, Project project, URLClassLoader cl) {
        try {
            if (cl.findResource(PluginConstants.METADATA_LIST) != null) {
                Class<?> cyBenchMetadataList = cl.loadClass(PluginConstants.METADATA_LIST);
                Class<?> benchmarkMetaData = cl.loadClass(PluginConstants.BENCHMARK_METADATA);
                if (benchmarkMethod.isPresent()) {
                    Annotation[] annotation = benchmarkMethod.get().getDeclaredAnnotations();
                    for (Annotation ann : annotation) {
                        if (cyBenchMetadataList.equals(ann.annotationType())) {
                            parseCyBenchArrayMetadata(ann.toString(), project, benchmarkReport);
                        }
                        if (benchmarkMetaData.equals(ann.annotationType())) {
                            parseCyBenchMetadata(ann.toString().split(PluginConstants.BENCHMARK_METADATA_NAME, -1),
                                    project, benchmarkReport);
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void parseCyBenchArrayMetadata(String annotation, Project project, BenchmarkReport benchmarkReport) {
        String result = StringUtils.substringBetween(annotation, "[", "]");
        String[] metadataProps = result.split(PluginConstants.BENCHMARK_METADATA_NAME, -1);
        parseCyBenchMetadata(metadataProps, project, benchmarkReport);
    }

    private static void parseCyBenchMetadata(String[] metadataProps, Project project, BenchmarkReport benchmarkReport) {
        for (String prop : metadataProps) {
            String key = StringUtils.substringBetween(prop, "key=", ",");
            String value = StringUtils.substringBetween(prop, "value=", ")");
            if (key != null && value != null) {
                checkSetOldMetadataProps(key, value, benchmarkReport);
                benchmarkReport.addMetadata(key, value);
            }
        }
    }

    private static void checkSetOldMetadataProps(String key, String value, BenchmarkReport benchmarkReport) {
        if (key.equals("api")) {
            benchmarkReport.setCategory(value);
        }
        if (key.equals("context")) {
            benchmarkReport.setContext(value);
        }
        if (key.equals("version")) {
            benchmarkReport.setVersion(value);
        }
    }

    public static void getReportUploadStatus(BenchmarkOverviewReport report) {
        String reportUploadStatus = getProperty(Constants.REPORT_UPLOAD_STATUS);
        if (Constants.REPORT_PUBLIC.equals(reportUploadStatus)) {
            report.setUploadStatus(reportUploadStatus);
        } else if (Constants.REPORT_PRIVATE.equals(reportUploadStatus)) {
            report.setUploadStatus(reportUploadStatus);
        } else {
            report.setUploadStatus(Constants.REPORT_PUBLIC);
        }
    }

    public static String getProperty(String key) {
        return System.getProperty(key, cfg.getProperty(key));
    }

}
