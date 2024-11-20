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

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Window;
import java.net.URL;

/**
 * Application splash window.
 *
 * @author Curtis Rueden
 * @author Stefan Helfrich
 */
public class Splash {

	private static final int PROGRESS_MAX = 10000;

	private static Object splashWindow;
	private static Object progressBar;

	/**
	 * Displays a splash window. If the {@code scijava.app.splash-image} property
	 * references a valid image, it will be used as a logo.
	 */
	public static void show() {
		show(true);
	}

	/**
	 * Displays a splash window. If the {@code scijava.app.splash-image} property
	 * references a path to a valid image, it will be loaded and used as the splash logo.
	 *
	 * @param autoclose If true, creates a dedicated daemon thread to close the splash
	 *                   screen automatically as soon as any other windows become visible.
	 */
	public static void show(boolean autoclose) {
		show(null, autoclose);
	}

	/**
	 * Displays a splash window with the given logo image.
	 *
	 * @param logoPath Resource path to the logo image to display.
	 */
	public static void show(String logoPath) {
		show(logoPath, true);
	}

	/**
	 * Displays a splash window with the given logo image.
	 *
	 * @param logoPath Resource path to the logo image to display.
	 * @param autoclose If true, creates a dedicated daemon thread to close the splash
	 *                   screen automatically as soon as any other windows become visible.
	 */
	public static void show(String logoPath, boolean autoclose) {
		if (Boolean.getBoolean("java.awt.headless")) return;
		LookAndFeel.init();

		if (logoPath == null) logoPath = System.getProperty("scijava.app.splash-image");

		final JWindow window = new JWindow();
		splashWindow = window; // Save a non-AWT reference to the window.
		final JLabel logoImage;
		if (logoPath == null) logoImage = null;
		else {
			final URL logoURL = ClassLoaders.loadResource(Splash.class, logoPath);
			final ImageIcon imageIcon = new ImageIcon(logoURL);
			logoImage = new JLabel(imageIcon);
		}
		final JProgressBar bar = new JProgressBar();
		bar.setMaximum(PROGRESS_MAX);
		progressBar = bar; // Save a non-AWT reference to the progress bar.
		bar.setStringPainted(true);
		String appName = System.getProperty("scijava.app.name");
		if (appName == null) appName = "application";
		bar.setString("Starting " + appName + "...");

		// lay out components
		final JPanel pane = new JPanel();
		pane.setOpaque(false);
		pane.setLayout(new BorderLayout());
		if (logoImage != null) pane.add(logoImage, BorderLayout.CENTER);
		pane.add(bar, BorderLayout.SOUTH);
		window.setContentPane(pane);
		window.pack();

		window.setAlwaysOnTop(true);
		window.setLocationRelativeTo(null);
		window.setBackground(new Color(0, 0, 0, 0));

		window.setVisible(true);

		// Kill the splash window when any other window shows up.
		if (autoclose) startSplashAutocloseThread();
	}

	/**
	 * Updates the splash window's message.
	 *
	 * @param message The new message to display.
	 */
	public static void update(String message) {
		update(message, Double.NaN);
	}

	/**
	 * Updates the splash window's progress bar.
	 *
	 * @param progress The progress value, in the range [0.0, 1.0].
	 */
	public static void update(double progress) {
		update(null, progress);
	}

	/**
	 * Updates the splash window's message and/or progress bar.
	 *
	 * @param message The new message to display.
	 * @param progress The progress value, in the range [0.0, 1.0].
	 */
	public static void update(final String message, final double progress) {
		Runnable updater = () -> {
			JProgressBar jpBar = (JProgressBar) progressBar;
			if (jpBar == null) return;
			if (message != null) jpBar.setString(message);
			if (!Double.isNaN(progress)) {
				jpBar.setValue((int) (progress * PROGRESS_MAX));
			}
		};
		if (EventQueue.isDispatchThread()) updater.run();
		else EventQueue.invokeLater(updater);
	}

	/**
	 * Removes the splash window from view and disposes of it.
	 * Does nothing if no splash window is currently active.
	 */
	public static void hide() {
		if (splashWindow == null) return;
		((Window) splashWindow).dispose();
		splashWindow = null;
		progressBar = null;
	}

	private static void startSplashAutocloseThread() {
		Thread thread = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(100);
				}
				catch (final InterruptedException exc) {}
				if (splashWindow == null) return;
				final Window[] windows = Window.getWindows();
				for (final Window win : windows) {
					// Terminate the splash window as soon as another window is visible.
					if (win.isVisible() && win != splashWindow) {
						win.requestFocusInWindow();
						hide();
						return;
					}
				}
			}
		}, "Splash-Monitor");
		// Don't let this thread prevent shutdown of the JVM.
		thread.setDaemon(true);
		thread.start();
	}
}
