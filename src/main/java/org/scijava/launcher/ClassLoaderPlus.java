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

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ClassLoader} whose classpath can be augmented after instantiation.
 * 
 * @author Johannes Schindelin
 */
class ClassLoaderPlus extends URLClassLoader {

	// A frozen ClassLoaderPlus will add only to the urls array
	protected static Set<ClassLoader> frozen = new HashSet<>();
	protected static Map<URLClassLoader, List<URL>> urlsMap = new HashMap<>();
	protected static Method addURL;

	public static URLClassLoader get(final URLClassLoader classLoader, final File... files) {
		try {
			final URL[] urls = new URL[files.length];
			for (int i = 0; i < urls.length; i++) {
				urls[i] = files[i].toURI().toURL();
			}
			return get(classLoader, urls);
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Uh oh: " + e.getMessage());
		}
	}

	public static URLClassLoader get(URLClassLoader classLoader, final URL... urls) {
		if (classLoader == null) {
			final ClassLoader currentClassLoader = ClassLoaderPlus.class.getClassLoader();
			if (currentClassLoader instanceof URLClassLoader) {
				classLoader = (URLClassLoader)currentClassLoader;
			} else {
				final ClassLoader contextClassLoader =
					Thread.currentThread().getContextClassLoader();
				if (contextClassLoader instanceof URLClassLoader) {
					classLoader = (URLClassLoader)contextClassLoader;
				}
			}
		}
		if (classLoader == null) return new ClassLoaderPlus(urls);
		for (final URL url : urls) {
			add(classLoader, url);
		}
		return classLoader;
	}

	public static URLClassLoader getRecursively(URLClassLoader classLoader, final boolean onlyJars,
		final File directory)
	{
		try {
			if (!onlyJars)
				classLoader = get(classLoader, directory);
			final File[] list = directory.listFiles();
			if (list != null) {
				Arrays.sort(list);
				for (final File file : list) {
					if (file.isDirectory()) classLoader = getRecursively(classLoader, onlyJars, file);
					else if (file.getName().endsWith(".jar")) classLoader = get(classLoader, file);
				}
			}
			return classLoader;
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Uh oh: " + e.getMessage());
		}
	}

	public ClassLoaderPlus(final URL... urls) {
		super(urls, Thread.currentThread().getContextClassLoader());
		Thread.currentThread().setContextClassLoader(this);
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append(getClass().getName()).append("(");
		for (final URL url : getURLs()) {
			builder.append(" ").append(url.toString());
		}
		builder.append(" )");
		return builder.toString();
	}

	public static void add(final URLClassLoader classLoader, final URL url) {
		List<URL> urls = urlsMap.computeIfAbsent(classLoader, k -> new ArrayList<>());
		urls.add(url);
		if (!frozen.contains(classLoader)) {
			if (classLoader instanceof ClassLoaderPlus) {
				((ClassLoaderPlus) classLoader).addURL(url);
			}
			else try {
				if (addURL == null) {
					addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
					addURL.setAccessible(true);
				}
				addURL.invoke(classLoader, url);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}
	}

	public static void freeze(final ClassLoader classLoader) {
		frozen.add(classLoader);
	}

	public static String getClassPath(final ClassLoader classLoader) {
		List<URL> urls = urlsMap.get(classLoader);
		if (urls == null) return "";

		final StringBuilder builder = new StringBuilder();
		String sep = "";
		for (final URL url : urls)
			if (url.getProtocol().equals("file")) {
				builder.append(sep).append(url.getPath());
				sep = File.pathSeparator;
			}
		return builder.toString();
	}
}
