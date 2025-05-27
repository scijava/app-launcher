/*-
 * #%L
 * Launcher for SciJava applications.
 * %%
 * Copyright (C) 2007 - 2025 SciJava developers.
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

import java.lang.reflect.Method;

/**
 * A utility class to defeat JPMS encapsulation.
 * <p>
 * In Java 17 and later, reflection on modularized Java code is disallowed.
 * Normally, the only way to enable it is by passing {@code --add-opens}
 * arguments at launch, each of which unlocks a single package. This class
 * calls shenanigans on that requirement and unlocks everything at runtime.
 * The only argument needed at launch is
 * {@code --add-opens=java.base/java.lang=ALL-UNNAMED}, and maybe a
 * {@code --module-path} along with {@code --add-modules=ALL-MODULE-PATH}
 * so that they are in the set of modules unlocked by {@link #unlockAll()}.
 * </p>
 */
public final class ReflectionUnlocker {
	private static Method addOpens;
	private static Method addExports;
	private static Method getPackages;

	static {
		try {
			ClassLoader cl = Object.class.getClassLoader(); // java.base/java.lang
			Class<?> moduleClass = ClassLoaders.loadClass(cl, "java.lang.Module");
			getPackages = moduleClass.getMethod("getPackages");
			addOpens = moduleClass.getDeclaredMethod("implAddOpensToAllUnnamed", String.class);
			addOpens.setAccessible(true);
			addExports = moduleClass.getDeclaredMethod("implAddExportsToAllUnnamed", String.class);
			addExports.setAccessible(true);
		}
		catch (Exception e) {
			Log.debug("Failed to initialize ReflectionUnlocker");
			Log.debug(e);
		}
	}

	private ReflectionUnlocker() { }

	public static void unlockAll() {
		if (addOpens == null) return; // Assume not Java 9+.

		// ModuleLayer.boot().modules().forEach(ReflectionUnlocker::unlockModule);
		try {
			ClassLoader cl = Object.class.getClassLoader(); // java.base/java.lang
			Class<?> moduleLayerClass = ClassLoaders.loadClass(cl, "java.lang.ModuleLayer");
			Object moduleLayerBoot = moduleLayerClass.getMethod("boot").invoke(null);
			Method bootModules = moduleLayerBoot.getClass().getMethod("modules");
			Iterable<?> modules = (Iterable<?>) bootModules.invoke(moduleLayerBoot);
			modules.forEach(ReflectionUnlocker::unlockModule);
		}
		catch (Exception e) {
			Log.error("Failed to discover modules");
			Log.debug(e);
		}
	}

	private static void unlockModule(Object m) {
		try {
			for (String pkg : (Iterable<String>) getPackages.invoke(m)) {
				try {
					addOpens.invoke(m, pkg);
					addExports.invoke(m, pkg);
				}
				catch (Exception e) {
					Log.debug("Failed to unlock package: " + pkg);
					Log.debug(e);
				}
			}
		}
		catch (Exception e) {
			Log.error("Failed to unlock module: " + m);
			Log.debug(e);
		}
	}
}
