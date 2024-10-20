/*-
 * #%L
 * Launcher for SciJava applications.
 * %%
 * Copyright (C) 2007 - 2024 SciJava developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.launcher;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Internal utility class for more robust class and resource loading.
 * The preferred class loader plus fallback class construction is clunky
 * and not very general. But it's tailored to what the app-launcher needs.
 * If it ever becomes more elegant, we can perhaps make it public.
 *
 * @author Curtis Rueden
 */
final class ClassLoaders {

	private ClassLoaders() { }

	/**
	 * Stream of class loaders to use when attempting class and resource loading
	 * operations, with no fallback {@link Class}.
	 * @see #loaders(ClassLoader, Class)
	 */
	static Stream<ClassLoader> loaders(ClassLoader preferred) {
		return loaders(preferred, null);
	}

	/**
	 * Stream of class loaders to use when attempting class and resource loading
	 * operations, with no preferred {@link ClassLoader}.
	 * @see #loaders(ClassLoader, Class)
	 */
	static Stream<ClassLoader> loaders(Class<?> fallback) {
		return loaders(null, fallback);
	}

	/**
	 * Stream of class loaders to use when attempting class and resource loading
	 * operations. The returned order is:
	 * <ul>
	 *   <li>{@code preferred} {@link ClassLoader}</li>
	 *   <li>Current thread's context class loader
	 *     ({@link Thread#getContextClassLoader()})</li>
	 *   <li>System class loader
	 *     ({@link ClassLoader#getSystemClassLoader()}</li>
	 *   <li>Class loader of the given {@code fallback} {@link Class}
	 *     ({@link Class#getClassLoader()})</li>
	 * </ul>
	 * @param preferred Class loader to try first.
	 * @param fallback Class whose class loader should be used as a last resort.
	 * @return Stream of non-null class loaders in the above order.
	 */
	static Stream<ClassLoader> loaders(ClassLoader preferred, Class<?> fallback) {
		return Stream.of(
			preferred,
			Thread.currentThread().getContextClassLoader(),
			ClassLoader.getSystemClassLoader(),
			fallback == null ? null : fallback.getClassLoader()
		).filter(Objects::nonNull);
	}

	/**
	 * Tries to load the class with the given fully qualified name using the
	 * {@link ClassLoader}s returned by the {@link #loaders(ClassLoader)} method.
	 *
	 * @param preferred Preferred {@link ClassLoader} to try first.
	 * @param className Fully qualified name of the {@link Class} to load.
	 * @return The loaded {@link Class}.
	 * @throws UnsupportedClassVersionError
	 *   If the class version is not supported by this JVM.
	 * @throws ClassNotFoundException
	 *   If the class is not found by any of the class loaders.
	 */
	static Class<?> loadClass(ClassLoader preferred, String className)
		throws ClassNotFoundException {
		final String noSlashes = className.replace('/', '.');
		List<ClassNotFoundException> failures = new ArrayList<>();

		List<ClassLoader> classLoaders = loaders(preferred).collect(Collectors.toList());
		for (ClassLoader classLoader : classLoaders) {
			try {
				classLoader.loadClass(noSlashes);
			} catch (ClassNotFoundException exc) {
				failures.add(exc);
			}
		}
		ClassNotFoundException classNotFound =
			new ClassNotFoundException("Failed to load class: " + noSlashes);
		for (ClassNotFoundException failure : failures) {
			classNotFound.addSuppressed(failure);
		}
		throw classNotFound;
	}

	static URL loadResource(Class<?> fallback, String path) {
		return loaders(fallback)
			.map(classLoader -> classLoader.getResource(path))
			.filter(Objects::nonNull)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Failed to load resource: " + path));
	}

	/** Extracts the internal class version from the given exception. */
	static String extractClassVersion(UnsupportedClassVersionError e) {
		Pattern p = Pattern.compile(".*class file version ([\\d.]*).*");
		Matcher m = p.matcher(e.getMessage());
		return m.matches() ? m.group(1) : null;
	}
}
