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

import javax.swing.Icon;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class offering dialog-box-related methods.
 *
 * @author Curtis Rueden
 */
public final class Dialogs {

	private Dialogs() { }

	public enum Result { YES, NO, NEVER, CANCELED }

	public static Result ask(Component parent,
		String message, String yes, String no, String never)
	{
		LookAndFeel.init();
		String title = ClassLauncher.appName("SciJava App Launcher");
		int optionType = JOptionPane.DEFAULT_OPTION;
		int messageType = JOptionPane.QUESTION_MESSAGE;
		Icon icon = null;
		List<Object> optionsList = new ArrayList<>();
		if (yes != null) optionsList.add(yes);
		if (no != null) optionsList.add(no);
		if (never != null) optionsList.add(never);
		Object[] options = optionsList.toArray();
		if (options.length == 0) {
			throw new IllegalArgumentException(
				"At least one of yes, no, or never must be non-null");
		}
		Object initial = no == null ? options[0] : no;
		int rval = JOptionPane.showOptionDialog(parent, message,
			title, optionType, messageType, icon, options, initial);
		switch (rval) {
			case 0: return Result.YES;
			case 1: return Result.NO;
			case 2: return Result.NEVER;
			case -1: return Result.CANCELED;
			default: throw new RuntimeException("Unexpected value: " + rval);
		}
	}

	public static void main(String[] args) {
		System.setProperty("scijava.app.look-and-feel", "com.formdev.flatlaf.FlatLightLaf");
		Result result = Dialogs.ask(null,
			"Do you like green eggs and ham?",
			"Yes",
			"I do not like green eggs and ham",
			"I do not like them, Sam-I-am");
		System.out.println(result);
	}
}
