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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Utility class for downloading remote content asynchronously.
 *
 * @author Curtis Rueden
 */
public final class Downloader {

	private Downloader() { }

	/**
	 * Downloads content from the given {@link URL} into the specified {@link File}.
	 * Progress is reported to the given {@link Consumer} if one is provided:
	 * <ul>
	 *   <li>If the size of the content is known, the reported {@code double} values will be
	 *   the fraction of content downloaded thus far ({@code bytesDownloaded / contentLength},
	 *   in the range {@code [0.0, 1.0]}).</li>
	 *   <li>If not, the reported {@code double} values will equal the number of
	 *   downloaded bytes thus far.</li>
	 * </ul>
	 *
	 * @param source The {@link URL} where the content to download resides.
	 * @param dest The {@link File} into which the content should be stored.
	 * @param progressConsumer A {@link Consumer} to receive updates as the download
	 *                          proceeds, or {@code null} if no updates need to be reported.
	 * @return A {@link Future} representing the asynchronous download operation.
	 */
	public static Future<Void> download(URL source, File dest,
		Consumer<Double> progressConsumer)
	{
		ExecutorService executor = Executors.newSingleThreadExecutor();
		return executor.submit(() -> {
			try (
				ReadableByteChannel rbc = Channels.newChannel(openStream(source));
				FileOutputStream fos = new FileOutputStream(dest)
			) {
				FileChannel fileChannel = fos.getChannel();
				long total = source.openConnection().getContentLengthLong();
				long read = 0;
				long r;
				long chunkSize = 64 * 1024; // Start with 64KB chunks.
				long minChunkSize = 8 * 1024; // Minimum 8KB chunks.
				long maxChunkSize = 8 * 1024 * 1024; // Maximum 8MB chunks.
				long targetTime = 1000 / 10; // 10 updates per second.
				long time = System.currentTimeMillis();
				while ((r = fileChannel.transferFrom(rbc, read, chunkSize)) > 0) {
					if (Thread.currentThread().isInterrupted()) {
						throw new InterruptedException("Download thread interrupted");
					}
					long next = System.currentTimeMillis();
					long elapsed = next - time;
					// Adjust chunk size to get closer to the read op target time.
					chunkSize = Math.min(maxChunkSize,
						Math.max(minChunkSize, chunkSize * elapsed / targetTime));
					time = next;
					read += r;
					progressConsumer.accept(total > 0 ?
						(double) read / total : // Total is known; return [0.0, 1.0].
						(double) read); // Total is unknown; return bytes downloaded.
				}
				return null;
			}
			finally {
				executor.shutdown();
			}
		});
	}

	public static List<String> downloadText(URL source) throws IOException {
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(openStream(source))))
		{
			return reader.lines().collect(Collectors.toList());
		}
	}

	/** Like {@link URL#openStream()}, but following HTTP 3xx redirects. */
	public static InputStream openStream(URL source) throws IOException {
		URLConnection conn = source.openConnection();
		if (conn instanceof HttpURLConnection) {
			// Follow 3xx redirects automatically.
			((HttpURLConnection) conn).setInstanceFollowRedirects(true);
		}
		return conn.getInputStream();
	}
}
