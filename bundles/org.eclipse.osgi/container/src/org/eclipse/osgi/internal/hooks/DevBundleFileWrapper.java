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
package org.eclipse.osgi.internal.hooks;

import java.io.File;
import java.util.*;
import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.*;
import org.osgi.framework.wiring.BundleRevision;

/*
 * BundleFileWrapper.getFile(String, boolean) is not overridden because only
 * the base bundle file should be called for this method, which the superclass
 * already does. 
 */
public class DevBundleFileWrapper extends BundleFileWrapper {
	private final EquinoxConfiguration configuration;
	private final Generation generation;

	private volatile Collection<String> devClasspath;
	private volatile Collection<NestedDirBundleFile> nested;

	public DevBundleFileWrapper(BundleFile bundleFile, Generation generation, EquinoxConfiguration configuration) {
		super(bundleFile);
		if (generation == null || configuration == null)
			throw new NullPointerException();
		this.generation = generation;
		this.configuration = configuration;

	}

	@Override
	public boolean containsDir(String dir) {
		if (!isRootOnBundleClasspath())
			return getBundleFile().containsDir(dir);
		if (getBundleFile().containsDir(dir))
			return true;
		for (NestedDirBundleFile bundleFile : getNestedDirBundleFiles())
			if (bundleFile.containsDir(dir))
				return true;
		return false;
	}

	@Override
	public BundleEntry getEntry(String path) {
		if (!isRootOnBundleClasspath()) {
			if (isPathOnDevelopmentClasspath(path))
				return null;
			return getBundleFile().getEntry(path);
		}
		if (isPathOnDevelopmentClasspath(path))
			return getEntryFromNestedDirBundleFiles(path);
		return getEntryFromBundleFile(path);
	}

	@Override
	public Enumeration<String> getEntryPaths(String path, boolean recurse) {
		Collection<String> result = new LinkedHashSet<String>();
		if (!isRootOnBundleClasspath()) {
			if (!isPathOnDevelopmentClasspath(path))
				addEntryPathsFromBundleFile(path, recurse, result);
		} else if (isPathOnDevelopmentClasspath(path)) {
			addEntryPathsFromNestedDirBundleFiles(path, recurse, result);
		} else {
			addEntryPathsFromBundleFile(path, recurse, result);
			addEntryPathsFromNestedDirBundleFiles(path, recurse, result);
		}
		return result.isEmpty() ? null : Collections.enumeration(result);
	}

	@Override
	public File getFile(String path, boolean nativeCode) {
		return getBundleFile().getFile(path, nativeCode);
	}

	private Collection<NestedDirBundleFile> getNestedDirBundleFiles() {
		Collection<NestedDirBundleFile> result = nested;
		if (result == null) {
			synchronized (this) {
				if (result == null) {
					Collection<String> ss = getDevClasspath();
					if (ss == null)
						return Collections.emptyList();
					result = nested = new HashSet<NestedDirBundleFile>(ss.size());
					for (String s : ss)
						// Don't pass 'this' to NestedDirBundleFile or an
						// infinite loop results.
						result.add(new NestedDirBundleFile(getBundleFile(), s));
				}
			}
		}
		return result;
	}

	private Collection<String> getDevClasspath() {
		Collection<String> result = devClasspath;
		if (result == null) {
			synchronized (this) {
				if (result == null) {
					BundleRevision revision = generation.getRevision();
					if (revision == null)
						return null;
					String[] ss = configuration.getDevClassPath(revision.getSymbolicName());
					if (ss == null || ss.length == 0)
						result = devClasspath = Collections.emptyList();
					else {
						result = devClasspath = new LinkedHashSet<String>(ss.length);
						for (String s : ss)
							if (!s.contains("..")) //$NON-NLS-1$
								result.add(s);
					}
				}
			}
		}
		return result;
	}

	private BundleEntry getEntryFromBundleFile(String path) {
		return getBundleFile().getEntry(path);
	}

	private BundleEntry getEntryFromNestedDirBundleFiles(String path) {
		for (NestedDirBundleFile f : getNestedDirBundleFiles()) {
			BundleEntry entry = f.getEntry(path);
			if (entry != null)
				return entry;
		}
		return null;
	}

	private void addEntryPathsFromBundleFile(String path, boolean recurse, Collection<String> entryPaths) {
		Enumeration<String> e = getBundleFile().getEntryPaths(path, recurse);
		if (e == null)
			return;
		while (e.hasMoreElements()) {
			String s = strip(e.nextElement());
			if (s.length() > 0)
				entryPaths.add(s);
		}
	}

	private void addEntryPathsFromNestedDirBundleFiles(String path, boolean recurse, Collection<String> entryPaths) {
		for (NestedDirBundleFile f : getNestedDirBundleFiles()) {
			Enumeration<String> e = f.getEntryPaths(path, recurse);
			if (e != null)
				entryPaths.addAll(Collections.list(e));
		}
	}

	private String strip(String path) {
		for (String s : getDevClasspath())
			if (path.startsWith(s))
				return path.substring(s.length() + 1);
		return path;
	}

	private boolean isRootOnBundleClasspath() {
		BundleRevision revision = generation.getRevision();
		if (revision == null)
			return true;
		return Arrays.asList(ClasspathManager.getClassPath(revision)).contains("."); //$NON-NLS-1$
	}

	private String findMatchingPathOnDevelopmentClasspath(String pathToMatch) {
		return findMatchingPathOnDevelopmentClasspath(pathToMatch, getDevClasspath(), getBundleFile());
	}

	private boolean isPathOnDevelopmentClasspath(String path) {
		return findMatchingPathOnDevelopmentClasspath(path) != null;
	}

	static boolean isRoot(String path) {
		return "/".equals(path) || ".".equals(path); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String findMatchingPathOnDevelopmentClasspath(String pathToMatch, Collection<String> devClasspath, BundleFile bundleFile) {
		return findMatchingPathOnDevelopmentClasspath(pathToMatch, devClasspath == null ? new String[0] : devClasspath.toArray(new String[devClasspath.size()]), bundleFile);
	}

	static String findMatchingPathOnDevelopmentClasspath(String pathToMatch, String[] devClasspath, BundleFile bundleFile) {
		if (isRoot(pathToMatch))
			return null;
		// For each element on the development classpath...
		for (String path : devClasspath) {
			// ...see if the bundle file has a matching entry...
			BundleEntry entry = bundleFile.getEntry(path);
			if (entry == null)
				// ...if not, then the element is of no interest.
				continue;
			// ...if so, then see if the entry contains the given path...
			String name = entry.getName();
			if (pathToMatch.startsWith(name) || name.startsWith(pathToMatch))
				// ...if it does, we have our match.
				return name;
			// ...if prepending the path to match with the entry name find an
			// entry, then we have our match.
			if (bundleFile.getEntry(name + pathToMatch) != null)
				return name;
		}
		return null;
	}
}
