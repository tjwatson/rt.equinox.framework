/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.tests.bundles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.eclipse.osgi.launch.EquinoxFactory;
import org.eclipse.osgi.tests.OSGiTestsActivator;
import org.eclipse.osgi.tests.bundles.classes.Activator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ConnectContent.ConnectEntry;
import org.osgi.framework.connect.ConnectFactory;
import org.osgi.framework.connect.ConnectModule;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;

public class ConnectTests extends AbstractBundleTests {

	public static Test suite() {
		return new TestSuite(ConnectTests.class);
	}

	void doTestConnect(ConnectFactory connectFactory, Map<String, String> fwkConfig, Consumer<Framework> test) {
		File config = OSGiTestsActivator.getContext().getDataFile(getName());
		config.mkdirs();
		fwkConfig.put(Constants.FRAMEWORK_STORAGE, config.getAbsolutePath());
		FrameworkFactory fwkFactory = new EquinoxFactory();
		Framework framework = fwkFactory.newFramework(fwkConfig, connectFactory);
		boolean passed = false;
		try {
			test.accept(framework);
			passed = true;
		} finally {
			try {
				framework.stop();
				framework.waitForStop(10000);
			} catch (Exception e) {
				if (passed) {
					sneakyThrow(e);
				}
			}
		}
	}

	public static class TestCountingConnectFactory implements ConnectFactory {
		private final AtomicInteger initializeCalled = new AtomicInteger();
		private final Queue<String> getModuleCalled = new ConcurrentLinkedQueue<>();
		private final AtomicInteger createBundleActivatorCalled = new AtomicInteger();
		private final Map<String, ConnectModule> modules = new ConcurrentHashMap<String, ConnectModule>();

		@Override
		public void initialize(File storage, Map<String, String> config) {
			initializeCalled.getAndIncrement();
		}

		@Override
		public Optional<ConnectModule> getModule(String location) {
			getModuleCalled.add(location);
			ConnectModule m = modules.get(location);
			if (m == ILLEGAL_STATE_EXCEPTION) {
				throw new IllegalStateException();
			}
			return Optional.ofNullable(m);
		}

		@Override
		public Optional<BundleActivator> createBundleActivator() {
			createBundleActivatorCalled.getAndIncrement();
			return Optional.empty();
		}

		int getInitializeCnt() {
			return initializeCalled.get();
		}

		List<String> getModuleLocations() {
			return new ArrayList<>(getModuleCalled);
		}

		int getCreateBundleActivatorCnt() {
			return createBundleActivatorCalled.get();
		}

		void setModule(String location, ConnectModule module) {
			if (module == null) {
				modules.remove(location);
			} else {
				modules.put(location, module);
			}
		}
	}

	public static class TestConnectModule implements ConnectModule {
		private volatile TestConnectContent content;

		public TestConnectModule(TestConnectContent content) {
			this.content = content;
		}

		@Override
		public TestConnectContent getContent() {
			return content;
		}

		void setContent(TestConnectContent updatedContent) {
			this.content = updatedContent;
		}
	}

	public static class TestConnectContent implements ConnectContent {
		private final Map<String, String> headers;
		private final Map<String, ConnectEntry> entries = new LinkedHashMap<>();
		private final ClassLoader loader;
		private final AtomicBoolean isOpen = new AtomicBoolean();

		public TestConnectContent(Map<String, String> headers, ClassLoader loader) {
			this.headers = headers;
			this.loader = loader;
		}

		@Override
		public Optional<Map<String, String>> getHeaders() {
			checkOpen();
			return Optional.ofNullable(headers);
		}

		@SuppressWarnings("unused")
		@Override
		public Iterable<String> getEntries() throws IOException {
			checkOpen();
			return entries.keySet();
		}

		@Override
		public Optional<ConnectEntry> getEntry(String name) {
			checkOpen();
			return Optional.ofNullable(entries.get(name));
		}

		@Override
		public Optional<ClassLoader> getClassLoader() {
			checkOpen();
			return Optional.ofNullable(loader);
		}

		@SuppressWarnings("unused")
		@Override
		public void open() throws IOException {
			if (!isOpen.compareAndSet(false, true)) {
				throw new IllegalStateException("Already Opened.");
			}
		}

		@SuppressWarnings("unused")
		@Override
		public void close() throws IOException {
			if (!isOpen.compareAndSet(true, false)) {
				throw new IllegalStateException("Already Closed.");
			}
		}

		void addEntry(String path, ConnectEntry entry) {
			entries.put(path, entry);
		}

		private void checkOpen() {
			if (!isOpen.get()) {
				throw new IllegalStateException("Not Opened.");
			}
		}

		boolean isOpen() {
			return isOpen.get();
		}
	}

	public static class TestConnectEntryBytes implements ConnectEntry {
		private final String name;
		private final byte[] bytes;

		public TestConnectEntryBytes(String name, byte[] bytes) {
			this.name = name;
			this.bytes = bytes;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public long getContentLength() {
			return bytes.length;
		}

		@Override
		public long getLastModified() {
			return 0;
		}

		@Override
		public byte[] getBytes() {
			return bytes.clone();
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(bytes);
		}

	}

	public static class TestConnectEntryURL implements ConnectEntry {
		private final String name;
		private final URL content;

		public TestConnectEntryURL(String name, URL content) {
			this.name = name;
			this.content = content;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public long getContentLength() {
			try {
				return content.openConnection().getContentLengthLong();
			} catch (IOException e) {
				return 0;
			}
		}

		@Override
		public long getLastModified() {
			try {
				return content.openConnection().getLastModified();
			} catch (IOException e) {
				return 0;
			}
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return content.openStream();
		}

	}

	static final TestConnectModule ILLEGAL_STATE_EXCEPTION = new TestConnectModule(null);

	public void testConnectFactoryNoModules() {
		TestCountingConnectFactory connectFactory = new TestCountingConnectFactory();

		doTestConnect(connectFactory, new HashMap<>(), (f) -> {
			try {
				f.start();
				f.stop();
				f.waitForStop(5000);
				f.start();
				f.stop();
				f.waitForStop(5000);
			} catch (Throwable t) {
				sneakyThrow(t);
			}
		});
		doTestConnect(connectFactory, new HashMap<>(), (f) -> {
			try {
				f.start();
				f.stop();
			} catch (BundleException e) {
				sneakyThrow(e);
			}
		});

		assertEquals("Wrong number of init called.", 2, connectFactory.getInitializeCnt());
		assertEquals("Wrong number of create activator called.", 3, connectFactory.getCreateBundleActivatorCnt());
	}

	public void testConnectActivator() {
		final AtomicInteger bundleActvatorStartCalled = new AtomicInteger();
		final AtomicInteger bundleActvatorStopCalled = new AtomicInteger();
		ConnectFactory activatorFactory = new TestCountingConnectFactory() {
			@Override
			public Optional<BundleActivator> createBundleActivator() {
				super.createBundleActivator();
				return Optional.of(new BundleActivator() {

					@Override
					public void start(BundleContext context) throws Exception {
						bundleActvatorStartCalled.getAndIncrement();
					}

					@Override
					public void stop(BundleContext context) throws Exception {
						bundleActvatorStopCalled.getAndIncrement();
					}
				});
			}
		};

		doTestConnect(activatorFactory, new HashMap<>(), (f) -> {
			try {
				f.start();
				f.stop();
				f.waitForStop(5000);
				f.start();
				f.stop();
				f.waitForStop(5000);
			} catch (Exception e) {
				sneakyThrow(e);
			}
		});
		assertEquals("Wrong number of start called.", 2, bundleActvatorStartCalled.get());
		assertEquals("Wrong number of stop called.", 2, bundleActvatorStopCalled.get());
	}

	public void testConnectInit() {
		final AtomicReference<File> initFile = new AtomicReference<>();
		final AtomicReference<File> storeFile = new AtomicReference<>();
		final AtomicReference<Map<String, String>> initConfig = new AtomicReference<>();
		ConnectFactory activatorFactory = new TestCountingConnectFactory() {
			@Override
			public void initialize(File storage, Map<String, String> config) {
				super.initialize(storage, config);
				initFile.set(storage);
				initConfig.set(config);
			}
		};

		Map<String, String> config = new HashMap<>();
		config.put("k1", "v1");
		config.put("k2", "v2");

		doTestConnect(activatorFactory, config, (f) -> {
			try {
				f.init();
				BundleContext bc = f.getBundleContext();
				storeFile.set(new File(bc.getProperty(Constants.FRAMEWORK_STORAGE)));
			} catch (Exception e) {
				sneakyThrow(e);
			}
		});
		TestCase.assertEquals("Wrong init store file.", storeFile.get(), initFile.get());
		assertTrue("Did not find all init configs: " + initConfig.get(), initConfig.get().entrySet().containsAll(config.entrySet()));
		try {
			initConfig.get().put("k3", "v3");
			fail("Expected unmodifiable map");
		} catch (UnsupportedOperationException e) {
			// expected
		}
	}

	public void testConnectContentHeaders() throws IOException {
		doTestConnectContentSimple(false);
	}

	public void testConnectContentManifest() throws IOException {
		doTestConnectContentSimple(true);
	}

	void doTestConnectContentSimple(boolean withManifest) throws IOException {
		TestCountingConnectFactory connectFactory = new TestCountingConnectFactory();
		final List<String> locations = Arrays.asList("b.1", "b.2", "b.3", "b.4");
		for (String l : locations) {
			connectFactory.setModule(l, withManifest ? createSimpleManifestModule(l) : createSimpleHeadersModule(l));
		}

		doTestConnect(connectFactory, new HashMap<>(), (f) -> {
			try {
				f.init();
				for (String l : locations) {
					Bundle b = f.getBundleContext().installBundle(l);
					assertEquals("Wrong symbolic name.", l, b.getSymbolicName());
				}
			} catch (Throwable t) {
				sneakyThrow(t);
			}
		});

		doTestConnect(connectFactory, new HashMap<>(), (f) -> {
			try {
				f.init();
				Bundle[] bundles = f.getBundleContext().getBundles();
				assertEquals("Wrong number of bundles from cache.", locations.size() + 1, bundles.length);
				for (String l : locations) {
					Bundle b = f.getBundleContext().getBundle(l);
					assertNotNull("No bundle at location: " + l, b);
					assertEquals("Wrong symbolic name.", l, b.getSymbolicName());
				}
			} catch (BundleException e) {
				sneakyThrow(e);
			}
		});

		connectFactory.setModule("b.2", null);
		connectFactory.setModule("b.3", ILLEGAL_STATE_EXCEPTION);
		doTestConnect(connectFactory, new HashMap<>(), (f) -> {
			try {
				f.init();
				Bundle[] bundles = f.getBundleContext().getBundles();
				assertEquals("Wrong number of bundles from cache.", locations.size() - 1, bundles.length);
				for (String l : locations) {
					Bundle b = f.getBundleContext().getBundle(l);
					if ("b.2".equals(l) || "b.3".equals(l)) {
						assertNull("Found unexpected bundle.", b);
					} else {
						assertNotNull("No bundle at location: " + l, b);
						assertEquals("Wrong symbolic name.", l, b.getSymbolicName());
					}
				}
			} catch (BundleException e) {
				sneakyThrow(e);
			}
		});
	}

	public void testConnectContentActivatorsWithFrameworkLoaders() {
		doTestConnectContentActivators(false);
	}

	public void testConnectContentActivatorsWithProvidedLoaders() {
		doTestConnectContentActivators(true);
	}

	void doTestConnectContentActivators(boolean provideLoader) {
		TestCountingConnectFactory connectFactory = new TestCountingConnectFactory();
		final List<Integer> ids = Arrays.asList(1, 2, 3);
		for (Integer id : ids) {
			connectFactory.setModule(id.toString(), createAdvancedModule(id, provideLoader));
		}

		doTestConnect(connectFactory, new HashMap<>(), (f) -> {
			try {
				f.start();
				for (Integer id : ids) {
					Bundle b = f.getBundleContext().installBundle(id.toString());
					assertEquals("Wrong symbolic name.", id.toString(), b.getSymbolicName());
					b.start();
					ServiceReference<?>[] registered = b.getRegisteredServices();
					assertNotNull("No services found.", registered);
					assertEquals("Wrong number of services.", 1, registered.length);
					assertEquals("Wrong service property.", Activator.class.getSimpleName() + id, (String) registered[0].getProperty("activator"));
					if (provideLoader) {
						assertTrue("Expected the same classes.", Activator.class.equals(b.loadClass(Activator.class.getName())));
					} else {
						assertFalse("Expected different classes.", Activator.class.equals(b.loadClass(Activator.class.getName())));
					}
				}
			} catch (Throwable t) {
				sneakyThrow(t);
			}
		});
	}

	public void testConnectContentEntriesWithFrameworkLoaders() {
		doTestConnectContentEntries(false);
	}

	public void testConnectContentEntriesWithProvidedLoaders() {
		doTestConnectContentEntries(true);
	}

	void doTestConnectContentEntries(boolean provideLoader) {
		TestCountingConnectFactory connectFactory = new TestCountingConnectFactory();
		final List<Integer> ids = Arrays.asList(1, 2, 3);
		final Map<Integer, TestConnectModule> modules = new HashMap<>();
		for (Integer id : ids) {
			TestConnectModule m = createAdvancedModule(id, provideLoader);
			modules.put(id, m);
			connectFactory.setModule(id.toString(), m);
		}

		doTestConnect(connectFactory, new HashMap<>(), (f) -> {
			try {
				f.start();
				for (Integer id : ids) {
					Bundle b = f.getBundleContext().installBundle(id.toString());
					assertEquals("Wrong symbolic name.", id.toString(), b.getSymbolicName());
					TestConnectModule m = modules.get(id);
					List<String> entries = new ArrayList<>();
					for (String entry : m.getContent().getEntries()) {
						entries.add(entry);
					}

					Set<String> bundleEntryUrls = new HashSet<>();
					for (Enumeration<URL> eUrls = b.findEntries("/", "*", true); eUrls.hasMoreElements();) {
						// URL paths always begin with '/', remove it
						bundleEntryUrls.add(eUrls.nextElement().getPath().substring(1));
					}
					assertEquals("Wrong number of bundle entry URLs.", entries.size(), bundleEntryUrls.size());
					assertTrue("Wrong bundle entry URLs: " + bundleEntryUrls, entries.containsAll(bundleEntryUrls));

					List<String> bundleEntryPaths = new ArrayList<>();
					for (Enumeration<String> ePaths = b.getEntryPaths("org/eclipse/osgi/tests/bundles/resources"); ePaths.hasMoreElements();) {
						bundleEntryPaths.add(ePaths.nextElement());
					}
					assertEquals("Wrong number of bundle entry paths from root.", 1, bundleEntryPaths.size());
					assertEquals("Wrong bundle entry found at root.", "org/eclipse/osgi/tests/bundles/resources/" + id + ".txt", bundleEntryPaths.get(0));

					BundleWiring wiring = b.adapt(BundleWiring.class);
					assertNotNull("No wiring.", wiring);
					Collection<String> wiringResourcePaths = wiring.listResources("/", "*", BundleWiring.LISTRESOURCES_LOCAL | BundleWiring.LISTRESOURCES_RECURSE);
					assertEquals("Wrong number of resource paths.", entries.size(), wiringResourcePaths.size());
					assertTrue("Wrong resource paths: " + wiringResourcePaths, entries.containsAll(wiringResourcePaths));

					Set<String> wiringEntryUrls = new HashSet<>();
					for (URL url : wiring.findEntries("/", "*", BundleWiring.FINDENTRIES_RECURSE)) {
						// URL paths always begin with '/', remove it
						wiringEntryUrls.add(url.getPath().substring(1));
					}
					assertEquals("Wrong number of wiring entry URLs.", entries.size(), wiringEntryUrls.size());
					assertTrue("Wrong wiring entry URLs: " + wiringEntryUrls, entries.containsAll(wiringEntryUrls));

					String txtPath = "org/eclipse/osgi/tests/bundles/resources/" + id + ".txt";
					Optional<ConnectEntry> txtConnectEntry = m.getContent().getEntry(txtPath);
					assertTrue("Could not find text entry.", txtConnectEntry.isPresent());
					checkEntry(txtConnectEntry.get(), b.getEntry(txtPath), id);
					checkEntry(txtConnectEntry.get(), b.getResource(txtPath), id);
				}
			} catch (Throwable t) {
				sneakyThrow(t);
			}
		});
	}

	public void testOpenCloseUpdateConnectContent() {
		final String NAME1 = "testUpdate.1";
		final String NAME2 = "testUpdate.2";
		TestCountingConnectFactory connectFactory = new TestCountingConnectFactory();
		TestConnectModule m = createSimpleHeadersModule(NAME1);
		connectFactory.setModule(NAME1, m);

		doTestConnect(connectFactory, new HashMap<>(), (f) -> {
			try {
				f.start();
				Bundle b = f.getBundleContext().installBundle(NAME1);
				assertEquals("Wrong name.", NAME1, b.getSymbolicName());
				// make sure to open the bundle file
				assertNull(b.getEntry("doesNotExist.txt"));
				TestConnectContent original = m.getContent();
				assertTrue("Original content is not open.", original.isOpen());

				// set the new content but don't update
				m.setContent(createSimpleHeadersContent(NAME2));

				FrameworkWiring fwkWiring = f.adapt(FrameworkWiring.class);
				CountDownLatch refreshDone = new CountDownLatch(1);
				fwkWiring.refreshBundles(Collections.singletonList(b), (e) -> refreshDone.countDown());
				refreshDone.await();

				// should still be NAME1
				assertEquals("Wrong name.", NAME1, b.getSymbolicName());
				assertTrue("Original content is not open.", original.isOpen());

				// now update should stage in the new content
				b.update();
				assertEquals("Wrong name.", NAME2, b.getSymbolicName());
				// make sure to open the bundle file
				assertNull(b.getEntry("doesNotExist.txt"));
				TestConnectContent newContent = m.getContent();
				assertTrue("New content is not open.", newContent.isOpen());
				assertFalse("Original content is open.", original.isOpen());
			} catch (Throwable t) {
				sneakyThrow(t);
			}
		});
	}

	void checkEntry(ConnectEntry expected, URL actual, Integer id) throws IOException {
		assertEquals("Wring path.", expected.getName(), actual.getPath().substring(1));
		URLConnection connection = actual.openConnection();
		assertEquals("Wrong last modified.", expected.getLastModified(), connection.getLastModified());
		assertEquals("Wrong content length.", expected.getContentLength(), connection.getContentLengthLong());
		byte[] expectedBytes = getBytes(expected.getInputStream());
		byte[] actualBytes = getBytes(connection.getInputStream());
		assertEquals("Wrong input steam size.", expectedBytes.length, actualBytes.length);
		for (int i = 0; i < expectedBytes.length; i++) {
			assertEquals("Wrong byte at: " + i, expectedBytes[i], actualBytes[i]);
		}
		String actualString = new String(actualBytes);
		assertEquals("Wrong entry string.", id.toString(), actualString);
	}

	TestConnectModule createSimpleHeadersModule(String name) {
		return new TestConnectModule(createSimpleHeadersContent(name));
	}

	TestConnectContent createSimpleHeadersContent(String name) {
		Map<String, String> headers = new HashMap<>();
		headers.put(Constants.BUNDLE_MANIFESTVERSION, "2");
		headers.put(Constants.BUNDLE_SYMBOLICNAME, name);
		headers.put(Constants.IMPORT_PACKAGE, "org.osgi.framework");
		return new TestConnectContent(headers, null);
	}

	TestConnectModule createSimpleManifestModule(String name) throws IOException {
		Manifest manifest = new Manifest();
		Attributes headers = manifest.getMainAttributes();
		headers.putValue("Manifest-Version", "1");
		headers.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
		headers.putValue(Constants.BUNDLE_SYMBOLICNAME, name);
		headers.putValue(Constants.IMPORT_PACKAGE, "org.osgi.framework");
		ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
		manifest.write(manifestBytes);
		TestConnectContent c = new TestConnectContent(null, null);
		addEntry("META-INF/MANIFEST.MF", manifestBytes.toByteArray(), c);
		return new TestConnectModule(c);
	}

	TestConnectModule createAdvancedModule(Integer id, boolean provideLoader) {
		Map<String, String> headers = new HashMap<>();
		headers.put(Constants.BUNDLE_MANIFESTVERSION, "2");
		headers.put(Constants.BUNDLE_SYMBOLICNAME, id.toString());
		headers.put(Constants.IMPORT_PACKAGE, "org.osgi.framework");
		headers.put(Constants.BUNDLE_ACTIVATOR, Activator.class.getName() + id);
		TestConnectContent c = new TestConnectContent(headers, provideLoader ? getClass().getClassLoader() : null);
		addEntry("org/", c);
		addEntry("org/eclipse/", c);
		addEntry("org/eclipse/osgi/", c);
		addEntry("org/eclipse/osgi/tests/", c);
		addEntry("org/eclipse/osgi/tests/bundles/", c);
		addEntry("org/eclipse/osgi/tests/bundles/classes/", c);
		addEntry("org/eclipse/osgi/tests/bundles/classes/Activator.class", c);
		addEntry("org/eclipse/osgi/tests/bundles/classes/Activator" + id + ".class", c);
		addEntry("org/eclipse/osgi/tests/bundles/resources/", c);
		addEntry("org/eclipse/osgi/tests/bundles/resources/" + id + ".txt", c);
		return new TestConnectModule(c);
	}

	void addEntry(String name, TestConnectContent content) {
		content.addEntry(name, new TestConnectEntryURL(name, getClass().getResource("/" + name)));
	}

	void addEntry(String name, byte[] bytes, TestConnectContent content) {
		content.addEntry(name, new TestConnectEntryBytes(name, bytes));
	}

	static byte[] getBytes(InputStream in) throws IOException {
		byte[] classbytes;
		int bytesread = 0;
		int readcount;
		try {
			int length = 1024;
			classbytes = new byte[length];
			readloop: while (true) {
				for (; bytesread < length; bytesread += readcount) {
					readcount = in.read(classbytes, bytesread, length - bytesread);
					if (readcount <= 0) /* if we didn't read anything */
						break readloop; /* leave the loop */
				}
				byte[] oldbytes = classbytes;
				length += 1024;
				classbytes = new byte[length];
				System.arraycopy(oldbytes, 0, classbytes, 0, bytesread);
			}

			if (classbytes.length > bytesread) {
				byte[] oldbytes = classbytes;
				classbytes = new byte[bytesread];
				System.arraycopy(oldbytes, 0, classbytes, 0, bytesread);
			}
		} finally {
			try {
				in.close();
			} catch (IOException ee) {
				// nothing to do here
			}
		}
		return classbytes;
	}

	public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
		throw (E) e;
	}
}
