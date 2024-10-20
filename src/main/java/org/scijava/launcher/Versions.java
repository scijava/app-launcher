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

/**
 * Utility methods for working with version strings.
 *
 * @author Curtis Rueden
 */
public final class Versions {

	private Versions() { }

	/**
	 * Compares two version strings.
	 *
	 * @param v1 The first version string.
	 * @param v2 The second version string.
	 * @return a negative integer, zero, or a positive integer as the first
	 * argument is less than, equal to, or greater than the second.
	 */
	public static int compare(final String v1, final String v2) {
		final String[] t1 = splitDots(v1), t2 = splitDots(v2);
		final int count = Math.min(t1.length, t2.length);
		for (int t = 0; t < count; t++) {
			final int c = compareToken(t1[t], t2[t]);
			if (c != 0) return c;
		}
		if (t1.length == t2.length) return 0;
		// NB: Token count differs. More tokens means newer -- e.g. 1.5 < 1.5.1.
		return t1.length < t2.length ? -1 : 1;
	}

	/**
	 * Converts an internal major.minor Java class version into an external Java
	 * VM version. The mapping is as follows:
	 * <ul>
	 * <li>45.0 &rarr; 1.1</li>
	 * <li>46.0 &rarr; 1.2</li>
	 * <li>47.0 &rarr; 1.3</li>
	 * <li>48.0 &rarr; 1.4</li>
	 * <li>49.0 &rarr; 1.5</li>
	 * <li>50.0 &rarr; 1.6</li>
	 * <li>51.0 &rarr; 1.7</li>
	 * <li>52.0 &rarr; 1.8</li>
	 * <li>53.0 &rarr; 9</li>
	 * <li>54.0 &rarr; 10</li>
	 * <li>55.0 &rarr; 11</li>
	 * <li>...etc.</li>
	 * </ul>
	 */
	public static String classVersionToJavaVersion(String classVersion) {
		try {
			int dot = classVersion.indexOf('.');
			String major = dot < 0 ? classVersion : classVersion.substring(0, dot);
			int vmMajorDigit = Double.valueOf(major).intValue() - 44;
			return vmMajorDigit >= 9 ? "" + vmMajorDigit : "1." + vmMajorDigit;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Splits the given version string by dots.
	 */
	private static String[] splitDots(final String s) {
		// NB: -1 split limit causes split not to remove empty values.
		// See: https://stackoverflow.com/a/14602089/1207769
		return s.isEmpty() ? new String[0] : s.split("\\.", -1);
	}

	/**
	 * Compares one token of a multi-token version string.
	 */
	private static int compareToken(final String t1, final String t2) {
		final int i1 = digitIndex(t1), i2 = digitIndex(t2);
		String suffix1 = t1, suffix2 = t2;
		if (i1 > 0 && i2 > 0) {
			// Versions start with digits; compare them numerically.
			final long d1 = Long.parseLong(t1.substring(0, i1));
			final long d2 = Long.parseLong(t2.substring(0, i2));
			if (d1 < d2) return -1;
			if (d1 > d2) return 1;
			suffix1 = t1.substring(i1);
			suffix2 = t2.substring(i2);
		}

		// Final version (empty string) is larger than non-final (non-empty).
		// For example: 2.0.0 > 2.0.0-beta-1.
		if (suffix1.isEmpty() && suffix2.isEmpty()) return 0;
		if (suffix1.isEmpty()) return 1;
		if (suffix2.isEmpty()) return -1;

		// Compare lexicographically.
		return suffix1.compareTo(suffix2);
	}

	/**
	 * Gets the subsequent index to all the given string's leading digits.
	 */
	private static int digitIndex(final String s) {
		int index = 0;
		for (int i = 0; i < s.length(); i++) {
			final char ch = s.charAt(index);
			if (ch < '0' || ch > '9') break;
			index++;
		}
		return index;
	}
}
