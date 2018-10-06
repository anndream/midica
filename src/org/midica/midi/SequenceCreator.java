/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. 
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.midica.midi;

import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.midica.Midica;
import org.midica.file.CharsetUtils;
import org.midica.file.MidiParser;
import org.midica.file.MidicaPLParser;

import com.sun.media.sound.MidiUtils;

/**
 * This class is used to create a MIDI sequence. It is used by one of the parser methods while
 * parsing a MidicaPL or MIDI file.
 * 
 * @author Jan Trukenmüller
 */
public class SequenceCreator {
	
	public static final long NOW                =   0; // MIDI tick for channel initializations
	public static final long TICK_SOFTWARE      =   0; // MIDI tick for the software version
	public static final int  DEFAULT_RESOLUTION = 480; // ticks per quarter note
	public static final int  NUM_META_TRACKS    =   3; // number of non-channel tracks
	public static final int  NUM_TRACKS         = NUM_META_TRACKS + 16; // total number of tracks
	
	// used for the software and version meta info
	public  static final String GENERATED_BY = "Generated by ";
	
	private static String   fileType   = null; // last parsing attempt ("midica" or "mid")
	private static int      resolution = DEFAULT_RESOLUTION;
	private static String   charset    = null;
	private static Track[]  tracks     = null;
	private static Sequence seq;
	
	
	/**
	 * This class is only used statically so a public constructor is not needed.
	 */
	private SequenceCreator() {
	}
	
	/**
	 * Creates a new sequence and sets it's resolution to the default value.
	 * Initiates all necessary data structures.
	 * Adds a META message for the Midica version.
	 * This method is called by the {@link MidicaPLParser}.
	 * 
	 * @param chosenCharset  Charset to be used for text-based messages.
	 * @throws InvalidMidiDataException    if {@link Sequence}.PPQ is not a valid division type.
	 *                                     This should never happen.
	 */
	public static void reset( String chosenCharset ) throws InvalidMidiDataException {
		resolution = DEFAULT_RESOLUTION;
		reset( resolution, chosenCharset );
		fileType = "midica";
		
		// add midica version
		String info    = GENERATED_BY + "Midica " + Midica.VERSION;
		byte[] content = CharsetUtils.getBytesFromText( info, chosenCharset );
		MetaMessage metaMsg = new MetaMessage();
		metaMsg.setMessage( MidiListener.META_TEXT, content, content.length );
		MidiEvent event = new MidiEvent( metaMsg, TICK_SOFTWARE );
		tracks[ 0 ].add( event );
	}
	
	/**
	 * Creates a new sequence and sets it's resolution to the given value.
	 * Initiates all necessary data structures.
	 * This method is called by the {@link MidiParser}.
	 * 
	 * @param res                          Resolution of the new sequence.
	 * @param chosenCharset                Charset to be used for text-based messages.
	 * @throws InvalidMidiDataException    if {@link Sequence}.PPQ is not a valid division type.
	 *                                     This should never happen.
	 */
	public static void reset( int res, String chosenCharset ) throws InvalidMidiDataException {
		
		// create a new stream
		resolution = res;
		charset    = chosenCharset;
		seq        = new Sequence( Sequence.PPQ, resolution );
		tracks     = new Track[ NUM_TRACKS ];
		for ( int i = 0; i < NUM_TRACKS; i++ ) {
			tracks[ i ] = seq.createTrack();
		}
		fileType = "mid";
		
		return;
	}
	
	/**
	 * Returns the MIDI sequence.
	 * 
	 * @return    MIDI sequence.
	 */
	public static Sequence getSequence() {
		return seq;
	}
	
	/**
	 * Determins which file type was attempted to parse last.
	 * 
	 * @return "midica" or "mid", depending on the file type.
	 */
	public static String getFileType() {
		return fileType;
	}
	
	/**
	 * Sets the bank MSB or LSB by sending an according control change message.
	 * 
	 * @param channel    Channel number from 0 to 15.
	 * @param tick       Tickstamp of the bank select or -1 if the method is called
	 *                   during initialization.
	 *                   TODO: test, how much the tick must be BEFORE the program change
	 * @param value      The value to set.
	 * @param isLSB      **false**: set the MSB; **true**: set the LSB
	 */
	public static void setBank( int channel, long tick, int value, boolean isLSB ) throws InvalidMidiDataException {
		
		// choose the right controller
		int controller = 0x00;
		if (isLSB)
			controller = 0x20;
		
		// make sure that the tick is not negative
		if ( tick < 0 )
			tick = 0;
		
		// set bank MSB or LSB
		ShortMessage msg = new ShortMessage();
		msg.setMessage( ShortMessage.CONTROL_CHANGE, channel, controller, value );
		tracks[ channel + NUM_META_TRACKS ].add( new MidiEvent(msg, tick) );
	}
	
	/**
	 * Initiates or changes the given channel's instrument, bank and channel comment.
	 * 
	 * The following steps are performed:
	 * 
	 * - adding a meta message (INSTRUMENT NAME) containing the channel number
	 *   and the channel comment
	 * - adding a program change message
	 * 
	 * @param channel     Channel number from 0 to 15.
	 * @param instrNum    Instrument number - corresponds to the MIDI program number.
	 * @param comment     Comment to be used as the track name.
	 * @param tick        Tickstamp of the instrument change or 0 if the method is called
	 *                    during initialization.
	 * @throws InvalidMidiDataException if invalid MIDI data is used to create a MIDI message.
	 */
	public static void initChannel( int channel, int instrNum, String comment, long tick ) throws InvalidMidiDataException {
		
		// meta message: instrument name
		MetaMessage metaMsg = new MetaMessage();
		byte[] data = CharsetUtils.getBytesFromText( comment, charset );
		metaMsg.setMessage( MidiListener.META_INSTRUMENT_NAME, data, data.length );
		tracks[ channel + NUM_META_TRACKS ].add( new MidiEvent(metaMsg, tick) );
		
		// program change
		ShortMessage msg = new ShortMessage();
		msg.setMessage( ShortMessage.PROGRAM_CHANGE, channel, instrNum, 0 );
		tracks[ channel + NUM_META_TRACKS ].add( new MidiEvent(msg, tick) );
	}
	
	/**
	 * Adds the note-ON and note-OFF messages for one note to be played.
	 * 
	 * @param channel      Channel number from 0 to 15.
	 * @param note         Note number.
	 * @param startTick    Tickstamp of the note-ON event.
	 * @param endTick      Tickstamp of the note-OFF event.
	 * @param velocity     Velocity of the key stroke.
	 * @throws InvalidMidiDataException if invalid MIDI data is used to create a MIDI message.
	 */
	public static void addMessageKeystroke( int channel, int note, long startTick, long endTick, int velocity ) throws InvalidMidiDataException {
		addMessageNoteON( channel, note, startTick, velocity );
		addMessageNoteOFF( channel, note, endTick );
	}
	
	/**
	 * Adds a note-ON event.
	 * 
	 * @param channel     Channel number from 0 to 15.
	 * @param note        Note number.
	 * @param tick        Tickstamp of the event.
	 * @param velocity    Velocity of the key stroke.
	 * @throws InvalidMidiDataException if invalid MIDI data is used to create a MIDI message.
	 */
	public static void addMessageNoteON( int channel, int note, long tick, int velocity ) throws InvalidMidiDataException {
		ShortMessage msg = new ShortMessage();
		msg.setMessage( ShortMessage.NOTE_ON, channel, note, velocity );
		MidiEvent event = new MidiEvent( msg, tick );
		tracks[ channel + NUM_META_TRACKS ].add( event );
	}
	
	/**
	 * Adds a note-OFF event.
	 * 
	 * @param channel    Channel number from 0 to 15.
	 * @param note       Note number.
	 * @param tick       Tickstamp of the event.
	 * @throws InvalidMidiDataException if invalid MIDI data is used to create a MIDI message.
	 */
	public static void addMessageNoteOFF( int channel, int note, long tick ) throws InvalidMidiDataException {
		ShortMessage msg = new ShortMessage();
		msg.setMessage( ShortMessage.NOTE_OFF, channel, note, 0 );
		MidiEvent event = new MidiEvent( msg, tick );
		tracks[ channel + NUM_META_TRACKS ].add( event );
	}
	
	/**
	 * Sets the tempo in beats per minute by creating a tempo change message.
	 * 
	 * @param newBpm    Tempo in beats per minute.
	 * @param tick      Tickstamp of the tempo change event.
	 * @throws InvalidMidiDataException if invalid MIDI data is used to create a MIDI message.
	 */
	public static void addMessageBpm( int newBpm, long tick ) throws InvalidMidiDataException {
		// bpm (beats per minute) --> mpq (microseconds per quarter)
		int mpq = (int) MidiUtils.convertTempo( newBpm );
		int cmd = MidiListener.META_SET_TEMPO;
		
		MetaMessage msg = new MetaMessage();
		byte[] data = new byte[ 3 ];
		data[ 0 ] = (byte) ( (mpq >> 16) & 0xFF );
		data[ 1 ] = (byte) ( (mpq >>  8) & 0xFF );
		data[ 2 ] = (byte) (  mpq        & 0xFF );
		
		msg.setMessage( cmd, data, data.length );
		MidiEvent event = new MidiEvent( msg, tick );
		tracks[ 0 ].add( event );
	}
	
	/**
	 * Adds a channel-dependent generic message.
	 * This is called by the {@link MidiParser} to add messages that are not handled by another
	 * method.
	 * 
	 * @param msg        Generic MIDI message.
	 * @param channel    Channel number from 0 to 15.
	 * @param tick       Tickstamp of the event.
	 */
	public static void addMessageGeneric( MidiMessage msg, int channel, long tick ) {
		MidiEvent event = new MidiEvent( msg, tick );
		tracks[ channel + NUM_META_TRACKS ].add( event );
	}
	
	/**
	 * Adds a channel-independent generic message.
	 * This is called by the {@link MidiParser} to add messages that are not handled by another
	 * method.
	 * 
	 * Those messages are added to track 0.
	 * 
	 * @param msg     Generic MIDI message.
	 * @param tick    Tickstamp of the event.
	 */
	public static void addMessageGeneric( MidiMessage msg, long tick ) {
		MidiEvent event = new MidiEvent( msg, tick );
		tracks[ 0 ].add( event );
	}
	
	/**
	 * Adds a MIDI message to the given track.
	 * 
	 * This is needed for karaoke-related meta messages from foreign MIDI
	 * files. These messages must be put into the right track.
	 * That makes sure that they are later processed in the right order.
	 * (Sorted by tick, not by original track number).
	 * 
	 * @param msg    Meta message.
	 * @param track  Track number.
	 * @param tick   Tickstamp of the event.
	 */
	public static void addMessageToTrack( MidiMessage msg, int track, long tick ) {
		MidiEvent event = new MidiEvent( msg, tick );
		tracks[ track ].add( event );
	}
	
	/**
	 * Returns the resolution of the MIDI sequence in ticks per quarter note.
	 * 
	 * @return Resolution in ticks per quarter note.
	 */
	public static int getResolution() {
		return resolution;
	}
	
	/**
	 * Adds meta events of the type **marker** to the sequence. These events
	 * show that the activity state of some channels change at this point.
	 * 
	 * This is called by the {@link SequenceAnalyzer} after all other analyzing
	 * is done.
	 * 
	 * The markers are always added to track 0.
	 * 
	 * @param markers  First dimension: **tick**; Second dimension: **bitmasked channels**
	 *                 that change their activity (and/or other properties) at this tick.
	 * @throws InvalidMidiDataException if one of the marker messages cannot be created.
	 */
	public static void addMarkers( TreeMap<Long, TreeSet<Byte>> markers ) throws InvalidMidiDataException {
		
		for ( Entry<Long, TreeSet<Byte>> eventData : markers.entrySet() ) {
			
			// get general parameters for the event to be created
			long          tick              = eventData.getKey();
			TreeSet<Byte> bitmaskedChannels = eventData.getValue();
			int           length            = bitmaskedChannels.size();
			
			// message part (all channels in the current tick's marker)
			byte[] content = new byte[ length ];
			int i = 0;
			for ( Object channelObj : bitmaskedChannels.toArray() ) {
				byte bitmaskedChannel = (byte) channelObj;
				byte channel          = (byte) (bitmaskedChannel & MidiListener.MARKER_BITMASK_CHANNEL);
				content[ i ]          = bitmaskedChannel;
				i++;
			}
			
			// create and add the event
			MetaMessage metaMsg = new MetaMessage();
			metaMsg.setMessage( MidiListener.META_MARKER, content, length );
			MidiEvent event = new MidiEvent( metaMsg, tick );
			tracks[ 0 ].add( event );
		}
	}
}
