package workbook.editor.ui;

import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.google.common.eventbus.EventBus;

import workbook.event.MinorRefreshEvent;
import workbook.script.ScriptController;
import workbook.view.TabbedView;
import workbook.view.text.EditorText;

/**
 * An editor that shows and allows editing of a String.
 */
public class StringTabbedEditor extends Editor implements TabbedView {
	private final Composite parent;
	private final EventBus eventBus;
	private final EditorText editorText;
	
	private boolean disableModifyCallback;
	
	public StringTabbedEditor(Composite parent, EventBus eventBus, ScriptController scriptController) {
		super(eventBus, scriptController);
		
		this.parent = parent;
		this.eventBus = eventBus;
		
		this.editorText = new EditorText(parent);
		
		editorText.getStyledText().addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent event) {
				if(!disableModifyCallback) {
					writeValue();
				}
			}
		});
		
		registerEvents();
	}
	
	public void readValue() {
		if(reference != null) {
			reference.get().thenAccept(value -> {
				if(value instanceof String) {
					Display.getDefault().asyncExec(() -> {
						if(!editorText.getControl().isDisposed()) {
							disableModifyCallback = true;
							if(!editorText.getText().equals(value)) {
								editorText.setText((String)value);
							}
							disableModifyCallback = false;
						}
					});
				}
			});
		}
	}
	
	public void writeValue() {
		if(reference != null) {
			reference.set(editorText.getText()).thenRun(() -> {
				eventBus.post(new MinorRefreshEvent(this));
			});
		}
	}

	public Control getControl() {
		return editorText.getControl();
	}
}