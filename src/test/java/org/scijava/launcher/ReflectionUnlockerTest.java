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

import org.junit.jupiter.api.Test;

import java.awt.Panel;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link ReflectionUnlocker}.
 *
 * @author Curtis Rueden
 */
public class ReflectionUnlockerTest {

	private String getPrivateString() throws NoSuchFieldException, IllegalAccessException {
		Field f = Panel.class.getDeclaredField("base");
		f.setAccessible(true);
		return (String) f.get(null);
	}

	@Test
	public void testUnlockAll() throws Exception {
		int majorVersion = Integer.parseInt(System.getProperty("java.version").split("\\.")[0]);
		if (majorVersion >= 17) {
			// Check that we are *not* able to instantiate java.awt.Desktop with its private no-args constructor.
			try {
				final String value = getPrivateString();
				fail("Expected exception but got: " + value);
			}
			catch (Exception e) { }
		}
		ReflectionUnlocker.unlockAll();

		// Now try again.
		final String value = getPrivateString();
		assertEquals("panel", value);
	}
}
