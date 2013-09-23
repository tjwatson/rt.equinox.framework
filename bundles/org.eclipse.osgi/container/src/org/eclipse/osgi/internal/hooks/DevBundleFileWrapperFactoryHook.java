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

import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.internal.hookregistry.BundleFileWrapperFactoryHook;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.storage.bundlefile.BundleFileWrapper;

/*
 * Returns DevBundleFileWrapper objects for base bundle files that are
 * directories when in development mode. Ideally, this hook should not be
 * registered if not in development mode.
 */
public class DevBundleFileWrapperFactoryHook implements BundleFileWrapperFactoryHook {
	private EquinoxConfiguration configuration;

	public DevBundleFileWrapperFactoryHook(EquinoxConfiguration configuration) {
		if (configuration == null)
			throw new NullPointerException();
		this.configuration = configuration;
	}

	@Override
	public BundleFileWrapper wrapBundleFile(BundleFile bundleFile, Generation generation, boolean base) {
		if (base && configuration.inDevelopmentMode() && bundleFile.getBaseFile().isDirectory())
			return new DevBundleFileWrapper(bundleFile, generation, configuration);
		return null;
	}
}
