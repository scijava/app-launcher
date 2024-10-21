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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * This class acts as a central entry point into Java applications.
 * <p>
 * Benefits:
 * </p>
 * <ol>
 * <li>Call all Java main classes via this class, to be able to generate
 * appropriate class paths using the platform-independent convenience provided
 * by Java's class library.</li>
 * <li>Verify that the version of Java being used is appropriate
 * (e.g. new enough) for the application before attempting to launch it.</li>
 * <li>Display a splash window while the application is starting up.</li>
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

	public static void main(final String... args) {
		tryToRun(Splash::show);
		tryToRun(Java::check);
		run(args);
	}

	private interface Runnable<E extends Throwable> { void run() throws E; }
	private static void tryToRun(Runnable<?> r) {
		try { r.run(); }
		catch (Throwable t) { Log.error(t); }
	}

	protected static void run(String[] args) {
		boolean passClasspath = false;
		URLClassLoader classLoader = null;
		int i = 0;
		for (; i < args.length && args[i].charAt(0) == '-'; i++) {
			final String option = args[i];
			switch (option) {
				case "-cp":
				case "-classpath":
					classLoader = ClassLoaderPlus.get(classLoader, new File(args[++i]));
					break;
				case "-jarpath":
					final String jarPaths = args[++i];
					for (final String jarPath : jarPaths.split(File.pathSeparator)) {
						final File jarDir = new File(jarPath);
						if (!jarDir.exists()) continue;
						ClassLoaderPlus.getRecursively(classLoader, true, jarDir);
					}
					break;
				case "-pass-classpath":
					passClasspath = true;
					break;
				case "-freeze-classloader":
					ClassLoaderPlus.freeze(classLoader);
					break;
				default:
					Log.error("Unknown option: " + option + "!");
					System.exit(1);
			}
		}

		if (i >= args.length) {
			Log.error("Missing argument: main class");
			System.exit(1);
		}

		String mainClass = args[i];
		args = slice(args, i + 1);

		if (passClasspath && classLoader != null) {
			args = prepend(args, "-classpath", ClassLoaderPlus.getClassPath(classLoader));
		}

		Log.debug("Launching main class " + mainClass + " with parameters " + Arrays.toString(args));

		try {
			launch(classLoader, mainClass, args);
		}
		catch (final Throwable t) {
			Log.error(t);
			System.exit(1);
		}
	}

	protected static String[] slice(final String[] array, final int from) {
		return slice(array, from, array.length);
	}

	protected static String[] slice(final String[] array, final int from,
		final int to)
	{
		final String[] result = new String[to - from];
		if (result.length > 0) System.arraycopy(array, from, result, 0, result.length);
		return result;
	}

	protected static String[] prepend(final String[] array, final String... before) {
		if (before.length == 0) return array;
		final String[] result = new String[before.length + array.length];
		System.arraycopy(before, 0, result, 0, before.length);
		System.arraycopy(array, 0, result, before.length, array.length);
		return result;
	}

	protected static void launch(ClassLoader classLoader,
		final String className, final String... args)
	{
		Class<?> main = null;
		Log.debug("Class loader = " + classLoader);
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
			Log.error("Class '" + className + "' does not have a main method.");
			System.exit(1);
		}
		Integer result = 1;
		try {
			result = (Integer) mainMethod.invoke(null, new Object[] { args });
		}
		catch (final IllegalAccessException e) {
			Log.debug(e);
			Log.error("The main method of class '" + className + "' is not public.");
		}
		catch (final InvocationTargetException e) {
			Log.error("Error while executing the main method of class '" + className + "':");
			Log.error(e.getTargetException());
		}
		if (result != null) System.exit(result);
	}
}
