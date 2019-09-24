/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. 
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.midica.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.midica.Midica;
import org.midica.TestUtil;
import org.midica.config.Dict;
import org.midica.midi.SequenceAnalyzer;
import org.midica.midi.SequenceCreator;
import org.midica.ui.model.IMessageType;
import org.midica.ui.model.MidicaTreeModel;
import org.midica.ui.model.SingleMessage;

/**
 * This is the test class for {@link org.midica.file.MidicaPLParser}.
 * 
 * @author Jan Trukenmüller
 */
class MidicaPLParserTest extends MidicaPLParser {
	
	public MidicaPLParserTest() {
		super(true);
	}

	/**
	 * Initializes midica in test mode.
	 * 
	 * @throws InterruptedException       on interruptions while waiting for the event dispatching thread.
	 * @throws InvocationTargetException  on exceptions.
	 */
	@BeforeAll
	static void setUpBeforeClass() throws InvocationTargetException, InterruptedException {
		TestUtil.initMidica();
	}
	
	/**
	 * Test method for {@link org.midica.file.MidicaPLParser}.
	 */
	@Test
	void testParseDuration() throws ParseException {
		
		// tests that are supposed to return something
		assertEquals(              60, parseDuration("/32")                );
		assertEquals(             120, parseDuration("/16")                );
		assertEquals(             240, parseDuration("/8")                 );
		assertEquals(             480, parseDuration("/4")                 );
		assertEquals(             960, parseDuration("/2")                 );
		assertEquals(            1920, parseDuration("/1")                 );
		assertEquals(            1920, parseDuration("*1")                 );
		assertEquals(            3840, parseDuration("*2")                 );
		assertEquals(            7680, parseDuration("*4")                 );
		assertEquals(           15360, parseDuration("*8")                 );
		assertEquals(           30720, parseDuration("*16")                );
		assertEquals(           61440, parseDuration("*32")                );
		assertEquals(             480, parseDuration("4")                  );
		assertEquals(             384, parseDuration("5")                  );
		assertEquals(           11520, parseDuration("*4.")                );
		assertEquals(           13440, parseDuration("*4..")               );
		assertEquals(           13440, parseDuration("*4..")               );
		assertEquals(           14400, parseDuration("*4...")              );
		assertEquals(            5120, parseDuration("*4t")                );
		assertEquals(            3413, parseDuration("*4tt")               );
		assertEquals(            2276, parseDuration("*4ttt")              );
		assertEquals(            4389, parseDuration("*4t7:4")             );
		assertEquals(            2508, parseDuration("*4t7:4t7:4")         );
		assertEquals(            2006, parseDuration("*4t7:4t7:4t5:4")     );
		assertEquals(             480, parseDuration("/4t7:4t4:7t7:4t4:7") );
		assertEquals(     2508 + 3413, parseDuration("*4t7:4t7:4+*4tt")    );
		assertEquals( 480 + 60 + 1920, parseDuration("4+32+1")             );
		
		// tests that are supposed to throw a parsing exception
		assertThrows( ParseException.class, () -> parseDuration("/64") );
		assertThrows( ParseException.class, () -> parseDuration("*64") );
		assertThrows( ParseException.class, () -> parseDuration("xyz") );
		assertThrows( ParseException.class, () -> parseDuration("/4+") );
	}
	
	/**
	 * Tests for parsing full source files that are expected to work.
	 * 
	 * The files are located in test/org/midica/testfiles/working
	 * @throws ParseException if something went wrong.
	 */
	@Test
	void testParseFilesWorking() throws ParseException {
		
		// expected tickstamps after parsing
		parse(getWorkingFile("globals"));
		assertEquals(  960, instruments.get(0).getCurrentTicks() );
		assertEquals( 1440, instruments.get(1).getCurrentTicks() );
		
		parse(getWorkingFile("empty"));
		assertEquals( 0, SequenceCreator.getSequence().getTickLength() );
		
		parse(getWorkingFile("nestable-block-with-m"));
		assertEquals( 480, instruments.get(0).getCurrentTicks() );
		
		parse(getWorkingFile("macros-and-blocks"));
		assertEquals( 11040, instruments.get(0).getCurrentTicks() );
		
		parse(getWorkingFile("chords"));
		assertEquals( 13440, instruments.get(0).getCurrentTicks() );
		
		parse(getWorkingFile("define"));
		assertEquals( 3120, instruments.get(0).getCurrentTicks() );
		assertEquals( 2160, instruments.get(9).getCurrentTicks() );
		
		parse(getWorkingFile("using-unknown-drumkit"));
		assertEquals(       123, instruments.get(9).instrumentNumber );
		assertEquals( "testing", instruments.get(9).instrumentName   );
		
		parse(getWorkingFile("using-known-drumkit"));
		assertEquals(           8, instruments.get(9).instrumentNumber );
		assertEquals( "test room", instruments.get(9).instrumentName   );
		
		parse(getWorkingFile("instruments-with-banknumbers"));
		assertEquals(   2, instruments.get(1).instrumentNumber  );
		assertEquals(   0, instruments.get(1).getBankMSB()      );
		assertEquals(   0, instruments.get(1).getBankLSB()      );
		assertEquals(   2, instruments.get(2).instrumentNumber  );
		assertEquals(   0, instruments.get(2).getBankMSB()      );
		assertEquals(   0, instruments.get(2).getBankLSB()      );
		assertEquals(  24, instruments.get(10).instrumentNumber );
		assertEquals( 120, instruments.get(10).getBankMSB()     );
		assertEquals(   0, instruments.get(10).getBankLSB()     );
		assertEquals(  24, instruments.get(15).instrumentNumber );
		assertEquals( 120, instruments.get(15).getBankMSB()     );
		assertEquals(   1, instruments.get(15).getBankLSB()     );
		
		parse(getWorkingFile("instruments-with-banknumbers2"));
		assertEquals( 0, instruments.get(1).instrumentNumber  );
		assertEquals( 0, instruments.get(1).getBankMSB()      );
		assertEquals( 0, instruments.get(1).getBankLSB()      );
		assertEquals( 0, instruments.get(2).instrumentNumber  );
		assertEquals( 0, instruments.get(2).getBankMSB()      );
		assertEquals( 0, instruments.get(2).getBankLSB()      );
		assertEquals( 0, instruments.get(10).instrumentNumber );
		assertEquals( 0, instruments.get(10).getBankMSB()     );
		assertEquals( 0, instruments.get(10).getBankLSB()     );
		assertEquals( 0, instruments.get(15).instrumentNumber );
		assertEquals( 0, instruments.get(15).getBankMSB()     );
		assertEquals( 0, instruments.get(15).getBankLSB()     );
		
		parse(getWorkingFile("instruments-single-line"));
		assertEquals(  40, instruments.get(1).instrumentNumber  );
		assertEquals(   0, instruments.get(1).getBankMSB()      );
		assertEquals(   0, instruments.get(1).getBankLSB()      );
		assertEquals(  30, instruments.get(2).instrumentNumber  );
		assertEquals(   0, instruments.get(2).getBankMSB()      );
		assertEquals(   0, instruments.get(2).getBankLSB()      );
		assertEquals(  22, instruments.get(10).instrumentNumber );
		assertEquals( 120, instruments.get(10).getBankMSB()     );
		assertEquals(   0, instruments.get(10).getBankLSB()     );
		
		parse(getWorkingFile("meta"));
		assertEquals(
			"(c) test\r\n2nd line",
			getMetaMsgText(0, 0)  // copyright
		);
		assertEquals(
			  "{#title=Title with tab\\t!}"
			+ "{#composer=Wolfgang Amadeus Mozart\\r\\nHaydn}"
			+ "{#lyrics=Some\\\\One}"
			+ "{#artist=\\{Someone\\} \\[Else\\]}"
			+ "{#software=Midica " + Midica.VERSION + "}"
			+ "{#}",
			getMetaMsgText(2, 0)  // RP-026 tags
		);
		
		parse(getWorkingFile("lyrics"));
		String lyricsFull = (String) ((HashMap<String, Object>)SequenceAnalyzer.getSequenceInfo().get("karaoke")).get("lyrics_full");
		assertEquals( "happy birthday to you\nhappy birthday to you\n\nhappy", lyricsFull );
		
		parse(getWorkingFile("block-tuplets"));
		assertEquals( 3668, instruments.get(0).getCurrentTicks() );
		
		parse(getWorkingFile("drum-only-with-global"));
		assertEquals( 960, instruments.get(9).getCurrentTicks() );
		
		parse(getWorkingFile("drum-only-with-empty-multiple-block"));
		assertEquals( 0, instruments.get(9).getCurrentTicks() );
		
		parse(getWorkingFile("drum-only-with-empty-multiple-macro"));
		assertEquals( 0, instruments.get(9).getCurrentTicks() );
		
		parse(getWorkingFile("drum-only-with-multiple"));
		assertEquals( 0, instruments.get(9).getCurrentTicks() );
		
		parse(getWorkingFile("drum-only-with-channel-options"));
		assertEquals( 1920, instruments.get(9).getCurrentTicks() );
		
		parse(getWorkingFile("tremolo"));
		assertEquals( 11520, instruments.get(0).getCurrentTicks() );
		String rootString = ((MidicaTreeModel)SequenceAnalyzer.getSequenceInfo().get("banks_total")).getRoot().toString();
		assertEquals( "Total (33)", rootString );
		
		parse(getWorkingFile("const"));
		assertEquals( constants.get("$forte"),            "120"                                                              );
		assertEquals( constants.get("$piano"),            "30"                                                               );
		assertEquals( constants.get("$mezzoforte"),       "75"                                                               );
		assertEquals( constants.get("$staccato"),         "duration=50%"                                                     );
		assertEquals( constants.get("$legato"),           "duration=100%"                                                    );
		assertEquals( constants.get("$legato_forte"),     "duration=100% , v = 120"                                          );
		assertEquals( constants.get("$several_columns"),  "c  /4  duration=50%"                                              );
		assertEquals( constants.get("$cmd_with_columns"), "0  c  /4"                                                         );
		assertEquals( constants.get("$whole_line"),       "0  c  /4  duration=50%"                                           );
		assertEquals( constants.get("$complex_const"),    "START duration=100% , v = 120 MIDDLE duration=100% , v = 120 END" );
		
		parse(getWorkingFile("var"));
		assertEquals( "cent", constants.get("$cent")            );
		assertEquals( "123",  variables.get("$forte")           );
		assertEquals( "30",   variables.get("$piano")           );
		assertEquals( "80",   variables.get("$mezzoforte")      );
		assertEquals( "50%",  variables.get("$staccato")        );
		assertEquals( "100",  variables.get("$legato")          );
		assertEquals( "75%",  variables.get("$medium_duration") );
		assertEquals( "1",    variables.get("$c")               );
		assertEquals( "c",    variables.get("$n")               );
		assertEquals( "d",    variables.get("$n2")              );
		assertEquals( "/2",   variables.get("$l")               );
		assertEquals( "3",    variables.get("$q")               );
		// channel 0:
		ArrayList<SingleMessage> messages = getMessagesByChannel(0);
		{
			int i = 0;
			assertEquals( "0/0/90/c / 30",     messages.get(++i).toString() );  // c piano
			assertEquals( "240/0/80/c / 0",    messages.get(++i).toString() );  //   staccato=240 ticks
			assertEquals( "480/0/90/c / 30",   messages.get(++i).toString() );  // c
			assertEquals( "960/0/80/c / 0",    messages.get(++i).toString() );  //   legato=480 ticks
			assertEquals( "960/0/90/c / 123",  messages.get(++i).toString() );  // c forte   (INCLUDE test1)
			assertEquals( "1440/0/80/c / 0",   messages.get(++i).toString() );  //   legato=480 ticks
			assertEquals( "1440/0/90/c / 123", messages.get(++i).toString() );  // c         (INCLUDE test2)
			assertEquals( "1920/0/80/c / 0",   messages.get(++i).toString() );  //   legato=480 ticks
		}
		// channel 1:
		messages = getMessagesByChannel(1);
		{
			int i = 0;
			// macro test1, plain
			assertEquals( "0/1/91/c / 123",     messages.get(++i).toString() );  // c forte
			assertEquals( "456/1/81/c / 0",     messages.get(++i).toString() );  //   default duration: 456 ticks
			assertEquals( "480/1/91/c / 123",   messages.get(++i).toString() );  // c forte
			assertEquals( "720/1/81/c / 0",     messages.get(++i).toString() );  //   staccato=240 ticks
			// macro test1, block
			assertEquals( "960/1/91/c / 80",   messages.get(++i).toString()  );  // c mezzoforte
			assertEquals( "1200/1/81/c / 0",   messages.get(++i).toString()  );  //   staccato=240 ticks
			assertEquals( "1440/1/91/c / 80",  messages.get(++i).toString()  );  // c mezzoforte
			assertEquals( "1440/1/91/c+ / 80", messages.get(++i).toString()  );  // c+ mezzoforte
			assertEquals( "1800/1/81/c / 0",   messages.get(++i).toString()  );  //   medium_duration=360 ticks
			assertEquals( "1800/1/81/c+ / 0",  messages.get(++i).toString() );  //   medium_duration=360 ticks
		}
		// INLUDE test2
		assertEquals( 21, messages.size() ); // 1x program change, 10x note-on, 10x note-off
		
		parse(getWorkingFile("shift"));
		messages = getMessagesByStatus("90");
		assertEquals( 19, messages.size() );
		{
			int i = 0;
			assertEquals( "0/0/90/d / 64",      messages.get(i++).toString() ); // before all macros
			assertEquals( "480/0/90/e / 64",    messages.get(i++).toString() );
			assertEquals( "960/0/90/c / 64",    messages.get(i++).toString() );
			assertEquals( "1440/0/90/a#- / 64", messages.get(i++).toString() );
			assertEquals( "1920/0/90/c / 64",   messages.get(i++).toString() );
			assertEquals( "2400/0/90/c / 64",   messages.get(i++).toString() ); // INCLUDE test1
			assertEquals( "2880/0/90/c / 64",   messages.get(i++).toString() );
			assertEquals( "3360/0/90/c / 64",   messages.get(i++).toString() );     // c (chord)
			assertEquals( "3360/0/90/d / 64",   messages.get(i++).toString() );     // d (chord)
			assertEquals( "3840/0/90/a#- / 64", messages.get(i++).toString() );
			assertEquals( "4320/0/90/a#- / 64", messages.get(i++).toString() );     // a#- (chord)
			assertEquals( "4320/0/90/c / 64",   messages.get(i++).toString() );     // c (chord)
			assertEquals( "4800/0/90/c+ / 64",  messages.get(i++).toString() ); // INCLUDE test1  s=12
			assertEquals( "5280/0/90/c+ / 64",  messages.get(i++).toString() );
			assertEquals( "5760/0/90/c+ / 64",  messages.get(i++).toString() );     // c+ (chord)
			assertEquals( "5760/0/90/d+ / 64",  messages.get(i++).toString() );     // d+ (chord)
			assertEquals( "6240/0/90/a# / 64",  messages.get(i++).toString() );
			assertEquals( "6720/0/90/a# / 64",  messages.get(i++).toString() );     // a# (chord)
			assertEquals( "6720/0/90/c+ / 64",  messages.get(i++).toString() );     // c+ (chord)
		}
	}
	
	/**
	 * Tests for parsing full source files that are expected to throw a parsing exception.
	 * 
	 * The files are located in test/org/midica/testfiles/failing
	 */
	@Test
	void testParseFilesFailing() {
		ParseException e;
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("nestable-block-open-at-eof")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("macro-open-at-eof")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("file-that-does-not-exist")) );
		assertTrue( e.getMessage().startsWith("java.io.FileNotFoundException:") );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-failing-file")) );
		assertTrue( e.getFileName().endsWith("instruments-with-nestable-block.midica") );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-not-existing-file")) );
		assertEquals( 1, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("global-cmd-in-instruments")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-in-block")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("macro-in-block")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("using-channel-without-instr-def")) );
		assertEquals( 1, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("using-undefined-channel")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("using-invalid-drumkit")) );
		assertEquals( 10, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-with-param")) );
		assertEquals( 1, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("end-with-param")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("unmatched-end")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("unmatched-close")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("macro-nested")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("macro-redefined")) );
		assertEquals( 8, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("macro-with-second-param")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-soundfont-twice")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-soundfont-inside-block")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-soundfont-inside-macro")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("unknown-cmd")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("block-param-invalid")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-inside-macro")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-inside-instruments")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-without-param")) );
		assertEquals( 2, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-without-notes")) );
		assertEquals( 2, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-redefined")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-name-like-note")) );
		assertEquals( 2, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-name-like-percussion")) );
		assertEquals( 2, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-with-duplicate-note")) );
		assertEquals( 2, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-with-invalid-param")) );
		assertEquals( 6, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-with-recursion")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-undefined-macro")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("include-without-param")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-with-more-instr-sep")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-with-more-bank-sep")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-with-big-banknumber")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-with-big-msb")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-with-big-lsb")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-with-missing-bank")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instruments-with-missing-lsb")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("meta-in-block")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("meta-with-block")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("meta-in-macro")) );
		assertEquals( 5, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("meta-with-param")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-inside-meta")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("note-in-percussion-channel")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("note-unknown")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-with-unknown-note")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("chord-with-unknown-note-number")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("channel-cmd-missing-param")) );
		assertEquals( 3, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("instrument-in-instruments")) );
		assertEquals( 4, e.getLineNumber() );
		
		e = assertThrows( ParseException.class, () -> parse(getFailingFile("var-in-instruments")) );
		assertEquals( 6, e.getLineNumber() );
		assertEquals( true, e.getMessage().startsWith(Dict.get(Dict.ERROR_VAR_NOT_ALLOWED)) );
		
		// System.out.println(e.getMessage() + "\n" + e.getFileName());
	}
	
	/**
	 * Returns a source file for testing the parse() method with a
	 * file that is supposed to be parseble.
	 * 
	 * @param name file name without the extension .midicapl
	 * @return file object of the file to be tested
	 */
	private static File getWorkingFile(String name) {
		String sourceDir = TestUtil.getTestfileDirectory() + "working" + File.separator;
		File file = new File(sourceDir + name + ".midica");
		
		return file;
	}
	
	/**
	 * Returns a source file for testing the parse() method with a
	 * file that is supposed to be parseble.
	 * 
	 * @param name file name without the extension .midicapl
	 * @return file object of the file to be tested
	 */
	private static File getFailingFile(String name) {
		String sourceDir = TestUtil.getTestfileDirectory() + "failing" + File.separator;
		File file = new File(sourceDir + name + ".midica");
		
		return file;
	}
	
	/**
	 * Returns the text of the requested message, assuming that it is a meta message.
	 * 
	 * @param track    Track index, beginning with 0.
	 * @param i        Message index inside of the track.
	 * @return         Text of the message.
	 */
	private static String getMetaMsgText(int track, int i) {
		Sequence  seq = SequenceCreator.getSequence();
		
		MidiMessage msg  = seq.getTracks()[track].get(i).getMessage();
		byte[]      data = ((MetaMessage) msg).getData();
		String      text = CharsetUtils.getTextFromBytes(data, "UTF-8", null);
		
		return text;
	}
	
	/**
	 * Returns the message list, filtered by the given channel.
	 * 
	 * @param channel  MIDI channel
	 * @return all messages with the given channel.
	 */
	private static ArrayList<SingleMessage> getMessagesByChannel(int channel) {
		ArrayList<SingleMessage> allMessages = (ArrayList<SingleMessage>) SequenceAnalyzer.getSequenceInfo().get("messages");
		ArrayList<SingleMessage> messages = new ArrayList<>();
		for (SingleMessage msg : allMessages) {
			Integer ch = (Integer) msg.getOption(IMessageType.OPT_CHANNEL);
			if (ch != null && ch == channel)
				messages.add(msg);
		}
		
		return messages;
	}
	
	/**
	 * Returns the message list, filtered by the given status byte.
	 * 
	 * @param statusByte  status byte (first byte of the message)
	 * @return all messages with the given channel.
	 */
	private static ArrayList<SingleMessage> getMessagesByStatus(String statusByte) {
		ArrayList<SingleMessage> allMessages = (ArrayList<SingleMessage>) SequenceAnalyzer.getSequenceInfo().get("messages");
		ArrayList<SingleMessage> messages = new ArrayList<>();
		for (SingleMessage msg : allMessages) {
			String status = (String) msg.getOption(IMessageType.OPT_STATUS_BYTE);
			if (status.equals(statusByte))
				messages.add(msg);
		}
		
		return messages;
	}
}
