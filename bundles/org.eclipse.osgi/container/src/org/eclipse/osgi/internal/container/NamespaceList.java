/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
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
package org.eclipse.osgi.internal.container;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import org.eclipse.osgi.container.ModuleCapability;
import org.eclipse.osgi.container.ModuleRequirement;
import org.eclipse.osgi.container.ModuleWire;

/**
 * An immutable list of elements for which each element has a name space. All
 * elements are kept in a single list to avoid creating a list for each name
 * space. The elements provided at construction are assumed to be ordered such
 * that all elements with the same name space are ordered together in one
 * continuous sequence An indexes list is used to keep track of the beginning
 * indexes for each namespace contained in the list. Assuming the number of
 * namespaces used by a bundle is relatively small compared to the overall
 * number of elements this makes it faster to find the first index for a
 * specific namespace without having to iterate over the complete list of
 * elements.
 * 
 * @param <E> The element type which will have a name space associated with it
 */
public class NamespaceList<E> {

	public final static Function<ModuleWire, String> WIRE = wire -> {
		return wire.getCapability().getNamespace();
	};
	public final static Function<ModuleCapability, String> CAPABILITY = ModuleCapability::getNamespace;
	public final static Function<ModuleRequirement, String> REQUIREMENT = ModuleRequirement::getNamespace;

	private final List<E> elements;
	private final List<Integer> indexes;
	private final Function<E, String> getNamespace;

	/**
	 * Create a new name space list with the specified elements. The elements must
	 * be sorted properly by name space.
	 * 
	 * @param elements     the ordered list of elements
	 * @param getNamespace a function that retrieves the namespace for each element
	 */
	public NamespaceList(List<E> elements, Function<E, String> getNamespace) {
		this.elements = Collections.unmodifiableList(elements);
		this.getNamespace = getNamespace;
		int size = this.elements.size();
		String current = null;
		List<Integer> tmpIndexes = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			String namespace = getNamespace.apply(elements.get(i));
			if (!namespace.equals(current)) {
				current = namespace;
				tmpIndexes.add(i);
			}
		}
		this.indexes = Collections.unmodifiableList(tmpIndexes);
	}

	public boolean isEmpty() {
		return elements.isEmpty();
	}

	/**
	 * returns an unmodifiable list of elements with the specified name space. An
	 * empty list is returned if there are no elements with the specified namespace.
	 * A {@code null} namespace can be used to get all elements.
	 * 
	 * @param namespace the name space of the elements to return. May be
	 *                  {@code null}
	 * @return The list of elements found
	 */
	public List<E> getList(String namespace) {
		if (namespace == null) {
			return elements;
		}

		Entry<Integer, Integer> startEnd = getNamespaceIndex(namespace);
		if (startEnd == null) {
			return Collections.emptyList();
		}

		return elements.subList(startEnd.getKey(), startEnd.getValue());
	}

	/**
	 * Returns the beginning index (inclusively) and ending index (exclusively) or
	 * {@code null} if no element has the specified namespace
	 * 
	 * @param namespace the name space to find the indexes for
	 * @return indexes found for the namespace or {@code null} if no elements exist
	 *         with the name space.
	 */
	public Entry<Integer, Integer> getNamespaceIndex(String namespace) {
		int indexesSize = indexes.size();
		for (int i = 0; i < indexesSize; i++) {
			int candidateIndex = indexes.get(i);
			if (namespace.equals(getNamespace.apply(elements.get(candidateIndex)))) {
				Integer start = candidateIndex;
				Integer end = i == indexesSize - 1 ? elements.size() : indexes.get(i + 1);
				return new AbstractMap.SimpleEntry<>(start, end);
			}
		}
		return null;
	}

	/**
	 * Returns a copy of all the elements in this list
	 * 
	 * @return a copy of all the elements in this list
	 */
	public List<E> copyList() {
		return new ArrayList<>(elements);
	}
}
