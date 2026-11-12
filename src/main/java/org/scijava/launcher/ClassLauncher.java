/*-
 * #%L
 * Launcher for SciJava applications.
 * %%
 * Copyright (C) 2007 - 2026 SciJava developers.
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
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * This class acts as a central entry point into Java applications.
 * <p>
 * Benefits:
 * </p>
 * <ol>
 * <li>Verify that the version of Java being used is appropriate
 * (e.g. new enough) for the application before attempting to launch it.</li>
 * <li>Display a splash window while the application is starting up.</li>
 * <li>Call all Java main classes via this class, to be able to invoke
 * the above-mentioned pre-application-startup routines without
 * application code needing to depend on the app-launcher directly.</li>
 * </ol>
 *
 * @author Johannes Schindelin
 * @author Curtis Rueden
 */
public class ClassLauncher {

	public static String appName() { return appName(null); }

	public static String appName(String fallback) {
		String appName = System.getProperty("scijava.app.name");
		return appName == null ? fallback : appName;
	}

	/**
	 * Gets the folder indicated by the {@code scijava.app.directory} property. If
	 * the property is unset or empty, or points to a nonexistent directory, this
	 * method returns {@code null}.
	 */
	public static Path appDir() {
		String appDirValue = System.getProperty("scijava.app.directory");
		if (appDirValue != null) {
			Path appDirPath = Paths.get(appDirValue).normalize().toAbsolutePath();
			if (appDirPath.toFile().exists()) return appDirPath;
		}
		return null;
	}

	private static final String WARMUP_THREADS_PROPERTY =
		"scijava.app.warmup-threads";
	private static final int DEFAULT_WARMUP_THREADS = 64;
	private static final Set<File> WARMED_JARS = ConcurrentHashMap.newKeySet();

	/**
	 * Opens and closes unwarmed {@code .jar} files on the classpath in parallel
	 * across multiple threads so real-time antivirus scanners (e.g. Microsoft
	 * Defender) scan them concurrently before the JVM's {@code URLClassPath}
	 * walks the classpath sequentially.
	 */
	public static void warmClassPath() {
		final int maxThreads = Integer.getInteger(
			WARMUP_THREADS_PROPERTY, DEFAULT_WARMUP_THREADS);
		if (maxThreads <= 1) return;

		final List<File> jarsToWarm = new ArrayList<>();
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		for (ClassLoader cl = loader; cl != null; cl = cl.getParent()) {
			if (cl instanceof URLClassLoader) {
				for (final URL url : ((URLClassLoader) cl).getURLs()) {
					if ("file".equals(url.getProtocol())) {
						try {
							addJarIfUnwarmed(new File(url.toURI()), jarsToWarm);
						}
						catch (final URISyntaxException | IllegalArgumentException exc) {
							addJarIfUnwarmed(new File(url.getPath()), jarsToWarm);
						}
					}
				}
			}
		}

		final String classPath = System.getProperty("java.class.path");
		if (classPath != null && !classPath.isEmpty()) {
			for (final String element : classPath.split(
				Pattern.quote(File.pathSeparator)))
			{
				if (!element.isEmpty()) {
					addJarIfUnwarmed(new File(element), jarsToWarm);
				}
			}
		}

		final int count = jarsToWarm.size();
		if (count <= 1) return;

		final int numThreads = Math.min(count, maxThreads);
		final AtomicInteger cursor = new AtomicInteger(0);
		final Thread[] threads = new Thread[numThreads];
		for (int t = 0; t < numThreads; t++) {
			final Thread thread = new Thread(() -> {
				int idx;
				while ((idx = cursor.getAndIncrement()) < count) {
					try (FileInputStream in = new FileInputStream(jarsToWarm.get(idx))) {
						// Opening and closing the stream triggers the OS/AV scan.
					}
					catch (final IOException exc) {
						// Ignore missing or unreadable classpath entries.
					}
				}
			}, "SciJava-Launcher-Warmer-" + t);
			thread.setDaemon(true);
			thread.start();
			threads[t] = thread;
		}

		for (final Thread thread : threads) {
			try {
				thread.join();
			}
			catch (final InterruptedException exc) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private static void addJarIfUnwarmed(final File file,
		final List<File> jarsToWarm)
	{
		if (file != null && file.getName().endsWith(".jar") &&
			WARMED_JARS.add(file))
		{
			jarsToWarm.add(file);
		}
	}

	public static void main(final String... args) {
		tryToRun(ClassLauncher::warmClassPath);
		if (Boolean.getBoolean("scijava.app.unlock-modules")) {
			ReflectionUnlocker.unlockAll();
		}
		if (Boolean.getBoolean("scijava.app.single-instance")) {
			if (SingleInstance.tryHandoff(args)) System.exit(0);
		}
		tryToRun(Splash::show);
		tryToRun(Java::check);
		String appName = appName();
		appName = appName == null ? "" : " " + appName;
		Splash.update("Launching" + appName + "...");
		run(args);
	}

	private interface Runnable<E extends Throwable> { void run() throws E; }
	private static void tryToRun(Runnable<?> r) {
		try { r.run(); }
		catch (Throwable t) { Log.error(t); }
	}

	private static void run(String[] args) {
		URLClassLoader classLoader = null;
		int i = 0;
		for (; i < args.length && args[i].charAt(0) == '-'; i++) {
			final String option = args[i];
			switch (option) {
				// Note: No options for now, but might add some in the future.
				default:
					error("Unknown option: " + option + "!");
					System.exit(1);
			}
		}

		if (i >= args.length) {
			error("Missing argument: main class");
			System.exit(1);
		}

		String mainClass = args[i];
		args = slice(args, i + 1);

		debug("Launching main class " + mainClass + " with parameters " + Arrays.toString(args));

		try {
			launch(classLoader, mainClass, args);
		}
		catch (final Throwable t) {
			Log.error(t);
			System.exit(1);
		}
	}

	private static String[] slice(final String[] array, final int from) {
		return slice(array, from, array.length);
	}

	private static String[] slice(final String[] array, final int from,
		final int to)
	{
		final String[] result = new String[to - from];
		if (result.length > 0) System.arraycopy(array, from, result, 0, result.length);
		return result;
	}

	private static void debug(String message) {
		Log.debug("[ClassLauncher] " + message);
	}

	private static void error(String message) {
		Log.error("[ClassLauncher] " + message);
	}

	static void launch(ClassLoader classLoader,
		final String className, final String... args)
	{
		Class<?> main = null;
		debug("Class loader = " + classLoader);
		try {
			main = ClassLoaders.loadClass(classLoader, className);
		}
		catch (final ClassNotFoundException e) {
			// Main class is not available from any of the known class loaders.
			Log.error(e);
			System.exit(1);
		}
		catch (UnsupportedClassVersionError e) {
			// Main class is too new for the running JVM. Try to deal with it.
			Log.debug(e);
			Java.informAndMaybeUpgrade(e);
			System.exit(1);
		}
		Method mainMethod = null;
		try {
			mainMethod = main.getMethod("main", args.getClass());
		}
		catch (final NoSuchMethodException e) {
			Log.debug(e);
			error("Class '" + className + "' does not have a main method.");
			System.exit(1);
		}
		Integer result = 1;
		try {
			result = (Integer) mainMethod.invoke(null, new Object[] { args });
		}
		catch (final IllegalAccessException e) {
			Log.debug(e);
			error("The main method of class '" + className + "' is not public.");
		}
		catch (final InvocationTargetException e) {
			error("Error while executing the main method of class '" + className + "':");
			Log.error(e.getTargetException());
		}
		if (result != null) System.exit(result);
	}
}
