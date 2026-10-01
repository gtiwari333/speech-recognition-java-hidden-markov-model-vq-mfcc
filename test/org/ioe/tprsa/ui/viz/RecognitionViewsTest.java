package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.recognition.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class RecognitionViewsTest {

	static List< StepView< RecognitionTrace > > firstFour( ) {
		return List.of( new WaveformStep( ), new FramingStep( ), new SpectrumStep( ), new MfccStep( ) );
	}

	@Test
	void everyViewRendersForAMisrecognition( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		int middle = t.features( ).frameCount( ) / 2;
		for ( StepView< RecognitionTrace > v : firstFour( ) ) {
			for ( ViewState s : List.of( new ViewState( 0, null ), new ViewState( middle, "Ship" ), new ViewState( 10_000, null ) ) ) {
				assertTrue( ViewTestSupport.renderAll( v.build( t, s ) ) >= 1, v.title( ) );
				assertFalse( v.explanation( t, s ).isBlank( ), v.title( ) );
			}
		}
	}

	@Test
	void explanationsCarryTheKeyNumbers( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		assertTrue( new WaveformStep( ).explanation( t, new ViewState( 0, null ) ).contains( "σ" ) );
		assertTrue( new FramingStep( ).explanation( t, new ViewState( 4, null ) ).contains( "Frame " + Math.min( 5, t.preprocess( ).frameCount( ) ) + "/" ) );
		assertTrue( new SpectrumStep( ).explanation( t, new ViewState( 0, null ) ).contains( "30 triangular filters" ) );
		assertTrue( new MfccStep( ).explanation( t, new ViewState( 0, null ) ).contains( "√(2/30)" ) );
	}

	@Test
	void runsOfVoicedFrames( ) {
		List< int[] > runs = WaveformStep.runs( new boolean[] { false, true, true, false, true } );
		assertEquals( 2, runs.size( ) );
		assertArrayEquals( new int[] { 1, 3 }, runs.get( 0 ) );
		assertArrayEquals( new int[] { 4, 5 }, runs.get( 1 ) );
	}
}
