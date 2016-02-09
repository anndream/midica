/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. 
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.midica.ui.model;

import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * This class represents a node for a {@link JTree}.
 * 
 * It contains a name, a descendant counter and a number
 * representing a MIDI byte, double byte or byte range.
 * However the number can be **null**.
 * 
 * This class is designed to be used mainly from a {@link MidicaTreeModel}.
 * The following steps are necessary:
 * 
 * - Call one of the constructors.
 * - Call {@link #initChildren()}, if it's a branch. Not necessary for leafs.
 * - For each child:
 *   - Call {@link #addAndOrIncrement(String, String, String, boolean)}
 *   - Call {@link #increment()}
 * - After all children are added, connect them with each other in the right
 *   order by calling {@link #addChildren()}. This works recursively.
 * 
 * @author Jan Trukenmüller
 */
public class MidicaTreeNode extends DefaultMutableTreeNode {
	
	private static final long serialVersionUID = 1L;
	
	/** basic text to be displayed */
	private String name = null;
	
	/** number representation of the node (e.g. bank number, program, status byte, etc.) */
	private String number = null;
	
	/** number of leaves under the node's hierarchy */
	private int count = 0;
	
	/** stores the child nodes in a sorted form */
	private TreeMap<String, MidicaTreeNode> sortedChildren = null;
	
	/**
	 * Creates a new node with the given text and number.
	 * 
	 * @param name    Main text to be displayed.
	 * @param number  MIDI number representing this node.
	 */
	public MidicaTreeNode( String name, String number ) {
		this.number = number;
		this.name   = name;
	}
	
	/**
	 * Creates a new node with the given text but without a number.
	 * 
	 * @param name  Main text to be displayed.
	 */
	public MidicaTreeNode( String name ) {
		this.name = name;
	}
	
	/**
	 * Increments the count by 1.
	 * This is called if a descendant of this node is added to the tree.
	 */
	public void increment() {
		count++;
	}
	
	/**
	 * Returns the number representation of the node.
	 * 
	 * @return MIDI number.
	 */
	public String getNumber() {
		return number;
	}
	
	/**
	 * Returns the text of the node.
	 * 
	 * @return text of the node
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Initializes the data structure for the sorted children.
	 */
	public void initChildren() {
		sortedChildren = new TreeMap<String, MidicaTreeNode>();
	}
	
	/**
	 * Creates and/or increments the specified child.
	 * 
	 * If a new child is created, it is also added to the sorted children
	 * data structure. However it is not yet added to the tree.
	 * 
	 * @param id      Child identifier string -- unique for the children of this node.
	 * @param name    Child name.
	 * @param number  MIDI number representation of the child.
	 * @param isLeaf  Specifies if the child is a leaf or a branch.
	 * @return created or incremented child.
	 */
	public MidicaTreeNode addAndOrIncrement( String id, String name, String number, boolean isLeaf ) {
		
		// create child, if not yet done
		if ( ! sortedChildren.containsKey(id) ) {
			MidicaTreeNode child = new MidicaTreeNode( name, number );
			sortedChildren.put( id, child );
			if ( ! isLeaf )
				child.initChildren();
		}
		
		// increment child
		MidicaTreeNode child = sortedChildren.get( id );
		child.increment();
		
		return child;
	}
	
	/**
	 * Adds all children recursively.
	 */
	public void addChildren() {
		
		if ( null == sortedChildren )
			return;
		
		for ( Entry<String, MidicaTreeNode> childEntry : sortedChildren.entrySet() ) {
			MidicaTreeNode child = childEntry.getValue();
			child.addChildren();
			add( child );
		}
	}
	
	/**
	 * Returns the string to be displayed in the tree.
	 */
	@Override
	public String toString() {
		if ( null == number )
			return name + " (" + count + ")";
		return "[" + number + "] " + name + " (" + count + ")";
	}
}
