/*
 * Copyright (C) 2020-2022, K2N.IO.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */

package com.gocypher.cybench.utils;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
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
import com.gocypher.cybench.core.utils.SecurityUtils;
import com.gocypher.cybench.launcher.BenchmarkRunner;
import com.gocypher.cybench.launcher.model.BenchmarkReport;

public final class PluginUtils {
    private PluginUtils() {
    }

    public static String checkReportSaveLocation(String fileName) {
        if (!fileName.endsWith("/")) {
            fileName = fileName + "/";
        }
        return fileName;
    }

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void fingerprintAndHashGeneration(Project project, BenchmarkList benchmarkList,
			Map<String, String> generatedFingerprints, Map<String, String> manualFingerprints,
			Map<String, String> classFingerprints) {
		Set<BenchmarkListEntry> all = benchmarkList.getAll(new JMHUtils.SilentOutputFormat(),
				Collections.<String>emptyList());
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
					String fKey = cls.getName() + "." + method.getName();
					try {
						if (benchmarkMethods.contains(method.getName())) {
							String hash = SecurityUtils.hashByteArray(concatArrays(method.getName().getBytes(),
									method.getSignature().getBytes(), method.getCode().getCode()));
							generatedFingerprints.put(fKey, hash);
						}
					} catch (Exception e) {
						project.getLogger().error("Failed to compute hash for method {} in class {}", method.getName(),
								cls, e);
					}
				}
				String classHash = computeClassHash(cls, project);
				java.lang.reflect.Method[] methods = cls.getMethods();
				for (java.lang.reflect.Method method : methods) {
					String fKey = cls.getName() + "." + method.getName();
					if (method.getAnnotation(benchmarkAnnotationClass) != null) {
						if (cl.loadClass(PluginConstants.BENCHMARK_TAG) != null) {
							Class benchmarkAnnotationTagClass = cl.loadClass(PluginConstants.BENCHMARK_TAG);
							Annotation annotation = method.getAnnotation(benchmarkAnnotationTagClass);
							if (annotation != null) {
								String tag = null;
								if (annotation.toString().contains(PluginConstants.BENCHMARK_TAG)) {
									String result = StringUtils.substringBetween(annotation.toString(), "(", ")");
									tag = result.replace("tag=", "");
								}
								tag = tag.replaceAll("\"", "");
								manualFingerprints.put(fKey, tag);
							} else {
								Method javaMethod = javaClass.getMethod(method);
								if (javaMethod != null) {
									String methodSignature = javaClass.getClassName() + "." + javaMethod.getName()
											+ javaMethod.getGenericSignature();
									String hash = SecurityUtils.computeStringHash(methodSignature);
									project.getLogger().lifecycle("Computed method {} hash {}", methodSignature, hash);
									manualFingerprints.put(fKey, hash);
								}
							}

							classFingerprints.put(fKey, classHash);
						}
					}
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

    public static String computeClassHash(Class<?> clazz, Project project) {
        if (clazz != null) {
            try {
                byte[] classBytes = SecurityUtils.getObjectBytes(clazz);
                String classMD5Hash = SecurityUtils.hashByteArray(classBytes);
                return classMD5Hash;
            } catch (Exception e) {
                project.getLogger().lifecycle("Failed to compute hash for class {}", clazz, e);
            }
        }
        return null;
    }

    public static void updateFieldViaReflection(Object target, String fieldName, Class<?> classObject, Object value) {
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

    public static void appendMetadataFromAnnotated(Optional<? extends AnnotatedElement> annotated,
            BenchmarkReport benchmarkReport, Project project, URLClassLoader cl) {
    	project.getLogger().lifecycle("appending metadata from annotated...");
        try {
            if (cl.loadClass(PluginConstants.METADATA_LIST) != null) {
                Class<?> cyBenchMetadataList = cl.loadClass(PluginConstants.METADATA_LIST);
                Class<?> benchmarkMetaData = cl.loadClass(PluginConstants.BENCHMARK_METADATA);
                if (annotated.isPresent()) {
                    Annotation[] annotation = annotated.get().getDeclaredAnnotations();
                    for (Annotation ann : annotation) {
                        if (cyBenchMetadataList.equals(ann.annotationType())) {
                            parseCyBenchArrayMetadata(ann.toString(), project, benchmarkReport);
                        }
                        if (benchmarkMetaData.equals(ann.annotationType())) {
                            parseCyBenchMetadata(ann.toString().split(PluginConstants.BENCHMARK_METADATA_NAME, -1),
                                    project, benchmarkReport);
                        }
                    }
                } else {
                	project.getLogger().lifecycle("No annotation present.");
                }
            } else {
            	project.getLogger().lifecycle("metadata list was null?");
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void parseCyBenchArrayMetadata(String annotation, Project project, BenchmarkReport benchmarkReport) {
//        String result = StringUtils.substringBetween(annotation, "[", "]");
        String[] metadataProps = annotation.split(PluginConstants.BENCHMARK_METADATA_NAME, -1);
        parseCyBenchMetadata(metadataProps, project, benchmarkReport);
    }

    private static void parseCyBenchMetadata(String[] metadataProps, Project project, BenchmarkReport benchmarkReport) {
        for (String prop : metadataProps) {
            String key = StringUtils.substringBetween(prop, "key=", ",");
            String value = StringUtils.substringBetween(prop, "value=", ")");
            if (key != null && value != null) {
                key = key.replaceAll("\"", ""); //strip quotes from tag
                value = value.replaceAll("\"", "");

                BenchmarkRunner.checkSetOldMetadataProps(key, value, benchmarkReport);
                benchmarkReport.addMetadata(key, value);
            }
        }
    }
}
