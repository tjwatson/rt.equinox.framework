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
package org.eclipse.osgi.internal.connect;

import org.eclipse.osgi.internal.hookregistry.BundleFileWrapperFactoryHook;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.storage.bundlefile.BundleFileWrapper;

public class ConnectBundleFileFactory implements BundleFileWrapperFactoryHook {

	@Override
	public BundleFileWrapper wrapBundleFile(BundleFile connectBundleFile, Generation generation, boolean base) {
		return new ConnectBundleFileWrapper(connectBundleFile);
	}

	public static class ConnectBundleFileWrapper extends BundleFileWrapper {

		public ConnectBundleFileWrapper(BundleFile connectBundleFile) {
			super(connectBundleFile);
		}

		ConnectBundleFile getConnectBundleFile() {
			return (ConnectBundleFile) getBundleFile();
		}
	}
}
