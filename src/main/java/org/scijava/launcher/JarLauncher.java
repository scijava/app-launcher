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
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A convenience class to launch main classes defined in {@code .jar} files'
 * manifests.
 * <p>
 * The idea is to simulate what {@code java -jar ...} does, but through the
 * app launcher.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public class JarLauncher {

	public static void main(final String[] args) {
		if (args.length < 1) {
			System.err.println("Missing argument");
			System.exit(1);
		}
		final String[] shifted = new String[args.length - 1];
		System.arraycopy(args, 1, shifted, 0, shifted.length);
		launchJar(args[0], shifted);
	}

	/**
	 * Helper to launch .jar files (by inspecting their Main-Class attribute).
	 */
	private static void launchJar(final String jarPath, final String[] arguments) {
		JarFile jar = null;
		try {
			jar = new JarFile(jarPath);
		}
		catch (final IOException e) {
			System.err.println("Could not read '" + jarPath + "'.");
			System.exit(1);
			return; // NB: Avoids warnings below.
		}
		Manifest manifest = null;
		try {
			manifest = jar.getManifest();
		}
		catch (final IOException e) {
			// no action needed
		}
		if (manifest == null) {
			System.err.println("No manifest found in '" + jarPath + "'.");
			System.exit(1);
			return; // NB: Avoids warnings below.
		}
		final Attributes attributes = manifest.getMainAttributes();
		final String className =
			attributes == null ? null : attributes.getValue("Main-Class");
		if (className == null) {
			System.err.println("No main class attribute found in '" + jarPath + "'.");
			System.exit(1);
		}
		final URLClassLoader loader = ClassLoaderPlus.get(null, new File(jarPath));
		ClassLauncher.launch(loader, className, arguments);
	}
}
