package org.scijava.launcher;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link ClassLoaderPlus}.
 *
 * @author Curtis Rueden
 */
public class ClassLoaderPlusTest {

	/** Tests {@link ClassLoaderPlus#get}. */
	@Test
	public void testGet() throws Exception {
		File jarFile = Paths.get("src", "test", "resources", "greet.jar").toFile();
		assumeTrue(jarFile.isFile());
		URLClassLoader greetLoader = ClassLoaderPlus.get(null, jarFile);
		assertNotNull(greetLoader);
		Class<?> greetClass = greetLoader.loadClass("com.example.greet.Greet");
		assertNotNull(greetClass);
		assertEquals("com.example.greet.Greet", greetClass.getName());
	}

	@Test
	public void testGetFailure() throws Exception {
		File jarFile = Paths.get("src", "test", "resources", "ask.jar").toFile();
		assumeTrue(jarFile.isFile());
		URLClassLoader askLoader = ClassLoaderPlus.get(null, jarFile);
		assertNotNull(askLoader);
		Class<?> askClass = askLoader.loadClass("com.example.ask.Ask");
		assertNotNull(askClass);
		Exception expected = null;
		Object value = null;
		try {
			value = askClass.getMethod("question").invoke(null);
		}
		catch (Exception e) {
			expected = e;
			assertEquals(
				"java.lang.NoClassDefFoundError: com/example/greet/Greet",
				e.getCause().toString()
			);
		}
		if (expected == null) fail("Expected exception but got value: " + value);
	}

	/** Tests {@link ClassLoaderPlus#getRecursively}. */
	@Test
	public void testGetRecursively() throws Exception {
		File jarDir = Paths.get("src", "test", "resources").toFile();
		assumeTrue(jarDir.isDirectory());
		URLClassLoader classLoader = ClassLoaderPlus.getRecursively(null, true, jarDir);
		assertNotNull(classLoader);
		Class<?> askClass = classLoader.loadClass("com.example.ask.Ask");
		Method questionMethod = askClass.getMethod("question()");
		Object question = questionMethod.invoke(null);
		assertEquals("Hello! How are you?", question);
	}

	/** Tests {@link ClassLoaderPlus#getRecursively} failure cases. */
	@Test
	public void testGetRecursivelyFailure() throws Exception {
		File jarDir = Paths.get("src", "main", "java").toFile();
		assumeTrue(jarDir.isDirectory());
		URLClassLoader classLoader = ClassLoaderPlus.getRecursively(null, true, jarDir);
		assertNull(classLoader);
	}
}
