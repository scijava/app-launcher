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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages single-instance behavior for SciJava applications.
 * <p>
 * The first instance calls {@link #listen} to claim a TCP port and begin
 * accepting forwarded argument lists from later launches. Subsequent launches
 * call {@link #tryHandoff} (via {@link ClassLauncher}), which detects the
 * running instance, forwards its args over the socket, and signals the caller
 * to exit the JVM — before the splash screen ever appears.
 * </p>
 * <p>
 * A lockfile in the system temp directory stores the port and a 128-bit random
 * secret. The file is made owner-readable only (equivalent to chmod 0600), so
 * only the same OS user can read the secret and connect — the same security
 * boundary that would be provided by an RMI stub approach, without its
 * overhead or serialization risks.
 * </p>
 * <p>
 * Wire protocol: the client sends the secret as the first line, followed by
 * one arg per line, then closes the connection. The server rejects connections
 * that present the wrong secret and dispatches the rest immediately to the
 * provided {@link Consumer} without waiting for results, so the client exits
 * in milliseconds regardless of what the server does with the args.
 * </p>
 *
 * @author Curtis Rueden
 */
public class SingleInstance {

	/**
	 * Opens a server socket and begins accepting forwarded argument lists.
	 * <p>
	 * Call this once after application startup. Use {@code port = 0} to let the
	 * OS assign a free port automatically.
	 * </p>
	 *
	 * @param port TCP port to listen on, or 0 for an OS-assigned port.
	 * @param argReceiver called on a daemon thread each time args arrive.
	 */
	public static void listen(int port, Consumer<String[]> argReceiver) {
		try {
			ServerSocket server = new ServerSocket(port);
			int actualPort = server.getLocalPort();

			SecureRandom rng = new SecureRandom();
			byte[] buf = new byte[16];
			rng.nextBytes(buf); String secretGreeting = toHex(buf);
			rng.nextBytes(buf); String secretResponse = toHex(buf);

			Path lockFile = lockfilePath();
			Files.write(lockFile, (actualPort + "\n" + secretGreeting + "\n" + secretResponse + "\n").getBytes(StandardCharsets.UTF_8));
			setOwnerOnly(lockFile.toFile());
			lockFile.toFile().deleteOnExit();

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try { server.close(); } catch (IOException ignored) {}
				lockFile.toFile().delete();
			}, "SingleInstance-Shutdown"));

			Thread listener = new Thread(() -> acceptLoop(server, secretGreeting, secretResponse, argReceiver), "SingleInstance-Listener");
			listener.setDaemon(true);
			listener.start();

			Log.debug("[SingleInstance] Listening on port " + actualPort);
		}
		catch (IOException e) {
			Log.error(e);
		}
	}

	/**
	 * Attempts to forward {@code args} to a running single-instance listener.
	 *
	 * @return {@code true} if args were successfully handed off; the caller
	 *         should then exit the JVM. Returns {@code false} if no listener is
	 *         active, in which case normal startup should continue.
	 */
	static boolean tryHandoff(String[] args) {
		Path lockFile = lockfilePath();
		if (!lockFile.toFile().exists()) return false;
		try {
			List<String> lines = Files.readAllLines(lockFile, StandardCharsets.UTF_8);
			if (lines.size() < 3) return false;
			int port = Integer.parseInt(lines.get(0).trim());
			String secretGreeting = lines.get(1).trim();
			String secretResponse = lines.get(2).trim();
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
				PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
				out.println(secretGreeting);
				if (!secretResponse.equals(in.readLine())) {
					Log.debug("[SingleInstance] Handoff rejected (wrong process on port), removing stale lockfile.");
					lockFile.toFile().delete();
					return false;
				}
				for (String arg : args) out.println(arg);
			}
			Log.debug("[SingleInstance] Args handed off to existing instance.");
			return true;
		}
		catch (Exception e) {
			Log.debug("[SingleInstance] Handoff failed, removing stale lockfile: " + e.getMessage());
			lockFile.toFile().delete();
			return false;
		}
	}

	private static void acceptLoop(ServerSocket server, String secretGreeting, String secretResponse, Consumer<String[]> argReceiver) {
		while (!server.isClosed()) {
			try {
				Socket client = server.accept();
				Thread handler = new Thread(() -> handleConnection(client, secretGreeting, secretResponse, argReceiver), "SingleInstance-Handler");
				handler.setDaemon(true);
				handler.start();
			}
			catch (IOException e) {
				if (!server.isClosed()) Log.debug(e);
			}
		}
	}

	private static void handleConnection(Socket client, String secretGreeting, String secretResponse, Consumer<String[]> argReceiver) {
		try (Socket s = client; BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
			String receivedSecret = in.readLine();
			if (!secretGreeting.equals(receivedSecret)) {
				Log.debug("[SingleInstance] Rejected connection: wrong secret.");
				return;
			}
			PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
			out.println(secretResponse);
			List<String> args = new ArrayList<>();
			String line;
			while ((line = in.readLine()) != null) args.add(line);
			if (!args.isEmpty()) argReceiver.accept(args.toArray(new String[0]));
		}
		catch (IOException e) {
			Log.debug(e);
		}
	}

	private static Path lockfilePath() {
		String appName = ClassLauncher.appName("scijava");
		String userName = System.getProperty("user.name", "user");
		String tmpDir = System.getProperty("java.io.tmpdir");
		return Paths.get(tmpDir, "scijava-" + appName + "-" + userName + ".lock");
	}

	private static void setOwnerOnly(File f) {
		f.setReadable(false, false);
		f.setReadable(true, true);
		f.setWritable(false, false);
		f.setWritable(true, true);
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) sb.append(String.format("%02x", b));
		return sb.toString();
	}
}
