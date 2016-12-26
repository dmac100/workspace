package workbook;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import workbook.controller.MainController;
import workbook.editor.ui.Editor;
import workbook.event.MajorRefreshEvent;
import workbook.event.MinorRefreshEvent;
import workbook.event.ScriptTypeChangeEvent;
import workbook.layout.FillLayoutBuilder;
import workbook.layout.GridDataBuilder;
import workbook.layout.GridLayoutBuilder;
import workbook.script.Engine;
import workbook.script.GroovyEngine;
import workbook.script.JavascriptEngine;
import workbook.script.RubyEngine;
import workbook.util.SwtUtil;
import workbook.view.InputDialog;
import workbook.view.MenuBuilder;
import workbook.view.TabbedView;
import workbook.view.TabbedViewFactory;
import workbook.view.TabbedViewFactory.ViewInfo;
import workbook.view.TabbedViewLayout;
import workbook.view.TabbedViewLayout.FolderPosition;

/**
 * Main view for the workbook that contains all the controls of this program.
 */
public class MainView {
	private final Shell shell;
	private final MainController mainController;
	private final EventBus eventBus;
	private final TabbedViewLayout tabbedViewLayout;
	private final TabbedViewFactory viewFactory;
	
	private final Composite toolbarComposite;
	private final Composite tabsComposite;
	
	private String currentFileLocation = null;
	
	public MainView(Shell shell, MainController mainController, EventBus eventBus) {
		this.shell = shell;
		this.eventBus = eventBus;
		
		shell.setLayout(new GridLayoutBuilder().numColumns(1).makeColumnsEqualWidth(false).marginHeight(0).marginWidth(0).verticalSpacing(0).build());

		toolbarComposite = new Composite(shell, SWT.NONE);
		toolbarComposite.setLayout(new FillLayoutBuilder().marginHeight(0).marginWidth(0).build());
		toolbarComposite.setLayoutData(new GridDataBuilder().grabExcessHorizontalSpace(true).build());
		
		tabsComposite = new Composite(shell, SWT.NONE);
		tabsComposite.setLayoutData(new GridDataBuilder().fillHorizontal().fillVertical().build());
		tabsComposite.setLayout(new FillLayout());
		tabsComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));
		
		this.mainController = mainController;
		tabbedViewLayout = new TabbedViewLayout(tabsComposite);
		
		this.viewFactory = new TabbedViewFactory(tabbedViewLayout);
		
		registerEngine("Javascript", JavascriptEngine::new);
		registerEngine("Ruby", RubyEngine::new);
		registerEngine("Groovy", GroovyEngine::new);
		
		mainController.setEngine("Groovy");
		
		createMenuBar(shell);
		
		eventBus.register(this);
		shell.addDisposeListener(event -> eventBus.unregister(this));
	}
	
	/**
	 * Adds a new item to the toolbar that runs the callback when it is selected.
	 */
	public void addToolbarItem(String name, Runnable callback) {
		toolbarComposite.setLayout(new FillLayoutBuilder().marginHeight(3).marginWidth(3).build());
		Button button = new Button(toolbarComposite, SWT.NONE);
		button.setText(name);
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				mainController.getScriptController().exec(() -> {
					callback.run();
					return null;
				});
			}
		});
		shell.pack();
	}

	@Subscribe
	public void onScriptTypeChange(ScriptTypeChangeEvent event) {
		Display.getDefault().asyncExec(() -> createMenuBar(shell));
	}
	
	private void registerEngine(String name, Supplier<Engine> engineSupplier) {
		try {
			mainController.registerEngine(name, engineSupplier.get());
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
	
	public void registerView(Class<? extends TabbedView> type, String defaultTitle, FolderPosition defaultPosition, Function<Composite, TabbedView> factory) {
		viewFactory.registerView(type, defaultTitle, defaultPosition, factory);
		
		createMenuBar(shell);
	}
	
	public TabbedView addView(Class<? extends TabbedView> type) {
		return viewFactory.addView(type);
	}
	
	public TabbedView addView(Class<? extends TabbedView> type, String expression) {
		return viewFactory.addView(type, expression);
	}
	
	public void removeEmptyFolders() {
		tabbedViewLayout.removeEmptyFolders();
	}
	
	private void createMenuBar(final Shell shell) {
		MenuBuilder menuBuilder = new MenuBuilder(shell);
		
		menuBuilder.addMenu("&File")
			.addItem("Open...\tCtrl+O").addSelectionListener(() -> open()).setAccelerator(SWT.CONTROL | 'o')
			.addSeparator()
			.addItem("Save\tCtrl+S").addSelectionListener(() -> save()).setAccelerator(SWT.CONTROL | 's')
			.addItem("Save As...\tCtrl+Shift+S").addSelectionListener(() -> saveAs()).setAccelerator(SWT.CONTROL | SWT.SHIFT | 's')
			.addSeparator()
			.addItem("Reload...\tCtrl+R").addSelectionListener(() -> reload()).setAccelerator(SWT.CONTROL | 'r')
			.addSeparator()
			.addItem("E&xit\tCtrl+Q").addSelectionListener(() -> shell.dispose()).setAccelerator(SWT.CONTROL | 'q');
		
		addEditorsMenu(menuBuilder);
		
		menuBuilder.addMenu("&Script")
			.addItem("Refresh All\tCtrl+Shift+Enter").addSelectionListener(() -> eventBus.post(new MajorRefreshEvent())).setAccelerator(SWT.CONTROL | SWT.SHIFT | '\r')
			.addItem("Clear Global Variables").addSelectionListener(() -> mainController.clearGlobals())
			.addItem("Interrupt").addSelectionListener(() -> mainController.interrupt())
			.addSeparator()
			.addSubmenu("Engine", submenu -> submenu
				.addRadioItem("Javascript", Objects.equal(mainController.getEngine(), "Javascript")).addSelectionListener(() -> mainController.setEngine("Javascript"))
				.addRadioItem("Ruby", Objects.equal(mainController.getEngine(), "Ruby")).addSelectionListener(() -> mainController.setEngine("Ruby"))
				.addRadioItem("Groovy", Objects.equal(mainController.getEngine(), "Groovy")).addSelectionListener(() -> mainController.setEngine("Groovy"))
			);
		
		menuBuilder.build();
	}
	
	private void addEditorsMenu(MenuBuilder menuBuilder) {
		Collection<ViewInfo> viewInfos = viewFactory.getRegisteredViews();
		
		if(viewInfos.isEmpty()) return;
		
		menuBuilder.addMenu("&Editors");
		
		// Add not editor views that don't require an expression.
		viewInfos.forEach(viewInfo -> {
			if(!Editor.class.isAssignableFrom(viewInfo.getType())) {
				menuBuilder.addItem("New " + viewInfo.getDefaultTitle()).addSelectionListener(() -> {
					viewFactory.addView(viewInfo.getType());
				});
			}
		});
		
		menuBuilder.addSeparator();
		
		// Add editor views that require an expression.
		viewInfos.forEach(viewInfo -> {
			if(Editor.class.isAssignableFrom(viewInfo.getType())) {
				menuBuilder.addItem("New " + viewInfo.getDefaultTitle() + "...").addSelectionListener(() -> {
					String expression = InputDialog.open(shell, "Expression", "Expression");
					if(expression != null && !expression.trim().isEmpty()) {
						viewFactory.addView(viewInfo.getType(), expression.trim());
					}
				});
			}
		});
	}
	
	private void save() {
		if(currentFileLocation == null) {
			saveAs();
		} else {
			save(currentFileLocation);
		}
	}
	
	private void saveAs() {
		String location = selectSaveLocation();
		if(location != null) {
			currentFileLocation = location;
			save(location);
		}
	}
	
	public void save(String location) {
		String document = serialize();
		
		try {
			FileUtils.writeStringToFile(new File(location), document, "UTF-8");
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void open() {
		open(selectOpenLocation());
	}
	
	public void open(String location) {
		if(location != null) {
			currentFileLocation = location;
			
			try {
				String document = FileUtils.readFileToString(new File(location), "UTF-8");
				deserialize(document);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void reload() {
		try {
			deserialize(serialize());
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private String serialize() {
		Document document = new Document();
		
		Element workbookElement = new Element("Workbook");
		document.addContent(workbookElement);
		
		Element tabsElement = new Element("Tabs");
		workbookElement.addContent(tabsElement);
		tabbedViewLayout.serialize(tabsElement);
		
		Element controllerElement = new Element("Controller");
		workbookElement.addContent(controllerElement);
		mainController.serialize(controllerElement);
		
		return new XMLOutputter(Format.getPrettyFormat()).outputString(document);
	}
	
	public void deserialize(String documentText) throws JDOMException, IOException {
		Document document = new SAXBuilder().build(new StringReader(documentText));
		
		Element workbookElement = document.getRootElement().getChild("Workbook");
		
		Element tabsElement = document.getRootElement().getChild("Tabs");
		tabbedViewLayout.deserialize(viewFactory, tabsElement);
		
		Element controllerElement = document.getRootElement().getChild("Controller");
		mainController.deserialize(controllerElement);
		
		eventBus.post(new MinorRefreshEvent(this));
	}

	private String selectSaveLocation() {
		FileDialog dialog = new FileDialog(shell, SWT.SAVE);
		dialog.setText("Save");
		dialog.setFileName("book.wb");
		return dialog.open();
	}
	
	private String selectOpenLocation() {
		FileDialog dialog = new FileDialog(shell, SWT.OPEN);
		dialog.setText("Open");
		dialog.setFilterExtensions(new String[] { "*.wb", "*.*" });
		
		return dialog.open();
	}
	
	private void displayException(Exception e) {
		MessageBox messageBox = new MessageBox(shell);
		messageBox.setText("Error");
		messageBox.setMessage(e.getMessage() == null ? e.toString() : e.getMessage());
		e.printStackTrace();
		
		messageBox.open();
	}
	
	public List<TabbedView> getTabbedViews() {
		return tabbedViewLayout.getTabbedViews();
	}
}
