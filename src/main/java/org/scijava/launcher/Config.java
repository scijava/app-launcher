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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple utility for reading and writing a property map to/from plain text.
 *
 * @author Curtis Rueden
 */
public final class Config {

	private Config() { }

	public static Map<String, String> load(File file) throws IOException {
		Map<String, String> map = new LinkedHashMap<>(); // Keep insertion order.
		Files.readAllLines(file.toPath()).forEach(line -> {
			int equals = line.indexOf('=');
			if (equals >= 0) {
				String key = line.substring(0, equals);
				String val = line.substring(equals + 1);
				map.put(key, val);
			}
		});
		return map;
	}

	public static void save(File file, Map<String, String> config)
		throws IOException
	{
		try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
			for (Map.Entry<String, String> entry : config.entrySet()) {
				pw.println(entry.getKey() + "=" + entry.getValue());
			}
		}
	}

	/**
	 * Updates the given config file by mixing in the specified properties,
	 * creating the file if it does not already exist.
	 */
	public static void update(File file, Map<String, String> props)
		throws IOException
	{
		Map<String, String> config;
		if (file.isFile()) {
			config = load(file);
			config.putAll(props);
		}
		else config = props;
		save(file, config);
	}

	/**
	 * Updates the given config file with the specified property pair,
	 * creating the file if it does not already exist.
	 */
	public static void update(File file, String key, String val)
		throws IOException
	{
		update(file, Collections.singletonMap(key, val));
	}
}
