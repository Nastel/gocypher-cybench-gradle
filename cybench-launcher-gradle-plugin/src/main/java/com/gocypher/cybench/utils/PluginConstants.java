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

public class PluginConstants {
    public static final String BENCHMARK_LIST_FILE = "/META-INF/BenchmarkList";
    public static final String COMPILER_HINT_FILE = "/META-INF/CompilerHints";
    public static final String MAIN_SOURCE_ROOT = "/classes/java/main";
    public static final String TEST_SOURCE_ROOT = "/classes/java/test";
    public static final String BENCH_SOURCE = "Gradle plugin";
    public static final String DEFAULT_FILE_SAVE_LOCATION = "./reports";

    public static final String BENCHMARK_METADATA_NAME = "@com.gocypher.cybench.core.annotation.BenchmarkMetaData";
    public static final String METADATA_LIST = "com.gocypher.cybench.core.annotation.CyBenchMetadataList";
    public static final String BENCHMARK_METADATA = "com.gocypher.cybench.core.annotation.BenchmarkMetaData";
    public static final String BENCHMARK_TAG = "com.gocypher.cybench.core.annotation.BenchmarkTag";

}
