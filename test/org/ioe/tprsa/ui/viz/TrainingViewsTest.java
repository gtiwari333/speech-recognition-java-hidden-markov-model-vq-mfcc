package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.ui.viz.training.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class TrainingViewsTest {

	static List< StepView< TrainingSession > > all( ) {
		return List.of( new CodebookStep( ), new TrainingSequencesStep( ), new ConvergenceStep( ), new ModelStep( ), new SummaryStep( ) );
	}

	@Test
	void everyViewRendersForEveryWord( ) throws Exception {
		TrainingSession s = TraceFixtures.training( );
		for ( StepView< TrainingSession > v : all( ) ) {
			for ( String word : s.wordNames( ) ) {
				JComponent c = v.build( s, new ViewState( 0, word ) );
				assertNotNull( c, v.title( ) );
				ViewTestSupport.renderAll( c );
				assertFalse( v.explanation( s, new ViewState( 0, word ) ).isBlank( ), v.title( ) );
			}
		}
		assertEquals( 2, ViewTestSupport.renderAll( new CodebookStep( ).build( s, new ViewState( 0, null ) ) ) );
		assertEquals( 2, ViewTestSupport.renderAll( new ModelStep( ).build( s, new ViewState( 0, "Hello" ) ) ) );
	}

	@Test
	void skippedWordAndMissingStepsShowAMessage( ) throws Exception {
		TrainingSession s = TraceFixtures.training( );
		assertInstanceOf( JLabel.class, new ConvergenceStep( ).build( s, new ViewState( 0, "Empty" ) ) );
		assertTrue( ( ( JLabel ) new ModelStep( ).build( s, new ViewState( 0, "Empty" ) ) ).getText( ).contains( "no .wav files" ) );
		assertInstanceOf( JLabel.class, new CodebookStep( ).build( TrainingSession.EMPTY.withWords( s.words( ) ), new ViewState( 0, null ) ) );
		assertInstanceOf( JLabel.class, new TrainingSequencesStep( ).build( TrainingSession.EMPTY.withCodebook( s.codebook( ) ), new ViewState( 0, null ) ) );
	}

	@Test
	void summaryHasOneRowPerWord( ) throws Exception {
		TrainingSession s = TraceFixtures.training( );
		JTable table = ( JTable ) ( ( JScrollPane ) new SummaryStep( ).build( s, new ViewState( 0, null ) ) ).getViewport( ).getView( );
		assertEquals( s.words( ).size( ), table.getRowCount( ) );
		assertEquals( "Empty", table.getValueAt( 2, 0 ) );
		assertEquals( "no .wav files", table.getValueAt( 2, 5 ) );
	}
}
