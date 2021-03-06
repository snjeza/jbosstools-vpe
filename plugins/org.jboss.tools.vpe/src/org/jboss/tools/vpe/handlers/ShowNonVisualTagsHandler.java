/*******************************************************************************
 * Copyright (c) 2007-2010 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package org.jboss.tools.vpe.handlers;

import org.jboss.tools.jst.web.ui.internal.editor.preferences.IVpePreferencesPage;
import org.jboss.tools.vpe.editor.VpeController;

/**
 * Handler for ShowNonVisualTags
 */
public class ShowNonVisualTagsHandler extends ShowOptionAbstractHandler {
	public static final String COMMAND_ID = "org.jboss.tools.vpe.commands.showNonVisualTagsCommand"; //$NON-NLS-1$

	@Override
	protected void toogleShow(VpeController vpeController,
			boolean state) {
		vpeController.getVisualBuilder().setShowInvisibleTags(state);
	}

	@Override
	public String getPreferenceKey() {
		return IVpePreferencesPage.SHOW_NON_VISUAL_TAGS;
	}
}
