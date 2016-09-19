package editor.ui;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.events.TreeAdapter;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.events.TreeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

import editor.ScriptTableUtil;
import editor.reference.Reference;

public class TreeEditor implements Editor {
	private final Composite parent;
	private final String expression;
	private final ScriptTableUtil scriptTableUtil;
	
	private final Tree tree;
	private final org.eclipse.swt.custom.TreeEditor treeEditor;
	
	private Reference reference;
	
	public TreeEditor(Composite parent, String expression, ScriptTableUtil scriptTableUtil) {
		this.parent = parent;
		this.expression = expression;
		this.scriptTableUtil = scriptTableUtil;
		
		this.tree = new Tree(parent, SWT.NONE);
		this.treeEditor = new org.eclipse.swt.custom.TreeEditor(tree);
		treeEditor.horizontalAlignment = SWT.LEFT;
		treeEditor.grabHorizontal = true;
		
		tree.addMouseListener(new MouseAdapter() {
			public void mouseDown(MouseEvent event) {
				onMouseDown(event);
			}
		});
		
		tree.addTreeListener(new TreeAdapter() {
			public void treeExpanded(TreeEvent event) {
				onItemExpanded((TreeItem) event.item);
			}
		});
	}
	
	private void onMouseDown(MouseEvent event) {
		Rectangle clientArea = tree.getClientArea();
		for(int y = tree.indexOf(tree.getTopItem()); y < tree.getItemCount(); y++) {
			TreeItem item = tree.getItem(y);
			Rectangle bounds = item.getBounds(1);
			if(bounds.contains(new Point(event.x, event.y))) {
				editValue(item);
				return;
			}
			if(!bounds.intersects(clientArea)) {
				return;
			}
		}
	}

	private void editValue(TreeItem item) {
		Text text = new Text(tree, SWT.NONE);
		
		text.addFocusListener(new FocusAdapter() {
			public void focusLost(FocusEvent event) {
				if(!text.isDisposed()) {
					writeItemValue(item, text.getText());
					text.dispose();
				}
			}
		});
		
		text.addTraverseListener(new TraverseListener() {
			public void keyTraversed(TraverseEvent event) {
				if(!text.isDisposed()) {
					if(event.detail == SWT.TRAVERSE_RETURN) {
						writeItemValue(item, text.getText());
						text.dispose();
						event.doit = false;
					}
					
					if(event.detail == SWT.TRAVERSE_ESCAPE) {
						text.dispose();
						event.doit = false;
					}
				}
			}
		});
		
		treeEditor.setEditor(text, item, 1);
		text.setText(item.getText(1));
		text.selectAll();
		text.setFocus();
	}
	
	private void onItemExpanded(TreeItem treeItem) {
		Reference reference = (Reference) treeItem.getData();
		if(reference != null) {
			reference.get().thenAccept(value -> {
				if(value != null) {
					Map<String, Reference> rows = scriptTableUtil.getTableRow(value);
					tree.getDisplay().asyncExec(() -> {
						addTreeItems(treeItem, rows);
					});
				}
			});
		}
	}
	
	@Override
	public void setReference(Reference reference) {
		this.reference = reference;
		readValue();
	}

	public String getExpression() {
		return expression;
	}
	
	public void readValue() {
		if(reference != null) {
			reference.get().thenAccept(value -> {
				if(value != null) {
					Map<String, Reference> rows = scriptTableUtil.getTableRow(value);
					tree.getDisplay().asyncExec(() -> {
						if(!tree.isDisposed()) {
							setTreeItems(rows);
						}
					});
				}
			});
		}
	}
	
	private void setTreeItems(Map<String, Reference> rows) {
		for(TreeColumn treeColumn:tree.getColumns()) {
			treeColumn.dispose();
		}
		
		tree.setHeaderVisible(true);
		
		TreeColumn nameColumn = new TreeColumn(tree, SWT.NONE);
		nameColumn.setText("Name");
		nameColumn.setWidth(100);
		
		TreeColumn valueColumn = new TreeColumn(tree, SWT.NONE);
		valueColumn.setText("Value");
		valueColumn.setWidth(100);
		
		addTreeItems(tree, rows);
	}
	
	private void addTreeItems(Tree parent, Map<String, Reference> rows) {
		TreeItem[] oldItems = parent.getItems();
		
		rows.forEach((name, value) -> {
			TreeItem treeItem = new TreeItem(parent, SWT.NONE);
			treeItem.setText(0, name);
			treeItem.setData(value);
			readItemValue(treeItem, value);
		});
		
		for(TreeItem treeItem:oldItems) {
			treeItem.dispose();
		}
	}
	
	private void addTreeItems(TreeItem parent, Map<String, Reference> rows) {
		TreeItem[] oldItems = parent.getItems();
		
		rows.forEach((name, value) -> {
			TreeItem treeItem = new TreeItem(parent, SWT.NONE);
			treeItem.setText(0, name);
			treeItem.setData(value);
			readItemValue(treeItem, value);
		});
		
		for(TreeItem treeItem:oldItems) {
			treeItem.dispose();
		}
	}
	
	public void readItemValue(TreeItem treeItem, Reference reference) {
		if(reference != null) {
			reference.get().thenAccept(value -> {
				boolean hasChild = !scriptTableUtil.getTableRow(value).isEmpty();
				
				String stringValue = String.valueOf(value);
				treeItem.getDisplay().asyncExec(() -> {
					if(!treeItem.isDisposed()) {
						treeItem.setText(1, stringValue);
						if(hasChild) {
							new TreeItem(treeItem, SWT.NONE);
						}
					}
				});
			});
		}
	}
	
	private void writeItemValue(TreeItem treeItem, String value) {
		treeItem.setText(1, "");
		
		Reference reference = (Reference) treeItem.getData();
		if(reference != null) {
			reference.set(value).thenRunAlways(() -> {
				readItemValue(treeItem, reference);
			});
		}
	}
	
	public Control getControl() {
		return tree;
	}
}
