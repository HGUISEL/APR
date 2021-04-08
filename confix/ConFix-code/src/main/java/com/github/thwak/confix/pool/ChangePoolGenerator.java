package com.github.thwak.confix.pool;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays ;

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
	public List<Integer> changeList = new ArrayList<Integer>();
	public List<String> postfixList = new ArrayList<String>(Arrays.asList("++", "--"));
	public List<String> infixList = new ArrayList<String>(Arrays.asList("==", "!=", "<", "<=", ">", ">=", "&&", "||", "+", "-", "*", "%", "/", "+=", "-=")) ;
	public List<String> prefixList = new ArrayList<String>(Arrays.asList("!", "++", "--"));
	public List<String> fixList;

	public ChangePoolGenerator() {
		pool = new ChangePool();
	}

	public void collect(Script script) {
		// System.out.println(script.toString()) ;
		Integer newChangeHash;
		Change revChange;

		for (Change c : script.changes.keySet()) {
			ContextIdentifier identifier = pool.getIdentifier();
			List<EditOp> ops = script.changes.get(c);
			for (EditOp op : ops) {
				Context context = identifier.getContext(op);
				updateMethod(c);

				revChange = null;

				String nodeType = c.node.label.split("::")[0]; // for Insert and Delete
				String locationType = c.location.label.split("::")[0]; // for Replace and Update

				fixList = null;

				switch (c.type) {
					case Change.INSERT:
					case CHANGE.DELETE:
					if(nodeType.equals("InfixExpression"))
						fixList = infixList;
					else if(nodeType.equals("PostfixExpression"))
						fixList = postfixList ;
					else if(nodeType.equals("PrefixExpression"))
						fixList = prefixList;
					
					if(fixList == null)
						break;

					case Change.INSERT:
						revChange = new Change(c.id, Change.DELETE, c.node, c.location); // to revert operation

						for(String newInfix : fixList){
							Change cloneChange = new Change(c.id, Change.DELETE, c.node, c.location) ;
							cloneChange.node.label = locationType+ "::"+newInfix;
							cloneChange.node.value = newInfix ;

							newChangeHash = new Integer((cloneChange.type+cloneChange.node.label+cloneChange.location.label).toString().hashCode());
							if (changeList.contains(newChangeHash))
								continue;

							pool.add(context, cloneChange);
							changeList.add(newChangeHash);

							System.out.println("Added Change type: " + cloneChange.type);
							System.out.println("Added Change node: " + cloneChange.node.label);
							System.out.println("Added Change location: " + cloneChange.location.label);
							System.out.println("Added Change Context: " + context.toString()+"\n");
						}
						break;
					case Change.DELETE:
						revChange = new Change(c.id, Change.INSERT, c.node, c.location);  // to revert operation
						
						for(String newInfix : fixList){
							Change cloneChange = new Change(c.id, Change.INSERT, c.node, c.location) ;
							cloneChange.node.label = locationType+ "::"+newInfix;
							cloneChange.node.value = newInfix ;

							newChangeHash = new Integer((cloneChange.type+cloneChange.node.label+cloneChange.location.label).toString().hashCode());
							if (changeList.contains(newChangeHash))
								continue;

							pool.add(context, cloneChange);
							changeList.add(newChangeHash);

							System.out.println("Added Change type: " + cloneChange.type);
							System.out.println("Added Change node: " + cloneChange.node.label);
							System.out.println("Added Change location: " + cloneChange.location.label);
							System.out.println("Added Change Context: " + context.toString()+"\n");
						}
						break;

					case Change.UPDATE:
					case Change.REPLACE:
					if(locationType.equals("InfixExpression"))
							fixList = infixList;
						else if(locationType.equals("PostExpression"))
							fixList = postfixList ;
						else if(locationType.equals("PrefixExpression"))
							fixList = prefixList;

						if(fixList == null)
							break;

					case Change.UPDATE:
							
						for(String newInfix : fixList){
							Change cloneChange = new Change(c.id, Change.UPDATE, c.node, c.location) ;
							cloneChange.location.label = locationType+ "::"+newInfix;
							cloneChange.location.value = newInfix ;

							newChangeHash = new Integer((cloneChange.type+cloneChange.node.label+cloneChange.location.label).toString().hashCode());
							if (changeList.contains(newChangeHash))
								continue;

							pool.add(context, cloneChange);
							changeList.add(newChangeHash);

							System.out.println("Added Change type: " + cloneChange.type);
							System.out.println("Added Change node: " + cloneChange.node.label);
							System.out.println("Added Change location: " + cloneChange.location.label);
							System.out.println("Added Change Context: " + context.toString()+"\n");

							System.out.println("This is change to string\n" + cloneChange + "\n");

						}
						

						break;
					case Change.REPLACE:
					
						for(String newInfix : fixList){
							Change cloneChange = new Change(c.id, Change.UPDATE, c.node, c.location) ;
							cloneChange.location.label = locationType+ "::"+newInfix;
							cloneChange.location.value = newInfix ;

							newChangeHash = new Integer((cloneChange.type+cloneChange.node.label+cloneChange.location.label).toString().hashCode());
							if (changeList.contains(newChangeHash))
								continue;

							pool.add(context, cloneChange);
							changeList.add(newChangeHash);

							System.out.println("Added Change type: " + cloneChange.type);
							System.out.println("Added Change node: " + cloneChange.node.label);
							System.out.println("Added Change location: " + cloneChange.location.label);
							System.out.println("Added Change Context: " + context.toString()+"\n");

							System.out.println("This is change to string\n" + cloneChange + "\n");
						}
						
						break;

				}


	

				if(revChange != null){ // If proper reverted change is made
					newChangeHash = new Integer((revChange.type+revChange.node.label+revChange.location.label).toString().hashCode());
					if (changeList.contains(newChangeHash))
						continue;
					pool.add(context, revChange);
					changeList.add(newChangeHash);

					System.out.println("Added Change type: " + revChange.type);
					System.out.println("Added Change node: " + revChange.node.label);
					System.out.println("Added Change location: " + revChange.location.label);
					System.out.println("Added Change Context: " + context.toString()+"\n");
				}

				


				newChangeHash = new Integer((c.type+c.node.label+c.location.label).toString().hashCode());
				if (changeList.contains(newChangeHash))
					continue;
				pool.add(context, c);

				System.out.println("Added Change type: " + c.type);
				System.out.println("Added Change node: " + c.node.label);
				System.out.println("Added Change location: " + c.location.label);
				System.out.println("Added Change Context: " + context.toString()+"\n");
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

				if(bugFiles.get(i) == null || cleanFiles.get(i) == null)
					continue;

				// Generate EditScript from before and after.
				String oldCode = IOUtils.readFile(bugFiles.get(i));
				String newCode = IOUtils.readFile(cleanFiles.get(i));

				Tree before = TreeBuilder.buildTreeFromFile(bugFiles.get(i));
				Tree after = TreeBuilder.buildTreeFromFile(cleanFiles.get(i));

				if(before == null || after == null)
					System.out.println("Tree is null");

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

