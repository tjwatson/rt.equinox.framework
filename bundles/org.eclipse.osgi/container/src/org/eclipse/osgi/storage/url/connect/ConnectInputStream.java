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
package org.eclipse.osgi.storage.url.connect;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.osgi.storage.url.ContentProvider;
import org.eclipse.osgi.storage.url.ContentProviderType;

public class ConnectInputStream extends InputStream implements ContentProvider {

	/* This method should not be called.
	 */
	@Override
	public int read() throws IOException {
		throw new IOException();
	}

	public File getContent() {
		return null;
	}

	@Override
	public ContentProviderType getContentProviderType() {
		return ContentProviderType.CONNECT_INPUTSTREAM;
	}

}
