/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.generated;

import org.apache.flink.api.common.InvalidProgramException;
import org.apache.flink.api.java.tuple.Tuple2;

import org.apache.flink.shaded.guava18.com.google.common.cache.Cache;
import org.apache.flink.shaded.guava18.com.google.common.cache.CacheBuilder;

import org.codehaus.janino.SimpleCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Utilities to compile a generated code to a Class.
 */
public final class CompileUtils {

	// used for logging the generated codes to a same place
	private static final Logger CODE_LOG = LoggerFactory.getLogger(CompileUtils.class);

	/**
	 * Cache of compile, Janino generates a new Class Loader and a new Class file every compile
	 * (guaranteeing that the class name will not be repeated). This leads to multiple tasks of
	 * the same process that generate a large number of duplicate class, resulting in a large
	 * number of Meta zone GC (class unloading), resulting in performance bottlenecks. So we add
	 * a cache to avoid this problem.
	 */
	protected static final Cache<Tuple2<ClassLoader, String>, Class<?>> COMPILED_CACHE = CacheBuilder
		.newBuilder()
		.maximumSize(100)   // estimated cache size
		.build();

	/**
	 * Compiles a generated code to a Class.
	 * @param cl the ClassLoader used to load the class
	 * @param name  the class name
	 * @param code  the generated code
	 * @param <T>   the class type
	 * @return  the compiled class
	 */
	public static <T> Class<T> compile(ClassLoader cl, String name, String code) {
		Tuple2<ClassLoader, String> cacheKey = Tuple2.of(cl, name);
		Class<?> clazz = COMPILED_CACHE.getIfPresent(cacheKey);
		if (clazz == null) {
			clazz = doCompile(cl, name, code);
			COMPILED_CACHE.put(cacheKey, clazz);
		}
		//noinspection unchecked
		return (Class<T>) clazz;
	}

	private static <T> Class<T> doCompile(ClassLoader cl, String name, String code) {
		checkNotNull(cl, "Classloader must not be null.");
		CODE_LOG.debug("Compiling: %s \n\n Code:\n%s", name, code);
		SimpleCompiler compiler = new SimpleCompiler();
		compiler.setParentClassLoader(cl);
		try {
			compiler.cook(code);
		} catch (Throwable t) {
			throw new InvalidProgramException(
				"Table program cannot be compiled. This is a bug. Please file an issue.", t);
		}
		try {
			//noinspection unchecked
			return (Class<T>) compiler.getClassLoader().loadClass(name);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Can not load class " + name, e);
		}
	}
}
