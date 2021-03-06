/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. 
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.midica.file.write;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.midica.config.Dict;
import org.midica.file.Instrument;
import org.midica.file.read.MidicaPLParser;
import org.midica.midi.KaraokeAnalyzer;
import org.midica.midi.SequenceAnalyzer;

/**
 * This class is used to export the currently loaded MIDI sequence as a MidicaPL source file.
 * 
 * @author Jan Trukenmüller
 */
public class MidicaPLExporter extends Decompiler {
	
	/**
	 * Creates a new MidicaPL exporter.
	 */
	public MidicaPLExporter() {
		format = MIDICA;
	}
	
	/**
	 * Initializes MidicaPL specific data structures.
	 */
	public void init() {
	}
	
	/**
	 * Creates the MidicaPL string to be written into the export file.
	 * 
	 * @return MidicaPL string to be written into the export file
	 */
	public String createOutput() {
		StringBuilder output = new StringBuilder();
		
		// META block
		output.append( createMetaBlock() );
		
		// initial INSTRUMENTS block (tick 0)
		output.append( createInitialInstrumentsBlock() );
		
		// add chord definitions
		output.append( createChordDefinitions() );
		
		// SLICE:
		for (Slice slice : slices) {
			
			// if necessary: add rest from current tick to the slice's begin tick
			output.append( createRestBeforeSlice(slice) );
			
			// global commands
			output.append( createGlobalCommands(slice) );
			
			// channel commands and instrument changes
			for (byte channel = 0; channel < 16; channel++) {
				
				// block with rests that are only used for syllables that don't have a note
				if (slice.hasSyllableRests() && channel == lyricsChannels.get(0)) {
					output.append( createSyllableRestsBlock(slice) );
				}
				
				// normal commands
				output.append( createCommandsFromTimeline(slice, channel) );
			}
		}
		
		// config
		output.append(createConfig());
		
		// quality statistics
		output.append(createQualityStats());
		
		// strategy statistics
		output.append(createStrategyStats());
		
		return output.toString();
	}
	
	/**
	 * Creates a nestable block containing all rests with syllables that
	 * have no corresponting Note-ON event.
	 * 
	 * @param slice  the sequence slice
	 * @return the created block.
	 */
	private String createSyllableRestsBlock(Slice slice) {
		StringBuilder lines = new StringBuilder();
		TreeMap<Long, String> timeline = slice.getSyllableRestTimeline();
		
		// open the block
		lines.append(MidicaPLParser.BLOCK_OPEN + " " + MidicaPLParser.M);
		lines.append(NEW_LINE);
		
		// get channel and tickstamp
		byte channel      = lyricsChannels.get(0);
		long currentTicks = instrumentsByChannel.get(channel).getCurrentTicks();
		
		// TICK:
		for (Entry<Long, String> entry : timeline.entrySet()) {
			long   restTick = entry.getKey();
			String syllable = entry.getValue();
			
			// need a normal rest before the syllable?
			if (restTick < currentTicks) {
				long missingTicks = restTick - currentTicks;
				lines.append( "\t" + createRest(channel, missingTicks, currentTicks, null) );
				currentTicks = restTick;
			}
			
			// get tick distance until the next syllable
			Long nextTick = timeline.ceilingKey(currentTicks + 1);
			if (null == nextTick) {
				// last syllable in this slice
				nextTick = currentTicks + sourceResolution; // use a quarter note
			}
			
			long restLength = nextTick - currentTicks;
			
			// add the rest with the syllable
			lines.append( "\t" + createRest(channel, restLength, currentTicks, syllable) );
			currentTicks = nextTick;
		}
		
		// close the block
		lines.append(MidicaPLParser.BLOCK_CLOSE);
		lines.append(NEW_LINE);
		
		return lines.toString();
	}
	
	/**
	 * Creates channel commands and instrument changes from a slice's timeline.
	 * 
	 * Steps:
	 * 
	 * - Adds the following missing properties and elements to the notes and chords of the timeline:
	 *     - properties:
	 *         - length property for each note/chord
	 *         - multiple property, if neccessary
	 *     - elements:
	 *         - rests, if necessary
	 * - Creates the commands
	 * 
	 * @param slice    the sequence slice
	 * @param channel  MIDI channel
	 * @return the created commands (or an empty string, if the slice's timeline doesn't contain anything in the given channel)
	 */
	private String createCommandsFromTimeline(Slice slice, byte channel) {
		StringBuilder lines = new StringBuilder();
		TreeMap<Long, TreeMap<Byte, TreeMap<String, TreeMap<Byte, String>>>> timeline = slice.getTimeline(channel);
		
		// TICK:
		for (Entry<Long, TreeMap<Byte, TreeMap<String, TreeMap<Byte, String>>>> timelineSet : timeline.entrySet()) {
			long tick = timelineSet.getKey();
			TreeMap<Byte, TreeMap<String, TreeMap<Byte, String>>> events = timelineSet.getValue();
			
			// instrument change
			if (events.containsKey(ET_INSTR)) {
				lines.append( createInstrumentChange(channel, tick) );
			}
			
			// notes/chords
			if (events.containsKey(ET_NOTES)) {
				
				// all notes/chords with the same note-ON tick
				TreeMap<String, TreeMap<Byte, String>> notes = events.get(ET_NOTES);
				for (Entry<String, TreeMap<Byte, String>> entry : notes.entrySet()) {
					TreeMap<Byte, String> params = entry.getValue();
					long offTick = Long.parseLong( params.get(NP_OFF_TICK) );
					
					// calculate note length / duration
					long[] lengthProps  = getNoteLengthProperties(tick, offTick, channel);
					long   length       = lengthProps[0];
					long   endTick      = tick + length;
					String durationPerc = lengthProps[1] + "";
					ArrayList<Long> summands = getLengthsForSum(length, false);
					ArrayList<String> summandStrings = new ArrayList<>();
					for (Long summand : summands) {
						String summandStr = noteLength.get(summand);
						summandStrings.add(summandStr);
						incrementStats(STAT_NOTE_SUMMANDS, channel);
						if (summandStr.endsWith(MidicaPLParser.TRIPLET)) {
							incrementStats(STAT_NOTE_TRIPLETS, channel);
						}
					}
					String lengthStr = String.join(MidicaPLParser.LENGTH_PLUS, summandStrings);
					
					// add note length / duration to timeline
					params.put( NP_LENGTH,   lengthStr    );
					params.put( NP_END_TICK, endTick + "" );
					params.put( NP_DURATION, durationPerc );
					
					incrementStats(STAT_NOTES, channel);
				}
				
				// write MidicaPL
				lines.append( createNoteLines(slice, channel, tick, events.get(ET_NOTES)) );
			}
		}
		
		// add one empty line between channels
		if ( ! timeline.isEmpty() ) {
			lines.append(NEW_LINE);
		}
		
		return lines.toString();
	}
	
	/**
	 * Creates the META block, if the sequence contains any META information.
	 * 
	 * @return the META block, or an empty string if the sequence doesn't contain any meta information.
	 */
	private String createMetaBlock() {
		StringBuilder     block = new StringBuilder("");
		ArrayList<String> lines = new ArrayList<>();
		
		// get data structures
		HashMap<String, Object> sequenceInfo = (HashMap<String, Object>) SequenceAnalyzer.getSequenceInfo();
		HashMap<String, String> metaInfo     = (HashMap<String, String>) sequenceInfo.get("meta_info");
		HashMap<String, Object> karaokeInfo  = KaraokeAnalyzer.getKaraokeInfo();
		String copyright = (String) metaInfo.get("copyright");
		String[] fields = {"copyright", "title", "composer", "lyricist", "artist"};
		String[] values = new String[5];
		String[] mplIds = {
			MidicaPLParser.META_COPYRIGHT,
			MidicaPLParser.META_TITLE,
			MidicaPLParser.META_COMPOSER,
			MidicaPLParser.META_LYRICIST,
			MidicaPLParser.META_ARTIST,
		};
		values[0] = copyright;
		
		// process fields
		for (int i = 0; i < fields.length; i++) {
			
			// read value (skip copyright as we have it already)
			if (i > 0)
				values[i] = (String) karaokeInfo.get(fields[i]);
			
			// value not set
			if (null == values[i])
				continue;
			
			// split the line, if necessary
			String[] multiLines = values[i].split("\n");
			
			// LINE of this field
			for (String singleLine : multiLines) {
				if ( ! "".equals(singleLine) )
					lines.add("\t" + mplIds[i] + "\t" + singleLine + NEW_LINE);
			}
		}
		
		// add soft karaoke block, if necessary
		String skType = (String) karaokeInfo.get("sk_type");
		if (skType != null && "MIDI KARAOKE FILE".equals(skType.toUpperCase())) {
			isSoftKaraoke = true;
			lines.add(createSoftKaraokeBlock(karaokeInfo));
		}
		
		// no meta data found?
		if (lines.isEmpty())
			return "";
		
		// add block
		block.append(MidicaPLParser.META + NEW_LINE);
		for (String line : lines) {
			block.append(line);
		}
		block.append(MidicaPLParser.END + NEW_LINE + NEW_LINE);
		
		return block.toString();
	}
	
	/**
	 * Creates the SOFT_KARAOKE block inside of the META block.
	 * This is called only if the sequence uses SOFT KARAOKE.
	 * 
	 * @param karaokeInfo  Karaoke information extracted from the sequence.
	 * @return the created block.
	 */
	private String createSoftKaraokeBlock(HashMap<String, Object> karaokeInfo) {
		StringBuilder block = new StringBuilder("");
		
		// open the block
		block.append("\t" + MidicaPLParser.META_SOFT_KARAOKE + NEW_LINE);
		
		// read single-line fields
		String[] fields = {"sk_version", "sk_language", "sk_title", "sk_author", "sk_copyright"};
		String[] mplIds = {
			MidicaPLParser.META_SK_VERSION,
			MidicaPLParser.META_SK_LANG,
			MidicaPLParser.META_SK_TITLE,
			MidicaPLParser.META_SK_AUTHOR,
			MidicaPLParser.META_SK_COPYRIGHT,
		};
		
		// process single-line fields
		for (int i = 0; i < fields.length; i++) {
			
			// read value
			String value = (String) karaokeInfo.get(fields[i]);
			if (null == value)
				continue;
			
			// append the line
			block.append("\t\t" + mplIds[i] + "\t" + value + NEW_LINE);
		}
		
		// process info fields
		ArrayList<String> infos = (ArrayList<String>) karaokeInfo.get("sk_infos");
		if (infos != null) {
			for (String info : infos) {
				
				// append info line
				if ( ! "".equals(info) )
					block.append("\t\t" + MidicaPLParser.META_SK_INFO + "\t" + info + NEW_LINE);
			}
		}
		
		// close the block
		block.append("\t" + MidicaPLParser.END + NEW_LINE);
		
		return block.toString();
	}
	
	/**
	 * Creates the initial INSTRUMENTS block.
	 * 
	 * @return the created block.
	 */
	private String createInitialInstrumentsBlock() {
		
		// open block
		StringBuilder block = new StringBuilder("");
		block.append(MidicaPLParser.INSTRUMENTS + NEW_LINE);
		
		// add instruments
		for (byte channel = 0; channel < 16; channel++) {
			String instrLine = createInstrLine(0, channel);
			block.append(instrLine);
		}
		
		// close block
		block.append(MidicaPLParser.END + NEW_LINE + NEW_LINE);
		
		return block.toString();
	}
	
	/**
	 * Creates one INSTRUMENT line for an instrument change in the given channel and tick.
	 * 
	 * @param channel  MIDI channel
	 * @param tick     MIDI tick
	 * @return the created lines.
	 */
	private String createInstrumentChange(byte channel, long tick) {
		
		// prepare
		StringBuilder lines = new StringBuilder("");
		
		// add instruments
		Set<Long> changeTicks = instrumentHistory.get(channel).keySet();
		if (changeTicks.contains(tick)) {
			String instrLine = createInstrLine(tick, channel);
			if ( ! "".equals(instrLine) ) {
				lines.append(instrLine);
			}
		}
		
		return lines.toString();
	}
	
	/**
	 * Creates one line inside an INSTRUMENTS block **or** one single instrument change line.
	 * 
	 * If tick is 0, a line inside a block is created. Otherwise it's an instrument change line.
	 * 
	 * Returns an empty string, if no instruments must be defined or changed in the given channel and tick.
	 * 
	 * At the beginning this method is called for each channel (0-15).
	 * This considers:
	 * 
	 * - bank selects at tick 0
	 * - program changes at tick 0
	 * - channels without a program change that are used anyway
	 * 
	 * Afterwards this method is called for every tick and channel that contains one or more
	 * program changes at a tick higher than 0.
	 * 
	 * @param tick     The tickstamp of the program change event; or **0** during initialization.
	 * @param channel  The channel number.
	 * @return the instrument line or an empty string.
	 */
	private String createInstrLine(long tick, byte channel) {
		
		// channel used?
		if (0 == noteHistory.get(channel).size()) {
			return "";
		}
		
		// get the channel's history
		TreeMap<Long, Byte[]> chInstrHist = instrumentHistory.get(channel);
		Byte[]  instrConfig;
		boolean isAutoChannel = false;
		
		String cmd = "";
		if (0 == tick) {
			// initialization - either a program change at tick 0 or the default at a negative tick
			Entry<Long, Byte[]> initialInstr   = chInstrHist.floorEntry(tick);
			long                progChangeTick = initialInstr.getKey();
			instrConfig                        = initialInstr.getValue();
			if (progChangeTick < 0) {
				isAutoChannel = true;
			}
		}
		else {
			// program change at a tick > 0
			cmd         = MidicaPLParser.INSTRUMENT;
			instrConfig = chInstrHist.get(tick);
			
			// no program change at this tick?
			if (null == instrConfig) {
				return "";
			}
		}
		
		// get program and bank
		byte msb  = instrConfig[ 0 ];
		byte lsb  = instrConfig[ 1 ];
		byte prog = instrConfig[ 2 ];
		
		// initialize instrument
		Instrument instr = new Instrument(channel, prog, null, isAutoChannel);
		
		// get the strings to write into the instrument line
		String channelStr = 9 == channel ? MidicaPLParser.P : channel + "";
		String programStr = instr.instrumentName;
		if (Dict.get(Dict.UNKNOWN_DRUMKIT_NAME).equals(programStr)) {
			programStr = prog + "";
		}
		if (msb != 0 || lsb != 0) {
			programStr += MidicaPLParser.PROG_BANK_SEP + msb;
			if (lsb != 0) {
				programStr += MidicaPLParser.BANK_SEP + lsb;
			}
		}
		String commentStr    = instr.instrumentName;
		Long   instrNameTick = commentHistory.get(channel).floorKey(tick);
		if (instrNameTick != null) {
			commentStr = commentHistory.get(channel).get(instrNameTick);
		}
		
		// tick comment (only for instrument changes
		String lineEnd = NEW_LINE;
		if (tick > 0) {
			lineEnd = createTickComment(tick, true) + NEW_LINE;
		}
		
		// put everything together
		return (
			  cmd
			+ "\t"   + channelStr
			+ "\t"   + programStr
			+ "\t\t" + commentStr
			+ lineEnd
		);
	}
	
	/**
	 * Creates the CHORD definitions.
	 * 
	 * @return the CHORD commands.
	 */
	private String createChordDefinitions() {
		
		// no chords available?
		if (chords.isEmpty()) {
			return "";
		}
		
		// initialize
		StringBuilder chordBlock = new StringBuilder("");
		
		// get base notes in the right order, beginning with A
		ArrayList<String> orderedNotes = new ArrayList<>();
		for (int i=0; i<12; i++) {
			String baseName = Dict.getBaseNoteName(i);
			orderedNotes.add(baseName);
		}
		
		// note name that may be the base of several chords
		BASE_NAME:
		for (String baseName : orderedNotes) {
			
			// chords with the current baseName as the lowest note
			ArrayList<String> noteChords = chordsByBaseNote.get(baseName);
			
			// no chords with this base name?
			if (null == noteChords) {
				continue BASE_NAME;
			}
			
			// chords
			for (String notesStr : noteChords) {
				String chordName = chords.get(notesStr);
				chordBlock.append(MidicaPLParser.CHORD + "\t" + chordName + MidicaPLParser.CHORD_ASSIGNER);
				
				// notes
				String[]          noteNumbers = notesStr.split("\\,");
				ArrayList<String> noteNames   = new ArrayList<>();
				for (String noteNumber : noteNumbers) {
					String noteName = Dict.getNote(Integer.parseInt(noteNumber));
					noteNames.add(noteName);
				}
				chordBlock.append( String.join(MidicaPLParser.CHORD_SEPARATOR, noteNames) );
				chordBlock.append(NEW_LINE);
			}
		}
		chordBlock.append(NEW_LINE);
		
		return chordBlock.toString();
	}
	
	/**
	 * Creates a string with global commands for the given slice.
	 * 
	 * @param slice  the sequence slice
	 * @return the created string (or an empty string, if the slice doesn't contain any global commands)
	 */
	private String createGlobalCommands(Slice slice) {
		StringBuilder result = new StringBuilder("");
		
		// synchronize: set all channels to the highest tick
		long maxTick = Instrument.getMaxCurrentTicks(instrumentsByChannel);
		for (Instrument instr : instrumentsByChannel) {
			instr.setCurrentTicks(maxTick);
		}
		
		// tick comment
		result.append( createTickComment(slice.getBeginTick(), false) );
		
		// create global commands
		TreeMap<String, String> globalCmds = slice.getGlobalCommands();
		if (0 == globalCmds.size()) {
			if (slice.getBeginTick() > 0) {
				result.append(MidicaPLParser.GLOBAL + NEW_LINE + NEW_LINE);
			}
		}
		else {
			for (String cmdId : globalCmds.keySet()) {
				String value = globalCmds.get(cmdId);
				
				// get global command
				String globalCmd = MidicaPLParser.TEMPO;
				if ("time".equals(cmdId))
					globalCmd = MidicaPLParser.TIME_SIG;
				else if ("key".equals(cmdId))
					globalCmd = MidicaPLParser.KEY_SIG;
				
				// append command
				result.append(MidicaPLParser.GLOBAL + "\t" + globalCmd + "\t" + value + NEW_LINE);
			}
			result.append(NEW_LINE);
		}
		
		return result.toString();
	}
	
	/**
	 * Creates lines for all notes or chords that are played in a certain
	 * channel and begin at a certain tick.
	 * 
	 * Steps:
	 * 
	 * # If necessary, adds a REST so that the current tick is reached.
	 * # Chooses the LAST note/chord command to be printed.
	 * # Prints all lines apart from the last one, and adds the MULTIPLE option.
	 * # Prints the last line, and adds the MULTIPLE option only if necessary.
	 * # Increments current channel ticks (if the last element has no MULTIPLE option).
	 * 
	 * Strategy to choose the LAST note/chord command:
	 * 
	 * # Choose a note/chord ending in the same tick when the next note/chord starts, if available and in the same slice.
	 *     - no MULTIPLE option needed for the last note/chord
	 *     - no rests are necessary
	 * # Choose a note/chord ending at the end of the slice, if possible, and not later than the next ON-tick
	 *     - no MULTIPLE option needed for the last note/chord
	 *     - rests must be added LATER but not now
	 * # Choose the longest note/chord ending BEFORE the NEXT note/chord starts, if available.
	 *     - no MULTIPLE option needed for the last note/chord
	 *     - rest(s) must be added
	 * # Choose any other note/chord.
	 *     - all chords/notes need the MULTIPLE option, even the last one.
	 *     - rest(s) must be added
	 * 
	 * @param slice    the sequence slice
	 * @param channel  MIDI channel
	 * @param tick     MIDI tick
	 * @param events   All notes/chords with the same note-ON tick in the same channel (comes from the slice's timeline)
	 * @return the created note lines.
	 */
	// TODO: change docu about the strategy
	private String createNoteLines(Slice slice, byte channel, long tick, TreeMap<String, TreeMap<Byte, String>> events) {
		StringBuilder lines = new StringBuilder("");
		
		// add rest, if necessary
		Instrument instr  = instrumentsByChannel.get(channel);
		long currentTicks = instr.getCurrentTicks();
		if (tick > currentTicks) {
			long restTicks = tick - currentTicks;
			lines.append( createRest(channel, restTicks, tick, null) );
			instr.setCurrentTicks(tick);
		}
		
		// get the LAST note/chord to be printed.
		Long   nextOnTick            = noteHistory.get(channel).ceilingKey(tick + 1);
		long   sliceEndTick          = slice.getEndTick();
		String lastNoteOrCrdName     = null;
		long   highestFittingEndTick = -1;
		for (Entry<String, TreeMap<Byte, String>> noteSet : events.entrySet()) {
			String name = noteSet.getKey();
			TreeMap<Byte, String> note = noteSet.getValue();
			
			long endTick = Long.parseLong(note.get(NP_END_TICK));
			
			// next note-ON exists?
			if (nextOnTick != null) {
				
				// next note-ON is in the same slice?
				if (nextOnTick <= sliceEndTick) {
					
					// note/chord fits before next note-ON?
					if (nextOnTick >= endTick) {
						
						// no better candidate found yet?
						if (endTick > highestFittingEndTick) {
							highestFittingEndTick = endTick;
							lastNoteOrCrdName     = name;
						}
					}
				}
			}
			// no next note-ON but note/chord fits into the slice?
			else if (endTick <= sliceEndTick) {
				
				// no better candidate found yet?
				if (endTick > highestFittingEndTick) {
					highestFittingEndTick = endTick;
					lastNoteOrCrdName     = name;
				}
			}
		}
		
		// get notes/chords in the right order
		ArrayList<String> noteOrCrdNames = new ArrayList<>();
		for (Entry<String, TreeMap<Byte, String>> noteSet : events.entrySet()) {
			String name = noteSet.getKey();
			
			// skip the line to be printed last
			if (lastNoteOrCrdName != null && name.equals(lastNoteOrCrdName))
				continue;
			
			noteOrCrdNames.add(name);
		}
		if (lastNoteOrCrdName != null) {
			noteOrCrdNames.add(lastNoteOrCrdName);
		}
		
		// create the lines
		int i = 0;
		for (String name : noteOrCrdNames) {
			i++;
			TreeMap<Byte, String> note = events.get(name);
			
			// add multiple option, if necessary
			if (-1 == highestFittingEndTick || i < noteOrCrdNames.size()) {
				note.put(NP_MULTIPLE, null);
			}
			lines.append( createSingleNoteLine(channel, name, note, tick) );
		}
		
		// increment ticks, if necessary
		if (highestFittingEndTick > 0) {
			instr.setCurrentTicks(highestFittingEndTick);
		}
		
		return lines.toString();
	}
	
	/**
	 * Prints a single channel command for a note or chord.
	 * (Or a rest with a syllable, if orphaned syllables are configured as INLINE.)
	 * 
	 * @param channel    MIDI channel
	 * @param noteName   note or chord name
	 * @param noteOrCrd  note properties (from the slice's timeline)
	 * @param tick       MIDI tickstamp.
	 * @return the created line.
	 */
	private String createSingleNoteLine(byte channel, String noteName, TreeMap<Byte, String> noteOrCrd, long tick) {
		StringBuilder line = new StringBuilder("");
		
		Instrument instr = instrumentsByChannel.get(channel);
		
		// main part of the command
		line.append(channel + "\t" + noteName + "\t" + noteOrCrd.get(NP_LENGTH));
		
		// get options that must be appended
		ArrayList<String> options = new ArrayList<>();
		{
			// multiple
			if (noteOrCrd.containsKey(NP_MULTIPLE)) {
				options.add(MidicaPLParser.M);
				incrementStats(STAT_NOTE_MULTIPLE, channel);
			}
			
			// duration and velocity
			if ( ! noteName.equals(MidicaPLParser.REST) ) {
				
				// duration
				float duration           = Float.parseFloat( noteOrCrd.get(NP_DURATION) ) / 100;
				float oldDuration        = instr.getDurationRatio();
				int   durationPercent    = (int) ((duration    * 1000 + 0.5f) / 10);
				int   oldDurationPercent = (int) ((oldDuration * 1000 + 0.5f) / 10);
				if (durationPercent != oldDurationPercent) {
					// don't allow 0%
					String durationPercentStr = durationPercent + "";
					if (durationPercent < 1) {
						durationPercentStr = "0.5";
						duration = 0.005f;
					}
					options.add(MidicaPLParser.D + MidicaPLParser.OPT_ASSIGNER + durationPercentStr + MidicaPLParser.DURATION_PERCENT);
					instr.setDurationRatio(duration);
					incrementStats(STAT_NOTE_DURATIONS, channel);
				}
				
				// velocity
				int velocity    = Integer.parseInt( noteOrCrd.get(NP_VELOCITY) );
				int oldVelocity = instr.getVelocity();
				if (velocity != oldVelocity) {
					options.add(MidicaPLParser.V + MidicaPLParser.OPT_ASSIGNER + velocity);
					instr.setVelocity(velocity);
					incrementStats(STAT_NOTE_VELOCITIES, channel);
				}
			}
			
			// add syllable, if needed
			if (noteOrCrd.containsKey(NP_LYRICS)) {
				String syllable = noteOrCrd.get(NP_LYRICS);
				syllable = escapeSyllable(syllable);
				options.add(MidicaPLParser.L + MidicaPLParser.OPT_ASSIGNER + syllable);
			}
		}
		
		// append options
		if (options.size() > 0) {
			String optionsStr = String.join(MidicaPLParser.OPT_SEPARATOR + " ", options);
			line.append("\t" + optionsStr);
		}
		
		// finish the line
		line.append( createTickComment(tick, true) );
		line.append(NEW_LINE);
		
		return line.toString();
	}
	
	/**
	 * Creates a channel command with a rest.
	 * 
	 * @param channel    MIDI channel
	 * @param ticks      tick length of the rest to create
	 * @param beginTick  used for the tick comment (negative value: don't include a tick comment)
	 * @param syllable   a lyrics syllable or (in most cases): **null**
	 * @return the channel command containing the rest.
	 */
	protected String createRest(byte channel, long ticks, long beginTick, String syllable) {
		StringBuilder line = new StringBuilder("");
		
		// split length into elements
		ArrayList<Long> lengthElements = getLengthsForSum(ticks, true);
		
		// transform to strings
		ArrayList<String> lengthSummands = new ArrayList<>();
		for (Long length : lengthElements) {
			String summandStr = restLength.get(length);
			lengthSummands.add(summandStr);
			incrementStats(STAT_REST_SUMMANDS, channel);
			if (summandStr.endsWith(MidicaPLParser.TRIPLET)) {
				incrementStats(STAT_REST_TRIPLETS, channel);
			}
		}
		
		// add line
		if (lengthSummands.size() > 0) {
			String length = String.join(MidicaPLParser.LENGTH_PLUS, lengthSummands);
			line.append(channel + "\t" + MidicaPLParser.REST + "\t" + length);
			incrementStats(STAT_RESTS, channel);
		}
		else {
			// TODO: Dict
			// TODO: add warning
			System.err.println("rest too small to be handled: " + ticks + " ticks");
			line.append("// rest too small to be handled: " + ticks + " ticks");
			incrementStats(STAT_REST_SKIPPED, channel);
		}
		
		// add lyrics option, if needed
		if (syllable != null) {
			syllable = escapeSyllable(syllable);
			line.append("\t" + MidicaPLParser.L + MidicaPLParser.OPT_ASSIGNER + syllable);
		}
		
		// finish the line
		if (beginTick >= 0) {
			line.append( createTickComment(beginTick, true) );
		}
		line.append(NEW_LINE);
		
		return line.toString();
	}
	
	/**
	 * Escapes special characters in syllables.
	 * 
	 * @param syllable  The syllable to be escaped.
	 * @return the replaced syllable.
	 */
	private String escapeSyllable(String syllable) {
		
		// escape \r and \n
		if (isSoftKaraoke) {
			syllable = syllable.replaceAll("\n", "_").replaceAll("\r", "_");
		}
		else {
			syllable = syllable.replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r");
		}
		
		// escape space and comma
		return syllable.replaceAll(" ", "_").replaceAll(",",  "\\\\c");
	}
	
	/**
	 * Calculates which tick length corresponds to which note or rest length.
	 * That depends on the resolution of the current MIDI sequence.
	 * 
	 * The created rest lengths will contain a view more very short lengths.
	 * This is needed because rests should be less tolerant than notes.
	 * 
	 * This enables us to use more common lengths for notes but let the
	 * exported sequence be still as close as possible to the original one.
	 * 
	 * @param rest    **true** to initialize REST lengths, **false** for NOTE lengths
	 * @return Mapping between tick length and note length for the syntax.
	 */
	public TreeMap<Long, String> initLengths(boolean rest) {
		
		boolean useDots     = rest ? USE_DOTTED_RESTS     : USE_DOTTED_NOTES;
		boolean useTriplets = rest ? USE_TRIPLETTED_RESTS : USE_TRIPLETTED_NOTES;
		
		String triplet = MidicaPLParser.TRIPLET;
		String dot     = MidicaPLParser.DOT;
		String d1      = MidicaPLParser.LENGTH_1;
		String d2      = MidicaPLParser.LENGTH_2;
		String d4      = MidicaPLParser.LENGTH_4;
		String d8      = MidicaPLParser.LENGTH_8;
		String d16     = MidicaPLParser.LENGTH_16;
		String d32     = MidicaPLParser.LENGTH_32;
		String m2      = MidicaPLParser.LENGTH_M2;
		String m4      = MidicaPLParser.LENGTH_M4;
		String m8      = MidicaPLParser.LENGTH_M8;
		String m16     = MidicaPLParser.LENGTH_M16;
		String m32     = MidicaPLParser.LENGTH_M32;
		
		TreeMap<Long, String> lengthToSymbol = new TreeMap<>();
		
		// use very small lengths only for rests
		if (rest) {
			// 1/512
			long length512 = calculateTicks(1, 128);
			lengthToSymbol.put(length512, 512 + "");
			
			// 1/256
			long length256 = calculateTicks(1, 64);
			lengthToSymbol.put(length256, 256 + "");
			
			// 1/128
			long length128 = calculateTicks(1, 32);
			lengthToSymbol.put(length128, 128 + "");
			
			// 1/64
			long length64 = calculateTicks(1, 16);
			lengthToSymbol.put(length64, 64 + "");
		}
		
		// 32th
		long length32t = calculateTicks( 2, 8 * 3 ); // inside a triplet
		long length32  = calculateTicks( 1, 8     ); // normal length
		long length32d = calculateTicks( 3, 8 * 2 ); // dotted length
		if (useTriplets) lengthToSymbol.put( length32t, d32 + triplet ); // triplet
		                 lengthToSymbol.put( length32,  d32           ); // normal
		if (useDots)     lengthToSymbol.put( length32d, d32 + dot     ); // dotted
		
		// 16th
		long length16t = calculateTicks( 2, 4 * 3 );
		long length16  = calculateTicks( 1, 4     );
		long length16d = calculateTicks( 3, 4 * 2 );
		if (useTriplets) lengthToSymbol.put( length16t, d16 + triplet );
		                 lengthToSymbol.put( length16,  d16           );
		if (useDots)     lengthToSymbol.put( length16d, d16 + dot     );
		
		// 8th
		long length8t = calculateTicks( 2, 2 * 3 );
		long length8  = calculateTicks( 1, 2     );
		long length8d = calculateTicks( 3, 2 * 2 );
		if (useTriplets) lengthToSymbol.put( length8t, d8 + triplet );
		                 lengthToSymbol.put( length8,  d8           );
		if (useDots)     lengthToSymbol.put( length8d, d8 + dot     );
		
		// quarter
		long length4t = calculateTicks( 2, 3 );
		long length4  = calculateTicks( 1, 1 );
		long length4d = calculateTicks( 3, 2 );
		if (useTriplets) lengthToSymbol.put( length4t, d4 + triplet );
		                 lengthToSymbol.put( length4,  d4           );
		if (useDots)     lengthToSymbol.put( length4d, d4 + dot     );
		
		// half
		long length2t = calculateTicks( 2 * 2, 3 );
		long length2  = calculateTicks( 2,     1 );
		long length2d = calculateTicks( 2 * 3, 2 );
		if (useTriplets) lengthToSymbol.put( length2t, d2 + triplet );
		                 lengthToSymbol.put( length2,  d2           );
		if (useDots)     lengthToSymbol.put( length2d, d2 + dot     );
		
		// full
		long length1t = calculateTicks( 4 * 2, 3 );
		long length1  = calculateTicks( 4,     1 );
		long length1d = calculateTicks( 4 * 3, 2 );
		if (useTriplets) lengthToSymbol.put( length1t, d1 + triplet );
		                 lengthToSymbol.put( length1,  d1           );
		if (useDots)     lengthToSymbol.put( length1d, d1 + dot     );
		
		// 2 full notes
		long length_m2  = calculateTicks( 8,     1 );
		long length_m2d = calculateTicks( 8 * 3, 2 );
		             lengthToSymbol.put( length_m2,  m2        );
		if (useDots) lengthToSymbol.put( length_m2d, m2  + dot );
		
		// 4 full notes
		long length_m4  = calculateTicks( 16,     1 );
		long length_m4d = calculateTicks( 16 * 3, 2 );
		             lengthToSymbol.put( length_m4,  m4        );
		if (useDots) lengthToSymbol.put( length_m4d, m4  + dot );
		
		// 8 full notes
		long length_m8  = calculateTicks( 32,     1 );
		long length_m8d = calculateTicks( 32 * 3, 2 );
		             lengthToSymbol.put( length_m8,  m8        );
		if (useDots) lengthToSymbol.put( length_m8d, m8  + dot );
		
		// 16 full notes
		long length_m16  = calculateTicks( 64,     1 );
		long length_m16d = calculateTicks( 64 * 3, 2 );
		             lengthToSymbol.put( length_m16,  m16        );
		if (useDots) lengthToSymbol.put( length_m16d, m16  + dot );
		
		// 32 full notes
		long length_m32  = calculateTicks( 128,     1 );
		long length_m32d = calculateTicks( 128 * 3, 2 );
		             lengthToSymbol.put( length_m32,  m32        );
		if (useDots) lengthToSymbol.put( length_m32d, m32  + dot );
		
		return lengthToSymbol;
	}
	
	/**
	 * Creates a comment giving the current tick - if configured accordingly.
	 * 
	 * Adds a line break, if **must_append** is **true**.
	 * 
	 * @param tick        MIDI tickstamp.
	 * @param mustAppend  **true** for a comment to be appended to a line; **false** for a full-line comment.
	 * @return the comment string.
	 */
	private String createTickComment(long tick, boolean mustAppend) {
		
		// convert source tick to target tick
		long targetTick = (tick * targetResolution * 10 + 5) / (sourceResolution * 10);
		
		String comment = "";
		if (MUST_ADD_TICK_COMMENTS) {
			if (mustAppend)
				comment = "\t\t\t\t";
			
			comment += MidicaPLParser.COMMENT + " "
				+ Dict.get(Dict.EXPORTER_TICK)  + " "
				+ tick
				+ " ==> "
				+ targetTick;
		}
		
		if (mustAppend)
			return comment;
		return comment + NEW_LINE;
	}
}
