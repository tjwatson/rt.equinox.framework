/*******************************************************************************
 * Copyright (c) 2006, 2013 IBM Corporation and others.
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
import java.util.ArrayList;
import org.eclipse.osgi.framework.util.KeyedElement;
import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.internal.hookregistry.ClassLoaderHook;
import org.eclipse.osgi.internal.loader.classpath.*;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.osgi.framework.wiring.BundleRevision;

public class DevClassLoadingHook extends ClassLoaderHook implements KeyedElement {
	public static final String KEY = DevClassLoadingHook.class.getName();
	public static final int HASHCODE = KEY.hashCode();
	private static final String FRAGMENT = "@fragment@"; //$NON-NLS-1$

	private final EquinoxConfiguration configuration;

	public DevClassLoadingHook(EquinoxConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public boolean addClassPathEntry(ArrayList<ClasspathEntry> cpEntries, String cp, ClasspathManager hostmanager, Generation sourceGeneration) {
		// first check that we are in devmode for this sourcedata
		String[] devClassPath = !configuration.inDevelopmentMode() ? null : configuration.getDevClassPath(sourceGeneration.getRevision().getSymbolicName());
		if (devClassPath == null || devClassPath.length == 0)
			return false; // not in dev mode return
		// check that dev classpath entries have not already been added; we mark this in the first entry below
		if (cpEntries.size() > 0 && cpEntries.get(0).getUserObject(KEY) != null)
			return false; // this source has already had its dev classpath entries added.
		boolean result = false;
		for (int i = 0; i < devClassPath.length; i++) {
			BundleFile bundleFile = sourceGeneration.getBundleFile();
			// If the bundle file is a directory and contains the classpath as an
			// entry, don't add a classpath entry. The bundle file will do the work
			// itself. See bug 411877.
			if (!isClasspathEntryRequired(cp, sourceGeneration, devClassPath[i]))
				continue;
			if (hostmanager.addClassPathEntry(cpEntries, devClassPath[i], hostmanager, sourceGeneration))
				result = true;
			else {
				String devCP = devClassPath[i];
				boolean fromFragment = devCP.endsWith(FRAGMENT);
				if (!fromFragment && devCP.indexOf("..") >= 0) { //$NON-NLS-1$
					// if in dev mode, try using cp as a relative path from the base bundle file
					File base = bundleFile.getBaseFile();
					if (base.isDirectory()) {
						// this is only supported for directory bundles
						ClasspathEntry entry = hostmanager.getExternalClassPath(new File(base, devCP).getAbsolutePath(), sourceGeneration);
						if (entry != null) {
							cpEntries.add(entry);
							result = true;
						}
					}
				} else {
					// if in dev mode, try using the cp as an absolute path
					// we assume absolute entries come from fragments.  Find the source
					if (fromFragment)
						devCP = devCP.substring(0, devCP.length() - FRAGMENT.length());
					Generation fragSource = findFragmentSource(sourceGeneration, devCP, hostmanager, fromFragment);
					if (fragSource != null) {
						ClasspathEntry entry = hostmanager.getExternalClassPath(devCP, fragSource);
						if (entry != null) {
							cpEntries.add(entry);
							result = true;
						}
					}
				}
			}
		}
		// mark the first entry of the list.  
		// This way we can quickly tell that dev classpath entries have been added to the list
		if (result && cpEntries.size() > 0)
			cpEntries.get(0).addUserObject(this);
		return result;
	}

	private Generation findFragmentSource(Generation hostGeneration, String cp, ClasspathManager manager, boolean fromFragment) {
		if (hostGeneration != manager.getGeneration())
			return hostGeneration;

		File file = new File(cp);
		if (!file.isAbsolute())
			return hostGeneration;
		FragmentClasspath[] fragCP = manager.getFragmentClasspaths();
		for (int i = 0; i < fragCP.length; i++) {
			BundleFile fragBase = fragCP[i].getGeneration().getBundleFile();
			File fragFile = fragBase.getBaseFile();
			if (fragFile != null && file.getPath().startsWith(fragFile.getPath()))
				return fragCP[i].getGeneration();
		}
		return fromFragment ? null : hostGeneration;
	}

	public boolean compare(KeyedElement other) {
		return other.getKey() == KEY;
	}

	public Object getKey() {
		return KEY;
	}

	public int getKeyHashCode() {
		return HASHCODE;
	}

	private boolean isClasspathEntryRequired(String classpath, Generation generation, String devClasspath) {
		if (!generation.getBundleFile().getBaseFile().isDirectory())
			return true;
		if (devClasspath.contains("..")) //$NON-NLS-1$
			return true;
		if (!isPathOnDevelopmentClasspath(classpath, generation) && !DevBundleFileWrapper.isRoot(classpath))
			return true;
		return false;
	}

	private boolean isPathOnDevelopmentClasspath(String path, Generation generation) {
		BundleRevision revision = generation.getRevision();
		return DevBundleFileWrapper.findMatchingPathOnDevelopmentClasspath(path, configuration.getDevClassPath(revision.getSymbolicName()), generation.getBundleFile()) != null;
	}
}
