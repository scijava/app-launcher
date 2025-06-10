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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility class for working with file archives.
 *
 * @author Curtis Rueden
 */
public final class Archives {

	private static final String NL = System.getProperty("line.separator");

	private Archives() { }

	private static final String TAR_PATTERN = ".*\\.tar(\\.[a-zA-Z0-9]*)?$";

	public static Future<Void> unpack(File file, File destDir, Consumer<String> outputConsumer) {
		if (file.getName().endsWith(".zip")) return unzip(file, destDir, outputConsumer);
		else if (file.getName().matches(TAR_PATTERN)) return untar(file, destDir, outputConsumer);
		else throw new IllegalArgumentException("Cannot unpack unsupported file: " + file);
	}

	public static Future<Void> unzip(File file, File destDir, Consumer<String> outputConsumer) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		return executor.submit(() -> {
			Path destPath = destDir.toPath().normalize();
			try (ZipFile zipFile = new ZipFile(file)) {
				Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
				while (zipEntries.hasMoreElements()) {
					ZipEntry entry = zipEntries.nextElement();
					Path entryPath = destPath.resolve(entry.getName()).normalize();

					// Do a security check to prevent zip slip vulnerability.
					if (!entryPath.startsWith(destPath)) {
						throw new IOException("Entry is outside of the target dir: " + entry.getName());
					}
					if (outputConsumer != null) outputConsumer.accept(entry.getName());

					if (entry.isDirectory()) {
						Files.createDirectories(entryPath);
					}
					else {
						Files.createDirectories(entryPath.getParent());
						Files.copy(zipFile.getInputStream(entry), entryPath, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			finally {
				executor.shutdown();
			}
			return null;
		});
	}

	public static Future<Void> untar(File file, File destDir, Consumer<String> outputConsumer) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		return executor.submit(() -> {
			Process p = new ProcessBuilder(
				"tar", "xvf", file.getAbsolutePath(), "-C", destDir.getAbsolutePath()
			).start();

			StringBuilder errorOutput = new StringBuilder();
			AtomicBoolean shouldStop = new AtomicBoolean(false);

			Thread stdoutThread = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
					readLines(reader, shouldStop, outputConsumer);
				}
				catch (IOException e) {
					appendException(errorOutput, e);
				}
			});

			Thread stderrThread = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
					readLines(reader, shouldStop, line -> errorOutput.append(line).append(NL));
				}
				catch (IOException e) {
					appendException(errorOutput, e);
				}
			});

			stdoutThread.start();
			stderrThread.start();

			try {
				int exitCode = p.waitFor();
				if (exitCode != 0) {
					throw new IOException("tar command failed with exit code: " + exitCode + NL + NL + errorOutput);
				}
			}
			catch (InterruptedException e) {
				shouldStop.set(true);
				p.destroy();
				try {
					if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly();
				}
				catch (InterruptedException ie) {
					appendException(errorOutput, ie);
				}
				stdoutThread.interrupt();
				stderrThread.interrupt();
				try {
					stdoutThread.join(500);
					stderrThread.join(500);
				}
				catch (InterruptedException ie) {
					errorOutput.append("Interrupted while waiting for I/O threads to terminate" + NL);
				}
				Thread.currentThread().interrupt();
				throw new IOException("Untar operation was interrupted" + NL + errorOutput, e);
			}
			finally {
				executor.shutdownNow();
			}
			return null;
		});
	}

	private static void readLines(BufferedReader reader, AtomicBoolean shouldStop, Consumer<String> lineHandler) throws IOException {
		String line;
		while (
			!shouldStop.get() &&
			!Thread.currentThread().isInterrupted() &&
			(line = reader.readLine()) != null
		) {
			if (lineHandler != null) lineHandler.accept(line);
		}
	}

	private static void appendException(StringBuilder sb, Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		synchronized (sb) { sb.append(NL).append(sw).append(NL); }
	}
}
