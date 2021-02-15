package com.github.thwak.confix.pool;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.util.IOUtils;

import script.ScriptGenerator;
import script.model.EditOp;
import script.model.EditScript;
import tree.Tree;
import tree.TreeBuilder;

public class ChangePoolGenerator {
	public ChangePool pool;

	public ChangePoolGenerator() {
		pool = new ChangePool();
	}

	public void collect(Script script) {
		for (Change c : script.changes.keySet()) {
			ContextIdentifier identifier = pool.getIdentifier();
			List<EditOp> ops = script.changes.get(c);
			for (EditOp op : ops) {
				Context context = identifier.getContext(op);
				updateMethod(c);
				if (c.type.compareTo(Change.INSERT) == 0) {
					Change revChange = new Change(c.id, Change.DELETE, c.node, c.location);
					pool.add(context, revChange);
				}
				if (c.type.compareTo(Change.DELETE) == 0) {
					Change revChange = new Change(c.id, Change.INSERT, c.node, c.location);
					pool.add(context, revChange);
				}
				pool.add(context, c);
			}
			
		}
	}

	private void updateMethod(Change c) {
		Node n = c.node;
		while (n.parent != null && n.parent.type != ASTNode.METHOD_DECLARATION) {
			n = n.parent;
		}
		StringBuffer sb = new StringBuffer(c.id);
		if (n.parent == null)
			sb.append(":");
		else {
			sb.append(":");
			if (n.parent.astNode != null) {
				MethodDeclaration md = (MethodDeclaration) n.parent.astNode;
				sb.append(md.getName().toString());
			}
			sb.append(":");
			sb.append(n.parent.startPos);
		}
		c.id = sb.toString();
	}

	public void collect(List<File> bugFiles, List<File> cleanFiles) {
		try {
			for (int i = 0; i < bugFiles.size(); i++) {
				// Generate EditScript from before and after.
				String oldCode = IOUtils.readFile(bugFiles.get(i));
				String newCode = IOUtils.readFile(cleanFiles.get(i));
				Tree before = TreeBuilder.buildTreeFromFile(bugFiles.get(i));
				Tree after = TreeBuilder.buildTreeFromFile(cleanFiles.get(i));
				EditScript editScript = ScriptGenerator.generateScript(before, after);
				// Convert EditScript to Script.
				editScript = Converter.filter(editScript);
				EditScript combined = Converter.combineEditOps(editScript);
				Script script = Converter.convert("0", combined, oldCode, newCode);
				collect(script);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
