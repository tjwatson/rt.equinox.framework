/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.tests.misc;

import java.io.File;
import java.net.URL;
import java.util.*;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.tests.harness.CoreTest;
import org.eclipse.osgi.launch.Equinox;
import org.eclipse.osgi.tests.OSGiTestsActivator;
import org.eclipse.osgi.tests.bundles.BundleInstaller;
import org.osgi.framework.*;

public class DevClasspathTests extends CoreTest {
	public static Test suite() {
		return new TestSuite(DevClasspathTests.class);
	}

	private static void assertEnumerationSize(String message, int expected, Enumeration<?> actual) {
		assertNotNull(message, actual);
		assertEquals(message, expected, Collections.list(actual).size());
	}

	private static void assertEquals(String message, Collection<?> expected, Enumeration<?> actual) {
		if (expected == null) {
			assertNull(message, actual);
			return;
		}
		assertNotNull(message, actual);
		Collection<?> actualCollection = Collections.list(actual);
		assertEquals(message, expected, actualCollection);
	}

	private static void assertEquals(String message, Collection<? extends Object> expected, Collection<? extends Object> actual) {
		if (expected == null && actual == null)
			return;
		if (expected == null || actual == null)
			fail(message);
		assertEquals(message, expected.size(), actual.size());
		for (Object o : expected)
			assertTrue(message, actual.contains(o));
	}

	private static void assertFragmentsAttachedToHost(Bundle host, int numOfFragments) {
		Enumeration<URL> entries = host.findEntries("/", "MANIFEST.MF", true);
		assertNotNull("No manifest entries found for host: " + host);
		int expected = numOfFragments + 1; // + 1 for the host bundle's manifest.
		int actual = Collections.list(entries).size();
		assertEquals("Wrong number of fragments for host: " + host, expected, actual);
	}

	private BundleInstaller installer;

	public void testBug351083() throws Exception {
		Equinox equinox = createAndStartEquinox("../devCP");
		Bundle tb1 = installBundle(equinox, "bug351083/tb1");
		URL resource = tb1.getResource("tb1/resource.txt");
		assertNotNull("Resource is null", resource);
		try {
			equinox.stop();
		} catch (BundleException e) {
			fail("Unexpected erorr stopping framework", e); //$NON-NLS-1$
		}
		try {
			equinox.waitForStop(10000);
		} catch (InterruptedException e) {
			fail("Unexpected interrupted exception", e); //$NON-NLS-1$
		}
		assertEquals("Wrong state for SystemBundle", Bundle.RESOLVED, equinox.getState()); //$NON-NLS-1$
	}

	public void testBundleFindEntriesOmitsDevClasspathEntriesWhenRootDirNotOnBundleClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb3 = installBundle(equinox, "tb3");
			Collection<String> expected = Arrays.asList("tb3.properties", "META-INF/", "META-INF/MANIFEST.MF");
			Enumeration<URL> actual = tb3.findEntries("/", "*", true);
			assertNotNull("Wrong entry paths", actual);
			assertEquals("Wrong entry paths", expected.size(), Collections.list(actual).size());
		} finally {
			equinox.stop();
		}
	}

	public void testBundleFindEntriesSquashesDevClasspathEntries() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			Collection<String> expected = Arrays.asList("tb2.properties", "META-INF/", "META-INF/MANIFEST.MF", "tb2/", "tb2/X.class", ".classpath");
			Enumeration<URL> actual = tb2.findEntries("/", "*", true);
			assertNotNull("Wrong entry paths", actual);
			assertEquals("Wrong entry paths", expected.size(), Collections.list(actual).size());
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetEntryPathsReturnsRootEntries() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			Collection<String> expected = Arrays.asList("tb2.properties", "META-INF/", "tb2/", ".classpath");
			Enumeration<String> actual = tb2.getEntryPaths("/");
			assertEquals("Wrong entry paths", expected, actual);
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetEntryReturnsRootEntry() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			assertNotNull("Entry should exist", tb2.getEntry("tb2.properties"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleLoadClassForClassNotOnDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox(null);
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			try {
				tb2.loadClass("tb2.X");
				fail("Class load should have failed");
			} catch (ClassNotFoundException e) {
				// Okay.
			}
		} finally {
			equinox.stop();
		}
	}

	public void testBundleLoadClassForClassOnDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			installBundle(equinox, "tb2.fragment");
			try {
				tb2.loadClass("tb2.X");
				tb2.loadClass("tb2.Y");
			} catch (ClassNotFoundException e) {
				fail("Class load should not have failed", e);
			}
		} finally {
			equinox.stop();
		}
	}

	public void testPathsContainingDevClasspathNull() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			Bundle tb2Fragment = installBundle(equinox, "tb2.fragment");
			assertFragmentsAttachedToHost(tb2, 1);
			String message = "Paths containing dev classpath should return null";
			// Bundle.findEntries
			assertNull(message, tb2.findEntries("bin", "*", true));
			assertNull(message, tb2Fragment.findEntries("bin", "*", true));
			// Bundle.getEntry
			assertNull(message, tb2.getEntry("bin"));
			assertNull(message, tb2.getEntry("bin/tb2"));
			assertNull(message, tb2.getEntry("bin/tb2/X.class"));
			// Bundle.getEntryPaths
			assertNull(message, tb2.getEntryPaths("bin"));
			// Bundle.getResource
			assertNull(message, tb2.getResource("bin"));
			assertNull(message, tb2.getResource("bin/tb2"));
			assertNull(message, tb2.getResource("bin/tb2/X.class"));
			// Bundle.getResources
			assertNull(message, tb2.getEntryPaths("bin"));
		} finally {
			equinox.stop();
		}
	}

	public void testPathsContainingDevClasspathNullWhenBundleRootDirNotOnBundleClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb3 = installBundle(equinox, "tb3");
			String message = "Paths containing dev classpath should return null";
			// Bundle.findEntries
			assertNull(message, tb3.findEntries("tb3/", "*", true));
			// Bundle.getEntry
			assertNull(message, tb3.getEntry("tb3/Y.class"));
			assertNull(message, tb3.getEntry("bin/tb3/Y.class"));
			// Bundle.getEntryPaths
			assertNull(message, tb3.getEntryPaths("tb3"));
		} finally {
			equinox.stop();
		}
	}

	public void testPathsOnDevClasspathNotNull() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			installBundle(equinox, "tb2.fragment");
			String message = "Paths on dev classpath should not return null";
			// Bundle.findEntries
			assertEnumerationSize(message, Arrays.asList("tb2/X.class", "tb2/Y.class").size(), tb2.findEntries("tb2", "*.class", false));
			// Bundle.getEntry
			assertNotNull(message, tb2.getEntry("tb2/X.class"));
			// Bundle.getEntryPaths
			assertEquals(message, Arrays.asList("tb2/X.class"), tb2.getEntryPaths("tb2"));
			// Bundle.getResource
			assertNotNull("Resource should exist", tb2.getResource(".classpath"));
			// Bundle.getResources
			assertEnumerationSize(message, 2, tb2.getResources(".classpath"));
		} finally {
			equinox.stop();
		}
	}

	public void testPathsOnDevClasspathNotNullWhenBundleRootDirNotOnBundleClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb3 = installBundle(equinox, "tb3");
			String message = "Paths on dev classpath should not return null";
			// Bundle.getResource
			assertNotNull(message, tb3.getResource("tb3/Y.class"));
			// Bundle.getResources
			assertNotNull(message, tb3.getResources("tb3/Y.class"));
		} finally {
			equinox.stop();
		}
	}

	protected void setUp() throws Exception {
		installer = new BundleInstaller("test_files/devClasspath/", OSGiTestsActivator.getContext());
	}

	protected void tearDown() throws Exception {
		installer.shutdown();
	}

	private Equinox createAndStartEquinox(String devClasspath) throws BundleException {
		File config = OSGiTestsActivator.getContext().getDataFile(getName());
		Map<String, Object> configuration = new HashMap<String, Object>();
		configuration.put(Constants.FRAMEWORK_STORAGE, config.getAbsolutePath());
		if (devClasspath != null)
			configuration.put("osgi.dev", devClasspath);
		Equinox equinox = new Equinox(configuration);
		equinox.start();
		return equinox;
	}

	private Bundle installBundle(Equinox equinox, String name) throws BundleException {
		String location = installer.getBundleLocation(name);
		BundleContext context = equinox.getBundleContext();
		return context.installBundle(location);
	}
}
