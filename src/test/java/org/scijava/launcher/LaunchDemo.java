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

import javax.swing.JOptionPane;
import java.awt.EventQueue;

public class LaunchDemo {

	public static void main(String... args) {
		// Uncomment the next line to forget about "Never ask again" dialog choices.
		//Preferences.userNodeForPackage(Java.class).clear();

		// The following Look & Feel will be loaded and set immediately, so that
		// the splash window and dialog boxes will match the application's L&F.
		// It only works if that class is available at runtime, of course.
		System.setProperty("scijava.app.look-and-feel", "com.formdev.flatlaf.FlatLightLaf");

		// The following splash image resource will be shown at launch start.
		System.setProperty("scijava.app.splash-image", "scijava.png");

		// If the running JVM does not meet the following version
		// constraints, the user will receive a warning about it.
		System.setProperty("scijava.app.java-version-minimum", "1.8");
		System.setProperty("scijava.app.java-version-recommended", "21");

		// If the running JVM lives under the specified java-root folder, and the version constraints
		// above are not met, then the launcher will ask the user if they want to upgrade Java.
		System.setProperty("scijava.app.java-root", Java.home().getParent().getParent().toString());

		// Launch our amazing program!
		ClassLauncher.main(MyAmazingProgram.class.getName());
	}

	public static class MyAmazingProgram {
		public static void main(String... args) throws InterruptedException {
			for (int i = 0; i <= 200; i++) {
				double progress = (double) i / 200;
				EventQueue.invokeLater(() -> Splash.update("Grokking...", progress));
				Thread.sleep(10);
			}
			Thread.sleep(100);
			JOptionPane.showMessageDialog(null, "Launch complete!");
		}
	}
}
