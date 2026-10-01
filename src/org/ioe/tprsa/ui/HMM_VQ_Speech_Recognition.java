/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.ui;

import org.ioe.tprsa.audio.JSoundCapture;
import org.ioe.tprsa.db.DataBase;
import org.ioe.tprsa.db.ObjectIODataBase;
import org.ioe.tprsa.db.TrainingTestingWaveFiles;
import org.ioe.tprsa.mediator.Operations;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.ui.viz.RecognitionInspector;
import org.ioe.tprsa.ui.viz.TrainingInspector;
import org.ioe.tprsa.util.ErrorManager;
import org.ioe.tprsa.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Main application- contains GUI and main method - train / test / data collection all can be done from here
 * 
 * @author Ganesh Tiwari
 */
public class HMM_VQ_Speech_Recognition extends JFrame {

	private static final long	serialVersionUID		= 1L;
	private JPanel				jContentPane			= null;
	private JSoundCapture		soundCapture			= null;
	private JTabbedPane			jTabbedPane				= null;
	private JPanel				verifyPanel				= null;
	private JPanel				trainPanel				= null;
	private JPanel				runTrainingPanel		= null;
	private JButton				getWordButton			= null;
	private JButton				btnVerify				= null;
	private JComboBox			wordsComboBoxVerify		= null;
	private JComboBox			wordsComboBoxAddWord	= null;

	private JButton				getWordButton1			= null;

	private final Operations			opr						= new Operations( );
	private final RecognitionInspector	recognitionInspector	= new RecognitionInspector( );
	private final TrainingInspector		trainingInspector		= new TrainingInspector( );
	private final JTabbedPane			inspectorTabs			= new JTabbedPane( );
	private final JLabel				jobStatus				= new JLabel( "Ready" );
	private final JProgressBar			jobProgress				= new JProgressBar( );
	private final JobRunner				jobs					= new JobRunner( jobStatus, jobProgress );
	private TrainingSession				trainingSession			= TrainingSession.EMPTY;
	private JLabel				aboutLBL;
	private JLabel				statusLBLRecognize;
	private JTextField			addWordToCombo			= null;
	private JButton				addWordToComboBtn		= null;
	private JButton				addTrainSampleBtn		= null;
	private JLabel				lblChooseAWord;
	private JLabel				lblAddANew;

	private JButton				generateCodeBookBtn;
	private JButton				btnNewButton_2;

	/**
	 * This is the default constructor
	 */
	public HMM_VQ_Speech_Recognition( ) {
		super( );
		initialize( );
		ErrorManager.setMessageLbl( getStatusLblRecognize( ) );
	}

	/**
	 * This method initializes this
	 * 
	 * @return void
	 */
	private void initialize( ) {
		this.setSize( 1200, 750 );
		this.setContentPane( getMainPane( ) );
		this.setTitle( "HMM/VQ Speech Recognition - by GT" );
		jobs.register( getBtnVerify( ), getGetWordButton( ), getGetWordButton1( ), getGenerateCodeBookBtn( ), getBtnNewButton_2( ) );
	}

	/**
	 * the original controls on the left, the step inspectors on the right, a status line at the bottom
	 */
	private JPanel getMainPane( ) {
		JPanel left = getJContentPane( );
		left.setPreferredSize( new Dimension( 470, 300 ) );
		JPanel leftHolder = new JPanel( new BorderLayout( ) );
		leftHolder.add( left, BorderLayout.NORTH );
		leftHolder.setMinimumSize( new Dimension( 470, 300 ) );
		inspectorTabs.addTab( "Recognition steps", recognitionInspector );
		inspectorTabs.addTab( "Training steps", trainingInspector );
		inspectorTabs.setMinimumSize( new Dimension( 300, 300 ) );
		JPanel statusBar = new JPanel( new BorderLayout( 8, 0 ) );
		statusBar.setBorder( BorderFactory.createEmptyBorder( 2, 8, 2, 8 ) );
		statusBar.add( jobStatus, BorderLayout.CENTER );
		statusBar.add( jobProgress, BorderLayout.EAST );
		JPanel main = new JPanel( new BorderLayout( ) );
		JSplitPane split = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, leftHolder, inspectorTabs );
		split.setDividerLocation( 480 );
		split.setResizeWeight( 0 );
		split.setContinuousLayout( true );
		main.add( split, BorderLayout.CENTER );
		main.add( statusBar, BorderLayout.SOUTH );
		return main;
	}

	private void showRecognition( RecognitionTrace trace ) {
		recognitionInspector.show( trace );
		inspectorTabs.setSelectedComponent( recognitionInspector );
	}

	private void showTraining( ) {
		trainingInspector.show( trainingSession );
		inspectorTabs.setSelectedComponent( trainingInspector );
	}

	private void showError( Throwable t ) {
		if ( !( t instanceof IllegalStateException || t instanceof IllegalArgumentException ) ) {
			t.printStackTrace( );
		}
		JOptionPane.showMessageDialog( this, JobRunner.message( t ), "Error", JOptionPane.ERROR_MESSAGE );
	}

	/** the captured audio, or null after showing an error */
	private float[] recordedAudio( ) {
		try {
			return soundCapture.getAudioData( );
		} catch ( Exception e ) {
			showError( e );
			return null;
		}
	}

	private void reloadRegisteredWords( ) {
		getWordsComboBoxVerify( ).removeAllItems( );
		DataBase db = new ObjectIODataBase( );
		db.setType( "hmm" );
		for ( String word : db.readRegistered( ) ) {
			getWordsComboBoxVerify( ).addItem( word );
		}
	}

	/**
	 * This method initializes jTabbedPane
	 * 
	 * @return javax.swing.JTabbedPane
	 */
	private JTabbedPane getJTabbedPane( ) {
		if ( jTabbedPane == null ) {
			jTabbedPane = new JTabbedPane( );
			jTabbedPane.setBounds( new Rectangle( 10, 94, 449, 178 ) );
			jTabbedPane.addTab( "Verify Word", null, getVerifyWordPanel( ), null );
			jTabbedPane.addTab( "Add Sample", null, getAddSamplePanel( ), null );
			jTabbedPane.addTab( "Run HMM Train", null, getRunTrainingPanel( ), null );
			jTabbedPane.addChangeListener(e -> {
				System.out.println( "state changed" );
				if ( jTabbedPane.getSelectedIndex( ) == 0 ) {
					soundCapture.setSaveFileName( null );
				} else if ( jTabbedPane.getSelectedIndex( ) == 1 ) {
					soundCapture.setSaveFileName( "TrainWav" + File.separator + getWordsComboBoxAddWord( ).getSelectedItem( ) + File.separator + getWordsComboBoxAddWord( ).getSelectedItem( ) );
				}

			});
		}
		return jTabbedPane;
	}

	private File getTestFile( ) {
		JFileChooser jfc = new JFileChooser( "Select WAVE File to Verify" );
		jfc.setFileSelectionMode( JFileChooser.FILES_AND_DIRECTORIES );
		jfc.setSize( new Dimension( 541, 326 ) );
		jfc.setFileFilter( new javax.swing.filechooser.FileFilter( ) {

			@Override
			public String getDescription( ) {
				return ".WAV & .WAVE Files";
			}

			@Override
			public boolean accept( File f ) {
				return ( f.getName( ).toLowerCase( ).endsWith( "wav" ) || f.getName( ).toLowerCase( ).endsWith( "wave" ) || f.isDirectory( ) );
			}
		} );
		int chooseOpt = jfc.showOpenDialog( this );
		if ( chooseOpt == JFileChooser.APPROVE_OPTION ) {
			File file = jfc.getSelectedFile( );
			System.out.println( "selected File " + file );
			return file;
		}
		return null;
	}

	/**
	 * This method initializes jPanel
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getVerifyWordPanel( ) {
		if ( verifyPanel == null ) {
			JLabel jLabel = new JLabel( );
			jLabel.setBounds( new Rectangle( 13, 55, 245, 20 ) );
			jLabel.setText( "Or... Select a Word From the List to Verify" );
			verifyPanel = new JPanel( );
			verifyPanel.setLayout( null );
			verifyPanel.add( getGetWordButton( ), null );
			verifyPanel.add( getWordsComboBoxVerify( ), null );
			verifyPanel.add( jLabel, null );
			verifyPanel.add( getGetWordButton1( ), null );
			verifyPanel.add( getBtnVerify( ) );
			verifyPanel.add( getStatusLblRecognize( ) );
		}
		return verifyPanel;
	}

	private void showNothingToRecognize( ) {
		jobStatus.setText( getWordsComboBoxVerify( ).getItemCount( ) == 0 ? "Train first: no trained words" : "Record a word first" );
	}

	private JButton getBtnVerify( ) {
		if ( btnVerify == null ) {
			btnVerify = new JButton( "Verify" );
			btnVerify.addActionListener(e -> {
				if ( soundCapture.isSoundDataAvailable( ) && getWordsComboBoxVerify( ).getItemCount( ) > 0 ) {
					String expected = getWordsComboBoxVerify( ).getSelectedItem( ).toString( );
					float[] audio = recordedAudio( );
					if ( audio != null ) {
						jobs.run( "Verifying \"" + expected + "\"", progress -> opr.recognizeWithTrace( audio, expected ), trace -> {
							getStatusLblRecognize( ).setText( trace.verified( ) ? "Verified" : "<html>Not Verified<br>(heard " + trace.recognizedWord( ) + ")</html>" );
							showRecognition( trace );
						}, this::showError );
					}
				} else {
					showNothingToRecognize( );
				}
			});
			btnVerify.setBounds( 126, 111, 89, 24 );
		}
		return btnVerify;
	}

	/**
	 * This method initializes jPanel1
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getAddSamplePanel( ) {
		if ( trainPanel == null ) {
			trainPanel = new JPanel( );
			trainPanel.setLayout( null );
			trainPanel.add( getWordsComboBoxAddWord( ), null );
			trainPanel.add( getAddWordToCombo( ), null );
			trainPanel.add( getAddWordToComboBtn( ), null );
			trainPanel.add( getLblChooseAWord( ) );
			trainPanel.add( getLblAddANew( ) );
			// trainPanel.add(getAddTrainSampleBtn(), null);
		}
		return trainPanel;
	}

	/**
	 * This method initializes runTrainingPanel
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getRunTrainingPanel( ) {
		if ( runTrainingPanel == null ) {
			runTrainingPanel = new JPanel( );
			runTrainingPanel.setLayout( null );
			runTrainingPanel.add( getGenerateCodeBookBtn( ) );
			runTrainingPanel.add( getBtnNewButton_2( ) );
		}
		return runTrainingPanel;
	}

	/**
	 * This method initializes getWordButton
	 * 
	 * @return javax.swing.JButton
	 */
	private JButton getGetWordButton( ) {
		if ( getWordButton == null ) {
			getWordButton = new JButton( "Recognize With Just Recorded" );
			getWordButton.addActionListener(arg0 -> {
				if ( soundCapture.isSoundDataAvailable( ) && getWordsComboBoxVerify( ).getItemCount( ) > 0 ) {
					float[] audio = recordedAudio( );
					if ( audio != null ) {
						jobs.run( "Recognizing the recording", progress -> opr.recognizeWithTrace( audio, null ), trace -> {
							getStatusLblRecognize( ).setText( trace.recognizedWord( ) );
							showRecognition( trace );
						}, this::showError );
					}
				} else {
					showNothingToRecognize( );
				}
			});
			getWordButton.setBounds( new Rectangle( 13, 8, 202, 24 ) );
		}
		return getWordButton;
	}

	/**
	 * This method initializes wordsComboBox
	 * 
	 * @return javax.swing.JComboBox
	 */
	private JComboBox getWordsComboBoxVerify( ) {
		if ( wordsComboBoxVerify == null ) {
			DataBase db = new ObjectIODataBase( );
			db.setType( "hmm" );
			wordsComboBoxVerify = new JComboBox( );
			try {
				List< String > regs = db.readRegistered( );
				for ( String string: regs ) {
					wordsComboBoxVerify.addItem( string );
				}
			} catch ( Exception ignored) {
			}
			wordsComboBoxVerify.setBounds( new Rectangle( 13, 75, 202, 24 ) );
		}
		return wordsComboBoxVerify;
	}

	private JComboBox getWordsComboBoxAddWord( ) {
		if ( wordsComboBoxAddWord == null ) {
			TrainingTestingWaveFiles ttwf = new TrainingTestingWaveFiles( "train" );
			wordsComboBoxAddWord = new JComboBox( );
			try {
				List< String > regs = ttwf.readWordWavFolder( );
				for (String reg : regs) {
					wordsComboBoxAddWord.addItem(reg);
				}
			} catch ( Exception ignored) {
			}
			wordsComboBoxAddWord.setBounds( new Rectangle( 11, 103, 202, 24 ) );
			wordsComboBoxAddWord.addItemListener(e -> soundCapture.setSaveFileName( "TrainWav" + File.separator + getWordsComboBoxAddWord( ).getSelectedItem( ) + File.separator + getWordsComboBoxAddWord( ).getSelectedItem( ) ));
		}
		return wordsComboBoxAddWord;
	}

	/**
	 * This method initializes getWordButton1
	 * 
	 * @return javax.swing.JButton
	 */
	private JButton getGetWordButton1( ) {
		if ( getWordButton1 == null ) {
			getWordButton1 = new JButton( "Recognize a Saved WAV File" );
			getWordButton1.addActionListener(e -> {
				File f = getTestFile( );
				if ( f != null ) {
					jobs.run( "Recognizing " + f.getName( ), progress -> opr.recognizeWithTrace( f, null ), trace -> {
						getStatusLblRecognize( ).setText( trace.recognizedWord( ) );
						showRecognition( trace );
					}, this::showError );
				}
			});
			getWordButton1.setBounds( new Rectangle( 225, 8, 189, 24 ) );
		}
		return getWordButton1;
	}

	/**
	 * This method initializes jContentPane
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getJContentPane( ) {
		if ( jContentPane == null ) {
			jContentPane = new JPanel( );
			jContentPane.setLayout( null );
			jContentPane.add( getJTabbedPane( ) );
			jContentPane.add( getSoundCapture( ) );
			jContentPane.add( getAboutLBL( ) );
		}
		return jContentPane;
	}

	private JSoundCapture getSoundCapture( ) {
		if ( soundCapture == null ) {
			soundCapture = new JSoundCapture( true, true );
			soundCapture.setBounds( 10, 10, 431, 74 );
		}
		return soundCapture;
	}

	private JLabel getAboutLBL( ) {
		if ( aboutLBL == null ) {
			aboutLBL = new JLabel( "Developer: Ganesh Tiwari,Visit ganeshtiwaridotcomdotnp.blogspot.com For MORE " );
			aboutLBL.setHorizontalAlignment( SwingConstants.CENTER );
			aboutLBL.setFont( new Font( "Tahoma", Font.PLAIN, 11 ) );
			aboutLBL.setBounds( 10, 275, 449, 16 );
		}
		return aboutLBL;
	}

	private JLabel getStatusLblRecognize( ) {
		if ( statusLBLRecognize == null ) {
			statusLBLRecognize = new JLabel( "" );
			statusLBLRecognize.setHorizontalAlignment( SwingConstants.CENTER );
			statusLBLRecognize.setBounds( 225, 71, 189, 68 );
		}
		return statusLBLRecognize;
	}

	/**
	 * This method initializes addWordToCombo
	 * 
	 * @return javax.swing.JTextField
	 */
	private JTextField getAddWordToCombo( ) {
		if ( addWordToCombo == null ) {
			addWordToCombo = new JTextField( );
			addWordToCombo.setBounds( new Rectangle( 10, 42, 202, 24 ) );
		}
		return addWordToCombo;
	}

	/**
	 * This method initializes addWordToComboBtn
	 * 
	 * @return javax.swing.JButton
	 */
	private JButton getAddWordToComboBtn( ) {
		if ( addWordToComboBtn == null ) {
			addWordToComboBtn = new JButton( "Add Word" );
			addWordToComboBtn.addActionListener(e -> {
				String newWord = Utils.clean( getAddWordToCombo( ).getText( ) );
				boolean isAlreadyRegistered = false;
				if ( !newWord.isEmpty( ) ) {
					// already in combo box
					for ( int i = 0; i < getWordsComboBoxAddWord( ).getItemCount( ); i++ ) {
						if ( getWordsComboBoxAddWord( ).getItemAt( i ).toString( ).equalsIgnoreCase( newWord ) ) {
							isAlreadyRegistered = true;
							break;
						}
					}
					// if not add
					if ( !isAlreadyRegistered ) {
						getWordsComboBoxAddWord( ).addItem( getAddWordToCombo( ).getText( ) );
						getWordsComboBoxAddWord( ).repaint( );
						getAddWordToCombo( ).setText( "" );
					}
				}
			});
			addWordToComboBtn.setBounds( new Rectangle( 222, 42, 142, 24 ) );
		}
		return addWordToComboBtn;
	}

	/**
	 * This method initializes addTrainSample
	 * 
	 * @return javax.swing.JButton
	 */
	private JButton getAddTrainSampleBtn( ) {
		if ( addTrainSampleBtn == null ) {
			addTrainSampleBtn = new JButton( "Record" );
			addTrainSampleBtn.setBounds( new Rectangle( 223, 103, 141, 24 ) );
			addTrainSampleBtn.addActionListener(e -> {
				if ( addTrainSampleBtn.getText( ).startsWith( "Record" ) ) {
					soundCapture.startRecord( );
					addTrainSampleBtn.setText( "Save Captured" );
				} else if ( addTrainSampleBtn.getText( ).startsWith( "Save" ) ) {
					// TODO: decouple path, may be singleton conf for path
					soundCapture.setSaveFileName( "TrainWav" + File.separator + getWordsComboBoxAddWord( ).getSelectedItem( ) + File.separator + getWordsComboBoxAddWord( ).getSelectedItem( ) );

					try {

						soundCapture.getFileNameAndSaveFile( );
						addTrainSampleBtn.setText( "Record" );

					} catch ( Exception e2 ) {
						e2.printStackTrace( );
					}
				}
			});
		}
		return addTrainSampleBtn;
	}

	/**
	 * @param args
	 */
	public static void main( String[] args ) {
		try {
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName( ) );
		} catch ( Exception e ) {
			System.out.println( e.toString( ) );
		}
		SwingUtilities.invokeLater(() -> {
			HMM_VQ_Speech_Recognition test = new HMM_VQ_Speech_Recognition( );
			test.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

			test.setVisible( true );
		});
	}

	private JLabel getLblChooseAWord( ) {
		if ( lblChooseAWord == null ) {
			lblChooseAWord = new JLabel( "Choose a word to record sound and save to corresponding folder" );
			lblChooseAWord.setBounds( 11, 77, 325, 14 );
		}
		return lblChooseAWord;
	}

	private JLabel getLblAddANew( ) {
		if ( lblAddANew == null ) {
			lblAddANew = new JLabel( "Add a new Word" );
			lblAddANew.setBounds( 11, 11, 126, 14 );
		}
		return lblAddANew;
	}

	private JButton getGenerateCodeBookBtn( ) {
		if ( generateCodeBookBtn == null ) {
			generateCodeBookBtn = new JButton( "Generate CodeBook" );
			generateCodeBookBtn.addActionListener(e -> jobs.run( "Generating the codebook", opr::generateCodebookWithTrace, codebook -> {
				trainingSession = trainingSession.withCodebook( codebook );
				showTraining( );
			}, this::showError ));
			generateCodeBookBtn.setBounds( 10, 32, 167, 23 );
		}
		return generateCodeBookBtn;
	}

	private JButton getBtnNewButton_2( ) {
		if ( btnNewButton_2 == null ) {
			btnNewButton_2 = new JButton( "Train HMM" );
			btnNewButton_2.addActionListener(e -> jobs.run( "Training the word HMMs", opr::hmmTrainWithTrace, words -> {
				trainingSession = trainingSession.withWords( words );
				showTraining( );
				reloadRegisteredWords( );
			}, this::showError ));
			btnNewButton_2.setBounds( 10, 74, 167, 23 );
		}
		return btnNewButton_2;
	}
}
