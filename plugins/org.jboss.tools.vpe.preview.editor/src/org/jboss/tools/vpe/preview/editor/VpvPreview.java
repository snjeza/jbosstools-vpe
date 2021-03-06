package org.jboss.tools.vpe.preview.editor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorPart;
import org.jboss.tools.vpe.editor.mozilla.listener.EditorLoadWindowListener;

public class VpvPreview extends VpvEditor {
	private EditorLoadWindowListener editorLoadWindowListener;

	public VpvPreview(IEditorPart sourceEditor) {
		setSourceEditor(sourceEditor);
		// XXX: this code avoids NPE into JSP editor. Need to handle this error better
		sourceEditor.getAdapter(IDocument.class);
	}
	
	@Override
	public void createPartControl(Composite parent) {
		try {
			Browser browser = new Browser(parent, SWT.NONE);
			browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			setBrowser(browser);
			if (editorLoadWindowListener != null) {
				editorLoadWindowListener.load();
			}
			inizializeEditorListener();
		} catch (Throwable t) {
			//cannot create browser. show error message then
			errorWrapper.showError(parent, t);
		}
	}
	
	public void load() {
		reload();
	}
	
	@Override
	public void setEditorLoadWindowListener(EditorLoadWindowListener listener) {
		editorLoadWindowListener = listener;
	}
	
	@Override
	public void dispose() {
		setEditorLoadWindowListener(null);
		super.dispose();
	}
}
