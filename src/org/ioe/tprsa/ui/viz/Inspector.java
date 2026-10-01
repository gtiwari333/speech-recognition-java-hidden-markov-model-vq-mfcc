package org.ioe.tprsa.ui.viz;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * step list + the selected step's view + explanation, frame slider and word picker; shows the most recent trace
 */
public abstract class Inspector< T > extends JPanel {

	private final List< StepView< T > >	steps;
	private final JList< String >		stepList;
	private final JLabel				header		= new JLabel( " " );
	private final JPanel				viewHolder	= new JPanel( new BorderLayout( ) );
	private final JTextArea				explanation	= new JTextArea( 3, 40 );
	private final JLabel				frameTitle	= new JLabel( "frame" );
	private final JSlider				frameSlider	= new JSlider( 0, 0, 0 );
	private final JLabel				frameLabel	= new JLabel( );
	private final JLabel				wordTitle	= new JLabel( "word" );
	private final JComboBox< String >	wordBox		= new JComboBox<>( );
	private T							trace;
	private JComponent					currentView;
	private boolean						updating;

	protected Inspector( List< StepView< T > > steps, String emptyMessage ) {
		super( new BorderLayout( 6, 6 ) );
		this.steps = List.copyOf( steps );
		stepList = new JList<>( steps.stream( ).map( StepView::title ).toArray( String[]::new ) );
		stepList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		stepList.setSelectedIndex( 0 );
		stepList.addListSelectionListener( e -> {
			if ( !e.getValueIsAdjusting( ) ) {
				rebuild( );
			}
		} );
		explanation.setLineWrap( true );
		explanation.setWrapStyleWord( true );
		explanation.setEditable( false );
		frameSlider.addChangeListener( e -> {
			frameLabel.setText( ( frameSlider.getValue( ) + 1 ) + " / " + ( frameSlider.getMaximum( ) + 1 ) );
			if ( !frameSlider.getValueIsAdjusting( ) ) {
				rebuild( );
			}
		} );
		wordBox.addActionListener( e -> rebuild( ) );

		JPanel controls = new JPanel( new FlowLayout( FlowLayout.LEFT ) );
		controls.add( frameTitle );
		controls.add( frameSlider );
		controls.add( frameLabel );
		controls.add( wordTitle );
		controls.add( wordBox );
		JPanel south = new JPanel( new BorderLayout( ) );
		south.add( new JScrollPane( explanation ), BorderLayout.CENTER );
		south.add( controls, BorderLayout.SOUTH );
		JPanel right = new JPanel( new BorderLayout( ) );
		right.add( viewHolder, BorderLayout.CENTER );
		right.add( south, BorderLayout.SOUTH );
		header.setBorder( BorderFactory.createEmptyBorder( 4, 6, 0, 6 ) );
		add( header, BorderLayout.NORTH );
		add( new JScrollPane( stepList ), BorderLayout.WEST );
		add( right, BorderLayout.CENTER );

		currentView = Charts.message( emptyMessage );
		viewHolder.add( currentView );
		setControlsVisible( false, false );
	}

	protected abstract List< String > words( T trace );

	/** word preselected in the picker, or null */
	protected abstract String defaultWord( T trace );

	protected abstract int frameCount( T trace );

	protected abstract String header( T trace );

	/** show a new trace, keeping the selected step */
	public void show( T newTrace ) {
		trace = newTrace;
		updating = true;
		try {
			wordBox.removeAllItems( );
			for ( String w : words( newTrace ) ) {
				wordBox.addItem( w );
			}
			String preselected = defaultWord( newTrace );
			if ( preselected != null ) {
				wordBox.setSelectedItem( preselected );
			}
			int frames = Math.max( 1, frameCount( newTrace ) );
			frameSlider.setMaximum( frames - 1 );
			frameSlider.setValue( frames / 2 );
		} finally {
			updating = false;
		}
		header.setText( header( newTrace ) );
		rebuild( );
	}

	private void rebuild( ) {
		if ( updating || trace == null ) {
			return;
		}
		StepView< T > step = steps.get( Math.max( 0, stepList.getSelectedIndex( ) ) );
		ViewState state = new ViewState( frameSlider.getValue( ), ( String ) wordBox.getSelectedItem( ) );
		setControlsVisible( step.usesFrame( ), step.usesWord( ) && wordBox.getItemCount( ) > 0 );
		frameLabel.setText( ( state.frame( ) + 1 ) + " / " + ( frameSlider.getMaximum( ) + 1 ) );
		viewHolder.removeAll( );
		currentView = step.build( trace, state );
		viewHolder.add( currentView );
		explanation.setText( step.explanation( trace, state ) );
		explanation.setCaretPosition( 0 );
		viewHolder.revalidate( );
		viewHolder.repaint( );
	}

	private void setControlsVisible( boolean frame, boolean word ) {
		frameTitle.setVisible( frame );
		frameSlider.setVisible( frame );
		frameLabel.setVisible( frame );
		wordTitle.setVisible( word );
		wordBox.setVisible( word );
	}

	public void selectStep( int index ) {
		stepList.setSelectedIndex( index );
	}

	public void selectWord( String word ) {
		wordBox.setSelectedItem( word );
	}

	public void setFrame( int frame ) {
		frameSlider.setValue( frame );
	}

	public int stepCount( ) {
		return steps.size( );
	}

	public JComponent currentView( ) {
		return currentView;
	}

	public String explanationText( ) {
		return explanation.getText( );
	}

	public String headerText( ) {
		return header.getText( );
	}

	public boolean isFrameControlVisible( ) {
		return frameSlider.isVisible( );
	}

	public boolean isWordControlVisible( ) {
		return wordBox.isVisible( );
	}
}
