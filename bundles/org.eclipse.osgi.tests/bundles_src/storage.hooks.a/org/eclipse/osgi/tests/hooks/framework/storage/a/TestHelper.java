/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
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
package org.eclipse.osgi.tests.hooks.framework.storage.a;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public class TestHelper implements FrameworkUtil.Helper {
	final Bundle testBundle;

	public TestHelper(Bundle testBundle) {
		this.testBundle = testBundle;
	}

	@Override
	public Bundle getBundle(Class<?> classFromBundle) {
		return testBundle;
	}
}
