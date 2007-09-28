/*******************************************************************************
 * Copyright (c) 2007 Exadel, Inc. and Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Exadel, Inc. and Red Hat, Inc. - initial API and implementation
 ******************************************************************************/ 
package org.jboss.tools.vpe.editor.template.expression;

import org.jboss.tools.vpe.VpePlugin;
import org.jboss.tools.vpe.editor.context.VpePageContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class VpeAttributeOperand extends VpeOperand {
	private String name;
	private boolean caseSensitive;

	VpeAttributeOperand(String name, boolean caseSensitive) {
		this.name = name;
		this.caseSensitive = caseSensitive;
	}
	
	int getPriority() {
		return PRIORITY_OPERAND;
	}

	public VpeValue exec(VpePageContext pageContext, Node sourceNode) {
		String value = null; 

		try {
			value = ((Element)sourceNode).getAttribute(name);
			if (value == null) {
				value = "";
			}
		} catch (Exception e) {
//			throw new VpeExpressionError(x.getMessage());
			VpePlugin.getPluginLog().logError(e);
		}
		return new VpeValue((value == null ? "" : value));
	}
	
	public String getAttributeName() {
		return name;
	}
}
