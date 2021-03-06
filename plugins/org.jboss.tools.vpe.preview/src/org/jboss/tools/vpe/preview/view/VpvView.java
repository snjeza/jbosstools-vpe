/*******************************************************************************
 * Copyright (c) 2013-2014 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.vpe.preview.view;

import static org.jboss.tools.vpe.preview.core.server.HttpConstants.ABOUT_BLANK;
import static org.jboss.tools.vpe.preview.Messages.VISUAL_PREVIEW;

import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.EditorReference;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.jboss.tools.usage.event.UsageEventType;
import org.jboss.tools.usage.event.UsageReporter;
import org.jboss.tools.vpe.preview.Activator;
import org.jboss.tools.vpe.preview.core.exceptions.BrowserErrorWrapper;
import org.jboss.tools.vpe.preview.core.exceptions.CannotOpenExternalFileException;
import org.jboss.tools.vpe.preview.core.exceptions.Messages;
import org.jboss.tools.vpe.preview.core.transform.VpvVisualModel;
import org.jboss.tools.vpe.preview.core.transform.VpvVisualModelHolder;
import org.jboss.tools.vpe.preview.core.util.ActionBarUtil;
import org.jboss.tools.vpe.preview.core.util.EditorUtil;
import org.jboss.tools.vpe.preview.core.util.NavigationUtil;
import org.jboss.tools.vpe.preview.core.util.SuitableFileExtensions;

/**
 * @author Yahor Radtsevich (yradtsevich)
 * @author Ilya Buziuk (ibuziuk)
 */
@SuppressWarnings("restriction")
public class VpvView extends ViewPart implements VpvVisualModelHolder {
	public static final String ID = "org.jboss.tools.vpe.vpv.view.VpvView"; //$NON-NLS-1$

	private Browser browser;
	private ActionBarUtil actionBarUtil;
	private Job currentJob;
	private VpvVisualModel visualModel;
	private int modelHolderId;
	private EditorListener editorListener;
	private SelectionListener selectionListener;
	private IEditorPart currentEditor;
	private IDocumentListener documentListener;
	
	protected BrowserErrorWrapper errorWrapper = new BrowserErrorWrapper();

	public VpvView() {
		setModelHolderId(Activator.getDefault().getVisualModelHolderRegistry().registerHolder(this));
		UsageEventType eventType = Activator.getDefault().getPreviewEventType();
		UsageReporter.getInstance().countEvent(eventType.event(UsageEventType.OPEN_ACTION));
	}

	@Override
	public void dispose() {
		if (editorListener != null) {
			getSite().getPage().removePartListener(editorListener);
		}
		if (selectionListener != null) {
			getSite().getPage().removePostSelectionListener(selectionListener);			
		}
		Activator.getDefault().getVisualModelHolderRegistry().unregisterHolder(this);
		super.dispose();
	}

	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		try {
			browser = new Browser(parent, SWT.NONE);
			browser.setUrl(ABOUT_BLANK);

			browser.addLocationListener(new LocationAdapter() {
				@Override
				public void changed(LocationEvent event) {
					NavigationUtil.disableAlert(browser);
					NavigationUtil.disableLinks(browser);
					NavigationUtil.disableInputs(browser);

					ISelection currentSelection = getCurrentSelection();
					NavigationUtil.updateSelectionAndScrollToIt(currentSelection, browser, visualModel);
				}
			});

			browser.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent event) {
					NavigationUtil.navigateToVisual(currentEditor, browser, visualModel, event.x, event.y);
				}

			});
	
			inizializeSelectionListener();
			inizializeEditorListener(browser, modelHolderId);
	
			IActionBars bars = getViewSite().getActionBars();
			actionBarUtil = new ActionBarUtil(browser);
			actionBarUtil.fillLocalToolBar(bars.getToolBarManager());
		} catch (Throwable t) {
			errorWrapper.showError(parent, t);
		}
	}

	private void inizializeEditorListener(Browser browser, int modelHolderId) {
		editorListener = new EditorListener();
		getSite().getPage().addPartListener(editorListener);
		editorListener.showBootstrapPart();
	}

	private void inizializeSelectionListener() {
		selectionListener = new SelectionListener();
		getSite().getPage().addPostSelectionListener(selectionListener);
	}

	public void setFocus() {
		if (browser != null) {
			browser.setFocus();
		}
	}

	@Override
	public void setVisualModel(VpvVisualModel visualModel) {
		this.visualModel = visualModel;
	}

	public void setModelHolderId(int modelHolderId) {
		this.modelHolderId = modelHolderId;
	}

	public IEditorPart getCurrentEditor() {
		return currentEditor;
	}

	public void editorChanged(IEditorPart editor) {
		if (currentEditor == editor) {
			// do nothing
		} else if (editor == null) {
			browser.setUrl(ABOUT_BLANK);
			setCurrentEditor(null);
		} else if (EditorUtil.isImportant(editor)) {
			formRequestToServer(editor);
			setCurrentEditor(editor);
		} else {
			browser.setUrl(ABOUT_BLANK);
			setCurrentEditor(null);
		}
	}

	private void setCurrentEditor(IEditorPart currentEditor) {
		if (this.currentEditor != null) {
			IDocument document = (IDocument) this.currentEditor.getAdapter(IDocument.class);
			if (document != null) {
				document.removeDocumentListener(getDocumentListener());
			}
		}

		this.currentEditor = currentEditor;

		if (this.currentEditor != null) {
			IDocument document = (IDocument) this.currentEditor.getAdapter(IDocument.class);
			String fileExtension = EditorUtil.getFileExtensionFromEditor(this.currentEditor);
			
			// Trying to extract document for js and css files
			if (document == null && SuitableFileExtensions.isCssOrJs(fileExtension)) {
				document = ((AbstractDecoratedTextEditor) this.currentEditor).getDocumentProvider().getDocument(currentEditor.getEditorInput());
			}

			if (document != null) {
				document.addDocumentListener(getDocumentListener());
			}
		}
	}

	private IDocumentListener getDocumentListener() {
		if (documentListener == null) {
			documentListener = new IDocumentListener() {

				@Override
				public void documentAboutToBeChanged(DocumentEvent event) {
				}

				@Override
				public void documentChanged(DocumentEvent event) {
					if (actionBarUtil.isAutomaticRefreshEnabled()) {
						String fileExtension = EditorUtil.getFileExtensionFromEditor(currentEditor);
						if (SuitableFileExtensions.isCssOrJs(fileExtension)) {
							currentEditor.doSave(new NullProgressMonitor()); // saving all js and css stuff
						}
						updatePreview();
					}
				}

			};
		}

		return documentListener;
	}

	

	private Job createPreviewUpdateJob() {
		Job job = new UIJob("Preview Update") { //$NON-NLS-1$
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				if (!browser.isDisposed()) {
					refresh(browser);
				}
				return Status.OK_STATUS;
			}
		};
		return job;
	}

	private void updatePreview() {
		if (currentJob == null || currentJob.getState() != Job.WAITING) {
			if (currentJob != null && currentJob.getState() == Job.SLEEPING) {
				currentJob.cancel();
			}
			currentJob = createPreviewUpdateJob();
		}

		currentJob.schedule(500);
	}

	private void refresh(Browser browser) {
		browser.setUrl(browser.getUrl());
	}

	private ISelection getCurrentSelection() {
		Activator activator = Activator.getDefault();
		IWorkbench workbench = activator.getWorkbench();
		IWorkbenchWindow workbenchWindow = workbench.getActiveWorkbenchWindow();
		ISelectionService selectionService = workbenchWindow.getSelectionService();
		ISelection selection = selectionService.getSelection();
		return selection;
	}

	private boolean isCurrentEditor(IEditorPart editorPart) {
		if (currentEditor == editorPart) {
			return true;
		}
		return false;
	}

	private void formRequestToServer(IEditorPart editor) {
		IFile file = EditorUtil.getFileOpenedInEditor(editor);
		String fileExtension = null;
		removeErrorMessage();
		if (file != null && file.exists()) {
			fileExtension = file.getFileExtension();
			changeControlVisibility(browser, true);
			browser.getParent().setLayout(new FillLayout());
		} else {
			changeControlVisibility(browser, false);
			errorWrapper.showError(browser.getParent(), 
					new CannotOpenExternalFileException(MessageFormat.format(Messages.CANNOT_SHOW_EXTERNAL_FILE, VISUAL_PREVIEW)));
		}
		browser.getParent().layout();
		
		if (SuitableFileExtensions.contains(fileExtension)) {
			if (SuitableFileExtensions.isHTML(fileExtension)) {
				String url;
				try {
					url = EditorUtil.formUrl(file, modelHolderId, "" + Activator.getDefault().getServer().getPort()); //$NON-NLS-1$
					browser.setUrl(url);
				} catch (UnsupportedEncodingException e) {
					Activator.logError(e);
				}
			}
		} else {
			browser.setUrl(ABOUT_BLANK);
		}
	}

	private void changeControlVisibility(Control c, boolean visible) {
		GridData data = new GridData();
		data.exclude = !visible;
		c.setLayoutData(data);
		c.setVisible(visible);
	}
	
	private void removeErrorMessage() {
		Control[] children = browser.getParent().getChildren();
		for(Control c : children) {
			if(!c.equals(browser)) {
				c.dispose();
			}
		}
	}
	
	private class EditorListener implements IPartListener2 {

		@Override
		public void partActivated(IWorkbenchPartReference partRef) {
			if (partRef instanceof EditorReference) {
				IEditorPart editor = ((EditorReference) partRef).getEditor(false);
				editorChanged(editor);
			}
		}

		@Override
		public void partOpened(IWorkbenchPartReference partRef) {
		}

		@Override
		public void partClosed(IWorkbenchPartReference partRef) {
			if (partRef instanceof EditorReference) {
				IEditorPart editorPart = ((EditorReference) partRef).getEditor(false);
				if (isCurrentEditor(editorPart)) {
					editorChanged(null);
				}
			}
		}

		@Override
		public void partBroughtToTop(IWorkbenchPartReference partRef) {
		}

		@Override
		public void partDeactivated(IWorkbenchPartReference partRef) {
		}

		@Override
		public void partHidden(IWorkbenchPartReference partRef) {
		}

		@Override
		public void partVisible(IWorkbenchPartReference partRef) {
		}

		@Override
		public void partInputChanged(IWorkbenchPartReference partRef) {
			IWorkbenchPage page = partRef.getPage();
			if (page != null) {
				IEditorPart editor = page.getActiveEditor();
				if (editor != null) {
					formRequestToServer(editor);
					setCurrentEditor(editor);
				}
			}
		}

		public void showBootstrapPart() {
			IEditorPart activeEditor = getActivePage().getActiveEditor();
			formRequestToServer(activeEditor);
		}

	}

	private class SelectionListener implements ISelectionListener {

		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (selection instanceof IStructuredSelection && EditorUtil.isInCurrentEditor((IStructuredSelection) selection, currentEditor)) {
				NavigationUtil.updateSelectionAndScrollToIt(selection, browser, visualModel);
			}
		}
	}
	
	private IWorkbenchPage getActivePage() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
	}
		
}
