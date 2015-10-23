/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. 
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.midica.file;

import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.midica.config.Dict;

/**
 * This class can be extended by exporter classes which write a music file based on a MIDI sequence.
 * 
 * Derived classes are {@link MidiExporter} and {@link MidicaPLExporter}.
 * 
 * @author Jan Trukenmüller
 */
public abstract class Exporter {
	
	/**
	 * Checks and creates the file.
	 * Checks if the specified file can be created/written or already exists.
	 * If it already exists, asks for overwriting or not.
	 * If everything is correct, creates the file.
	 * 
	 * @param file the file to be written
	 * @return true, if the file can be written. Otherwise: false.
	 * @throws ExportException if the file is not writable.
	 */
	protected boolean createFile( File file ) throws ExportException {
		
		try {
    		
			// file exists already?
    		if ( ! file.createNewFile() ) {
    			int mayOverwrite = JOptionPane.showConfirmDialog( null, Dict.get(Dict.OVERWRITE_FILE), Dict.get(Dict.TITLE_CONFIRMATION), JOptionPane.YES_NO_OPTION );
    			if ( JOptionPane.YES_OPTION != mayOverwrite )
    				return false;
    		}
    		
    		// writable
    		if ( ! file.canWrite() )
    			throw new ExportException( Dict.get(Dict.ERROR_FILE_NOT_WRITABLE) );
    		
    		return true;
    		
		}
		catch ( IOException e ) {
			throw new ExportException( e.getMessage() );
		}
		
	}
	
	/**
	 * Exports a file.
	 * 
	 * @param   file             Export file based on the loaded midi stream.
	 * @return                   Warnings that occured during the export.
	 * @throws  ExportException  If the file can not be exported correctly.
	 */
	public abstract ExportResult export( File file ) throws ExportException;
}