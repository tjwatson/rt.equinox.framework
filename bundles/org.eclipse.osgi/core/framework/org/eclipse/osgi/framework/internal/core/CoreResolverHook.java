/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.framework.internal.core;

import java.util.*;
import org.eclipse.osgi.framework.debug.Debug;
import org.eclipse.osgi.internal.serviceregistry.*;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.*;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.Capability;

/**
 * This class encapsulates the delegation to ResolverHooks that are registered with the service
 * registry.  This way the resolver implementation only has to call out to a single hook
 * which does all the necessary service registry lookups.
 * 
 * This class is not thread safe and expects external synchronization.
 *
 */
public class CoreResolverHook implements ResolverHook {
	// need a tuple to hold the service reference and hook object
	// do not use a map for performance reasons; no need to hash based on a key.
	static class HookReference {
		public HookReference(ServiceReferenceImpl<ResolverHook> reference, ResolverHook hook) {
			this.reference = reference;
			this.hook = hook;
		}

		final ServiceReferenceImpl<ResolverHook> reference;
		final ResolverHook hook;
	}

	private final BundleContextImpl context;
	private final ServiceRegistry registry;
	private final List<HookReference> hooks = new ArrayList<HookReference>(0);

	public CoreResolverHook(BundleContextImpl context, ServiceRegistry registry) {
		this.context = context;
		this.registry = registry;
	}

	private void handleHookException(Throwable t, ResolverHook hook, String method, Bundle hookBundle) {
		if (Debug.DEBUG_HOOKS) {
			Debug.println(hook.getClass().getName() + "." + method + "() exception: " + t.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			Debug.printStackTrace(t);
		}
		// allow the adaptor to handle this unexpected error
		context.framework.getAdaptor().handleRuntimeError(t);
		ServiceException se = new ServiceException(NLS.bind(Msg.SERVICE_FACTORY_EXCEPTION, hook.getClass().getName(), method), t);
		context.framework.publishFrameworkEvent(FrameworkEvent.ERROR, hookBundle, se);
	}

	private ServiceReferenceImpl<ResolverHook>[] getHookReferences() {
		try {
			@SuppressWarnings("unchecked")
			ServiceReferenceImpl<ResolverHook>[] result = (ServiceReferenceImpl<ResolverHook>[]) registry.getServiceReferences(context, ResolverHook.class.getName(), null, false, false);
			return result;
		} catch (InvalidSyntaxException e) {
			// cannot happen; no filter
			return null;
		}
	}

	public void begin() {
		if (Debug.DEBUG_HOOKS) {
			Debug.println("ResolverHook.begin"); //$NON-NLS-1$
		}
		ServiceReferenceImpl<ResolverHook>[] refs = getHookReferences();
		if (refs == null || refs.length == 0)
			return;
		for (ServiceReferenceImpl<ResolverHook> hookRef : refs) {
			ResolverHook hook = context.getService(hookRef);
			if (hook != null) {
				hooks.add(new HookReference(hookRef, hook));
				try {
					hook.begin();
				} catch (Throwable t) {
					handleHookException(t, hook, "begin", hookRef.getBundle()); //$NON-NLS-1$
				}
			}
		}
	}

	public void filterResolvable(Collection<BundleRevision> candidates) {
		if (Debug.DEBUG_HOOKS) {
			Debug.println("ResolverHook.filterResolvable(" + candidates + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (hooks.isEmpty())
			return;
		candidates = new ShrinkableCollection<BundleRevision>(candidates);
		for (Iterator<HookReference> iHooks = hooks.iterator(); iHooks.hasNext();) {
			HookReference hookRef = iHooks.next();
			if (hookRef.reference.getBundle() == null) {
				iHooks.remove();
			} else {
				try {
					hookRef.hook.filterResolvable(candidates);
				} catch (Throwable t) {
					handleHookException(t, hookRef.hook, "filterResolvable", hookRef.reference.getBundle()); //$NON-NLS-1$
				}
			}
		}
	}

	public void filterSingletonCollisions(Capability singleton, Collection<Capability> collisionCandidates) {
		if (Debug.DEBUG_HOOKS) {
			Debug.println("ResolverHook.filterSingletonCollisions(" + singleton + ", " + collisionCandidates + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		if (hooks.isEmpty())
			return;
		collisionCandidates = new ShrinkableCollection<Capability>(collisionCandidates);
		for (Iterator<HookReference> iHooks = hooks.iterator(); iHooks.hasNext();) {
			HookReference hookRef = iHooks.next();
			if (hookRef.reference.getBundle() == null) {
				iHooks.remove();
			} else {
				try {
					hookRef.hook.filterSingletonCollisions(singleton, collisionCandidates);
				} catch (Throwable t) {
					handleHookException(t, hookRef.hook, "filterSingletonCollisions", hookRef.reference.getBundle()); //$NON-NLS-1$
				}
			}
		}
	}

	public void filterMatches(BundleRevision requirer, Collection<Capability> candidates) {
		if (Debug.DEBUG_HOOKS) {
			Debug.println("ResolverHook.filterMatches(" + requirer + ", " + candidates + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		if (hooks.isEmpty())
			return;
		candidates = new ShrinkableCollection<Capability>(candidates);
		for (Iterator<HookReference> iHooks = hooks.iterator(); iHooks.hasNext();) {
			HookReference hookRef = iHooks.next();
			if (hookRef.reference.getBundle() == null) {
				iHooks.remove();
			} else {
				try {
					hookRef.hook.filterMatches(requirer, candidates);
				} catch (Throwable t) {
					handleHookException(t, hookRef.hook, "filterMatches", hookRef.reference.getBundle()); //$NON-NLS-1$
				}
			}
		}
	}

	public void end() {
		if (Debug.DEBUG_HOOKS) {
			Debug.println("ResolverHook.end"); //$NON-NLS-1$
		}
		if (hooks.isEmpty())
			return;
		for (Iterator<HookReference> iHooks = hooks.iterator(); iHooks.hasNext();) {
			HookReference hookRef = iHooks.next();
			// We do not remove unregistered services here because we are going to remove of of them at the end
			if (hookRef.reference.getBundle() != null) {
				try {
					hookRef.hook.end();
				} catch (Throwable t) {
					handleHookException(t, hookRef.hook, "end", hookRef.reference.getBundle()); //$NON-NLS-1$
				}
				context.ungetService(hookRef.reference);
			}
		}
		hooks.clear();
	}

}