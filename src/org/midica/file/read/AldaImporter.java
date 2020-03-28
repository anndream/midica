/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. 
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.midica.file.read;

import java.io.File;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;

import org.midica.config.Config;
import org.midica.config.Dict;
import org.midica.file.Foreign;
import org.midica.file.ForeignException;

/**
 * This class is used to import an ALDA file using the alda executable.
 * This works only if ALDA is installed.
 * 
 * The process contains the following steps:
 * 
 * - Start the ALDA server, if not yet done
 * - Convert ALDA to a MIDI tempfile, using the alda executable
 * - Parse the MIDI file using the parent class
 * - Delete the MIDI file
 * 
 * @author Jan Trukenmüller
 */
public class AldaImporter extends MidiParser {
	
	// foreign program description for error messages
	private static String programName = Dict.get(Dict.FOREIGN_PROG_ALDA);
	
	/**
	 * Returns the absolute path of the successfully parsed ALDA file.
	 * Returns **null**, if no file has been successfully parsed or the successfully parsed file
	 * is not an ALDA file.
	 * 
	 * @return file path or **null**.
	 */
	public static String getFilePath() {
		return getFilePath(FORMAT_ALDA);
	}
	
	/**
	 * Parses an ALDA file.
	 * 
	 * @param file  ALDA file to be parsed.
	 */
	public void parse(File file) throws ParseException {
		
		// reset file name and file type
		preprocess(file);
		midiFileCharset = null;
		chosenCharset   = "US-ASCII";
		
		try {
			String execPath = Config.get(Config.EXEC_PATH_IMP_ALDA);
			
			// alda up
			String[] aldaUp = {execPath, "up"};
			Foreign.execute(aldaUp, programName, true);
			
			// create temp midi file
			File tempfile = Foreign.createTempMidiFile();
			
			// convert from the ALDA file to the tempfile
			String[] aldaConvert = {execPath, "export", "-f", file.getAbsolutePath(), "-o", tempfile.getAbsolutePath()};
			Foreign.execute(aldaConvert, programName, false);
			
			// get MIDI from tempfile
			Sequence sequence = MidiSystem.getSequence(tempfile);
			
			// delete tempfile
			Foreign.deleteTempFile(tempfile);
			
			// transform and analyze the sequence
			createSequence(sequence);
			postprocessSequence(sequence, FORMAT_ALDA, chosenCharset); // analyze the original sequence
		}
		catch (ForeignException | InvalidMidiDataException | IOException e) {
			throw new ParseException(e.getMessage());
		}
	}
}
