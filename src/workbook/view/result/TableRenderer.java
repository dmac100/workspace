package workbook.view.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import workbook.editor.ScriptTableUtil;
import workbook.editor.reference.Reference;
import workbook.script.Engine;
import workbook.script.ScriptController;

public class TableRenderer implements ResultRenderer {
	private final ResultRenderer next;
	private final ScriptController scriptController;

	public TableRenderer(ResultRenderer next, ScriptController scriptController) {
		this.next = next;
		this.scriptController = scriptController;
	}
	
	public void addView(Composite parent, Object value, Runnable callback) {
		scriptController.exec(() -> {
			ScriptTableUtil scriptTableUtil = new ScriptTableUtil(scriptController);
			
			Engine script = scriptController.getScriptSync();
			
			if(value != null) {
				Map<String, List<Reference>> columns = scriptTableUtil.getTable(value);
				
				if(scriptTableUtil.isIterable(value)) {
					if(!columns.isEmpty()) {
						addTable(parent, value, callback);
						return null;
					}
				}
			}
			
			next.addView(parent, value, callback);
			return null;
		});
	}

	private void addTable(Composite parent, Object value, Runnable callback) {
		Map<String, List<Reference>> columns = new ScriptTableUtil(scriptController).getTable(value);
		
		Display.getDefault().asyncExec(() -> {
			Table table = new Table(parent, SWT.BORDER);
			table.setHeaderVisible(true);
			
			List<TableItem> rows = new ArrayList<>();
			
			Map<String, Integer> columnIndexes = new HashMap<>();
			
			columns.forEach((name, values) -> {
				TableColumn nameColumn = new TableColumn(table, SWT.NONE);
				nameColumn.setText(name);
				nameColumn.setWidth(100);
				
				columnIndexes.put(name, columnIndexes.size());
			});
			
			columns.forEach((name, values) -> {
				for(int i = 0; i < values.size(); i++) {
					if(rows.size() <= i) {
						rows.add(new TableItem(table, SWT.NONE));
					}
					
					TableItem item = rows.get(rows.size() - 1);
					
					Reference reference = values.get(i);
					readItemValue(item, columnIndexes.get(name), reference);
				}
			});
				
			callback.run();
		});
	}
	
	public void readItemValue(TableItem tableItem, int index, Reference reference) {
		if(reference != null) {
			reference.get().thenAccept(value -> {
				String stringValue = String.valueOf(value);
				tableItem.getDisplay().asyncExec(() -> {
					if(!tableItem.isDisposed()) {
						tableItem.setText(index, stringValue);
					}
				});
			});
		}
	}
}