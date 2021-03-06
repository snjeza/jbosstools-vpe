/******************************************************************************* 
 * Copyright (c) 2007 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/


package org.jboss.tools.vpe.dnd;

import static org.jboss.tools.vpe.xulrunner.util.XPCOM.queryInterface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.eclipse.swt.events.TypedEvent;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.jboss.tools.common.model.ui.editors.dnd.context.DropContext;
import org.jboss.tools.common.model.ui.editors.dnd.context.IDNDTextEditor;
import org.jboss.tools.jst.web.kb.PageContextFactory;
import org.jboss.tools.jst.web.ui.internal.editor.editor.IVisualController;
import org.jboss.tools.vpe.editor.VisualController;
import org.jboss.tools.vpe.editor.context.AbstractPageContext;
import org.jboss.tools.vpe.editor.context.VpePageContext;
import org.jboss.tools.vpe.editor.util.HTML;
import org.jboss.tools.vpe.editor.util.SourceDomUtil;
import org.jboss.tools.vpe.editor.util.VpeDndUtil;
import org.jboss.tools.vpe.xulrunner.util.XPCOM;
import org.mozilla.interfaces.nsIComponentManager;
import org.mozilla.interfaces.nsIDOMDocument;
import org.mozilla.interfaces.nsIDOMElement;
import org.mozilla.interfaces.nsIDOMNSDocument;
import org.mozilla.interfaces.nsIDragService;
import org.mozilla.interfaces.nsIDragSession;
import org.mozilla.interfaces.nsIServiceManager;
import org.mozilla.interfaces.nsISupports;
import org.mozilla.interfaces.nsISupportsString;
import org.mozilla.interfaces.nsITransferable;
import org.mozilla.xpcom.Mozilla;
import org.w3c.dom.Node;


/**
 * The Class DndUtil.
 * 
 * @author Eugene Stherbin
 */
public class DndUtil {
    

    /** The Constant kTextMime. */
    public static final String kTextMime = "text/plain"; //$NON-NLS-1$

    /** The Constant kUnicodeMime. */
    public static final String kUnicodeMime = "text/unicode"; //$NON-NLS-1$

    /** The Constant kHTMLMime. */
    public static final String kHTMLMime = "text/html"; //$NON-NLS-1$

    /** The Constant kAOLMailMime. */
    public static final String kAOLMailMime = "AOLMAIL"; //$NON-NLS-1$

    /** The Constant kPNGImageMime. */
    public static final String kPNGImageMime = "image/png"; //$NON-NLS-1$

    /** The Constant kJPEGImageMime. */
    public static final String kJPEGImageMime = "image/jpg"; //$NON-NLS-1$

    /** The Constant kGIFImageMime. */
    public static final String kGIFImageMime = "image/gif"; //$NON-NLS-1$

    /** The Constant kFileMime. */
    public static final String kFileMime = "application/x-moz-file"; //$NON-NLS-1$

    /** The Constant kURLMime. */
    public static final String kURLMime = "text/x-moz-url"; //$NON-NLS-1$

    /** The Constant kURLDataMime. */
    public static final String kURLDataMime = "text/x-moz-url-data"; //$NON-NLS-1$

    /** The Constant kURLDescriptionMime. */
    public static final String kURLDescriptionMime = "text/x-moz-url-desc"; //$NON-NLS-1$

    /** The Constant kNativeImageMime. */
    public static final String kNativeImageMime = "application/x-moz-nativeimage"; //$NON-NLS-1$

    /** The Constant kNativeHTMLMime. */
    public static final String kNativeHTMLMime = "application/x-moz-nativehtml"; //$NON-NLS-1$

    /** The Constant kFilePromiseURLMime. */
    public static final String kFilePromiseURLMime = "application/x-moz-file-promise-url"; //$NON-NLS-1$

    /** The Constant kFilePromiseMime. */
    public static final String kFilePromiseMime = "application/x-moz-file-promise"; //$NON-NLS-1$

    /** The Constant kFilePromiseDirectoryMime. */
    public static final String kFilePromiseDirectoryMime = "application/x-moz-file-promise-dir"; //$NON-NLS-1$
    
	public static final String VPE_XPATH_FLAVOR = "vpe/xpath"; //$NON-NLS-1$
    
    /**
     * The Constructor.
     */
    private DndUtil() {
        super();
    }

    /**
     * Fire dn D event.
     * 
     * @param dropContext the drop context
     * @param event the event
     * @param textEditor the text editor
     */
    public static void fireDnDEvent(DropContext dropContext,
    		IDNDTextEditor textEditor, TypedEvent event) {
        dropContext.runDropCommand(textEditor, event);
    }

    /**
     * Returns an instance of {@link DragTransferData}
     * for the current DnD session.
     */
	public static DragTransferData getDragTransferData(String... flavors) {
		nsIDragSession dragSession = getCurrentDragSession();
		/*
		 * https://issues.jboss.org/browse/JBIDE-10728
		 */
		if (dragSession != null) {
			String []supportedFlavors = getSupportedDataFlavors(dragSession, flavors);
			if (supportedFlavors.length > 0) {
				nsITransferable iTransferable = createTransferable(supportedFlavors);
				
				String[] aFlavor = new String[1];
				nsISupports[] aValue = new nsISupports[1];
				long[] aDataLen = new long[1];
				
				dragSession.getData(iTransferable, 0);
				iTransferable.getAnyTransferData(aFlavor, aValue, aDataLen);
				return new DragTransferData(aFlavor[0], aValue[0], aDataLen[0]);
			}
		}
		return null;
	}
	
	public static String getDragTransferDataAsString(String... flavors) {
		DragTransferData transferData = getDragTransferData(flavors);
		if (transferData == null || transferData.getValue() == null) {
			return null;
		}
		
		nsISupports value = transferData.getValue();
		if (VpeDndUtil.isNsIStringInstance(value)) {
			return queryInterface(value, nsISupportsString.class).getData();
		} else {
			return null;
		}
	}

	public static nsITransferable createTransferable(String... flavors) {
		final nsIComponentManager componentManager
        		= Mozilla.getInstance().getComponentManager();

        final nsITransferable iTransferable = (nsITransferable) 
        		componentManager.createInstanceByContractID(
        				XPCOM.NS_TRANSFERABLE_CONTRACTID, null,
        				nsITransferable.NS_ITRANSFERABLE_IID);

        for (final String flavor : flavors) {
        	iTransferable.addDataFlavor(flavor);
        }

		return iTransferable;
	}

	public static String[] getSupportedDataFlavors(
			final nsIDragSession dragSession, String... flavors) {
		final List<String> supportedDataFlavors = new ArrayList<String>();
		/*
		 * https://issues.jboss.org/browse/JBIDE-10728
		 */
		if (dragSession != null) {
			for (final String flavor : flavors) {
				if (dragSession.isDataFlavorSupported(flavor)) {
					supportedDataFlavors.add(flavor);
				}
			}
		}
		return supportedDataFlavors.toArray(new String[supportedDataFlavors.size()]);
	}

	public static nsIDragSession getCurrentDragSession() {
		nsIServiceManager serviceManager = Mozilla.getInstance()
				.getServiceManager();
        nsIDragService dragService = (nsIDragService) serviceManager
        		.getServiceByContractID(
        				"@mozilla.org/widget/dragservice;1", //$NON-NLS-1$
        				nsIDragService.NS_IDRAGSERVICE_IID);
        final nsIDragSession dragSession = dragService.getCurrentSession();
		return dragSession;
	}
	
	public static class DragTransferData {
		private final String flavor;
		private final long dataLen;
		private final nsISupports value;

		public DragTransferData(final String flavor, final nsISupports value,
				final long dataLen) {
			this.flavor = flavor;
			this.value = value;
			this.dataLen = dataLen;
		}

		public String getFlavor() {
			return flavor;
		}
		public nsISupports getValue() {
			return value;
		}
		public long getDataLen() {
			return dataLen;
		}
	}
	
	
	public static void setTemporaryDndElement(nsIDOMElement element,
			boolean temporary) {
		if (temporary) {
			element.setAttribute("vpeTemporaryDndElement",
					Boolean.TRUE.toString());
		} else {
			element.removeAttribute("vpeTemporaryDndElement");
		}
	}

	public static boolean isTemporaryDndElement(nsIDOMElement element) {
		String attribute = element.getAttribute("vpeTemporaryDndElement");
		if (Boolean.TRUE.toString().equals(attribute)) {
			return true;
		} else {
			return false;
		}
	}
	
	public static nsIDOMElement getElementFromPoint(nsIDOMDocument document,
			int clientX, int clientY) {
		nsIDOMNSDocument nsDocument = queryInterface(document, nsIDOMNSDocument.class);

		nsIDOMElement element = nsDocument.elementFromPoint(clientX, clientY);

		Stack<nsIDOMElement> hiddenElements = new Stack<nsIDOMElement>();
		Stack<String> hiddenElementsStyles = new Stack<String>();
		while (element != null && isTemporaryDndElement(element)) {
			hiddenElements.push(element);
			hiddenElementsStyles.push(element.getAttribute(HTML.ATTR_STYLE));

			element.setAttribute(HTML.ATTR_STYLE, "display:none !important;");
			element = nsDocument.elementFromPoint(clientX, clientY);
		}

		while (!hiddenElements.empty()) {
			nsIDOMElement element2 = hiddenElements.pop();
			String style = hiddenElementsStyles.pop();
			if (style == null) {
				element2.removeAttribute(HTML.ATTR_STYLE);
			} else {
				element2.setAttribute(HTML.ATTR_STYLE, style);
			}
		}

		return element;
	}
	
	public static Node getNodeFromDragSession(IVisualController controller) {
	//public static Node getNodeFromDragSession(AbstractPageContext pageContext) {
		String xPath = DndUtil.getDragTransferDataAsString(VPE_XPATH_FLAVOR);
		if (xPath != null) {
			return SourceDomUtil.getNodeByXPath(
					((IDOMModel)controller.getModel()).getDocument(), xPath);
		} else {
			return null;
		}
	}
}
