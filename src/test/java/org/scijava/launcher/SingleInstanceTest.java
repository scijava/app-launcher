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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link SingleInstance}.
 *
 * @author Curtis Rueden
 */
public class SingleInstanceTest {

	private static final String APP_NAME = "app-launcher-test";

	@BeforeEach
	public void setup() {
		System.setProperty("scijava.app.name", APP_NAME);
	}

	@AfterEach
	public void cleanup() {
		System.clearProperty("scijava.app.name");
		lockfilePath().toFile().delete();
	}

	@Test
	public void testNoLockfile() {
		assertFalse(SingleInstance.tryHandoff(new String[]{"arg1"}));
	}

	@Test
	public void testHandoffDeliversArgs() throws Exception {
		List<String[]> received = new CopyOnWriteArrayList<>();
		CountDownLatch latch = new CountDownLatch(1);
		SingleInstance.listen(0, args -> {
			received.add(args);
			latch.countDown();
		});

		String[] sent = {"fiji://hello/dialog?greeting=Two", "--headless"};
		assertTrue(SingleInstance.tryHandoff(sent));
		assertTrue(latch.await(2, TimeUnit.SECONDS), "argReceiver was not called");
		assertArrayEquals(sent, received.get(0));
	}

	@Test
	public void testStaleLockfileIsDeleted() throws Exception {
		// Grab a port, then release it immediately so nothing is listening on it.
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		Path lockFile = lockfilePath();
		Files.write(lockFile, (port + "\nhello\nwazzzzzup\n").getBytes(StandardCharsets.UTF_8));

		assertFalse(SingleInstance.tryHandoff(new String[]{"arg1"}));
		assertFalse(lockFile.toFile().exists());
	}

	@Test
	public void testWrongProcessOnPortIsRejected() throws Exception {
		// An impostor that accepts connections but sends a wrong server secret.
		ServerSocket impostor = new ServerSocket(0);
		int port = impostor.getLocalPort();
		Path lockFile = lockfilePath();
		Files.write(lockFile, (port + "\nhowdy\nexpected\n").getBytes(StandardCharsets.UTF_8));

		Thread t = new Thread(() -> {
			try (Socket s = impostor.accept()) {
				// Drain the client secret, then send a wrong server secret.
				new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream())).readLine();
				new java.io.PrintWriter(s.getOutputStream(), true).println("wrong");
			}
			catch (IOException ignored) {}
			finally { try { impostor.close(); } catch (IOException ignored) {} }
		});
		t.setDaemon(true);
		t.start();

		assertFalse(SingleInstance.tryHandoff(new String[]{"arg1"}));
		assertFalse(lockFile.toFile().exists());
	}

	private static Path lockfilePath() {
		String userName = System.getProperty("user.name", "user");
		String tmpDir = System.getProperty("java.io.tmpdir");
		return Paths.get(tmpDir, "scijava-" + APP_NAME + "-" + userName + ".lock");
	}
}
