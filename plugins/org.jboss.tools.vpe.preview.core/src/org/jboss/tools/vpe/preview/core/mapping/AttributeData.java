/******************************************************************************* 
 * Copyright (c) 2007-2014 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.vpe.preview.core.mapping;

import org.w3c.dom.Attr;
import org.w3c.dom.Node;

/**
 * 
 * @author Sergey Dzmitrovich
 * 
 *         Keep information about output Attribute. Set up a correspondence
 *         source node and visual node
 * 
 */
public class AttributeData extends NodeData {

	/**
	 * some attributes can have a visual representation but have not a source
	 * representation
	 */
	private String attributeName;

	public AttributeData(Attr attr, Node visualNode, boolean editable) {
		super(attr, visualNode, editable);
	}

	public AttributeData(String attributeName, Node visualNode,
			boolean editable) {

		super(null, visualNode, editable);

		// initialize attributeName field
		this.attributeName = attributeName;

	}

	public AttributeData(String attributeName, Node visualNode) {

		super(null, visualNode, true);

		// initialize attributeName field
		this.attributeName = attributeName;

	}

	public String getAttributeName() {
		return attributeName;
	}

	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

	@Override
	public int getType() {
		return ATTRIBUTE;
	}
}
