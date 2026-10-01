package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.TrainingSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class InspectorTest {

	@Test
	void recognitionInspectorShowsEveryStep( ) throws Exception {
		RecognitionInspector inspector = new RecognitionInspector( );
		assertInstanceOf( JLabel.class, inspector.currentView( ), "empty state before the first run" );
		RecognitionTrace t = TraceFixtures.misrecognized( );
		inspector.show( t );
		assertEquals( 8, inspector.stepCount( ) );
		assertTrue( inspector.headerText( ).contains( t.recognizedWord( ) ) && inspector.headerText( ).contains( "Ship" ) );
		for ( int i = 0; i < inspector.stepCount( ); i++ ) {
			inspector.selectStep( i );
			assertTrue( ViewTestSupport.renderAll( inspector.currentView( ) ) >= 1, "step " + i );
			assertFalse( inspector.explanationText( ).isBlank( ) );
		}
		inspector.selectStep( 1 );
		assertTrue( inspector.isFrameControlVisible( ) );
		assertFalse( inspector.isWordControlVisible( ) );
		inspector.selectStep( 7 );
		assertTrue( inspector.isWordControlVisible( ) );
		inspector.selectWord( "Ship" );
		assertTrue( inspector.explanationText( ).contains( "Ship's left-to-right model" ), inspector.explanationText( ) );
	}

	@Test
	void frameSliderSelectsTheFrameShownInTheExplanation( ) throws Exception {
		// the misrecognized fixture has only 1 frame, so the frame slider is exercised on the multi-frame fixture
		RecognitionInspector inspector = new RecognitionInspector( );
		inspector.show( TraceFixtures.recognized( ) );
		inspector.selectStep( 1 );
		inspector.setFrame( 4 );
		assertTrue( inspector.explanationText( ).contains( "Frame 5/" ), inspector.explanationText( ) );
	}

	@Test
	void trainingInspectorShowsEveryStepAndWord( ) throws Exception {
		TrainingInspector inspector = new TrainingInspector( );
		TrainingSession s = TraceFixtures.training( );
		inspector.show( s );
		assertEquals( 5, inspector.stepCount( ) );
		for ( int i = 0; i < inspector.stepCount( ); i++ ) {
			inspector.selectStep( i );
			for ( String word : s.wordNames( ) ) {
				inspector.selectWord( word );
				assertNotNull( inspector.currentView( ) );
				ViewTestSupport.renderAll( inspector.currentView( ) );
			}
		}
		inspector.show( TrainingSession.EMPTY.withCodebook( s.codebook( ) ) );
		inspector.selectStep( 2 );
		assertInstanceOf( JLabel.class, inspector.currentView( ), "no words trained yet" );
	}

	@Test
	void showingANewTraceKeepsTheSelectedStep( ) throws Exception {
		RecognitionInspector inspector = new RecognitionInspector( );
		inspector.show( TraceFixtures.misrecognized( ) );
		inspector.selectStep( 6 );
		inspector.show( TraceFixtures.silent( ) );
		assertTrue( inspector.explanationText( ).startsWith( "Each word's HMM" ), inspector.explanationText( ) );
	}
}
