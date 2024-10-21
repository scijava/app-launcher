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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Tests {@link Archives}.
 *
 * @author Curtis Rueden
 */
public class ArchivesTest {

	@Test
	public void testUnzip() throws Exception {
		unpackAndAssert("test-bundle.zip", "zipped-bundle", "Hello zip!");
	}

	@Test
	public void testUntar() throws Exception {
		assumeFalse(System.getProperty("os.name").contains("Windows"));
		unpackAndAssert("test-bundle.tar.gz", "tarred-bundle", "Hello tar.gz!");
	}

	private void unpackAndAssert(String archiveFilename, String folderName, String readmeMessage) throws Exception {
		// Unpack the file to a temporary directory.
		File archive = new File("src/test/resources/" + archiveFilename);
		assertTrue(archive.exists());
		Path destPath = Files.createTempDirectory("scijava-app-launcher-");
		File destFile = destPath.toFile();
		destFile.deleteOnExit();
		String outputPrefix = folderName + "/";
		List<String> expectedOutput = Arrays.asList(outputPrefix, outputPrefix + "readme.txt", outputPrefix + "data.dat");
		List<String> output = new ArrayList<>();
		Archives.unpack(archive, destFile, output::add).get();
		assertEquals(expectedOutput, output);

		// Check for unpacked folder.
		Path unpackedFolder = destPath.resolve(folderName);
		assertTrue(unpackedFolder.toFile().isDirectory());

		// Check for readme.txt.
		Path readme = unpackedFolder.resolve("readme.txt");
		List<String> readmeLines = Files.readAllLines(readme);
		assertEquals(Collections.singletonList(readmeMessage), readmeLines);

		// Check for data.dat.
		Path data = unpackedFolder.resolve("data.dat");
		byte[] actualBytes = Files.readAllBytes(data);
		byte[] expectedBytes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
		assertArrayEquals(expectedBytes, actualBytes);

		// Check that there are no rogue files.
		File[] unpackedFiles = unpackedFolder.toFile().listFiles();
		assertNotNull(unpackedFiles);
		Arrays.sort(unpackedFiles);
		assertEquals(
			Arrays.asList(data.toFile(), readme.toFile()),
			Arrays.asList(unpackedFiles)
		);
	}
}
