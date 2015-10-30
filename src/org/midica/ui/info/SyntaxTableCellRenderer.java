/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. 
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.midica.ui.info;

import java.awt.Component;
import java.util.ArrayList;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import org.midica.config.Config;
import org.midica.config.Dict;

/**
 * Cell renderer for the syntax table in the configuration overview of the info window.
 * 
 * The categories are displayed in another color than the plain syntax elements.
 * 
 * @author Jan Trukenmüller
 */
public class SyntaxTableCellRenderer extends DefaultTableCellRenderer {
	
	private static final long serialVersionUID = 1L;
	
	/** List containing all syntax elements including category entries */
	private ArrayList<SyntaxElement> syntaxList;
	
	/**
	 * Creates a cell renderer for the syntax table in the configuration overview
	 * of the info window.
	 */
	public SyntaxTableCellRenderer() {
		this.syntaxList = Dict.getSyntaxList();
	}
	
	@Override
	public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col ) {
		Component cell = super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, col );
		if ( syntaxList.get(row).category ) {
			cell.setBackground( Config.TABLE_CELL_CATEGORY_COLOR );
		}
		else {
			cell.setBackground( Config.TABLE_CELL_DEFAULT_COLOR );
		}
		return cell;
	}
	
}