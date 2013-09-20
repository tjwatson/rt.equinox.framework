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

	public void testBundleFindEntriesReturnsEntriesOnDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			installBundle(equinox, "tb2.fragment");
			Collection<String> expected = Arrays.asList("tb2/X.class", "tb2/Y.class");
			Enumeration<URL> actual = tb2.findEntries("tb2", "*.class", false);
			String message = "Wrong entries";
			assertNotNull(message, actual);
			assertEquals(message, expected.size(), Collections.list(actual).size());
		} finally {
			equinox.stop();
		}
	}

	public void testBundleFindEntriesReturnsNullForDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			Bundle tb2Fragment = installBundle(equinox, "tb2.fragment");
			// Verify the fragment bundle is attached to host.
			Enumeration<URL> entries = tb2.findEntries("/", "MANIFEST.MF", true);
			entries.nextElement();
			entries.nextElement();
			String message = "Entries should not exist";
			assertNull(message, tb2.findEntries("bin", "*", true));
			assertNull(message, tb2Fragment.findEntries("bin", "*", true));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleFindEntriesReturnsNullForEntriesOnDevClasspathWhenRootDirNotOnBundleClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb3 = installBundle(equinox, "tb3");
			assertNull("Entries should not exist", tb3.findEntries("tb3/", "*", true));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetEntryReturnsEntryOnDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			assertNotNull("Entry should exist", tb2.getEntry("tb2/X.class"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetEntryReturnsNullForDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			String message = "Entry should not exist";
			assertNull(message, tb2.getEntry("bin"));
			assertNull(message, tb2.getEntry("bin/tb2"));
			assertNull(message, tb2.getEntry("bin/tb2/X.class"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetEntryReturnsNullForEntryOnDevClasspathWhenRootDirNotOnBundleClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb3 = installBundle(equinox, "tb3");
			String message = "Entry should not exist";
			assertNull(message, tb3.getEntry("tb3/Y.class"));
			assertNull(message, tb3.getEntry("bin/tb3/Y.class"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetEntryPathsReturnsEntryPathsOnDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			Collection<String> expected = Arrays.asList("tb2/X.class");
			Enumeration<String> actual = tb2.getEntryPaths("tb2");
			assertEquals("Wrong entry paths", expected, actual);
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetEntryPathsReturnsNullForDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			assertNull("Entry paths should not exist", tb2.getEntryPaths("bin"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetEntryPathsReturnsNullForEntriesOnDevClasspathWhenRootDirNotOnBundleClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb3 = installBundle(equinox, "tb3");
			assertNull("Entries should not exist", tb3.getEntryPaths("tb3"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetResourceReturnsResourceOnDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			assertNotNull("Resource should exist", tb2.getResource(".classpath"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetResourceReturnsNullForDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			String message = "Resource should not exist";
			assertNull(message, tb2.getResource("bin"));
			assertNull(message, tb2.getResource("bin/tb2"));
			assertNull(message, tb2.getResource("bin/tb2/X.class"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetResourceReturnsResourceOnDevClasspathWhenRootDirNotOnBundleClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb3 = installBundle(equinox, "tb3");
			assertNotNull("Resource should exist", tb3.getResource("tb3/Y.class"));
			assertNull("Resource should not exist", tb3.getResource("bin/tb3/Y.class"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetResourcesReturnsResourcesOnDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			installBundle(equinox, "tb2.fragment");
			Enumeration<URL> actual = tb2.getResources(".classpath");
			String message = "Wrong resources";
			assertNotNull(message, actual);
			assertEquals(message, 2, Collections.list(actual).size());
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetResourcesReturnsNullForDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			assertNull("Resources should not exist", tb2.getEntryPaths("bin"));
		} finally {
			equinox.stop();
		}
	}

	public void testBundleGetResourcesReturnsResourcesOnDevClasspathWhenRootDirNotOnBundleClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb3 = installBundle(equinox, "tb3");
			assertNotNull("Resources should exist", tb3.getResources("tb3/Y.class"));
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

	public void testBundleGetEntryReturnsRootEntry() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			assertNotNull("Entry should exist", tb2.getEntry("tb2.properties"));
		} finally {
			equinox.stop();
		}
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

	public void testBundleEntryPathsReturnsEntryPathsOnDevClasspath() throws Exception {
		Equinox equinox = createAndStartEquinox("bin");
		try {
			Bundle tb2 = installBundle(equinox, "tb2");
			Collection<String> expected = Arrays.asList("tb2/X.class");
			Enumeration<String> actual = tb2.getEntryPaths("tb2");
			assertEquals("Wrong entry paths", expected, actual);
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

	protected void setUp() throws Exception {
		installer = new BundleInstaller("test_files/devClasspath/", OSGiTestsActivator.getContext());
	}

	protected void tearDown() throws Exception {
		installer.shutdown();
	}

	private void assertEquals(String message, Collection<?> expected, Enumeration<?> actual) {
		if (expected == null) {
			assertNull(message, actual);
			return;
		}
		assertNotNull(message, actual);
		Collection<?> actualCollection = Collections.list(actual);
		assertEquals(message, expected, actualCollection);
	}

	private void assertEquals(String message, Collection<? extends Object> expected, Collection<? extends Object> actual) {
		if (expected == null && actual == null)
			return;
		if (expected == null || actual == null)
			fail(message);
		assertEquals(message, expected.size(), actual.size());
		for (Object o : expected)
			assertTrue(message, actual.contains(o));
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
