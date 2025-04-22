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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for working with Java installations.
 * The primary purpose of this class is the {@link #check()} method,
 * which evaluates whether the running Java version is good enough.
 *
 * @author Curtis Rueden
 */
public class Java {

	/**
	 * Checks that the version of running JVM is new enough for the application,
	 * and if not, offers to download+install a newer version as appropriate.
	 *
	 * @author Curtis Rueden
	 */
	public static void check() throws IOException {
		Splash.update("Checking Java version...");

		if (!isBelowRecommended()) return; // The running Java version is all good! \^_^/
		String appName = ClassLauncher.appName("the application");
		String warnAboutOldJavaVersion =
			"The Java version currently in use, " + currentVersion() +
			", is below the " +
			(isBelowMinimum() ?
				"<strong>required</strong> minimum of " + minimumVersion() :
				"recommended minimum of " + recommendedVersion()) +
			".";
		String questionPrompt = "<br><br>How would you like to proceed?";
		String appMightNotWorkProperly =
			"<br>It is strongly recommended to upgrade it, or " +
				appName + " may not work properly.";
		String appMightCrash =
			"<br>If you continue the launch with this version of Java, " +
			appName + " might crash.";

		if (isManaged()) {
			// Running Java version is managed by us; warn the user, and maybe offer to upgrade.
			Path good = goodInstallation();
			// CTR START HERE: If we cannot upgrade due to missing javaLinks or javaPlatform values,
			// We should fall back to the "unmanaged" warning behavior. How to restructure these logic branches
			// to do that as elegantly as possible?
			if (good == null) {
				// No existing good-enough installation; offer to download and install one.
				String message =
					warnAboutOldJavaVersion +
					(isBelowMinimum() ? appMightNotWorkProperly : "") +
					questionPrompt;
				boolean doUpgrade = askIfAllowed("skipUpgradePrompt",
					message, "Upgrade", "Not now", "Never ask again");
				if (doUpgrade) Java.upgrade();
			}
			else {
				// Point out the good-enough installation that is already present.
				String informAboutExistingGoodVersion =
					"It appears there is a good-enough version of Java already installed at " + good +
					", which is " + (isBelowMinimum() ? "strongly" : "") + "recommended to use instead.";
				String message =
					warnAboutOldJavaVersion + "<br>" +
					informAboutExistingGoodVersion +
					(isBelowMinimum() ? appMightCrash : "") +
					questionPrompt;
				boolean doQuit = askIfAllowed("skipVersionWarning",
					message, "Quit", "Launch anyway", "Launch and never warn again");
				if (doQuit) System.exit(1);
			}
		}
		else {
			// Running Java version is not managed by us; just issue a warning.
			String message =
				warnAboutOldJavaVersion +
				(isBelowMinimum() ? appMightCrash : "") +
				questionPrompt;
			boolean doQuit = askIfAllowed("skipVersionWarning",
				message, "Quit", "Launch anyway", "Launch and never warn again");
			if (doQuit) System.exit(1);
		}
	}

	public static String currentVersion() {
		return sysProp("java.version",
			"Cannot resolve current Java version");
	}

	public static String minimumVersion() {
		return sysProp("scijava.app.java-version-minimum",
			"No minimum Java version specified");
	}

	public static String recommendedVersion() {
		return sysProp("scijava.app.java-version-recommended",
			"No recommended Java version specified");
	}

	/**
	 * Gets the list of "managed" Java installations, beneath the {@link #root()}
	 * directory, or an empty list if no root has been indicated.
	 */
	public static List<Path> installations() throws IOException {
		Path javaRoot = root();
		if (javaRoot == null) return Collections.emptyList();
		// Find all */release files beneath the java root path.
		try (Stream<Path> paths = Files.walk(javaRoot, 2, FileVisitOption.FOLLOW_LINKS)) {
			return paths
				.filter(path -> path.getFileName().toString().equals("release"))
				.filter(path -> path.getNameCount() == javaRoot.getNameCount() + 2)
				.collect(Collectors.toList());
		}
	}

	public static Path goodInstallation() throws IOException {
		String recommended = recommendedVersion();
		for (Path javaHome : installations()) {
			String v = readVersion(javaHome);
			if (v != null && Versions.compare(v, recommended) >= 0) return javaHome;
		}
		return null;
	}

	public static boolean isBelowMinimum() {
		try {
			return Versions.compare(currentVersion(), minimumVersion()) < 0;
		}
		catch (IllegalStateException exc) {
			Log.debug(exc);
			return false; // No minimum version specified.
		}
	}

	public static boolean isBelowRecommended() {
		try {
			return Versions.compare(currentVersion(), recommendedVersion()) < 0;
		}
		catch (IllegalStateException exc) {
			Log.debug(exc);
			return false; // No recommended version specified.
		}
	}

	/**
	 * Gets the folder indicated by the {@code java.home} property, presumably the
	 * Java installation used to launch this application. If the property is unset
	 * or empty, or points to a nonexistent directory, this method returns
	 * {@code null}.
	 */
	public static Path home() {
		String javaHomeValue = System.getProperty("java.home");
		if (javaHomeValue == null) return null;
		Path javaHome = Paths.get(javaHomeValue);
		return javaHome.toFile().exists() ? javaHome : null;
	}

	/**
	 * Gets the folder beneath which "managed" Java installations should be located.
	 * This folder is indicated by the {@code scijava.app.java-root} system property.
	 * If the property is unset or empty, or points to a nonexistent directory,
	 * this method returns {@code null}.
	 */
	public static Path root() { return root(false); }

	/**
	 * Gets the folder beneath which "managed" Java installations should be located.
	 * This folder is indicated by the {@code scijava.app.java-root} system property.
	 * If the property is unset or empty, this method returns {@code null}. If it
	 * points to a nonexistent directory, this method either returns {@code null}
	 * or creates the directory depending on the value of the {@code create} flag.
	 *
	 * @param create If true, and the directory does not exist, it is created.
	 * @return Path to the existing folder, or {@code null} if none.
	 */
	public static Path root(boolean create) {
		String javaDir = System.getProperty("scijava.app.java-root");
		if (javaDir == null || javaDir.isEmpty()) return null;
		Path javaDirPath = Paths.get(javaDir);
		File javaDirFile = javaDirPath.toFile();
		if (create && !javaDirFile.exists()) javaDirFile.mkdirs();
		return javaDirFile.exists() ? javaDirPath : null;
	}

	/**
	 * Gets whether the running JVM is considered "managed" with the application.
	 * A managed JVM is one residing on the filesystem beneath the folder indicated by the
	 * {@code scijava.app.java-root} system property. (If the property is not set, or
	 * points to a nonexistent directory, the running JVM is never considered managed.)
	 */
	public static boolean isManaged() {
		Path javaHome = home();
		Path javaRoot = root();
		return isNested(javaHome, javaRoot);
	}

	/**
	 * Gets whether the running JVM is considered "bundled" with the application.
	 * A bundled JVM is one residing on the filesystem beneath the folder indicated
	 * by the {@code app-dir} system property.
	 */
	public static boolean isBundled() {
		Path javaHome = home();
		Path appDir = ClassLauncher.appDir();
		return isNested(javaHome, appDir);
	}

	public static boolean isHeadless() {
		return Boolean.getBoolean("java.awt.headless");
	}

	public static void upgrade() {
		upgrade(isHeadless());
	}

	public static void upgrade(boolean headless) {
		if (!headless) Splash.show(false);
		try {
			if (headless) {
				String[] message = {""};
				upgrade((s, fraction) -> {
					StringBuilder sb = new StringBuilder();
					sb.append(s == null ? "Downloading Java" : s);
					if (fraction != null) {
						int p = (int) (100 * fraction);
						sb.append(" [").append(p).append("]");
					}
					// Display the message only if it has changed.
					String latest = sb.toString();
					if (!latest.equals(message[0])) {
						System.out.println(message[0] = latest);
					}
				});
			}
			else upgrade(Splash::update);
		}
		catch (IOException e) {
			Log.error(e);
		}
		finally {
			if (!headless) Splash.hide();
		}
	}

	public static void upgrade(BiConsumer<String, Double> subscriber)
		throws IOException
	{
		// Discern the Java root directory, creating it if necessary.
		Path javaRootPath = root(true);
		File javaRootFile = javaRootPath == null ? null : javaRootPath.toFile();
		if (javaRootFile == null || !javaRootFile.isDirectory()) {
			throw new IOException("Invalid Java root directory");
		}

		subscriber.accept("Updating Java...", Double.NaN);


		// If no valid link found, fail.
		final String javaLink = getJavaLink();
		if (javaLink == null) {
			Log.error("No Java download available for platform: " +
					sysProp("scijava.app.java-platform"));
			return;
		}

		// Create a temp file to house the downloaded Java archive.
		String prefix = Java.class.getName() + "-";
		Matcher m = Pattern.compile(".*?((\\.tar)?\\.[^.]*)$").matcher(javaLink);
		String suffix = m.matches() ? m.group(1) : null;
		File tmpArchive = Files.createTempFile(prefix, suffix).toFile();
		tmpArchive.deleteOnExit();

		// Perform the download.
		waitForTask(Downloader.download(new URL(javaLink), tmpArchive,
			d -> subscriber.accept("Downloading Java...", d)));

		// Unpack the downloaded archive.
		String[] dir = {null};
		waitForTask(Archives.unpack(tmpArchive, javaRootFile, s -> {
			// Save a reference to the first directory being unpacked.
			// This is only a heuristic, but it works for most Java archives.
			if (s != null && dir[0] == null && s.endsWith("/")) dir[0] = s;
			// Forward the message on to our upgrade subscriber.
			subscriber.accept("Unpacking " + s, Double.NaN);
		}));

		// Write new installation location into the requested configuration file.
		if (dir[0] != null) {
			Path newJavaPath = javaRootPath.resolve(dir[0]).normalize().toAbsolutePath();
			String configFileValue = System.getProperty("scijava.app.config-file");
			if (configFileValue != null && !configFileValue.isEmpty()) {
				File configFile = new File(configFileValue);
				Path appDirPath = ClassLauncher.appDir();
				if (appDirPath != null) {
					// If possible, use a path relative to the application directory.
					// This improves portability if the application gets moved
					// elsewhere, and/or accessed from multiple operating systems.
					try {
						newJavaPath = appDirPath.relativize(newJavaPath);
					}
					catch (IllegalArgumentException exc) {
						Log.debug(exc);
					}
				}
				Config.update(configFile, "jvm-dir", newJavaPath.toString());
			}
		}

		subscriber.accept("Java update complete", Double.NaN);
	}

	/**
	 * Helper method to fetch the latest Java URLs for each platform and return
	 * the best option for the current platform. Returns {@code null} if no valid
	 * link was found.
	 */
	private static String getJavaLink() throws IOException {
		// Download the mapping of platforms to Java download links.
		String javaLinks = sysProp("scijava.app.java-links");
		List<String> lines = Downloader.downloadText(new URL(javaLinks));

		// Extract the relevant Java download link from the mapping.
		String javaPlatform = sysProp("scijava.app.java-platform");
		return lines.stream()
				.filter(line -> line.startsWith(javaPlatform + "="))
				.map(line -> line.substring(javaPlatform.length() + 1))
				.findFirst().orElse(null);
	}

	/**
	 * @return true if {@code test} and {@code root} are non-null and {@code test}
	 * is a subdirectory of {@code root}
	 */
	private static boolean isNested(Path test, Path root) {
		if (root == null || test == null) return false;
		return test.normalize().startsWith(root);
	}

	private static String sysProp(String key) {
		return sysProp(key, key + " is unset");
	}

	private static String sysProp(String key, String errorMessage) {
		String value = System.getProperty(key);
		if (value == null || value.isEmpty()) {
			throw new IllegalStateException(errorMessage);
		}
		return value;
	}

	private static String readVersion(Path releasePath) {
		try {
			String prefix = "JAVA_VERSION=";
			return Files.readAllLines(releasePath).stream()
				.filter(line -> line.startsWith(prefix))
				.map(line -> line.substring(prefix.length()).replaceAll("\"", ""))
				.findFirst().orElse(null);
		}
		catch (IOException e) {
			Log.debug(e);
			return null;
		}
	}

	/**
	 * Helper method that prompts using the given message if the given preference
	 * is unset. Sets the preference is the "never" option is selected. Returns
	 * false if "no" or "never" are selected, or the preference key was previously
	 * set.
	 */
	private static boolean askIfAllowed(String prefKey,
		String message, String yes, String no, String never)
	{
		Preferences prefs = Preferences.userNodeForPackage(Java.class);
		boolean skipPrompt = prefs.getBoolean(prefKey, false);
		if (skipPrompt) return false; // User previously said to "never ask again".

		Dialogs.Result choice = Dialogs.ask(null,
			"<html>" + message, yes, no, never);

		switch (choice) {
			case YES: return true;
			case NEVER: prefs.putBoolean(prefKey, true); return false;
			case NO: case CANCELED: default: return false;
		}
	}

	static void informAndMaybeUpgrade(UnsupportedClassVersionError e) {
		if (isHeadless()) throw e; // Fail fast when headless.

		StringBuilder message = new StringBuilder("<html>");
		message.append("<p>")
			.append(ClassLauncher.appName("The application"))
			.append(" failed to launch because it requires a newer version of Java.")
			.append("</p>");

		// What is our current version of Java?
		String currentVersion = System.getProperty("java.version");
		message.append("<ul><li>Current Java version: ")
			.append(currentVersion).append("</li>");

		// What Java version would be new enough?
		String classVersion = ClassLoaders.extractClassVersion(e);
		String neededVersion = classVersion == null ? "&lt;unknown&gt;" :
			Versions.classVersionToJavaVersion(classVersion);
		message.append("<li>Needed Java version: ")
			.append(neededVersion).append("</li>");

		message.append("</ul><p>How would you like to proceed?</p>");

		Dialogs.Result choice = Dialogs.ask(null,
			message.toString(), "Upgrade Java", "Just quit", null);
		if (choice == Dialogs.Result.YES) Java.upgrade();
		else System.exit(1);
	}

	private static <T> T waitForTask(Future<T> task) throws IOException {
		try {
			return task.get();
		}
		catch (ExecutionException | InterruptedException e) {
			throw new IOException(e);
		}
	}
}
