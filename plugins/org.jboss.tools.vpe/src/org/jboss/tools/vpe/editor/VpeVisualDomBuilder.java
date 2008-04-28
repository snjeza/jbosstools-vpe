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
package org.jboss.tools.vpe.editor;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.wst.sse.core.internal.provisional.INodeAdapter;
import org.eclipse.wst.sse.core.internal.provisional.INodeNotifier;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.xml.core.internal.document.ElementImpl;
import org.eclipse.wst.xml.core.internal.document.NodeImpl;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMElement;
import org.jboss.tools.common.model.XModel;
import org.jboss.tools.common.model.XModelObject;
import org.jboss.tools.common.model.project.IModelNature;
import org.jboss.tools.common.model.util.EclipseResourceUtil;
import org.jboss.tools.jst.jsp.preferences.VpePreference;
import org.jboss.tools.jst.web.model.helpers.WebAppHelper;
import org.jboss.tools.vpe.VpeDebug;
import org.jboss.tools.vpe.VpePlugin;
import org.jboss.tools.vpe.dnd.VpeDnD;
import org.jboss.tools.vpe.editor.bundle.BundleMap;
import org.jboss.tools.vpe.editor.context.VpePageContext;
import org.jboss.tools.vpe.editor.css.CSSReferenceList;
import org.jboss.tools.vpe.editor.css.ResourceReference;
import org.jboss.tools.vpe.editor.mapping.VpeDomMapping;
import org.jboss.tools.vpe.editor.mapping.VpeElementMapping;
import org.jboss.tools.vpe.editor.mapping.VpeNodeMapping;
import org.jboss.tools.vpe.editor.mozilla.MozillaEditor;
import org.jboss.tools.vpe.editor.template.VpeChildrenInfo;
import org.jboss.tools.vpe.editor.template.VpeCreationData;
import org.jboss.tools.vpe.editor.template.VpeCreatorUtil;
import org.jboss.tools.vpe.editor.template.VpeDefaultPseudoContentCreator;
import org.jboss.tools.vpe.editor.template.VpeHtmlTemplate;
import org.jboss.tools.vpe.editor.template.VpeTagDescription;
import org.jboss.tools.vpe.editor.template.VpeTemplate;
import org.jboss.tools.vpe.editor.template.VpeTemplateManager;
import org.jboss.tools.vpe.editor.template.VpeToggableTemplate;
import org.jboss.tools.vpe.editor.template.dnd.VpeDnd;
import org.jboss.tools.vpe.editor.util.HTML;
import org.jboss.tools.vpe.editor.util.TextUtil;
import org.jboss.tools.vpe.editor.util.VisualDomUtil;
import org.jboss.tools.vpe.editor.util.VpeStyleUtil;
import org.jboss.tools.vpe.xulrunner.editor.XulRunnerEditor;
import org.jboss.tools.vpe.xulrunner.editor.XulRunnerVpeUtils;
import org.mozilla.interfaces.nsIDOMAttr;
import org.mozilla.interfaces.nsIDOMDocument;
import org.mozilla.interfaces.nsIDOMElement;
import org.mozilla.interfaces.nsIDOMHTMLInputElement;
import org.mozilla.interfaces.nsIDOMMouseEvent;
import org.mozilla.interfaces.nsIDOMNode;
import org.mozilla.interfaces.nsIDOMNodeList;
import org.mozilla.interfaces.nsIDOMRange;
import org.mozilla.interfaces.nsIDOMText;
import org.mozilla.xpcom.XPCOMException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VpeVisualDomBuilder extends VpeDomBuilder {

	public static final String VPE_USER_TOGGLE_ID = "vpe-user-toggle-id"; //$NON-NLS-1$
	public static final String VPE_USER_TOGGLE_LOOKUP_PARENT = "vpe-user-toggle-lookup-parent"; //$NON-NLS-1$

	/** REGEX_EL */
    private static final Pattern REGEX_EL = Pattern.compile(
	    "[\\$|\\#]\\{.*\\}", Pattern.MULTILINE + Pattern.DOTALL); //$NON-NLS-1$
    
    private static final String PSEUDO_ELEMENT = "br"; //$NON-NLS-1$
    private static final String PSEUDO_ELEMENT_ATTR = "vpe:pseudo-element"; //$NON-NLS-1$
    private static final String INIT_ELEMENT_ATTR = "vpe:init-element"; //$NON-NLS-1$
    private static final String MOZ_ANONCLASS_ATTR = "_MOZ_ANONCLASS"; //$NON-NLS-1$
    private static final String COMMENT_STYLE = "font-style:italic; color:green"; //$NON-NLS-1$
    private static final String COMMENT_PREFIX = ""; //$NON-NLS-1$
    private static final String COMMENT_SUFFIX = ""; //$NON-NLS-1$
    private static final String INCLUDE_ELEMENT_ATTR = "vpe:include-element"; //$NON-NLS-1$
    private static final int DRAG_AREA_WIDTH = 10;
    private static final int DRAG_AREA_HEIGHT = 10;
    private static final String ATTR_DRAG_AVAILABLE_CLASS = "__drag__available_style"; //$NON-NLS-1$
	private static String DOTTED_BORDER = "border: 1px dotted #FF6600; padding: 5px;"; //$NON-NLS-1$

    private MozillaEditor visualEditor;
    private XulRunnerEditor xulRunnerEditor;
    private nsIDOMDocument visualDocument;
    private nsIDOMElement visualContentArea;
    private VpePageContext pageContext;
    private VpeDnD dnd;
    private nsIDOMNode headNode;
    private List includeStack;
    // TODO Max Areshkau JBIDE-1457
    // boolean rebuildFlag = false;

    /** faceletFile */
    private boolean faceletFile = false;

    private static final String ATTR_VPE = "vpe"; //$NON-NLS-1$
    private static final String ATTR_VPE_INLINE_LINK_VALUE = "inlinelink"; //$NON-NLS-1$

    private static final String ATTR_REL_STYLESHEET_VALUE = "stylesheet"; //$NON-NLS-1$

    private static final String YES_STRING = "yes"; //$NON-NLS-1$
    private static final String ZERO_STRING = "0"; //$NON-NLS-1$
    private static final String EMPTY_STRING = ""; //$NON-NLS-1$

    private static final String ATRIBUTE_BORDER = "border"; //$NON-NLS-1$
    private static final String ATRIBUTE_CELLSPACING = "cellspacing"; //$NON-NLS-1$
    private static final String ATRIBUTE_CELLPADDING = "cellpadding"; //$NON-NLS-1$

    private static final String DOTTED_BORDER_STYLE = "border : 1px dotted #808080"; //$NON-NLS-1$
    private static final String DOTTED_BORDER_STYLE_FOR_IMG = "1px dotted #808080"; //$NON-NLS-1$
    private static final String DOTTED_BORDER_STYLE_FOR_TD = "border-left : 1px dotted #808080; border-right : 1px dotted #808080; border-top : 1px dotted #808080; border-bottom : 0px; color:#0051DD; background-color:#ECF3FF; padding-left: 3px;  padding-right: 3px;  line-height : 10px; font-family : arial; font-size : 10px; text-align:top; margin : 1px; -moz-user-modify : read-only"; //$NON-NLS-1$
    private static final String DOTTED_BORDER_STYLE_FOR_SPAN = "border : 1px solid #0051DD; color:#0051DD; background-color:#ECF3FF; padding-left: 3px;  padding-right: 3px;  line-height : 10px; font-family : arial; font-size : 10px; text-align:top; margin : 1px; -moz-user-modify : read-only"; //$NON-NLS-1$

    static private HashSet<String> unborderedSourceNodes = new HashSet<String>();
    static {
	unborderedSourceNodes.add(HTML.TAG_HTML);
	unborderedSourceNodes.add(HTML.TAG_HEAD);
	unborderedSourceNodes.add(HTML.TAG_BODY);
    }

    static private HashSet<String> unborderedVisualNodes = new HashSet<String>();
    static {
	unborderedVisualNodes.add(HTML.TAG_TBODY);
	unborderedVisualNodes.add(HTML.TAG_THEAD);
	unborderedVisualNodes.add(HTML.TAG_TR);
	unborderedVisualNodes.add(HTML.TAG_TD);
	unborderedVisualNodes.add(HTML.TAG_COL);
	unborderedVisualNodes.add(HTML.TAG_COLS);
	unborderedVisualNodes.add(HTML.TAG_COLGROUP);
	unborderedVisualNodes.add(HTML.TAG_LI);
	unborderedVisualNodes.add(HTML.TAG_BR);
    }
    private VpeDnd dropper;

    private Map<IFile, Document> includeDocuments = new HashMap<IFile, Document>();

    
    /**
	 * facelet elements, if there are these elements on a page then other
	 * elements are deleted
	 */
	static private HashSet<String> faceletRootElements = new HashSet<String>();

	static {
		faceletRootElements.add("composition"); //$NON-NLS-1$
		faceletRootElements.add("component"); //$NON-NLS-1$
	}
    
    public VpeVisualDomBuilder(VpeDomMapping domMapping,
	    INodeAdapter sorceAdapter, VpeTemplateManager templateManager,
	    MozillaEditor visualEditor, VpePageContext pageContext) {
	super(domMapping, sorceAdapter, templateManager);
	this.visualEditor = visualEditor;
	xulRunnerEditor = visualEditor.getXulRunnerEditor();
	this.visualDocument = visualEditor.getDomDocument();
	this.visualContentArea = visualEditor.getContentArea();
	this.dnd = new VpeDnD();
	this.pageContext = pageContext;
	this.headNode = visualEditor.getHeadNode();
	dropper = new VpeDnd();
	dropper.setDndData(false, true);

	if (isFacelet()) {
	    faceletFile = true;
	} else {
	    faceletFile = false;
	}

    }

    public void buildDom(Document sourceDocument) {
		VpeSourceDomBuilder sourceBuilder = pageContext.getSourceBuilder();
		IDocument document = sourceBuilder.getStructuredTextViewer()
				.getDocument();
		if (document == null)
			return;
		includeStack = new ArrayList();
		IEditorInput input = pageContext.getEditPart().getEditorInput();
		if (input instanceof IFileEditorInput) {
			IFile file = ((IFileEditorInput) input).getFile();
			if (file != null) {
				includeStack.add(new VpeIncludeInfo(null, file, pageContext
						.getSourceBuilder().getSourceDocument()));
			}
		}
		pageContext.refreshConnector();
		pageContext.installIncludeElements();
		if (isFacelet()) {
			Element root = getRootElement(sourceDocument);
			if(root != null)
			{
				addNode(root, null, visualContentArea);
			}
		} else {
			addChildren(null, sourceDocument, visualContentArea);
		}
		/*
		 * Fixes http://jira.jboss.com/jira/browse/JBIDE-2126.
		 * To provide appropriate context menu functionality
		 * visual content area should be mapped in any case. 
		 */
		registerNodes(new VpeNodeMapping(sourceDocument, visualContentArea));
	}

    private Element getRootElement(Document sourceDocument) {

		NodeList nodeList = sourceDocument.getChildNodes();
		Element root = null;

		for (int i = 0; i < nodeList.getLength(); i++) {
			Node child = nodeList.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				root = (Element) child;
				break;
			}
		}

		if (root != null) {
			Element trimmedElement = findFaceletRootElement(root);
			if (trimmedElement != null)
				root = trimmedElement;
		}
		return root;
	}

	private Element findFaceletRootElement(Element element) {

		NodeList children = element.getChildNodes();

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {

				Element trimmedElement = findFaceletRootElement((Element) child);
				if (trimmedElement != null)
					return trimmedElement;

			}
		}

		if (faceletRootElements.contains(element.getLocalName()))
			return element;
		return null;
	}
    
    public void rebuildDom(Document sourceDocument) {
	// clearIncludeDocuments();
	cleanHead();
	domMapping.clear(visualContentArea);
	super.dispose();

	pageContext.clearAll();
	refreshExternalLinks();
	pageContext.getBundle().refreshRegisteredBundles();

	nsIDOMNodeList children = visualContentArea.getChildNodes();
	long len = children.getLength();
	for (long i = len - 1; i >= 0; i--) {
	    visualContentArea.removeChild(children.item(i));
	}

	if (sourceDocument != null) {
	    buildDom(sourceDocument);
	}

    }

    // temporary, will be change to prefference's variable
    // private boolean borderVisible = true;
    
    private boolean addNode(Node sourceNode, nsIDOMNode visualNextNode,
	    nsIDOMNode visualContainer) {
    	
	nsIDOMNode visualNewNode = createNode(sourceNode, visualContainer);
	
	// Fix for JBIDE-1097
	try {
	    if (visualNewNode != null) {
		nsIDOMHTMLInputElement iDOMInputElement = (nsIDOMHTMLInputElement) visualNewNode
			.queryInterface(nsIDOMHTMLInputElement.NS_IDOMHTMLINPUTELEMENT_IID);
		iDOMInputElement.setReadOnly(true);
	    }
	} catch (XPCOMException ex) {
	    // just ignore this exception
	}
	if (visualNewNode != null) {
	    if (visualNextNode == null) {
		visualContainer.appendChild(visualNewNode);
	    } else {
		visualContainer.insertBefore(visualNewNode, visualNextNode);
	    }
	    return true;
	}

	return false;
    }

    private nsIDOMElement createBorder(Node sourceNode,
	    nsIDOMElement visualNode, boolean block) {
	nsIDOMElement border = null;
	if (visualNode == null)
	    return null;
	if (unborderedSourceNodes.contains(sourceNode.getNodeName()
		.toLowerCase()))
	    return null;
	if (unborderedVisualNodes.contains(visualNode.getNodeName()
		.toLowerCase()))
	    return null;
	if (HTML.TAG_IMG.equalsIgnoreCase(visualNode.getNodeName())) {
	    String width = visualNode.getAttribute(ATRIBUTE_BORDER);
	    if (width == null || ZERO_STRING.equalsIgnoreCase(width)
		    || EMPTY_STRING.equalsIgnoreCase(width)) {
		String style = visualNode
			.getAttribute(VpeStyleUtil.ATTRIBUTE_STYLE);
		style = VpeStyleUtil.setParameterInStyle(style,
			ATRIBUTE_BORDER, DOTTED_BORDER_STYLE_FOR_IMG);
		visualNode.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE, style);
	    }
	    return null;
	}
	if (block) {
	    if (YES_STRING.equals(VpePreference.USE_DETAIL_BORDER.getValue())) {
		border = visualDocument.createElement(HTML.TAG_TABLE);
		border.setAttribute(ATRIBUTE_CELLSPACING, ZERO_STRING);
		border.setAttribute(ATRIBUTE_CELLPADDING, ZERO_STRING);

		nsIDOMElement tr1 = visualDocument.createElement(HTML.TAG_TR);
		border.appendChild(tr1);
		nsIDOMElement td1 = visualDocument.createElement(HTML.TAG_TD);
		td1.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE,
			DOTTED_BORDER_STYLE_FOR_TD);
		nsIDOMText text = visualDocument.createTextNode(sourceNode
			.getNodeName());
		td1.appendChild(text);
		tr1.appendChild(td1);
		nsIDOMElement tr2 = visualDocument.createElement(HTML.TAG_TR);
		border.appendChild(tr2);
		nsIDOMElement td2 = visualDocument.createElement(HTML.TAG_TD);
		tr2.appendChild(td2);
		nsIDOMElement p = visualDocument.createElement(HTML.TAG_P);
		p.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE,
			DOTTED_BORDER_STYLE);
		td2.appendChild(p);

		p.appendChild(visualNode);

	    } else {
		border = visualDocument.createElement(HTML.TAG_TABLE);
		border.setAttribute(ATRIBUTE_CELLSPACING, ZERO_STRING);
		border.setAttribute(ATRIBUTE_CELLPADDING, ZERO_STRING);

		nsIDOMElement tr2 = visualDocument.createElement(HTML.TAG_TR);
		border.appendChild(tr2);
		nsIDOMElement td2 = visualDocument.createElement(HTML.TAG_TD);
		tr2.appendChild(td2);
		nsIDOMElement p = visualDocument.createElement(HTML.TAG_P);
		p.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE,
			DOTTED_BORDER_STYLE);
		td2.appendChild(p);

		p.appendChild(visualNode);
	    }
	} else {
	    border = visualDocument.createElement(HTML.TAG_SPAN);
	    border.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE,
		    DOTTED_BORDER_STYLE);
	    if (YES_STRING.equals(VpePreference.USE_DETAIL_BORDER.getValue())) {
		nsIDOMElement name = visualDocument
			.createElement(HTML.TAG_SPAN);
		name.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE,
			DOTTED_BORDER_STYLE_FOR_SPAN);
		nsIDOMText text = visualDocument.createTextNode(sourceNode
			.getNodeName());
		name.appendChild(text);
		border.appendChild(name);
	    }
	    border.appendChild(visualNode);
	}
	if (VpeStyleUtil.getAbsolute((Element) sourceNode) && border != null) {
	    int top = VpeStyleUtil.getSizeFromStyle((Element) sourceNode,
		    VpeStyleUtil.ATTRIBUTE_STYLE + VpeStyleUtil.DOT_STRING
			    + VpeStyleUtil.PARAMETER_TOP);
	    int left = VpeStyleUtil.getSizeFromStyle((Element) sourceNode,
		    VpeStyleUtil.ATTRIBUTE_STYLE + VpeStyleUtil.DOT_STRING
			    + VpeStyleUtil.PARAMETER_LEFT);

	    String style = visualNode
		    .getAttribute(VpeStyleUtil.ATTRIBUTE_STYLE);
	    style = VpeStyleUtil.deleteFromString(style,
		    VpeStyleUtil.PARAMETER_POSITION,
		    VpeStyleUtil.SEMICOLON_STRING);
	    style = VpeStyleUtil.deleteFromString(style,
		    VpeStyleUtil.PARAMETER_TOP, VpeStyleUtil.SEMICOLON_STRING);
	    style = VpeStyleUtil.deleteFromString(style,
		    VpeStyleUtil.PARAMETER_LEFT, VpeStyleUtil.SEMICOLON_STRING);
	    visualNode.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE, style);

	    style = border.getAttribute(VpeStyleUtil.ATTRIBUTE_STYLE);
	    style = VpeStyleUtil.setAbsolute(style);
	    if (top != -1)
		style = VpeStyleUtil.setSizeInStyle(style,
			VpeStyleUtil.PARAMETER_TOP, top);
	    if (left != -1)
		style = VpeStyleUtil.setSizeInStyle(style,
			VpeStyleUtil.PARAMETER_LEFT, left);
	    border.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE, style);
	}
	return border;
    }

    protected nsIDOMNode createNode(Node sourceNode,
	    nsIDOMNode visualOldContainer) {
	boolean registerFlag = isCurrentMainDocument();
	switch (sourceNode.getNodeType()) {
	case Node.ELEMENT_NODE:
	    // Map<?, ?> xmlnsMap = createXmlns((Element) sourceNode);
	    Set<Node> ifDependencySet = new HashSet<Node>();
	    pageContext.setCurrentVisualNode(visualOldContainer);
	    VpeTemplate template = templateManager.getTemplate(pageContext,
		    (Element) sourceNode, ifDependencySet);

	    VpeCreationData creationData = null;
	    // FIX FOR JBIDE-1568, added by Max Areshkau
	    try {
//	    	if(getPageContext().isCreationDataExistInCash(sourceNode)) {
	    		
//	    		creationData = getPageContext().getVpeCreationDataFromCash(sourceNode).createHashCopy();
//	    	} else {
	    		creationData = template.create(getPageContext(), sourceNode,
	    									getVisualDocument());
//	    		if(creationData.getNode()!=null) {
//	    			
//	    		getPageContext().addCreationDataToCash(sourceNode, creationData.createHashCopy());
//	    		
//	    		}
//	    	}
	    } catch (XPCOMException ex) {
		VpePlugin.getPluginLog().logError(ex);
		VpeTemplate defTemplate = templateManager.getDefTemplate();
		creationData = defTemplate.create(getPageContext(), sourceNode,
			getVisualDocument());
	    }
	    
	    pageContext.setCurrentVisualNode(null);
	    nsIDOMElement visualNewElement = null;

	    	if(creationData.getNode()!=null) {
	    	
	     visualNewElement = (nsIDOMElement) creationData
		    			.getNode().queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID);
	    	}
	    	
 		if ((visualNewElement != null) && template.hasImaginaryBorder()) {

				visualNewElement.setAttribute(HTML.ATTR_STYLE, visualNewElement
						.getAttribute(HTML.ATTR_STYLE)
						+ VpeStyleUtil.SEMICOLON_STRING + DOTTED_BORDER);

			}
	    
	    if (visualNewElement != null)
		correctVisualAttribute(visualNewElement);
	    


	    nsIDOMElement border = null;
	    setTooltip((Element) sourceNode, visualNewElement);
	    if (YES_STRING.equals(VpePreference.SHOW_BORDER_FOR_ALL_TAGS
		    .getValue())
		    && visualNewElement != null) {
		boolean block = true;
		if (template.getTagDescription(null, null, null,
			visualNewElement, null).getDisplayType() == VpeTagDescription.DISPLAY_TYPE_INLINE) {
		    block = false;
		}
		border = createBorder(sourceNode, visualNewElement, block);
	    }
	    if (!isCurrentMainDocument() && visualNewElement != null) {
		setReadOnlyElement(visualNewElement);
	    }
	    if (registerFlag) {
				VpeElementMapping elementMapping = new VpeElementMapping(
						(Element) sourceNode, visualNewElement, border,
						template, ifDependencySet, creationData.getData(),
						creationData.getElementData());
				// elementMapping.setXmlnsMap(xmlnsMap);
				registerNodes(elementMapping);
			}
	    if (template.isChildren()) {
		List<?> childrenInfoList = creationData.getChildrenInfoList();
		if (childrenInfoList == null) {
		    addChildren(template, sourceNode,
			    visualNewElement != null ? visualNewElement
				    : visualOldContainer);
		} else {
		    addChildren(template, sourceNode, visualOldContainer,
			    childrenInfoList);
		}
	    }
	    pageContext.setCurrentVisualNode(visualOldContainer);
	    template.validate(pageContext, (Element) sourceNode,
		    visualDocument, creationData);
	    pageContext.setCurrentVisualNode(null);
	    if (border != null)
		return border;
	    else
		return visualNewElement;
	case Node.TEXT_NODE:
	    return createTextNode(sourceNode, registerFlag);
	case Node.COMMENT_NODE:
	    if (!YES_STRING.equals(VpePreference.SHOW_COMMENTS.getValue())) {
		return null;
	    }
	    nsIDOMElement visualNewComment = createComment(sourceNode);
	    if (registerFlag) {
		registerNodes(new VpeNodeMapping(sourceNode, visualNewComment));
	    }
	    return visualNewComment;
	}
	return null;
    }

    private void correctVisualAttribute(nsIDOMElement element) {

	String styleValue = element.getAttribute(HTML.TAG_STYLE);
	String backgroundValue = element
		.getAttribute(VpeStyleUtil.PARAMETR_BACKGROND);

	if (styleValue != null) {
	    styleValue = VpeStyleUtil.addFullPathIntoURLValue(styleValue,
		    pageContext.getEditPart().getEditorInput());
	    element.setAttribute(HTML.TAG_STYLE, styleValue);
	}
	if (backgroundValue != null) {
	    backgroundValue = VpeStyleUtil
		    .addFullPathIntoBackgroundValue(backgroundValue,
			    pageContext.getEditPart().getEditorInput());
	    element.setAttribute(VpeStyleUtil.PARAMETR_BACKGROND,
		    backgroundValue);
	}
    }

    protected nsIDOMElement createComment(Node sourceNode) {
	nsIDOMElement div = visualDocument.createElement(HTML.TAG_DIV);
	div.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE, COMMENT_STYLE);
	String value = COMMENT_PREFIX + sourceNode.getNodeValue()
		+ COMMENT_SUFFIX;
	nsIDOMText text = visualDocument.createTextNode(value);
	div.appendChild(text);
	return div;
    }

    protected void addChildren(VpeTemplate containerTemplate,
			Node sourceContainer, nsIDOMNode visualContainer) {
    	
    	/*
		 * Fixes http://jira.jboss.com/jira/browse/JBIDE-1944
		 * author: Denis Maliarevich
		 * This method is called when template has no childrenInfoList.
		 * In this case h:dataTable and h:panelGrid should display pseudo text
		 */
    	if (containerTemplate instanceof VpeHtmlTemplate) {
			int type = ((VpeHtmlTemplate) containerTemplate).getType();
			if ((VpeHtmlTemplate.TYPE_DATATABLE == type)
					|| (VpeHtmlTemplate.TYPE_PANELGRID == type)) {
				setPseudoContent(containerTemplate, sourceContainer,
						visualContainer);
				return;
			}
		}
    	
		NodeList sourceNodes = sourceContainer.getChildNodes();
		int len = sourceNodes.getLength();
		int childrenCount = 0;
		for (int i = 0; i < len; i++) {
			Node sourceNode = sourceNodes.item(i);
			if (addNode(sourceNode, null, visualContainer)) {
				if (Node.ELEMENT_NODE == sourceNode.getNodeType()) {
				}
				childrenCount++;
			}
		}

		if (childrenCount == 0) {
			setPseudoContent(containerTemplate, sourceContainer,
					visualContainer);
		}
	}

    protected void addChildren(VpeTemplate containerTemplate,
	    Node sourceContainer, nsIDOMNode visualOldContainer,
	    List<?> childrenInfoList) {
	for (int i = 0; i < childrenInfoList.size(); i++) {
	    VpeChildrenInfo info = (VpeChildrenInfo) childrenInfoList.get(i);
	    nsIDOMNode visualParent = info.getVisualParent();
	    if (visualParent == null)
		visualParent = visualOldContainer;
	    List<?> sourceChildren = info.getSourceChildren();
	    int childrenCount = 0;
	    if (sourceChildren != null) {
		for (int j = 0; j < sourceChildren.size(); j++) {
		    if (addNode((Node) sourceChildren.get(j), null,
			    visualParent)) {
			childrenCount++;
		    }
		}
	    }
	    if (childrenCount == 0 && childrenInfoList.size() == 0) {
		setPseudoContent(containerTemplate, sourceContainer,
			visualParent);
	    }
	}
    }

    // /////////////////////////////////////////////////////////////////////////
    public nsIDOMNode addStyleNodeToHead(String styleText) {
	nsIDOMNode newStyle = visualDocument
		.createElement(VpeStyleUtil.ATTRIBUTE_STYLE);

	if (styleText != null) {
	    nsIDOMText newText = visualDocument.createTextNode(styleText);
	    newStyle.appendChild(newText);
	}
	headNode.appendChild(newStyle);
	return newStyle;
    }

    public nsIDOMNode replaceStyleNodeToHead(nsIDOMNode oldStyleNode,
	    String styleText) {
	nsIDOMElement newStyle = visualDocument
		.createElement(VpeStyleUtil.ATTRIBUTE_STYLE);

	if (styleText != null) {
	    nsIDOMNode newText = visualDocument.createTextNode(styleText);
	    newStyle.appendChild(newText);
	}

	headNode.replaceChild(newStyle, oldStyleNode);
	return newStyle;
    }

    public void removeStyleNodeFromHead(nsIDOMNode oldStyleNode) {
	headNode.removeChild(oldStyleNode);
    }

    void addExternalLinks() {
	IEditorInput input = pageContext.getEditPart().getEditorInput();
	IFile file = null;
	if (input instanceof IFileEditorInput) {
	    file = ((IFileEditorInput) input).getFile();
	}
	ResourceReference[] l = null;
	if (file != null) {
	    l = CSSReferenceList.getInstance().getAllResources(file);
	}
	if (l != null) {
	    for (int i = 0; i < l.length; i++) {
		ResourceReference item = l[i];
		addLinkNodeToHead("file:///" + item.getLocation(), YES_STRING); //$NON-NLS-1$
	    }
	}
    }

    void removeExternalLinks() {
	nsIDOMNodeList childs = headNode.getChildNodes();
	long length = childs.getLength();
	for (long i = length - 1; i >= 0; i--) {
	    nsIDOMNode node = childs.item(i);
	    if (node.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
		boolean isLink = false;
		boolean isStyle = false;
		if ((isLink = HTML.TAG_LINK
			.equalsIgnoreCase(node.getNodeName()))
			|| (isStyle = HTML.TAG_STYLE.equalsIgnoreCase(node
				.getNodeName()))) {
		    nsIDOMElement element = (nsIDOMElement) node
			    .queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID);
		    if ((isLink || (isStyle && ATTR_VPE_INLINE_LINK_VALUE
			    .equalsIgnoreCase(element.getAttribute(ATTR_VPE))))
			    && YES_STRING.equalsIgnoreCase(element
				    .getAttribute(VpeTemplateManager.ATTR_LINK_EXT))) {
			headNode.removeChild(node);
		    }
		}
	    }
	}
    }

    void refreshExternalLinks() {
	removeExternalLinks();
	addExternalLinks();
    }

    // ==========================================================
    void resetPseudoElement(nsIDOMNode visualNode) {
	if (visualNode != null) {
	    nsIDOMNode visualParent = visualNode.getParentNode();
	    if (visualParent != null) {
		PseudoInfo info = getPseudoInfo(visualParent);
		if (info.pseudoNode == null && !info.isElements) {
		    addPseudoElementImpl(visualParent);
		} else if (info.pseudoNode != null && info.isElements) {
		    visualParent.removeChild(info.pseudoNode);
		}
	    }
	}
    }

    private PseudoInfo getPseudoInfo(nsIDOMNode visualParent) {
	nsIDOMNode pseudoNode = null;
	boolean isElements = false;

	if (visualParent == null)
	    return new PseudoInfo();
	nsIDOMNodeList visualNodes = visualParent.getChildNodes();
	if (visualNodes == null)
	    return new PseudoInfo();

	long length = visualNodes.getLength();
	for (long i = 0; i < length; i++) {
	    nsIDOMNode visualNode = visualNodes.item(i);
	    if (pseudoNode == null && isPseudoElement(visualNode)) {
		pseudoNode = visualNode;
	    } else if (!isEmptyText(visualNode)) {
		isElements = true;
	    }
	    if (pseudoNode != null && isElements) {
		break;
	    }
	}
	return new PseudoInfo(pseudoNode, isElements);
    }

    static boolean isInitElement(nsIDOMNode visualNode) {
	if (visualNode == null) {
	    return false;
	}

	if (visualNode.getNodeType() != Node.ELEMENT_NODE) {
	    return false;
	}

	if (YES_STRING.equalsIgnoreCase(((nsIDOMElement) visualNode)
		.getAttribute(INIT_ELEMENT_ATTR))) {
	    return true;
	}

	return false;
    }

    static boolean isPseudoElement(nsIDOMNode visualNode) {
	if (visualNode == null) {
	    return false;
	}

	if (visualNode.getNodeType() != Node.ELEMENT_NODE) {
	    return false;
	}

	if (YES_STRING.equalsIgnoreCase(((nsIDOMElement) visualNode
		.queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID))
		.getAttribute(PSEUDO_ELEMENT_ATTR))) {
	    return true;
	}

	return false;
    }

    private void setPseudoContent(VpeTemplate containerTemplate,
	    Node sourceContainer, nsIDOMNode visualContainer) {
	if (containerTemplate != null) {
	    containerTemplate.setPseudoContent(pageContext, sourceContainer,
		    visualContainer, visualDocument);
	} else {
	    VpeDefaultPseudoContentCreator.getInstance().setPseudoContent(
		    pageContext, sourceContainer, visualContainer,
		    visualDocument);
	}

	// if (isEmptyElement(visualContainer)) {
	// addPseudoElementImpl(visualContainer);
	// }
    }

    private void addPseudoElementImpl(nsIDOMNode visualParent) {
	if (!templateManager.isWithoutPseudoElementContainer(visualParent
		.getNodeName())) {
	    if (VpeDebug.VISUAL_ADD_PSEUDO_ELEMENT) {
		System.out.println("-------------------- addPseudoElement: " //$NON-NLS-1$
			+ visualParent.getNodeName());
	    }
	    nsIDOMElement visualPseudoElement = visualDocument
		    .createElement(PSEUDO_ELEMENT);
	    visualPseudoElement.setAttribute(PSEUDO_ELEMENT_ATTR, YES_STRING);
	    visualParent.appendChild(visualPseudoElement);
	}
    }

    public boolean isEmptyElement(nsIDOMNode visualParent) {
	nsIDOMNodeList visualNodes = visualParent.getChildNodes();
	long len = visualNodes.getLength();

	if ((len == 0) || (len == 1 && isEmptyText(visualNodes.item(0)))) {
	    return true;
	}

	return false;
    }

    public boolean isEmptyDocument() {
	nsIDOMNodeList visualNodes = visualContentArea.getChildNodes();
	long len = visualNodes.getLength();
	if ((len == 0)
		|| (len == 1 && (isEmptyText(visualNodes.item(0)) || isPseudoElement(visualNodes
			.item(0))))) {
	    return true;
	}

	return false;
    }

    private boolean isEmptyText(nsIDOMNode visualNode) {
	if (visualNode == null
		|| (visualNode.getNodeType() != nsIDOMNode.TEXT_NODE)) {
	    return false;
	}

	if (visualNode.getNodeValue().trim().length() == 0) {
	    return true;
	}

	return false;
    }

    // ==========================================================

    public void updateNode(Node sourceNode) {
	if (sourceNode == null)
	    return;

	switch (sourceNode.getNodeType()) {
	case Node.DOCUMENT_NODE:
	    rebuildDom((Document) sourceNode);
	    break;
	case Node.COMMENT_NODE:
	    updateComment(sourceNode);
	    break;
	default:
	    updateElement(getNodeForUpdate(sourceNode));
	}
    }

    // TODO Ssergey Vasilyev make a common code for figuring out
    // if it is need to update parent node or not
    private Node getNodeForUpdate(Node sourceNode) {
	/* Changing of <tr> or <td> tags can affect whole the table */
	Node sourceTable = getParentTable(sourceNode, 2);
	if (sourceTable != null) {
	    return sourceTable;
	}

	/* Changing of an <option> tag can affect the parent select */
	Node sourceSelect = getParentSelect(sourceNode);
	if (sourceSelect != null) {
	    return sourceSelect;
	}

	return sourceNode;
    }

    private void updateComment(Node sourceNode) {
	VpeNodeMapping mapping = domMapping.getNodeMapping(sourceNode);
	if (mapping != null
		&& mapping.getType() == VpeNodeMapping.COMMENT_MAPPING) {
	    nsIDOMNodeList visualNodes = mapping.getVisualNode()
		    .getChildNodes();

	    if (visualNodes.getLength() > 0) {
		visualNodes.item(0).setNodeValue(sourceNode.getNodeValue());
	    }
	}
    }

    private void updateElement(Node sourceNode) {
	VpeElementMapping elementMapping = null;
	VpeNodeMapping nodeMapping = domMapping.getNodeMapping(sourceNode);
	if (nodeMapping instanceof VpeElementMapping) {
	
	    elementMapping = (VpeElementMapping) nodeMapping;
	    if (elementMapping != null && elementMapping.getTemplate() != null) {
				Node updateNode = elementMapping.getTemplate()
						.getNodeForUptate(pageContext,
								elementMapping.getSourceNode(),
								elementMapping.getVisualNode(),
								elementMapping.getData());

				/*
				 * special processing of "style" element
				 * 
				 * for unification of updating nodes - or redevelop updating
				 * mechanism (for example : transfer this function to template )
				 * or redevelop template of "style" element
				 */
				if (HTML.TAG_STYLE.equalsIgnoreCase(sourceNode.getNodeName())) {
					// refresh style node
					VpeStyleUtil.refreshStyleElement(this, elementMapping);
					return;
				}
				if (updateNode != null && updateNode != sourceNode) {
					updateNode(updateNode);
					return;
				}
			}
	}
	
    
	nsIDOMNode visualOldNode = domMapping.remove(sourceNode);
	getSourceNodes().remove(sourceNode);
	
	if (sourceNode instanceof INodeNotifier) {
	    ((INodeNotifier) sourceNode).removeAdapter(getSorceAdapter());
	}
	if (visualOldNode != null) {
	    if (elementMapping != null) {
		nsIDOMElement border = elementMapping.getBorder();
		if (border != null) {
		    visualOldNode = border;
		}
	    }
	    nsIDOMNode visualContainer = visualOldNode.getParentNode();
	    nsIDOMNode visualNextNode = visualOldNode.getNextSibling();
	    if (visualContainer != null) {
		visualContainer.removeChild(visualOldNode);
		addNode(sourceNode, visualNextNode, visualContainer);
	    }
	} else {
	    // Max Areshkau Why we need update parent node when we update text
	    // node?
	    // lookd like we haven't need do it.
	    if (sourceNode.getNodeType() == Node.TEXT_NODE) {
		updateNode(sourceNode.getParentNode());
	    }
	}
    }

    public void removeNode(Node sourceNode) {
    //remove from cash should be called first
    getPageContext().removeNodeFromVpeCash(sourceNode);
	domMapping.remove(sourceNode);
	getSourceNodes().remove(sourceNode);
	if (sourceNode instanceof INodeNotifier) {
	    ((INodeNotifier) sourceNode).removeAdapter(getSorceAdapter());
	}
    }

    private Node getParentTable(Node sourceNode, int depth) {
	Node parentNode = sourceNode.getParentNode();
	for (int i = 0; parentNode != null && i < depth; parentNode = parentNode
		.getParentNode(), i++) {
	    if (HTML.TAG_TABLE.equalsIgnoreCase(parentNode.getNodeName())) {
		return parentNode;
	    }
	}
	return null;
    }

    private Node getParentSelect(Node sourceNode) {
	if (HTML.TAG_OPTION.equalsIgnoreCase(sourceNode.getNodeName())) {
	    Node parentNode = sourceNode.getParentNode();
	    if (HTML.TAG_SELECT.equalsIgnoreCase(parentNode.getNodeName())) {
		return parentNode;
	    }
	}
	return null;
    }

    // public void setText(Node sourceText) {
    // Node sourceParent = sourceText.getParentNode();
    // if (sourceParent != null && sourceParent.getLocalName() != null) {
    // String sourceParentName = sourceParent.getLocalName();
    // if (HTML.TAG_TEXTAREA.equalsIgnoreCase(sourceParentName)
    // || HTML.TAG_OPTION.equalsIgnoreCase(sourceParentName)) {
    // updateNode(sourceText.getParentNode());
    // return;
    // }
    // }
    // nsIDOMNode visualText = domMapping.getVisualNode(sourceText);
    // if (visualText != null) {
    // String visualValue = TextUtil.visualText(sourceText.getNodeValue());
    // visualText.setNodeValue(visualValue);
    // }else {
    // VpeNodeMapping nodeMapping = domMapping
    // .getNodeMapping(sourceParent);
    // if (nodeMapping != null
    // && nodeMapping.getType() == VpeNodeMapping.ELEMENT_MAPPING) {
    // VpeTemplate template = ((VpeElementMapping) nodeMapping)
    // .getTemplate();
    // if (template != null) {
    // if (!template.containsText()) {
    // return;
    // }
    public boolean setText(Node sourceText) {
	Node sourceParent = sourceText.getParentNode();
	if (sourceParent != null && sourceParent.getLocalName() != null) {
	    String sourceParentName = sourceParent.getLocalName();
	    if (HTML.TAG_TEXTAREA.equalsIgnoreCase(sourceParentName)
		    || HTML.TAG_OPTION.equalsIgnoreCase(sourceParentName) || HTML.TAG_STYLE.equalsIgnoreCase(sourceParentName)) {
		updateNode(sourceText.getParentNode());
		return true;
	    }
	}
	nsIDOMNode visualText = domMapping.getVisualNode(sourceText);
	if (visualText != null) {
	    String visualValue = TextUtil.visualText(sourceText.getNodeValue());
	    visualText.setNodeValue(visualValue);
	} else {
	    VpeNodeMapping nodeMapping = domMapping
		    .getNodeMapping(sourceParent);
	    if (nodeMapping != null
		    && nodeMapping.getType() == VpeNodeMapping.ELEMENT_MAPPING) {
		VpeTemplate template = ((VpeElementMapping) nodeMapping)
			.getTemplate();
		if (template != null) {
		    if (!template.containsText()) {
			return false;
		    }
		}
	    }
	    updateNode(sourceText);
	    return true;
	}

	// }
	// updateNode(sourceText);
	return false;
    }

    // }

    public void setAttribute(Element sourceElement, String name, String value) {
	VpeElementMapping elementMapping = (VpeElementMapping) domMapping
		.getNodeMapping(sourceElement);
	if (elementMapping != null) {
	    if (elementMapping.isIfDependencyFromAttribute(name)) {
		updateElement(sourceElement);
	    } else {
		VpeTemplate template = elementMapping.getTemplate();
		if (elementMapping.getBorder() != null) {
		    updateElement(sourceElement);
		} else if (template.isRecreateAtAttrChange(pageContext,
			sourceElement, visualDocument,
			(nsIDOMElement) elementMapping.getVisualNode(),
			elementMapping.getData(), name, value)) {
		    updateElement(sourceElement);
		} else {
		    nsIDOMElement visualElement = (nsIDOMElement) elementMapping
			    .getVisualNode();
		    if (visualElement != null) {
			String visualElementName = visualElement.getNodeName();
			if (HTML.TAG_SELECT.equalsIgnoreCase(visualElementName)) {
			    updateElement(sourceElement);
			    return;
			} else if (HTML.TAG_OPTION
				.equalsIgnoreCase(visualElementName)) {
			    updateElement(sourceElement.getParentNode());
			    return;
			} else if (HTML.TAG_INPUT
				.equalsIgnoreCase(visualElementName)) {
			    updateElement(sourceElement);
			    // Fixes JBIDE-1744 author dmaliarevich
			    // unified h:dataTable border lookup
			    // after attribute change and
			    // after visual editor refresh
			} else if (HTML.TAG_TABLE
				.equalsIgnoreCase(visualElementName)) {
			    updateElement(sourceElement);
			}
			// End of fix
		    }
		    // setXmlnsAttribute(elementMapping, name, value);
		    template.setAttribute(pageContext, sourceElement,
			    visualDocument, visualElement, elementMapping
				    .getData(), name, value);
		    resetTooltip(sourceElement, visualElement);
		}
	    }
	}
    }

    public void stopToggle(Node sourceNode) {
	if (!(sourceNode instanceof Element))
	    return;

	Element sourceElement = (Element) sourceNode;
	VpeElementMapping elementMapping = (VpeElementMapping) domMapping
		.getNodeMapping(sourceElement);
	if (elementMapping != null) {
	    VpeTemplate template = elementMapping.getTemplate();

	    if (template instanceof VpeToggableTemplate) {
		((VpeToggableTemplate) template).stopToggling(sourceElement);
	    }
	}
    }

    public boolean doToggle(nsIDOMNode visualNode) {
		if (visualNode == null) {
			return false;
		}
		nsIDOMElement visualElement = null;
		try {
			visualElement = (nsIDOMElement) visualNode
					.queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID);
		} catch (XPCOMException exception) {
			visualElement = (nsIDOMElement) visualNode.getParentNode()
					.queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID);
		}
		if (visualElement == null) {
			return false;
		}

		nsIDOMAttr toggleIdAttr = visualElement
				.getAttributeNode(VPE_USER_TOGGLE_ID);
		if (toggleIdAttr == null) {
			return false;
		}
		String toggleId = toggleIdAttr.getNodeValue();

		if (toggleId == null) {
			return false;
		}

		boolean toggleLookup = false;
		nsIDOMAttr toggleLookupAttr = visualElement
				.getAttributeNode(VPE_USER_TOGGLE_LOOKUP_PARENT);
		if (toggleLookupAttr != null) {
			toggleLookup = "true".equals(toggleLookupAttr.getNodeValue());
		}

		nsIDOMElement selectedElem = getLastSelectedElement();
		// Fixes JBIDE-1823 author dmaliarevich
		if (null == selectedElem) {
			return false;
		}
		VpeElementMapping elementMapping = null;
		VpeNodeMapping nodeMapping = domMapping.getNodeMapping(selectedElem);
		if (nodeMapping instanceof VpeElementMapping) {
			elementMapping = (VpeElementMapping) nodeMapping;
		}
		// end of fix
		if (elementMapping == null) {
			// may be toggle with facet
			while (!selectedElem.getNodeName().equals(HTML.TAG_TABLE)) {
				selectedElem = (nsIDOMElement) selectedElem.getParentNode()
				.queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID);
			}
			// Fixes JBIDE-1823 author dmaliarevich
			nodeMapping = domMapping.getNodeMapping(selectedElem);
			if (nodeMapping instanceof VpeElementMapping) {
				elementMapping = (VpeElementMapping) nodeMapping;
			}
			// end of fix
		}
		Node sourceNode = (Node) domMapping.getSourceNode(selectedElem);
		if (sourceNode == null) {
			return false;
		}

		Element sourceElement = (Element) (sourceNode instanceof Element ? sourceNode
				: sourceNode.getParentNode());

		// Fixes JBIDE-1823 author dmaliarevich
		// Template is looked according to <code>selectedElem</code>
		// so <code>toggleLookupAttr</code> should be retrieved 
		// from this element
		toggleLookupAttr = selectedElem
			.getAttributeNode(VPE_USER_TOGGLE_LOOKUP_PARENT);
		if (toggleLookupAttr != null) {
			toggleLookup = "true".equals(toggleLookupAttr.getNodeValue());
		}
		// end of fix
		
		if (elementMapping != null) {
			VpeTemplate template = elementMapping.getTemplate();

			while (toggleLookup && sourceElement != null
					&& !(template instanceof VpeToggableTemplate)) {
				sourceElement = (Element) sourceElement.getParentNode();
				if (sourceElement == null) {
					break;
				}
				// Fixes JBIDE-1823 author dmaliarevich
				nodeMapping = domMapping.getNodeMapping(sourceElement);
				if (nodeMapping instanceof VpeElementMapping) {
					elementMapping = (VpeElementMapping) nodeMapping;
				}
				// end of fix
				if (elementMapping == null) {
					continue;
				}
				template = elementMapping.getTemplate();
			}

			if (template instanceof VpeToggableTemplate) {
				((VpeToggableTemplate) template).toggle(this, sourceElement,
						toggleId);
				updateElement(sourceElement);
				return true;
			}
		}
		return false;
	}

    public void removeAttribute(Element sourceElement, String name) {
	VpeElementMapping elementMapping = (VpeElementMapping) domMapping
		.getNodeMapping(sourceElement);
	if (elementMapping != null) {
	    if (elementMapping.isIfDependencyFromAttribute(name)) {
		updateElement(sourceElement);
	    } else {
		VpeTemplate template = elementMapping.getTemplate();
		if (template.isRecreateAtAttrChange(pageContext, sourceElement,
			visualDocument, (nsIDOMElement) elementMapping
				.getVisualNode(), elementMapping.getData(),
			name, null)) {
		    updateElement(sourceElement);
		}
		// else {
		// removeXmlnsAttribute(elementMapping, name);
		// template.removeAttribute(pageContext, sourceElement,
		// visualDocument, (nsIDOMElement) elementMapping
		// .getVisualNode(), elementMapping.getData(),
		// name);
		// resetTooltip(sourceElement, (nsIDOMElement) elementMapping
		// .getVisualNode());
		// }
	    }
	}
    }

    public void refreshBundleValues(Element sourceElement) {
	VpeElementMapping elementMapping = (VpeElementMapping) domMapping
		.getNodeMapping(sourceElement);
	if (elementMapping != null) {
	    VpeTemplate template = elementMapping.getTemplate();
	    template.refreshBundleValues(pageContext, sourceElement,
		    elementMapping.getData());
	}
    }

    boolean isContentArea(nsIDOMNode visualNode) {
	return visualContentArea.equals(visualNode);
    }

    nsIDOMElement getContentArea() {
	return visualContentArea;
    }

    public void setSelectionRectangle(nsIDOMElement visualElement) {
	setSelectionRectangle(visualElement, true);
    }

    void setSelectionRectangle(nsIDOMElement visualElement, boolean scroll) {
	int resizerConstrains = getResizerConstrains(visualElement);
	visualEditor.setSelectionRectangle(visualElement, resizerConstrains,
		scroll);
    }

    public nsIDOMNode addLinkNodeToHead(String href_val, String ext_val) {
	nsIDOMElement newNode = createLinkNode(href_val,
		ATTR_REL_STYLESHEET_VALUE, ext_val);

	//TODO Dzmitry Sakovich
	// Fix priority CSS classes  JBIDE-1713
	nsIDOMNode firstNode = headNode.getFirstChild();
	headNode.insertBefore(newNode, firstNode);
	return newNode;
    }

    public nsIDOMNode replaceLinkNodeToHead(nsIDOMNode oldNode,
	    String href_val, String ext_val) {
	nsIDOMNode newNode = createLinkNode(href_val,
		ATTR_REL_STYLESHEET_VALUE, ext_val);
	headNode.replaceChild(newNode, oldNode);
	return newNode;
    }

    public nsIDOMNode replaceLinkNodeToHead(String href_val, String ext_val) {
	nsIDOMNode newNode = null;
	nsIDOMNode oldNode = getLinkNode(href_val, ext_val);
	if (oldNode == null) {
	    newNode = addLinkNodeToHead(href_val, ext_val);
	}
	return newNode;
    }

    public void removeLinkNodeFromHead(nsIDOMNode node) {
	headNode.removeChild(node);
    }

    private nsIDOMElement createLinkNode(String href_val, String rel_val,
	    String ext_val) {
	nsIDOMElement linkNode = null;
	if ((ATTR_REL_STYLESHEET_VALUE.equalsIgnoreCase(rel_val))
		&& href_val.startsWith("file:")) {
	    /*
	     * Because of the Mozilla caches the linked css files we replace tag
	     * <link rel="styleseet" href="file://..."> with tag <style
	     * vpe="ATTR_VPE_INLINE_LINK_VALUE">file content</style> It is
	     * LinkReplacer
	     */
	    linkNode = visualDocument.createElement(HTML.TAG_STYLE);
	    linkNode.setAttribute(ATTR_VPE, ATTR_VPE_INLINE_LINK_VALUE);

	    /* Copy links attributes into our <style> */
	    linkNode.setAttribute(VpeTemplateManager.ATTR_LINK_HREF, href_val);
	    linkNode.setAttribute(VpeTemplateManager.ATTR_LINK_EXT, ext_val);
	    try {
		StringBuffer styleText = new StringBuffer(EMPTY_STRING);
		URL url = new URL((new Path(href_val)).toOSString());
		String fileName = url.getFile();
		BufferedReader in = new BufferedReader(new FileReader(
			(fileName)));
		String str = EMPTY_STRING;
		while ((str = in.readLine()) != null) {
		    styleText.append(str);
		}

		String styleForParse = styleText.toString();
		styleForParse = VpeStyleUtil.addFullPathIntoURLValue(
			styleForParse, href_val);

		in.close();
		nsIDOMText textNode = visualDocument
			.createTextNode(styleForParse);
		linkNode.appendChild(textNode);
		return linkNode;
	    } catch (FileNotFoundException fnfe) {
		/* File which was pointed by user is not exists. Do nothing. */
	    } catch (IOException ioe) {
		VpePlugin.getPluginLog().logError(ioe.getMessage(), ioe);
	    }
	}

	linkNode = visualDocument.createElement(HTML.TAG_LINK);
	linkNode.setAttribute(VpeTemplateManager.ATTR_LINK_REL, rel_val);
	linkNode.setAttribute(VpeTemplateManager.ATTR_LINK_HREF, href_val);
	linkNode.setAttribute(VpeTemplateManager.ATTR_LINK_EXT, ext_val);

	return linkNode;
    }

    private boolean isLinkReplacer(nsIDOMNode node) {
	return HTML.TAG_STYLE.equalsIgnoreCase(node.getNodeName())
		&& ATTR_VPE_INLINE_LINK_VALUE
			.equalsIgnoreCase(((nsIDOMElement) node
				.queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID))
				.getAttribute(ATTR_VPE));
    }

    private nsIDOMNode getLinkNode(String href_val, String ext_val) {
	nsIDOMNodeList children = headNode.getChildNodes();
	long len = children.getLength();
	for (long i = len - 1; i >= 0; i--) {
	    nsIDOMNode node = children.item(i);
	    if (node.getNodeType() == Node.ELEMENT_NODE) {
		if (HTML.TAG_LINK.equalsIgnoreCase(node.getNodeName())
			|| isLinkReplacer(node)) {
		    nsIDOMElement element = (nsIDOMElement) node
			    .queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID);
		    if (ext_val.equalsIgnoreCase(element
			    .getAttribute(VpeTemplateManager.ATTR_LINK_EXT))
			    && href_val
				    .equalsIgnoreCase(element
					    .getAttribute(VpeTemplateManager.ATTR_LINK_HREF))) {
			return node;
		    }
		}
	    }
	}
	return null;
    }

    private void cleanHead() {
	nsIDOMNodeList children = headNode.getChildNodes();
	long len = children.getLength();
	for (long i = len - 1; i >= 0; i--) {
	    nsIDOMNode node = children.item(i);
	    if (node.getNodeType() == Node.ELEMENT_NODE) {
		if (isLinkReplacer(node)) {
		    /*Added by Max Areshkau(Fix for JBIDE-1941)
		     * Ext. attribute used for adding external styles 
		     * to editor. If was added external attribute, this 
		     * property is true.
		     */
		    if (!YES_STRING.equalsIgnoreCase(((nsIDOMElement) node
			    .queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID))
			    .getAttribute(VpeTemplateManager.ATTR_LINK_EXT))) {
			// int linkAddress =
			// MozillaSupports.queryInterface(node,
			// nsIStyleSheetLinkingElement.NS_ISTYLESHEETLINKINGELEMENT_IID);
			// nsIStyleSheetLinkingElement linkingElement = new
			// nsIStyleSheetLinkingElement(linkAddress);
			// linkingElement.removeStyleSheet();
			node = headNode.removeChild(node);
		    }
		} else if (HTML.TAG_STYLE.equalsIgnoreCase(node.getNodeName())
			&& (!YES_STRING
				.equalsIgnoreCase(((nsIDOMElement) node
					.queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID))
					.getAttribute(ATTR_VPE)))) {
		    node = headNode.removeChild(node);
		}
	    }
	}
    }

    private class PseudoInfo {
	private nsIDOMNode pseudoNode;
	private boolean isElements;

	private PseudoInfo() {
	    this(null, false);
	}

	private PseudoInfo(nsIDOMNode pseudoNode, boolean isElements) {
	    this.pseudoNode = pseudoNode;
	    this.isElements = isElements;
	}
    }

    void showDragCaret(nsIDOMNode node, int offset) {
	xulRunnerEditor.showDragCaret(node, offset);
    }

    void hideDragCaret() {

	xulRunnerEditor.hideDragCaret();
    }

    private int getResizerConstrains(nsIDOMNode visualNode) {
	VpeNodeMapping nodeMapping = domMapping.getNodeMapping(visualNode);
	if (nodeMapping != null
		&& nodeMapping.getType() == VpeNodeMapping.ELEMENT_MAPPING) {
	    return ((VpeElementMapping) nodeMapping).getTemplate()
		    .getTagDescription(pageContext,
			    (Element) nodeMapping.getSourceNode(),
			    visualDocument,
			    (nsIDOMElement) nodeMapping.getVisualNode(),
			    ((VpeElementMapping) nodeMapping).getData())
		    .getResizeConstrains();
	}
	return VpeTagDescription.RESIZE_CONSTRAINS_NONE;
    }

    public void resize(nsIDOMElement element, int resizerConstrains, int top,
	    int left, int width, int height) {
	VpeElementMapping elementMapping = (VpeElementMapping) domMapping
		.getNodeMapping(element);
	if (elementMapping != null) {
	    elementMapping.getTemplate().resize(pageContext,
		    (Element) elementMapping.getSourceNode(), visualDocument,
		    element, elementMapping.getData(), resizerConstrains, top,
		    left, width, height);
	}
    }

    static boolean isAnonElement(nsIDOMNode visualNode) {
	if (visualNode != null
		&& visualNode.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
	    String attrValue = ((nsIDOMElement) visualNode
		    .queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID))
		    .getAttribute(MOZ_ANONCLASS_ATTR);

	    return attrValue != null && attrValue.length() > 0;
	}

	return false;
    }

    boolean canInnerDrag(nsIDOMElement visualDragElement) {
	VpeNodeMapping node = domMapping.getNodeMapping(visualDragElement);
	if (node instanceof VpeElementMapping) {
	    VpeElementMapping elementMapping = (VpeElementMapping) node;
	    if (elementMapping != null) {
		return elementMapping.getTemplate().canInnerDrag(pageContext,
			(Element) elementMapping.getSourceNode(),
			visualDocument, visualDragElement,
			elementMapping.getData());
	    }
	}
	return false;
    }

    VpeSourceInnerDropInfo getSourceInnerDropInfo(Node sourceDragNode,
	    VpeVisualInnerDropInfo visualDropInfo, boolean checkParentTemplates) {
	nsIDOMNode visualDropContainer = visualDropInfo.getDropContainer();
	long visualDropOffset = visualDropInfo.getDropOffset();
	Node sourceDropContainer = null;
	int sourceDropOffset = 0;

	switch (visualDropContainer.getNodeType()) {
	case nsIDOMNode.ELEMENT_NODE:
	    nsIDOMNode visualOffsetNode = null;
	    boolean afterFlag = false;
	    long visualChildCount = VisualDomUtil
		    .getChildCount(visualDropContainer);
	    if (visualDropOffset < visualChildCount) {
		visualOffsetNode = VisualDomUtil.getChildNode(
			visualDropContainer, visualDropOffset);
		if (isPseudoElement(visualOffsetNode)
			|| isAnonElement(visualOffsetNode)) {
		    visualOffsetNode = getLastAppreciableVisualChild(visualDropContainer);
		    afterFlag = true;
		}
	    } else {
		visualOffsetNode = getLastAppreciableVisualChild(visualDropContainer);
		afterFlag = visualChildCount != 0;
	    }
	    if (visualOffsetNode != null) {
		Node sourceOffsetNode = domMapping
			.getSourceNode(visualOffsetNode);
		if (sourceOffsetNode != null) {
		    sourceDropContainer = sourceOffsetNode.getParentNode();
		    sourceDropOffset = ((NodeImpl) sourceOffsetNode).getIndex();
		    if (afterFlag) {
			sourceDropOffset++;
		    }
		}
	    }
	    if (sourceDropContainer == null) {
		sourceDropContainer = domMapping
			.getNearSourceNode(visualDropContainer);
		if (sourceDropContainer != null) {
		    sourceDropOffset = sourceDropContainer.getChildNodes()
			    .getLength();
		}
	    }
	    if (sourceDropContainer == null) {
		sourceDropContainer = domMapping
			.getNearSourceNode(visualContentArea);
		sourceDropOffset = sourceDropContainer.getChildNodes()
			.getLength();
	    }
	    break;
	case nsIDOMNode.TEXT_NODE:
	    VpeNodeMapping nodeMapping = domMapping
		    .getNearNodeMapping(visualDropContainer);
	    switch (nodeMapping.getType()) {
	    case VpeNodeMapping.TEXT_MAPPING:
		sourceDropContainer = nodeMapping.getSourceNode();
		sourceDropOffset = TextUtil.sourceInnerPosition(
			sourceDropContainer.getNodeValue(), visualDropOffset);
		break;
	    case VpeNodeMapping.ELEMENT_MAPPING:
		// it's attribute
		if (isTextEditable(visualDropContainer)) {
		    String[] atributeNames = ((VpeElementMapping) nodeMapping)
			    .getTemplate().getOutputAtributeNames();
		    if (atributeNames != null && atributeNames.length > 0) {
			Element sourceElement = (Element) nodeMapping
				.getSourceNode();
			sourceDropContainer = sourceElement
				.getAttributeNode(atributeNames[0]);
			sourceDropOffset = TextUtil.sourceInnerPosition(
				sourceDropContainer.getNodeValue(),
				visualDropOffset);
		    }
		}
		nodeMapping.getVisualNode();
	    }
	    break;
	}
	if (sourceDropContainer != null) {
	    return getSourceInnerDropInfo(sourceDragNode, sourceDropContainer,
		    sourceDropOffset, checkParentTemplates);
	} else {
	    return new VpeSourceInnerDropInfo(null, 0, false);
	}
    }

    VpeSourceInnerDropInfo getSourceInnerDropInfo(Node dragNode,
	    Node container, int offset, boolean checkParentsTemplates) {
	// Thread.dumpStack();
	boolean canDrop = false;
	switch (container.getNodeType()) {
	case Node.ELEMENT_NODE:
	    VpeNodeMapping nodeMapping = domMapping.getNodeMapping(container);
	    if (nodeMapping != null
		    && nodeMapping.getType() == VpeNodeMapping.ELEMENT_MAPPING) {
		canDrop = ((VpeElementMapping) nodeMapping).getTemplate()
			.canInnerDrop(pageContext, container, dragNode);
	    }
	    if (!canDrop) {
		if (!checkParentsTemplates)
		    return new VpeSourceInnerDropInfo(container, offset,
			    canDrop);
		// offset = ((NodeImpl)container).getIndex();
		// container = container.getParentNode();
		// TODO Max Areshkau unclear logic , if we can drop on element
		// why we trying to drop
		// this on parent
		// return getSourceInnerDropInfo(dragNode, container, offset,
		// false);
		return new VpeSourceInnerDropInfo(container, offset, canDrop);
	    }
	    break;
	case Node.TEXT_NODE:
	case Node.DOCUMENT_NODE:
	    canDrop = true;
	    break;
	case Node.ATTRIBUTE_NODE:
	    canDrop = true;
	    break;
	}
	if (canDrop) {
	    return new VpeSourceInnerDropInfo(container, offset, canDrop);
	} else {
	    return new VpeSourceInnerDropInfo(null, 0, canDrop);
	}
    }

    public void innerDrop(Node dragNode, Node container, int offset) {
	VpeNodeMapping mapping = domMapping.getNearNodeMapping(container);
	if (mapping != null) {
	    nsIDOMNode visualDropContainer = mapping.getVisualNode();
	    switch (mapping.getType()) {
	    case VpeNodeMapping.TEXT_MAPPING:
		break;
	    case VpeNodeMapping.ELEMENT_MAPPING:
		nsIDOMNode visualParent = visualDropContainer.getParentNode();
		VpeNodeMapping oldMapping = mapping;
		mapping = domMapping.getNearNodeMapping(visualParent);
		if (mapping != null
			&& mapping.getType() == VpeNodeMapping.ELEMENT_MAPPING) {
		    ((VpeElementMapping) mapping).getTemplate()
			    .innerDrop(
				    pageContext,
				    new VpeSourceInnerDragInfo(dragNode, 0, 0),
				    new VpeSourceInnerDropInfo(container,
					    offset, true));
		} else {
		    ((VpeElementMapping) oldMapping).getTemplate()
			    .innerDrop(
				    pageContext,
				    new VpeSourceInnerDragInfo(dragNode, 0, 0),
				    new VpeSourceInnerDropInfo(container,
					    offset, true));
		}
	    }

	}
    }

    void innerDrop(VpeSourceInnerDragInfo dragInfo,
	    VpeSourceInnerDropInfo dropInfo) {
	dropper.drop(pageContext, dragInfo, dropInfo);
    }

    nsIDOMElement getNearDragElement(Element visualElement) {
	VpeElementMapping elementMapping = domMapping
		.getNearElementMapping(visualElement);
	while (elementMapping != null) {
	    if (canInnerDrag(elementMapping.getVisualElement())) {
		return elementMapping.getVisualElement();
	    }
	    elementMapping = domMapping.getNearElementMapping(elementMapping
		    .getVisualNode().getParentNode());
	}
	return null;
    }

    nsIDOMElement getDragElement(nsIDOMElement visualElement) {
	VpeElementMapping elementMapping = domMapping
		.getNearElementMapping(visualElement);
	if (elementMapping != null
		&& canInnerDrag(elementMapping.getVisualElement())) {
	    return elementMapping.getVisualElement();
	}
	return null;
    }

    public boolean isTextEditable(nsIDOMNode visualNode) {

		if (visualNode != null) {
			nsIDOMNode parent = visualNode.getParentNode();
			if (parent != null
					&& parent.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
				nsIDOMElement element = (nsIDOMElement) parent
						.queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID);
				nsIDOMAttr style = element.getAttributeNode("style");
				if (style != null) {
					String styleValue = style.getNodeValue();
					String[] items = styleValue.split(";");
					for (int i = 0; i < items.length; i++) {
						String[] item = items[i].split(":");
						if ("-moz-user-modify".equals(item[0].trim())
								&& "read-only".equals(item[1].trim())) {
							return false;
						}
					}
				}
				nsIDOMAttr classAttr = element.getAttributeNode("class");
				if (classAttr != null) {
					String classValue = classAttr.getNodeValue().trim();
					if ("__any__tag__caption".equals(classValue)) {
						return false;
					}
				}
			}
		}
		return true;
	}

    VpeVisualInnerDropInfo getInnerDropInfo(Node sourceDropContainer,
	    int sourceDropOffset) {
	nsIDOMNode visualDropContainer = null;
	long visualDropOffset = 0;

	switch (sourceDropContainer.getNodeType()) {
	case Node.TEXT_NODE:
	    visualDropContainer = domMapping.getVisualNode(sourceDropContainer);
	    visualDropOffset = TextUtil.visualInnerPosition(sourceDropContainer
		    .getNodeValue(), sourceDropOffset);
	    break;
	case Node.ELEMENT_NODE:
	case Node.DOCUMENT_NODE:
	    NodeList sourceChildren = sourceDropContainer.getChildNodes();
	    if (sourceDropOffset < sourceChildren.getLength()) {
		Node sourceChild = sourceChildren.item(sourceDropOffset);
		nsIDOMNode visualChild = domMapping.getVisualNode(sourceChild);
		if (visualChild != null) {
		    visualDropContainer = visualChild.getParentNode();

		    visualDropOffset = VisualDomUtil.getOffset(visualChild);
		}
	    }
	    if (visualDropContainer == null) {
		visualDropContainer = domMapping
			.getNearVisualNode(sourceDropContainer);
		nsIDOMNode visualChild = getLastAppreciableVisualChild(visualDropContainer);
		if (visualChild != null) {
		    visualDropOffset = VisualDomUtil.getOffset(visualChild) + 1;
		} else {
		    visualDropOffset = 0;
		}
	    }
	    break;
	case Node.ATTRIBUTE_NODE:
	    Element sourceElement = ((Attr) sourceDropContainer)
		    .getOwnerElement();
	    VpeElementMapping elementMapping = domMapping
		    .getNearElementMapping(sourceElement);
	    nsIDOMNode textNode = elementMapping.getTemplate()
		    .getOutputTextNode(pageContext, sourceElement,
			    elementMapping.getData());
	    if (textNode != null) {
		visualDropContainer = textNode;
		visualDropOffset = TextUtil.visualInnerPosition(
			sourceDropContainer.getNodeValue(), sourceDropOffset);
	    }
	    break;
	}
	if (visualDropContainer == null) {
	    return null;
	}
	return new VpeVisualInnerDropInfo(visualDropContainer,
		visualDropOffset, 0, 0);
    }

    protected void setTooltip(Element sourceElement, nsIDOMElement visualElement) {
	if (visualElement != null && sourceElement != null
		&& !((IDOMElement) sourceElement).isJSPTag()) {
	    if (HTML.TAG_HTML.equalsIgnoreCase(sourceElement.getNodeName()))
		return;
	    String titleValue = getTooltip(sourceElement);

	    if (titleValue != null) {
		titleValue = titleValue.replaceAll("&", "&amp;");
		titleValue = titleValue.replaceAll("<", "&lt;");
		titleValue = titleValue.replaceAll(">", "&gt;");
	    }

	    if (titleValue != null) {
		// visualElement.setAttribute("title", titleValue);
		setTooltip(visualElement, titleValue);
	    }
	}
    }

    protected void setTooltip(nsIDOMElement visualElement, String titleValue) {
	visualElement.setAttribute(HTML.ATTR_TITLE, titleValue);
	nsIDOMNodeList children = visualElement.getChildNodes();
	long len = children.getLength();
	for (long i = 0; i < len; i++) {
	    nsIDOMNode child = children.item(i);
	    if (child.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
		setTooltip(((nsIDOMElement) child
			.queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID)),
			titleValue);
	    }
	}
    }

    private void resetTooltip(Element sourceElement, nsIDOMElement visualElement) {
	if (visualElement != null && sourceElement != null
		&& !((ElementImpl) sourceElement).isJSPTag()) {
	    if (HTML.TAG_HTML.equalsIgnoreCase(sourceElement.getNodeName()))
		return;
	    String titleValue = getTooltip(sourceElement);

	    if (titleValue != null) {
		titleValue = titleValue.replaceAll("&", "&amp;"); //$NON-NLS-1$//$NON-NLS-2$
		titleValue = titleValue.replaceAll("<", "&lt;");  //$NON-NLS-1$//$NON-NLS-2$
		titleValue = titleValue.replaceAll(">", "&gt;");  //$NON-NLS-1$//$NON-NLS-2$
	    }

	    if (titleValue != null) {
		resetTooltip(visualElement, titleValue);
	    }
	}
    }

    private void resetTooltip(nsIDOMElement visualElement, String titleValue) {
	visualElement.setAttribute(HTML.ATTR_TITLE, titleValue);
	nsIDOMNodeList children = visualElement.getChildNodes();
	long len = children.getLength();
	for (long i = 0; i < len; i++) {
	    nsIDOMNode child = children.item(i);
	    if (child.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
		if (domMapping.getNodeMapping(child) == null) {
		    resetTooltip((nsIDOMElement) child
			    .queryInterface(nsIDOMElement.NS_IDOMELEMENT_IID),
			    titleValue);
		}
	    }
	}
    }

    private String getTooltip(Element sourceElement) {
	StringBuffer buffer = new StringBuffer();
	buffer.append(sourceElement.getNodeName());
	NamedNodeMap attrs = sourceElement.getAttributes();
	int len = attrs.getLength();
	for (int i = 0; i < len; i++) {
	    if (i == 7) {
		return buffer.append("\n\t... ").toString(); //$NON-NLS-1$
	    }
	    int valueLength = attrs.item(i).getNodeValue().length();
	    if (valueLength > 30) {
		StringBuffer temp = new StringBuffer();
		temp.append(attrs.item(i).getNodeValue().substring(0, 15)
			+ " ... " //$NON-NLS-1$
			+ attrs.item(i).getNodeValue().substring(
				valueLength - 15, valueLength));
		buffer.append("\n" + attrs.item(i).getNodeName() + ": " + temp); //$NON-NLS-1$ //$NON-NLS-2$
	    } else
		buffer.append("\n" + attrs.item(i).getNodeName() + ": " //$NON-NLS-1$ //$NON-NLS-2$
			+ attrs.item(i).getNodeValue());

	}

	return buffer.toString();
    }

    Rectangle getNodeBounds(nsIDOMNode visualNode) {

	return XulRunnerVpeUtils.getElementBounds(visualNode);
    }

    static boolean canInsertAfter(int x, int y, Rectangle rect) {
	if (y > (rect.y + rect.height) || x > (rect.x + rect.width)) {
	    return true;
	}
	return y >= rect.x && x > (rect.x + rect.width / 2);
    }

    static nsIDOMNode getLastAppreciableVisualChild(nsIDOMNode visualParent) {
	nsIDOMNode visualLastChild = null;
	nsIDOMNodeList visualChildren = visualParent.getChildNodes();
	long len = visualChildren.getLength();
	for (long i = len - 1; i >= 0; i--) {
	    nsIDOMNode visualChild = visualChildren.item(i);
	    if (!isPseudoElement(visualChild) && !isAnonElement(visualChild)) {
		visualLastChild = visualChild;
		break;
	    }
	}
	return visualLastChild;
    }

    void correctVisualDropPosition(VpeVisualInnerDropInfo newVisualDropInfo,
	    VpeVisualInnerDropInfo oldVisualDropInfo) {
	nsIDOMNode newVisualDropContainer = newVisualDropInfo
		.getDropContainer();
	nsIDOMNode oldVisualDropContainer = oldVisualDropInfo
		.getDropContainer();

	if (newVisualDropContainer.equals(oldVisualDropContainer)) {
	    newVisualDropInfo.setDropOffset(oldVisualDropInfo.getDropOffset());
	    return;
	}

	nsIDOMNode child = oldVisualDropContainer;
	while (child != null && child.getNodeType() != Node.DOCUMENT_NODE) {
	    nsIDOMNode parent = child.getParentNode();
	    if (newVisualDropContainer.equals(parent)) {
		long offset = VisualDomUtil.getOffset(child);
		Rectangle rect = getNodeBounds(child);
		if (canInsertAfter(oldVisualDropInfo.getMouseX(),
			oldVisualDropInfo.getMouseY(), rect)) {
		    offset++;
		}
		newVisualDropInfo.setDropOffset(offset);
	    }
	    child = parent;
	}
    }

    public nsIDOMRange createDOMRange() {
	return xulRunnerEditor.createDOMRange();
    }

    public nsIDOMRange createDOMRange(nsIDOMNode selectedNode) {
	nsIDOMRange range = createDOMRange();
	range.selectNode(selectedNode);
	return range;
    }

    public static boolean isIncludeElement(nsIDOMElement visualElement) {
	return YES_STRING.equalsIgnoreCase(visualElement
		.getAttribute(INCLUDE_ELEMENT_ATTR));
    }

    public static void markIncludeElement(nsIDOMElement visualElement) {
	visualElement.setAttribute(INCLUDE_ELEMENT_ATTR, YES_STRING);
    }

    protected void setReadOnlyElement(nsIDOMElement node) {
	String style = node.getAttribute(VpeStyleUtil.ATTRIBUTE_STYLE);
	style = VpeStyleUtil.setParameterInStyle(style, "-moz-user-modify", //$NON-NLS-1$
		"read-only"); //$NON-NLS-1$
	node.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE, style);
    }

    void setMoveCursor(nsIDOMMouseEvent mouseEvent) {

	nsIDOMElement selectedElement = xulRunnerEditor
		.getLastSelectedElement();
	if (selectedElement != null && canInnerDrag(selectedElement)) {
	    String styleClasses = selectedElement.getAttribute(HTML.ATTR_CLASS);
	    if (inDragArea(getNodeBounds(selectedElement), VisualDomUtil
		    .getMousePoint(mouseEvent))) {
		// change cursor
		if (styleClasses == null
			|| !(styleClasses.contains(ATTR_DRAG_AVAILABLE_CLASS))) {
		    // change cursor style to move
		    styleClasses = ATTR_DRAG_AVAILABLE_CLASS + " " //$NON-NLS-1$
			    + styleClasses;
		}
	    } else {
		// change cursor style to normal
		if (styleClasses != null) {

		    styleClasses = styleClasses.replaceAll(
			    ATTR_DRAG_AVAILABLE_CLASS, ""); //$NON-NLS-1$
		}
	    }
	    selectedElement.setAttribute(HTML.ATTR_CLASS, styleClasses);
	}
    }

    private boolean inDragArea(Rectangle dragArea, Point mousePoint) {
	// TODO add drag and drop support
	return dragArea.contains(mousePoint)
		&& mousePoint.x < (dragArea.x + DRAG_AREA_WIDTH)
		&& mousePoint.y < (dragArea.y + DRAG_AREA_HEIGHT);
    }

    nsIDOMElement getDragElement(nsIDOMMouseEvent mouseEvent) {

	nsIDOMElement selectedElement = xulRunnerEditor
		.getLastSelectedElement();
	if (selectedElement != null && canInnerDrag(selectedElement)) {
	    if (inDragArea(getNodeBounds(selectedElement), VisualDomUtil
		    .getMousePoint(mouseEvent))) {
		return selectedElement;
	    }
	}
	return null;
    }

    VpeSourceInnerDragInfo getSourceInnerDragInfo(
	    VpeVisualInnerDragInfo visualDragInfo) {
	nsIDOMNode visualNode = visualDragInfo.getNode();
	int offset = visualDragInfo.getOffset();
	int length = visualDragInfo.getLength();

	VpeNodeMapping nodeMapping = domMapping.getNearNodeMapping(visualNode);
	Node sourceNode = nodeMapping.getSourceNode();

	if (sourceNode != null) {
	    switch (sourceNode.getNodeType()) {
	    case Node.TEXT_NODE:
		int end = TextUtil.sourceInnerPosition(visualNode
			.getNodeValue(), offset + length);
		offset = TextUtil.sourceInnerPosition(
			visualNode.getNodeValue(), offset);
		length = end - offset;
		break;
	    case Node.ELEMENT_NODE:
		if (visualNode.getNodeType() == Node.TEXT_NODE) {
		    // it's attribute
		    sourceNode = null;
		    if (isTextEditable(visualNode)) {
			String[] atributeNames = ((VpeElementMapping) nodeMapping)
				.getTemplate().getOutputAtributeNames();
			if (atributeNames != null && atributeNames.length > 0) {
			    Element sourceElement = (Element) nodeMapping
				    .getSourceNode();
			    sourceNode = sourceElement
				    .getAttributeNode(atributeNames[0]);
			    end = TextUtil.sourceInnerPosition(visualNode
				    .getNodeValue(), offset + length);
			    offset = TextUtil.sourceInnerPosition(visualNode
				    .getNodeValue(), offset);
			    length = end - offset;
			}
		    }
		}
		break;
	    }
	}
	return new VpeSourceInnerDragInfo(sourceNode, offset, length);
    }

    public nsIDOMText getOutputTextNode(Attr attr) {
	Element sourceElement = ((Attr) attr).getOwnerElement();
	VpeElementMapping elementMapping = domMapping
		.getNearElementMapping(sourceElement);
	if (elementMapping != null) {

			return elementMapping.getTemplate().getOutputTextNode(pageContext,
					sourceElement, elementMapping.getData());
		}
	return null;
    }

    nsIDOMElement getLastSelectedElement() {

	return xulRunnerEditor.getLastSelectedElement();
    }

    public void pushIncludeStack(VpeIncludeInfo includeInfo) {
	includeStack.add(includeInfo);
    }

    public VpeIncludeInfo popIncludeStack() {
	VpeIncludeInfo includeInfo = null;
	if (includeStack.size() > 0) {
	    includeInfo = (VpeIncludeInfo) includeStack.remove(includeStack
		    .size() - 1);
	}
	return includeInfo;
    }

    public boolean isFileInIncludeStack(IFile file) {
	if (file == null)
	    return false;
	for (int i = 0; i < includeStack.size(); i++) {
	    if (file.equals(((VpeIncludeInfo) includeStack.get(i)).getFile())) {
		return true;
	    }
	}
	return false;
    }

    protected boolean isCurrentMainDocument() {
	return includeStack.size() <= 1;
    }

    public int getCurrentMainIncludeOffset() {
	if (includeStack.size() <= 1)
	    return -1;
	VpeIncludeInfo info = (VpeIncludeInfo) includeStack.get(1);
	return ((IndexedRegion) info.getElement()).getStartOffset();
    }

    public VpeIncludeInfo getCurrentIncludeInfo() {
	if (includeStack.size() <= 0)
	    return null;
	return (VpeIncludeInfo) includeStack.get(includeStack.size() - 1);
    }

    public VpeIncludeInfo getRootIncludeInfo() {
	if (includeStack.size() <= 1)
	    return null;
	return (VpeIncludeInfo) includeStack.get(1);
    }

    public void dispose() {
	clearIncludeDocuments();
	includeDocuments = null;
	cleanHead();
	domMapping.clear(visualContentArea);
	pageContext.dispose();
	super.dispose();
    }

    private void clearIncludeDocuments() {
	Collection<Document> documents = includeDocuments.values();
	for (Iterator iterator = documents.iterator(); iterator.hasNext();) {
	    Document document = (Document) iterator.next();
	    VpeCreatorUtil.releaseDocumentFromRead(document);
	}
	includeDocuments.clear();
    }

    // protected Map createXmlns(Element sourceNode) {
    // NamedNodeMap attrs = ((Element) sourceNode).getAttributes();
    // if (attrs != null) {
    // Map xmlnsMap = new HashMap();
    // for (int i = 0; i < attrs.getLength(); i++) {
    // addTaglib(sourceNode, xmlnsMap, attrs.item(i).getNodeName(),
    // true);
    // }
    // if (xmlnsMap.size() > 0) {
    // return xmlnsMap;
    // }
    // }
    // return null;
    // }

    // private void setXmlnsAttribute(VpeElementMapping elementMapping,
    // String name, String value) {
    // Element sourceElement = (Element) elementMapping.getSourceNode();
    // if (sourceElement != null) {
    // Map xmlnsMap = elementMapping.getXmlnsMap();
    // if (xmlnsMap == null)
    // xmlnsMap = new HashMap();
    // addTaglib(sourceElement, xmlnsMap, name, true);
    // elementMapping.setXmlnsMap(xmlnsMap.size() > 0 ? xmlnsMap : null);
    // }
    // }

    // private void removeXmlnsAttribute(VpeElementMapping elementMapping,
    // String name) {
    // Element sourceElement = (Element) elementMapping.getSourceNode();
    // if (sourceElement != null) {
    // Map xmlnsMap = elementMapping.getXmlnsMap();
    // if (xmlnsMap != null) {
    // Object id = xmlnsMap.remove(name);
    // if (id != null) {
    // pageContext.setTaglib(((Integer) id).intValue(), null,
    // null, true);
    // elementMapping.setXmlnsMap(xmlnsMap.size() > 0 ? xmlnsMap
    // : null);
    // }
    // }
    // }
    // }
    //
    // private void addTaglib(Element sourceElement, Map xmlnsMap,
    // String attrName, boolean ns) {
    // Attr attr = sourceElement.getAttributeNode(attrName);
    // if (ATTR_XMLNS.equals(attr.getPrefix())) {
    // xmlnsMap.put(attr.getNodeName(), Integer.valueOf(attr.hashCode()));
    // pageContext.setTaglib(attr.hashCode(), attr.getNodeValue(), attr
    // .getLocalName(), ns);
    // }
    // }

    /**
     * @return the dnd
     */
    public VpeDnD getDnd() {

	return dnd;
    }

    /**
     * @param dnd
     *                the dnd to set
     */
    public void setDnd(VpeDnD dnd) {

	this.dnd = dnd;
    }

    /**
     * @return the pageContext
     */
    protected VpePageContext getPageContext() {
	return pageContext;
    }

    /**
     * @param pageContext
     *                the pageContext to set
     */
    protected void setPageContext(VpePageContext pageContext) {
	this.pageContext = pageContext;
    }

    /**
     * @return the visualDocument
     */
    protected nsIDOMDocument getVisualDocument() {
	return visualDocument;
    }

    /**
     * @param visualDocument
     *                the visualDocument to set
     */
    protected void setVisualDocument(nsIDOMDocument visualDocument) {
	this.visualDocument = visualDocument;
    }

    /**
     * Check this file is facelet
     * 
     * @return this if file is facelet, otherwize false
     */
    private boolean isFacelet() {
	boolean isFacelet = false;

	IEditorInput iEditorInput = pageContext.getEditPart().getEditorInput();
	if (iEditorInput instanceof IFileEditorInput) {
	    IFileEditorInput iFileEditorInput = (IFileEditorInput) iEditorInput;

	    IFile iFile = iFileEditorInput.getFile();

	    IProject project = iFile.getProject();
	    IModelNature nature = EclipseResourceUtil.getModelNature(project);
	    if (nature != null) {
		XModel model = nature.getModel();
		XModelObject webXML = WebAppHelper.getWebApp(model);
		XModelObject param = WebAppHelper.findWebAppContextParam(
			webXML, "javax.faces.DEFAULT_SUFFIX"); //$NON-NLS-1$
		if (param != null) {
		    String value = param.getAttributeValue("param-value"); //$NON-NLS-1$

		    if (value.length() != 0 && iFile.getName().endsWith(value)) {
			isFacelet = true;
		    }
		}
	    }
	}

	return isFacelet;
    }

    /**
     * Create a visual element for text node
     * 
     * @param sourceNode
     * @param registerFlag
     * @return a visual element for text node
     */

    protected nsIDOMNode createTextNode(Node sourceNode, boolean registerFlag) {
	String sourceText = sourceNode.getNodeValue();

	/*
	 * Max Areshkau this code causes very slow work of visual editor
	 * when we editing in big files txt nodes.For example exmployee.xhtml
	 * from JBIDE1105
	 * 
	 * Denis Maliarevich: 
	 * To fix JBIDE-2003 and JBIDE-2042 
	 * this code should be uncommented.
	 */
	if (sourceText.trim().length() <= 0) {
		if (registerFlag) {
			registerNodes(new VpeNodeMapping(sourceNode, null));
		}
		return null;
	}

	if (faceletFile) {
	    Matcher matcher_EL = REGEX_EL.matcher(sourceText);
	    if (matcher_EL.find()) {
		BundleMap bundle = pageContext.getBundle();
		int offset = pageContext.getVisualBuilder()
			.getCurrentMainIncludeOffset();
		if (offset == -1)
		    offset = ((IndexedRegion) sourceNode).getStartOffset();
		String jsfValue = bundle.getBundleValue(sourceText, offset);
		sourceText = jsfValue;
	    }
	}
	String visualText = TextUtil.visualText(sourceText);

	nsIDOMNode visualNewTextNode = visualDocument
		.createTextNode(visualText);
	nsIDOMElement element = visualDocument.createElement(HTML.TAG_SPAN);
	element.setAttribute(HTML.ATTR_STYLE, ""); //$NON-NLS-1$
	element.appendChild(visualNewTextNode);
	if (registerFlag) {
	    registerNodes(new VpeNodeMapping(sourceNode, element));
	}

	return element;
    }

    /**
     * @return the xulRunnerEditor
     */
    public XulRunnerEditor getXulRunnerEditor() {
	return xulRunnerEditor;
    }

    /**
     * @param xulRunnerEditor
     *                the xulRunnerEditor to set
     */
    public void setXulRunnerEditor(XulRunnerEditor xulRunnerEditor) {
	this.xulRunnerEditor = xulRunnerEditor;
    }

    public Map<IFile, Document> getIncludeDocuments() {
	return includeDocuments;
    }
}
