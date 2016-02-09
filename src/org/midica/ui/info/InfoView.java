/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. 
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.midica.ui.info;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;

import org.midica.Midica;
import org.midica.config.Config;
import org.midica.config.Dict;
import org.midica.file.SoundfontParser;
import org.midica.midi.SequenceAnalyzer;
import org.midica.ui.model.InstrumentTableModel;
import org.midica.ui.model.MidicaTreeModel;
import org.midica.ui.model.NoteTableModel;
import org.midica.ui.model.PercussionTableModel;
import org.midica.ui.model.SoundfontInstrumentsTableModel;
import org.midica.ui.model.SoundfontResourceTableModel;
import org.midica.ui.model.SyntaxTableModel;
import org.midica.ui.renderer.FlowLabel;
import org.midica.ui.renderer.InstrumentTableCellRenderer;
import org.midica.ui.renderer.SoundfontInstrumentTableCellRenderer;
import org.midica.ui.renderer.SoundfontResourceTableCellRenderer;
import org.midica.ui.renderer.SyntaxTableCellRenderer;

/**
 * This class defines the GUI view for the informations about the current state
 * of the program instance. It contains the following types of information:
 * 
 * - General version and build information.
 * - Configuration (currently configured value4s for different configurable elements)
 *     - Note names
 *     - Percussion instrument shortcuts
 *     - Syntax keywords for MidicaPL
 *     - Instrument shortcuts for non-percussion instruments
 * - Information about the currently loaded soundbank
 *     - General information
 *     - Drum kits and Instruments
 *     - Resources
 * - Information about the currently loaded MIDI sequence. (NOT YET IMPLEMENTED)
 * 
 * @author Jan Trukenmüller
 */
public class InfoView extends JDialog {
	
	private static final long serialVersionUID = 1L;
	
	// widths and heights, used for dimensions
	private static final int COL_WIDTH_NOTE_NUM          =  60;
	private static final int COL_WIDTH_NOTE_NAME         = 140;
	private static final int COL_WIDTH_PERC_NUM          =  60;
	private static final int COL_WIDTH_PERC_NAME         = 140;
	private static final int COL_WIDTH_SYNTAX_NAME       = 180;
	private static final int COL_WIDTH_SYNTAX_DESC       = 230;
	private static final int COL_WIDTH_SYNTAX_KEYWORD    = 130;
	private static final int COL_WIDTH_INSTR_NUM         =  60;
	private static final int COL_WIDTH_INSTR_NAME        = 180;
	private static final int COL_WIDTH_SF_INSTR_PROGRAM  =  60;
	private static final int COL_WIDTH_SF_INSTR_BANK     =  60;
	private static final int COL_WIDTH_SF_INSTR_NAME     = 200;
	private static final int COL_WIDTH_SF_INSTR_CHANNELS =  80;
	private static final int COL_WIDTH_SF_INSTR_KEYS     = 120;
	private static final int COL_WIDTH_SF_RES_INDEX      =  45;
	private static final int COL_WIDTH_SF_RES_TYPE       =  55;
	private static final int COL_WIDTH_SF_RES_NAME       = 130;
	private static final int COL_WIDTH_SF_RES_FRAMES     =  60;
	private static final int COL_WIDTH_SF_RES_FORMAT     = 260;
	private static final int COL_WIDTH_SF_RES_CLASS      = 130;
	private static final int TABLE_HEIGHT                = 400;
	private static final int GENERAL_INFO_VALUE_WIDTH    = 500;
	private static final int GENERAL_INFO_VALUE_HEIGHT   =  16;
	private static final int SOUNDFONT_DESC_HEIGHT       = 155;
	private static final int BANK_TREE_WIDTH             = 300;
	private static final int BANK_TREE_HEIGHT            = 400;
	private static final int COLLAPSE_EXPAND_WIDTH       =  25;
	private static final int COLLAPSE_EXPAND_HEIGHT      =  25;
	
	// dimensions
	private static Dimension noteTableDim       = null;
	private static Dimension percTableDim       = null;
	private static Dimension syntaxTableDim     = null;
	private static Dimension instrTableDim      = null;
	private static Dimension sfInstrTableDim    = null;
	private static Dimension sfResourceTableDim = null;
	private static Dimension sfDescriptionDim   = null;
	private static Dimension generalInfoDim     = null;
	private static Dimension bankTreeDim        = null;
	private static Dimension collapseExpandDim  = null;
	
	private static InfoView infoView = null;
	
	private InfoController        controller       = null;
	private KeyEventPostProcessor keyProcessor     = null;
	private JTabbedPane           content          = null;
	private JTabbedPane           contentConfig    = null;
	private JTabbedPane           contentSoundfont = null;
	
	/**
	 * Creates a new info view window with owner = null.
	 */
	private InfoView() {
		this( (JDialog) null );
	}
	
	/**
	 * Creates a new info view window and sets the given owner Dialog.
	 * 
	 * @param owner  window to be set as the info view's owner
	 */
	private InfoView( JDialog owner ) {
		super( owner );
		setTitle( Dict.get(Dict.TITLE_INFO_VIEW) );
		
		// initialize table dimensions
		int noteWidth       = COL_WIDTH_NOTE_NUM    + COL_WIDTH_NOTE_NAME;
		int percWidth       = COL_WIDTH_PERC_NUM    + COL_WIDTH_PERC_NAME;
		int syntaxWidth     = COL_WIDTH_SYNTAX_NAME + COL_WIDTH_SYNTAX_KEYWORD
		                    + COL_WIDTH_SYNTAX_DESC;
		int instrWidth      = COL_WIDTH_INSTR_NUM        + COL_WIDTH_INSTR_NAME;
		int sfInstrWidth    = COL_WIDTH_SF_INSTR_PROGRAM + COL_WIDTH_SF_INSTR_BANK
		                    + COL_WIDTH_SF_INSTR_NAME    + COL_WIDTH_SF_INSTR_CHANNELS
		                    + COL_WIDTH_SF_INSTR_KEYS;
		int sfResourceWidth = COL_WIDTH_SF_RES_INDEX  + COL_WIDTH_SF_RES_TYPE
		                    + COL_WIDTH_SF_RES_NAME   + COL_WIDTH_SF_RES_FRAMES
		                    + COL_WIDTH_SF_RES_FORMAT + COL_WIDTH_SF_RES_CLASS;
		noteTableDim       = new Dimension( noteWidth,       TABLE_HEIGHT );
		percTableDim       = new Dimension( percWidth,       TABLE_HEIGHT );
		syntaxTableDim     = new Dimension( syntaxWidth,     TABLE_HEIGHT );
		instrTableDim      = new Dimension( instrWidth,      TABLE_HEIGHT );
		sfInstrTableDim    = new Dimension( sfInstrWidth,    TABLE_HEIGHT );
		sfResourceTableDim = new Dimension( sfResourceWidth, TABLE_HEIGHT );
		
		// initialize dimensions for info value labels
		generalInfoDim   = new Dimension( GENERAL_INFO_VALUE_WIDTH, GENERAL_INFO_VALUE_HEIGHT );
		sfDescriptionDim = new Dimension( GENERAL_INFO_VALUE_WIDTH, SOUNDFONT_DESC_HEIGHT );
		
		// initialize dimensions for the trees and the collapse-all/expand-all buttons
		bankTreeDim       = new Dimension( BANK_TREE_WIDTH, BANK_TREE_HEIGHT );
		collapseExpandDim = new Dimension( COLLAPSE_EXPAND_WIDTH, COLLAPSE_EXPAND_HEIGHT );
		
		// create content
		init();
		
		// show everything
		pack();
		setVisible( true );
	}
	
	/**
	 * Initializes the content of all the tabs inside the info view.
	 */
	private void init() {
		// content
		content = new JTabbedPane( JTabbedPane.LEFT );
		getContentPane().add( content );
		
		// enable key bindings
		this.controller = new InfoController( this );
		addWindowListener( this.controller );
		
		// add tabs
		content.addTab( Dict.get(Dict.TAB_CONFIG),        createConfigArea()       );
		content.addTab( Dict.get(Dict.TAB_SOUNDFONT),     createSoundfontArea()    );
		content.addTab( Dict.get(Dict.TAB_MIDI_SEQUENCE), createMidiSequenceArea() );
		content.addTab( Dict.get(Dict.TAB_MIDICA),        createVersionArea()      );
	}
	
	/**
	 * Creates the configuration tab.
	 * This contains the following sub tabs:
	 * 
	 * - Note details
	 * - Percussion details
	 * - Syntax details
	 * - Instrument details
	 * 
	 * @return the created configuration area.
	 */
	private Container createConfigArea() {
		// content
		contentConfig = new JTabbedPane( JTabbedPane.TOP );
		
		// add tabs
		contentConfig.addTab( Dict.get(Dict.TAB_NOTE_DETAILS),       createNoteArea()       );
		contentConfig.addTab( Dict.get(Dict.TAB_PERCUSSION_DETAILS), createPercussionArea() );
		contentConfig.addTab( Dict.get(Dict.SYNTAX),                 createSyntaxArea()     );
		contentConfig.addTab( Dict.get(Dict.INSTRUMENT),             createInstrumentArea() );
		
		return contentConfig;
	}
	
	/**
	 * Creates the soundfont tab.
	 * This contains the following sub tabs:
	 * 
	 * - General soundfont info
	 * - Instruments and drum kits
	 * - Resources
	 * 
	 * @return the created soundfont area.
	 */
	private Container createSoundfontArea() {
		// content
		contentSoundfont = new JTabbedPane( JTabbedPane.TOP );
		
		// add tabs
		contentSoundfont.addTab( Dict.get(Dict.TAB_SOUNDFONT_INFO),        createSoundfontInfoArea()       );
		contentSoundfont.addTab( Dict.get(Dict.TAB_SOUNDFONT_INSTRUMENTS), createSoundfontInstrumentArea() );
		contentSoundfont.addTab( Dict.get(Dict.TAB_SOUNDFONT_RESOURCES),   createSoundfontResourceArea()   );
		
		return contentSoundfont;
	}
	
	/**
	 * Creates the MIDI sequence tab.
	 * This contains the following sub tabs:
	 * 
	 * - General sequence info
	 * - Used banks, instruments and played notes
	 * - MIDI Messages (TODO: implement)
	 * 
	 * @return the created soundfont area.
	 */
	private Container createMidiSequenceArea() {
		// content
		contentSoundfont = new JTabbedPane( JTabbedPane.TOP );
		
		// add tabs
		contentSoundfont.addTab( Dict.get(Dict.TAB_MIDI_SEQUENCE_INFO), createMidiSequenceInfoArea() );
		contentSoundfont.addTab( Dict.get(Dict.TAB_BANK_INSTR_NOTE),    createBankInstrNoteArea()    );
//		contentSoundfont.addTab( Dict.get(Dict.TAB_MESSAGES),           createMessagesArea()         );
		
		return contentSoundfont;
	}
	
	/**
	 * Creates the note area containing the translation table for note names.
	 * The table translates between MIDI note values and their configured names.
	 * 
	 * @return the created note area.
	 */
	private Container createNoteArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill   = GridBagConstraints.NONE;
		constraints.insets = new Insets( 2, 2, 2, 2 );
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 1;
		constraints.weighty    = 1;
		
		// label
		JLabel label = new JLabel( Dict.get(Dict.TAB_NOTE_DETAILS) );
		area.add( label, constraints );
		
		// table
		constraints.gridy++;
		JTable table = new JTable();
		table.setModel( new NoteTableModel() );
		JScrollPane scroll = new JScrollPane( table );
		scroll.setPreferredSize( noteTableDim );
		scroll.setMinimumSize( noteTableDim );
		area.add( scroll, constraints );
		
		// set column sizes and colors
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( COL_WIDTH_NOTE_NUM  );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( COL_WIDTH_NOTE_NAME );
		table.getTableHeader().setBackground( Config.TABLE_HEADER_COLOR );
		
		return area;
	}
	
	/**
	 * Creates the percussion area containing the translation table for percussion shortcuts.
	 * The table translates between MIDI note values for the percussion channel and their
	 * configured shortcuts.
	 * 
	 * @return the created percussion area
	 */
	private Container createPercussionArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill   = GridBagConstraints.NONE;
		constraints.insets = new Insets( 2, 2, 2, 2 );
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 1;
		constraints.weighty    = 1;
		
		// label
		JLabel label = new JLabel( Dict.get(Dict.TAB_PERCUSSION_DETAILS) );
		area.add( label, constraints );
		
		// table
		constraints.gridy++;
		JTable table = new JTable();
		table.setModel( new PercussionTableModel() );
		JScrollPane scroll = new JScrollPane( table );
		scroll.setPreferredSize( percTableDim );
		scroll.setMinimumSize( percTableDim );
		area.add( scroll, constraints );
		
		// set column sizes
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( COL_WIDTH_PERC_NUM  );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( COL_WIDTH_PERC_NAME );
		table.getTableHeader().setBackground( Config.TABLE_HEADER_COLOR );
		
		return area;
	}
	
	/**
	 * Creates the syntax area containing the translation table for syntax keywords.
	 * 
	 * @return the created syntax area
	 */
	private Container createSyntaxArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill   = GridBagConstraints.NONE;
		constraints.insets = new Insets( 2, 2, 2, 2 );
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 1;
		constraints.weighty    = 1;
		
		// label
		JLabel label = new JLabel( Dict.get(Dict.SYNTAX) );
		area.add( label, constraints );
		
		// table
		constraints.gridy++;
		JTable table = new JTable();
		table.setModel( new SyntaxTableModel() );
		table.setDefaultRenderer( Object.class, new SyntaxTableCellRenderer() );
		JScrollPane scroll = new JScrollPane( table );
		scroll.setPreferredSize( syntaxTableDim );
		scroll.setMinimumSize( syntaxTableDim );
		area.add( scroll, constraints );
		
		// set column sizes
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( COL_WIDTH_SYNTAX_NAME    );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( COL_WIDTH_SYNTAX_KEYWORD );
		table.getColumnModel().getColumn( 2 ).setPreferredWidth( COL_WIDTH_SYNTAX_DESC    );
		table.getTableHeader().setBackground( Config.TABLE_HEADER_COLOR );
		
		return area;
	}
	
	/**
	 * Creates the instrument area containing the translation table for instrument names.
	 * The table translates between MIDI instrument values and their configured names.
	 * 
	 * @return the created instrument area
	 */
	private Container createInstrumentArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill   = GridBagConstraints.NONE;
		constraints.insets = new Insets( 2, 2, 2, 2 );
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 1;
		constraints.weighty    = 1;
		
		// label
		JLabel label = new JLabel( Dict.get(Dict.INSTRUMENT) );
		area.add( label, constraints );
		
		// table
		constraints.gridy++;
		JTable table = new JTable();
		table.setModel( new InstrumentTableModel() );
		table.setDefaultRenderer( Object.class, new InstrumentTableCellRenderer() );
		JScrollPane scroll = new JScrollPane( table );
		scroll.setPreferredSize( instrTableDim );
		scroll.setMinimumSize( instrTableDim );
		area.add( scroll, constraints );
		
		// set column sizes
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( COL_WIDTH_INSTR_NUM  );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( COL_WIDTH_INSTR_NAME );
		table.getTableHeader().setBackground( Config.TABLE_HEADER_COLOR );
		
		return area;
	}
	
	/**
	 * Creates the area for general soundfont information.
	 * 
	 * @return the created soundfont info area.
	 */
	private Container createSoundfontInfoArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill       = GridBagConstraints.NONE;
		constraints.insets     = new Insets( 2, 2, 2, 2 );
		constraints.gridx      = 0;
		constraints.gridy      = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 0;
		constraints.weighty    = 0;
		
		// get general soundfont info
		HashMap<String, String> soundfontInfo = SoundfontParser.getSoundfontInfo();
		
		// file translation
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblFile     = new JLabel( Dict.get(Dict.FILE) + ": " );
		area.add( lblFile, constraints );
		
		// file name
		constraints.gridx++;
		constraints.anchor    = GridBagConstraints.NORTHWEST;
		FlowLabel lblFileContent = new FlowLabel( soundfontInfo.get("file") );
		lblFileContent.setPreferredSize( generalInfoDim );
		area.add( lblFileContent, constraints );
		
		// name translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblname     = new JLabel( Dict.get(Dict.NAME) + ": " );
		area.add( lblname, constraints );
		
		// name content
		constraints.gridx++;
		constraints.anchor       = GridBagConstraints.NORTHWEST;
		FlowLabel lblNameContent = new FlowLabel( soundfontInfo.get("name") );
		lblNameContent.setPreferredSize( generalInfoDim );
		area.add( lblNameContent, constraints );
		
		// version translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblVersion  = new JLabel( Dict.get(Dict.VERSION) + ": " );
		area.add( lblVersion, constraints );
		
		// version content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblVersionContent = new FlowLabel( soundfontInfo.get("version") );
		lblVersionContent.setPreferredSize( generalInfoDim );
		area.add( lblVersionContent, constraints );
		
		// vendor translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblVendor  = new JLabel( Dict.get(Dict.SOUNDFONT_VENDOR) + ": " );
		area.add( lblVendor, constraints );
		
		// vendor content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblVendorContent = new FlowLabel( soundfontInfo.get("vendor") );
		lblVendorContent.setPreferredSize( generalInfoDim );
		area.add( lblVendorContent, constraints );
		
		// creation date translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor     = GridBagConstraints.NORTHEAST;
		JLabel lblCreationDate = new JLabel( Dict.get(Dict.SOUNDFONT_CREA_DATE) + ": " );
		area.add( lblCreationDate, constraints );
		
		// creation date content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblCreationDateContent = new FlowLabel( soundfontInfo.get("creation_date") );
		lblCreationDateContent.setPreferredSize( generalInfoDim );
		area.add( lblCreationDateContent, constraints );
		
		// creation tools translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblTools    = new JLabel( Dict.get(Dict.SOUNDFONT_CREA_TOOLS) + ": " );
		area.add( lblTools, constraints );
		
		// version content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblToolsContent = new FlowLabel( soundfontInfo.get("tools") );
		lblToolsContent.setPreferredSize( generalInfoDim );
		area.add( lblToolsContent, constraints );
		
		// product translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblProduct  = new JLabel( Dict.get(Dict.SOUNDFONT_PRODUCT) + ": " );
		area.add( lblProduct, constraints );
		
		// version content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblProductContent = new FlowLabel( soundfontInfo.get("product") );
		lblProductContent.setPreferredSize( generalInfoDim );
		area.add( lblProductContent, constraints );
		
		// target engine translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor     = GridBagConstraints.NORTHEAST;
		JLabel lblTargetEngine = new JLabel( Dict.get(Dict.SOUNDFONT_TARGET_ENGINE) + ": " );
		area.add( lblTargetEngine, constraints );
		
		// version content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblTargetEngineContent = new FlowLabel( soundfontInfo.get("target_engine") );
		lblTargetEngineContent.setPreferredSize( generalInfoDim );
		area.add( lblTargetEngineContent, constraints );
		
		// chromatic instruments translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblChromatic  = new JLabel( Dict.get(Dict.SF_INSTR_CAT_CHROMATIC) + ": " );
		area.add( lblChromatic, constraints );
		
		// chromatic instruments content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblChromaticContent = new FlowLabel( soundfontInfo.get("chromatic_count") );
		lblChromaticContent.setPreferredSize( generalInfoDim );
		area.add( lblChromaticContent, constraints );
		
		// drum kits translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor       = GridBagConstraints.NORTHEAST;
		JLabel lblDrumkitsSingle = new JLabel( Dict.get(Dict.SOUNDFONT_DRUMKITS) + ": " );
		area.add( lblDrumkitsSingle, constraints );
		
		// drum kits content
		int drumSingle = Integer.parseInt( soundfontInfo.get("drumkit_single_count") );
		int drumMulti  = Integer.parseInt( soundfontInfo.get("drumkit_multi_count")  );
		int drumTotal  = drumSingle + drumMulti;
		String drumkitsContent = drumTotal  + " ("
		                       + drumSingle + " " + Dict.get( Dict.SINGLE_CHANNEL ) + ", "
		                       + drumMulti  + " " + Dict.get( Dict.MULTI_CHANNEL  ) + ")";
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblDrumkitsSingleContent = new FlowLabel( drumkitsContent );
		lblDrumkitsSingleContent.setPreferredSize( generalInfoDim );
		area.add( lblDrumkitsSingleContent, constraints );
		
		// resources translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblLayers  = new JLabel( Dict.get(Dict.TAB_SOUNDFONT_RESOURCES) + ": " );
		area.add( lblLayers, constraints );
		
		// resources content
		int resLayer   = Integer.parseInt( soundfontInfo.get("layer_count")            );
		int resSample  = Integer.parseInt( soundfontInfo.get("sample_count")           );
		int resUnknown = Integer.parseInt( soundfontInfo.get("unknown_resource_count") );
		int resourcesTotal = resLayer + resSample + resUnknown;
		String resourcesContent = resourcesTotal + " ("
		             + resLayer   + " " + Dict.get( Dict.SF_RESOURCE_CAT_LAYER  ) + ", "
		             + resSample  + " " + Dict.get( Dict.SF_RESOURCE_CAT_SAMPLE ) + ")";
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		FlowLabel lblLayersContent = new FlowLabel( resourcesContent );
		lblLayersContent.setPreferredSize( generalInfoDim );
		area.add( lblLayersContent, constraints );
		
		// total length translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblTotal    = new JLabel( Dict.get(Dict.SAMPLES_TOTAL) + ": " );
		area.add( lblTotal, constraints );
		
		// total length content
		constraints.gridx++;
		constraints.anchor  = GridBagConstraints.NORTHWEST;
		String totalContent = soundfontInfo.get( "frames_count" )  + " " + Dict.get( Dict.FRAMES ) + ", "
		                    + soundfontInfo.get( "seconds_count" ) + " " + Dict.get( Dict.SEC    ) + ", "
		                    + soundfontInfo.get( "bytes_count" )   + " " + Dict.get( Dict.BYTES  );
		FlowLabel lblTotalContent = new FlowLabel( totalContent );
		lblTotalContent.setPreferredSize( generalInfoDim );
		area.add( lblTotalContent, constraints );
		
		// average length translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblAverage  = new JLabel( Dict.get(Dict.SAMPLES_AVERAGE) + ": " );
		area.add( lblAverage, constraints );
		
		// average length content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		String avgContent  = soundfontInfo.get( "frames_avg"  ) + " " + Dict.get( Dict.FRAMES ) + ", "
		                   + soundfontInfo.get( "seconds_avg" ) + " " + Dict.get( Dict.SEC    ) + ", "
                           + soundfontInfo.get( "bytes_avg"   ) + " " + Dict.get( Dict.BYTES  );
		FlowLabel lblAverageContent = new FlowLabel( avgContent );
		lblAverageContent.setPreferredSize( generalInfoDim );
		area.add( lblAverageContent, constraints );
		
		// description translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor    = GridBagConstraints.NORTHEAST;
		JLabel lblDescription = new JLabel( Dict.get(Dict.DESCRIPTION) + ": " );
		area.add( lblDescription, constraints );
		
		// description content
		constraints.gridx++;
		constraints.anchor  = GridBagConstraints.NORTHWEST;
		constraints.weighty = 1;
		FlowLabel lblDescriptionContent = new FlowLabel( soundfontInfo.get("description") );
		lblDescriptionContent.setPreferredSize( sfDescriptionDim );
		area.add( lblDescriptionContent, constraints );
		
		return area;
	}
	
	/**
	 * Creates the area for general MIDI sequence information.
	 * 
	 * @return the created MIDI sequence info area.
	 */
	private Container createMidiSequenceInfoArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill       = GridBagConstraints.NONE;
		constraints.insets     = new Insets( 2, 2, 2, 2 );
		constraints.gridx      = 0;
		constraints.gridy      = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 0;
		constraints.weighty    = 0;
		
		// get general sequence info
		HashMap<String, Object> sequenceInfo = SequenceAnalyzer.getSequenceInfo();
		
		// file translation
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblFile     = new JLabel( Dict.get(Dict.FILE) + ": " );
		area.add( lblFile, constraints );
		
		// file name
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		String filename    = "-";
		String type        = (String) sequenceInfo.get( "parser_type" );
		if ( "midica".equals(type) )
			filename = Midica.uiController.getView().getChosenMidicaPLFileLbl().getText();
		else if ( "mid".equals(type) )
			filename = Midica.uiController.getView().getChosenMidiFileLbl().getText();
		FlowLabel lblFileContent = new FlowLabel( filename );
		lblFileContent.setPreferredSize( generalInfoDim );
		area.add( lblFileContent, constraints );
		
		// copyright translation
		constraints.gridy++;
		constraints.gridx  = 0;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblCopy     = new JLabel( Dict.get(Dict.COPYRIGHT) + ": " );
		area.add( lblCopy, constraints );
		
		// copyright content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		String copyright                 = "-";
		Object metaObj                   = sequenceInfo.get("meta_info");
		HashMap<String, String> metaInfo = new HashMap<String, String>();
		if ( metaObj != null )
			metaInfo = (HashMap<String, String>) metaObj;
		if ( metaInfo.containsKey("copyright") )
			copyright = metaInfo.get( "copyright" );
		FlowLabel lblCopyContent = new FlowLabel( copyright );
		lblCopyContent.setPreferredSize( generalInfoDim );
		area.add( lblCopyContent, constraints );
		
		// software translation
		constraints.gridy++;
		constraints.gridx  = 0;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblSoftware = new JLabel( Dict.get(Dict.SOFTWARE_VERSION) + ": " );
		area.add( lblSoftware, constraints );
		
		// software content
		constraints.gridx++;
		constraints.anchor  = GridBagConstraints.NORTHWEST;
		String software     = "-";
		if ( metaInfo.containsKey("software") ) {
			software = metaInfo.get( "software" );
		}
		if ( metaInfo.containsKey("software_date") ) {
			String softwareDate = metaInfo.get( "software_date" );
			software = software + " - " + Dict.get( Dict.SOFTWARE_DATE ) + " " + softwareDate;
		}
		FlowLabel lblSoftwareContent = new FlowLabel( software );
		lblSoftwareContent.setPreferredSize( generalInfoDim );
		area.add( lblSoftwareContent, constraints );
		
		// ticks translation
		constraints.gridy++;
		constraints.gridx  = 0;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblTicks    = new JLabel( Dict.get(Dict.TICK_LENGTH) + ": " );
		area.add( lblTicks, constraints );
		
		// ticks content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		String lenghStr = "-";
		Object ticksObj = sequenceInfo.get("ticks");
		if ( ticksObj != null )
			lenghStr = Long.toString( (Long) ticksObj );
		FlowLabel lblTicksContent = new FlowLabel( lenghStr );
		lblTicksContent.setPreferredSize( generalInfoDim );
		area.add( lblTicksContent, constraints );
		
		// time translation
		constraints.gridy++;
		constraints.gridx  = 0;
		constraints.anchor = GridBagConstraints.NORTHEAST;
		JLabel lblTime     = new JLabel( Dict.get(Dict.TIME_LENGTH) + ": " );
		area.add( lblTime, constraints );
		
		// time content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		lenghStr = "-";
		Object timeObj = sequenceInfo.get("time_length");
		if ( timeObj != null )
			lenghStr = (String) timeObj;
		FlowLabel lblTimeContent = new FlowLabel( (String) lenghStr );
		lblTimeContent.setPreferredSize( generalInfoDim );
		area.add( lblTimeContent, constraints );
		
		// resolution translation
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHEAST;
		JLabel lblResolution = new JLabel( Dict.get(Dict.RESOLUTION) + ": " );
		area.add( lblResolution, constraints );
		
		// resolution content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		String resolution  = "-";
		if ( null != sequenceInfo.get("resolution") )
			resolution = sequenceInfo.get("resolution") + " " + Dict.get( Dict.RESOLUTION_UNIT );
		FlowLabel lblResolutionContent = new FlowLabel( resolution );
		lblResolutionContent.setPreferredSize( generalInfoDim );
		area.add( lblResolutionContent, constraints );
		
		// spacer
		constraints.gridy++;
		constraints.gridx   = 0;
		JLabel spacerLine1 = new JLabel( " " );
		area.add( spacerLine1, constraints );
		
		// tempo BPM headline
		constraints.gridwidth = 2; // colspan
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHWEST;
		JLabel lblTempoBpmHeadline = new JLabel( Dict.get(Dict.TEMPO_BPM) );
		area.add( lblTempoBpmHeadline, constraints );
		constraints.gridwidth = 1; // end of colspan
		
		// BPM average translation
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHEAST;
		JLabel lblBpmAvg = new JLabel( Dict.get(Dict.AVERAGE) + ": " );
		area.add( lblBpmAvg, constraints );
		
		// BPM average content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		String tempoStr = "-";
		Object tempoObj = sequenceInfo.get("tempo_bpm_avg");
		if ( null != tempoObj )
			tempoStr = (String) tempoObj;
		FlowLabel lblBpmAvgContent = new FlowLabel( tempoStr );
		lblBpmAvgContent.setPreferredSize( generalInfoDim );
		area.add( lblBpmAvgContent, constraints );
		
		// BPM min translation
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHEAST;
		JLabel lblBpmMin = new JLabel( Dict.get(Dict.MIN) + ": " );
		area.add( lblBpmMin, constraints );
		
		// BPM min content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		tempoObj           = sequenceInfo.get("tempo_bpm_min");
		if ( null != tempoObj )
			tempoStr = (String) tempoObj;
		FlowLabel lblBpmMinContent = new FlowLabel( tempoStr );
		lblBpmMinContent.setPreferredSize( generalInfoDim );
		area.add( lblBpmMinContent, constraints );
		
		// BPM max translation
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHEAST;
		JLabel lblBpmMax = new JLabel( Dict.get(Dict.MAX) + ": " );
		area.add( lblBpmMax, constraints );
		
		// BPM max content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		tempoObj           = sequenceInfo.get("tempo_bpm_max");
		if ( null != tempoObj )
			tempoStr = (String) tempoObj;
		FlowLabel lblBpmMaxContent = new FlowLabel( tempoStr );
		lblBpmMaxContent.setPreferredSize( generalInfoDim );
		area.add( lblBpmMaxContent, constraints );
		
		// spacer
		constraints.gridy++;
		constraints.gridx   = 0;
		JLabel spacerLine2 = new JLabel( " " );
		area.add( spacerLine2, constraints );
		
		// tempo MPQ headline
		constraints.gridwidth = 2; // colspan
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHWEST;
		JLabel lblTempoMpqHeadline = new JLabel( Dict.get(Dict.TEMPO_MPQ) );
		area.add( lblTempoMpqHeadline, constraints );
		constraints.gridwidth = 1; // end of colspan
		
		// MPQ average translation
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHEAST;
		JLabel lblMpqAvg = new JLabel( Dict.get(Dict.AVERAGE) + ": " );
		area.add( lblMpqAvg, constraints );
		
		// MPQ average content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		tempoObj           = sequenceInfo.get("tempo_mpq_avg");
		if ( null != tempoObj )
			tempoStr = (String) tempoObj;
		FlowLabel lblMpqAvgContent = new FlowLabel( tempoStr );
		lblMpqAvgContent.setPreferredSize( generalInfoDim );
		area.add( lblMpqAvgContent, constraints );
		
		// MPQ min translation
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHEAST;
		JLabel lblMpqMin = new JLabel( Dict.get(Dict.MIN) + ": " );
		area.add( lblMpqMin, constraints );
		
		// MPQ min content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		tempoObj           = sequenceInfo.get("tempo_mpq_min");
		if ( null != tempoObj )
			tempoStr = (String) tempoObj;
		FlowLabel lblMpqMinContent = new FlowLabel( tempoStr );
		lblMpqMinContent.setPreferredSize( generalInfoDim );
		area.add( lblMpqMinContent, constraints );
		
		// MPQ max translation
		constraints.gridy++;
		constraints.gridx    = 0;
		constraints.anchor   = GridBagConstraints.NORTHEAST;
		JLabel lblMpqMax = new JLabel( Dict.get(Dict.MAX) + ": " );
		area.add( lblMpqMax, constraints );
		
		// MPQ max content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		tempoObj           = sequenceInfo.get("tempo_mpq_max");
		if ( null != tempoObj )
			tempoStr = (String) tempoObj;
		FlowLabel lblMpqMaxContent = new FlowLabel( tempoStr );
		lblMpqMaxContent.setPreferredSize( generalInfoDim );
		area.add( lblMpqMaxContent, constraints );
		
		// spacer
		constraints.gridy++;
		constraints.gridx   = 0;
		constraints.weighty = 1;
		JLabel spacer = new JLabel( " " );
		area.add( spacer, constraints );
		
		return area;
	}
	
	/**
	 * Creates the area for banks, instruments and notes
	 * used by the loaded MIDI sequence.
	 * 
	 * @return the created area.
	 */
	private Container createBankInstrNoteArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill       = GridBagConstraints.NONE;
		constraints.insets     = new Insets( 2, 2, 2, 2 );
		constraints.gridx      = 0;
		constraints.gridy      = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 0;
		constraints.weighty    = 0;
		constraints.anchor     = GridBagConstraints.NORTH;
		
		// get tree models and inform the controller
		HashMap<String, Object> sequenceInfo = SequenceAnalyzer.getSequenceInfo();
		Object          totalObj      = sequenceInfo.get( "banks_total" );
		Object          perChannelObj = sequenceInfo.get( "banks_per_channel" );
		MidicaTreeModel totalModel;
		MidicaTreeModel perChannelModel;
		if ( totalObj      != null && totalObj      instanceof MidicaTreeModel
		  && perChannelObj != null && perChannelObj instanceof MidicaTreeModel ) {
			totalModel      = (MidicaTreeModel) totalObj;
			perChannelModel = (MidicaTreeModel) perChannelObj;
			totalModel.postprocess();
			perChannelModel.postprocess();
		}
		else {
			totalModel      = new MidicaTreeModel( Dict.get(Dict.TOTAL)       );
			perChannelModel = new MidicaTreeModel( Dict.get(Dict.PER_CHANNEL) );
		}
		controller.setTreeModel( totalModel,      InfoController.NAME_TREE_BANKS_TOTAL       );
		controller.setTreeModel( perChannelModel, InfoController.NAME_TREE_BANKS_PER_CHANNEL );
		
		// total headline (translation, expand, collapse)
		String headline     = Dict.get( Dict.TOTAL );
		String btnName      = InfoController.NAME_TREE_BANKS_TOTAL;
		Container totalHead = createTreeHeadline( headline, btnName );
		area.add( totalHead, constraints );
		
		// per channel translation
		constraints.gridx++;
		headline = Dict.get( Dict.PER_CHANNEL );
		btnName  = InfoController.NAME_TREE_BANKS_PER_CHANNEL;
		Container perChannelHead = createTreeHeadline( headline, btnName );
		area.add( perChannelHead, constraints );
		
		// total tree
		constraints.gridx = 0;
		constraints.gridy++;
		JTree       totalTree   = new JTree( totalModel );
		JScrollPane totalScroll = new JScrollPane( totalTree );
		totalModel.setTree( totalTree );
		totalScroll.setPreferredSize( bankTreeDim );
		totalScroll.setMinimumSize( bankTreeDim );
		area.add( totalScroll, constraints );
		
		// per channel tree
		constraints.gridx++;
		JTree       channelTree   = new JTree( perChannelModel );
		JScrollPane channelScroll = new JScrollPane( channelTree );
		perChannelModel.setTree( channelTree );
		channelScroll.setPreferredSize( bankTreeDim );
		channelScroll.setMinimumSize( bankTreeDim );
		area.add( channelScroll, constraints );
		
		return area;
	}
	
	/**
	 * Creates the headline area for a JTree, containing a translation
	 * and collapse-all / expand-all buttons.
	 * 
	 * @param headline  Text to be displayed above the tree.
	 * @param btnName   Name for the buttons.
	 * @return the created area.
	 */
	private Container createTreeHeadline( String headline, String btnName ) {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill       = GridBagConstraints.NONE;
		constraints.insets     = new Insets( 2, 2, 2, 2 );
		constraints.gridx      = 0;
		constraints.gridy      = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 0;
		constraints.weighty    = 0;
		constraints.anchor     = GridBagConstraints.CENTER;
		
		// headline translation
		JLabel lblHeadline = new JLabel( headline );
		area.add( lblHeadline, constraints );
		
		// spacer
		constraints.gridx++;
		JLabel spacer1 = new JLabel( "<html>&nbsp;" );
		area.add( spacer1, constraints );
		
		// collapse button
		constraints.gridx++;
		Insets buttonInsets = new Insets( 0, 0, 0, 0 );
		JButton btnCollapse = new JButton( Dict.get(Dict.COLLAPSE_BUTTON) );
		btnCollapse.setToolTipText( Dict.get(Dict.COLLAPSE_TOOLTIP) );
		btnCollapse.setPreferredSize( collapseExpandDim );
		btnCollapse.setMaximumSize( collapseExpandDim );
		btnCollapse.setMargin( buttonInsets );
		btnCollapse.setName( btnName );
		btnCollapse.setActionCommand( InfoController.CMD_COLLAPSE );
		btnCollapse.addActionListener( controller );
		area.add( btnCollapse, constraints );
		
		// spacer
		constraints.gridx++;
		JLabel spacer2 = new JLabel( "" );
		area.add( spacer2, constraints );
		
		// expand button
		constraints.gridx++;
		JButton btnExpand = new JButton( Dict.get(Dict.EXPAND_BUTTON) );
		btnExpand.setToolTipText( Dict.get(Dict.EXPAND_TOOLTIP) );
		btnExpand.setPreferredSize( collapseExpandDim );
		btnExpand.setMaximumSize( collapseExpandDim );
		btnExpand.setMargin( buttonInsets );
		btnExpand.setName( btnName );
		btnExpand.setActionCommand( InfoController.CMD_EXPAND );
		btnExpand.addActionListener( controller );
		area.add( btnExpand, constraints );
		
		return area;
	}
	
	/**
	 * Creates the area for instruments and drumkits of the currently loaded soundfont.
	 * 
	 * @return the created soundfont instruments area.
	 */
	private Container createSoundfontInstrumentArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill   = GridBagConstraints.NONE;
		constraints.insets = new Insets( 2, 2, 2, 2 );
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 1;
		constraints.weighty    = 1;
		
		// label
		JLabel label = new JLabel( Dict.get(Dict.TAB_SOUNDFONT_INSTRUMENTS) );
		area.add( label, constraints );

		// table
		constraints.gridy++;
		JTable table = new JTable();
		table.setModel( new SoundfontInstrumentsTableModel() );
		table.setDefaultRenderer( Object.class, new SoundfontInstrumentTableCellRenderer() );
		JScrollPane scroll = new JScrollPane( table );
		scroll.setPreferredSize( sfInstrTableDim );
		scroll.setMinimumSize( sfInstrTableDim );
		area.add( scroll, constraints );
		
		// set column sizes and colors
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( COL_WIDTH_SF_INSTR_PROGRAM  );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( COL_WIDTH_SF_INSTR_BANK     );
		table.getColumnModel().getColumn( 2 ).setPreferredWidth( COL_WIDTH_SF_INSTR_NAME     );
		table.getColumnModel().getColumn( 3 ).setPreferredWidth( COL_WIDTH_SF_INSTR_CHANNELS );
		table.getColumnModel().getColumn( 4 ).setPreferredWidth( COL_WIDTH_SF_INSTR_KEYS     );
		table.getTableHeader().setBackground( Config.TABLE_HEADER_COLOR );
		
		return area;
	}
	
	/**
	 * Creates the area for resources of the currently loaded soundfont.
	 * 
	 * @return the created soundfont resource area.
	 */
	private Container createSoundfontResourceArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill   = GridBagConstraints.NONE;
		constraints.insets = new Insets( 2, 2, 2, 2 );
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 1;
		constraints.weighty    = 1;
		
		// label
		JLabel label = new JLabel( Dict.get(Dict.TAB_SOUNDFONT_RESOURCES) );
		area.add( label, constraints );
		
		// table
		constraints.gridy++;
		JTable table = new JTable();
		table.setModel( new SoundfontResourceTableModel() );
		table.setDefaultRenderer( Object.class, new SoundfontResourceTableCellRenderer() );
		JScrollPane scroll = new JScrollPane( table );
		scroll.setPreferredSize( sfResourceTableDim );
		scroll.setMinimumSize( sfResourceTableDim );
		area.add( scroll, constraints );
		
		// set column sizes and colors
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( COL_WIDTH_SF_RES_INDEX  );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( COL_WIDTH_SF_RES_TYPE   );
		table.getColumnModel().getColumn( 2 ).setPreferredWidth( COL_WIDTH_SF_RES_NAME   );
		table.getColumnModel().getColumn( 3 ).setPreferredWidth( COL_WIDTH_SF_RES_FRAMES );
		table.getColumnModel().getColumn( 4 ).setPreferredWidth( COL_WIDTH_SF_RES_FORMAT );
		table.getColumnModel().getColumn( 5 ).setPreferredWidth( COL_WIDTH_SF_RES_CLASS  );
		table.getTableHeader().setBackground( Config.TABLE_HEADER_COLOR );
		
		return area;
	}
	
	/**
	 * Creates the version area containing version, author and general information.
	 * 
	 * @return the created version area
	 */
	private Container createVersionArea() {
		// content
		JPanel area = new JPanel();
		
		// layout
		GridBagLayout layout = new GridBagLayout();
		area.setLayout( layout );
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill       = GridBagConstraints.NONE;
		constraints.insets     = new Insets( 2, 2, 2, 2 );
		constraints.gridx      = 0;
		constraints.gridy      = 0;
		constraints.gridheight = 1;
		constraints.gridwidth  = 1;
		constraints.weightx    = 0;
		constraints.weighty    = 0;
		
		// spacer
		JLabel spacerTop   = new JLabel( "<html><br><br><br>" );
		area.add( spacerTop, constraints );
		
		// version translation
		constraints.gridy++;
		constraints.gridx  = 0;
		constraints.anchor = GridBagConstraints.EAST;
		JLabel lblVersion  = new JLabel( Dict.get(Dict.VERSION) + ": " );
		area.add( lblVersion, constraints );
		
		// version content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.WEST;
		JLabel lblVersionContent = new JLabel( Midica.VERSION );
		area.add( lblVersionContent, constraints );
		
		// timestamp translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.EAST;
		JLabel lblTimestamp = new JLabel( Dict.get(Dict.DATE) + ": " );
		area.add( lblTimestamp, constraints );
		
		// timestamp content
		constraints.gridx++;
		constraints.anchor = GridBagConstraints.WEST;
		Date             timestamp = new Date( Midica.VERSION_MINOR * 1000L );
		SimpleDateFormat formatter = new SimpleDateFormat( Dict.get(Dict.TIMESTAMP_FORMAT) );
		JLabel lblTimestampContent = new JLabel( formatter.format(timestamp) );
		area.add( lblTimestampContent, constraints );
		
		// author translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.EAST;
		JLabel lblAuthor   = new JLabel( Dict.get(Dict.AUTHOR) + ": " );
		area.add( lblAuthor, constraints );
		
		// author content
		constraints.gridx++;
		constraints.anchor      = GridBagConstraints.WEST;
		JLabel lblAuthorContent = new JLabel( Midica.AUTHOR );
		area.add( lblAuthorContent, constraints );
		
		// source URL translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.EAST;
		JLabel lblSource   = new JLabel( Dict.get(Dict.SOURCE_URL) + ": " );
		area.add( lblSource, constraints );
		
		// source URL content
		constraints.gridx++;
		constraints.anchor      = GridBagConstraints.WEST;
		JLabel lblSourceContent = new JLabel( Midica.SOURCE_URL );
		area.add( lblSourceContent, constraints );
		
		// website translation
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.anchor = GridBagConstraints.EAST;
		JLabel lblWebsite  = new JLabel( Dict.get(Dict.WEBSITE) + ": " );
		area.add( lblWebsite, constraints );
		
		// website content
		constraints.gridx++;
		constraints.anchor       = GridBagConstraints.WEST;
		JLabel lblWebsiteContent = new JLabel( Midica.URL );
		area.add( lblWebsiteContent, constraints );
		
		// spacer
		constraints.gridy++;
		constraints.gridx   = 0;
		constraints.weighty = 1;
		JLabel spacer = new JLabel( " " );
		area.add( spacer, constraints );
		
		return area;
	}
	
	/**
	 * Creates and shows the info view passing **owner = null** to the constructor.
	 * This is done by calling {@link #showInfoWindow(JDialog)} with parameter **null**.
	 */
	public static void showInfoWindow() {
		showInfoWindow( null );
	}
	
	/**
	 * Creates and shows the info view passing the specified owner window to the constructor.
	 * 
	 * If an info view already exists it will be destroyed before.
	 * This is done in order to make sure that the newly created info view:
	 * 
	 * - exists only once
	 * - will be in the foreground
	 * - will be focused
	 * 
	 * @param owner  The GUI window owning the info window.
	 */
	public static void showInfoWindow( JDialog owner ) {
		
		// it cannot be guaranteed that the info view can be focused
		// so we have to destroy and rebuild it
		if ( null != infoView )
			infoView.close();
		infoView = new InfoView( owner );
	}
	
	/**
	 * Closes and destroys the info window.
	 */
	public void close() {
		setVisible( false );
		dispose();
		infoView = null;
	}
	
	// TODO: add key bindings for nested tabs
	/**
	 * Creates key bindings for the info view and adds them
	 * to the {@link java.awt.KeyboardFocusManager}.
	 * The following key bindings are created:
	 * 
	 * - **ESC** -- close the window
	 * - **N**   -- switch to the note names translation tab
	 * - **P**   -- switch to the percussion shortcut translation tab
	 * - **S**   -- switch to the syntax keyword definition tab
	 * - **I**   -- switch to the instrument shortcut translation tab
	 */
	public void addKeyBindings() {
		
		if ( null == keyProcessor ) {
			keyProcessor = new KeyEventPostProcessor() {
				public boolean postProcessKeyEvent( KeyEvent e ) {
					
					if ( KeyEvent.KEY_PRESSED == e.getID() ) {
						
						// don't handle already consumed shortcuts any more
						if ( e.isConsumed() )
							return true;
						
						if ( KeyEvent.VK_ESCAPE == e.getKeyCode() ) {
							close();
							return true;
						}
						
						else if ( KeyEvent.VK_N == e.getKeyCode() ) {
							content.setSelectedIndex( 0 );
							contentConfig.setSelectedIndex( 0 );
						}
						
						else if ( KeyEvent.VK_P == e.getKeyCode() ) {
							content.setSelectedIndex( 0 );
							contentConfig.setSelectedIndex( 1 );
						}
						
						else if ( KeyEvent.VK_S == e.getKeyCode() ) {
							content.setSelectedIndex( 0 );
							contentConfig.setSelectedIndex( 2 );
						}
						
						else if ( KeyEvent.VK_I == e.getKeyCode() ) {
							content.setSelectedIndex( 0 );
							contentConfig.setSelectedIndex( 3 );
						}
					}
					return e.isConsumed();
				}
			};
		}
		
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor( keyProcessor );
	}
	
	/**
	 * Removes the key bindings for the info view
	 * from the {@link java.awt.KeyboardFocusManager}.
	 */
	public void removeKeyBindings() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventPostProcessor( keyProcessor );
	}
}
