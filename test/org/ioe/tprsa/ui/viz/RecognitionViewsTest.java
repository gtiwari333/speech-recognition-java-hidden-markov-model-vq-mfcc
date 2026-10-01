package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.recognition.*;
import org.jfree.chart.ChartPanel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class RecognitionViewsTest {

	static List< StepView< RecognitionTrace > > firstFour( ) {
		return List.of( new WaveformStep( ), new FramingStep( ), new SpectrumStep( ), new MfccStep( ) );
	}

	static List< StepView< RecognitionTrace > > all( ) {
		return List.of( new WaveformStep( ), new FramingStep( ), new SpectrumStep( ), new MfccStep( ), new DeltaEnergyStep( ), new VqStep( ),
				new ScoresStep( ), new BestPathStep( ) );
	}

	@Test
	void allEightViewsRender( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		for ( StepView< RecognitionTrace > v : all( ) ) {
			for ( String word : List.of( "Ship", "Apple", "nonexistent" ) ) {
				ViewState s = new ViewState( 3, word );
				assertTrue( ViewTestSupport.renderAll( v.build( t, s ) ) >= 1, v.title( ) );
				assertFalse( v.explanation( t, s ).isBlank( ) );
			}
		}
	}

	@Test
	void viewsRenderASilentRecording( ) throws Exception {
		RecognitionTrace t = TraceFixtures.silent( );
		for ( StepView< RecognitionTrace > v : all( ) ) {
			assertTrue( ViewTestSupport.renderAll( v.build( t, new ViewState( 0, null ) ) ) >= 1, v.title( ) );
			assertFalse( v.explanation( t, new ViewState( 0, null ) ).isBlank( ), v.title( ) );
		}
		assertTrue( new WaveformStep( ).explanation( t, new ViewState( 0, null ) ).contains( "No speech detected" ) );
	}

	@Test
	void scoresAreRankedWithTheRecognisedWordHighlighted( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		ChartPanel panel = ( ChartPanel ) new ScoresStep( ).build( t, new ViewState( 0, null ) );
		org.jfree.chart.plot.CategoryPlot plot = panel.getChart( ).getCategoryPlot( );
		assertEquals( t.scores( ).size( ), plot.getDataset( ).getColumnCount( ) );
		assertEquals( t.recognizedWord( ), plot.getDataset( ).getColumnKey( 0 ) );
		assertEquals( Charts.HIGHLIGHT, plot.getRenderer( ).getItemPaint( 0, 0 ) );
		int expected = t.rankOf( "Ship" ) - 1;
		assertEquals( "Ship (expected)", plot.getDataset( ).getColumnKey( expected ) );
		assertEquals( Charts.MARK, plot.getRenderer( ).getItemPaint( 0, expected ) );
		String text = new ScoresStep( ).explanation( t, new ViewState( 0, null ) );
		assertTrue( text.contains( "margin" ) && text.contains( "✘" ), text );
	}

	@Test
	void bestPathComparesTheExpectedAndRecognisedWords( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		java.awt.Container stack = ( java.awt.Container ) new BestPathStep( ).build( t, new ViewState( 0, t.recognizedWord( ) ) );
		ChartPanel path = ( ChartPanel ) stack.getComponent( 0 );
		assertFalse( t.verified( ) );
		assertEquals( 2, path.getChart( ).getXYPlot( ).getDataset( ).getSeriesCount( ) );
		RecognitionTrace ok = TraceFixtures.recognized( );
		assertTrue( ok.verified( ) );
		java.awt.Container okStack = ( java.awt.Container ) new BestPathStep( ).build( ok, new ViewState( 0, null ) );
		assertEquals( 1, ( ( ChartPanel ) okStack.getComponent( 0 ) ).getChart( ).getXYPlot( ).getDataset( ).getSeriesCount( ) );
	}

	@Test
	void everyViewRendersForAMisrecognition( ) throws Exception {
		for ( RecognitionTrace t : List.of( TraceFixtures.misrecognized( ), TraceFixtures.recognized( ) ) ) {
			int middle = t.features( ).frameCount( ) / 2;
			for ( StepView< RecognitionTrace > v : firstFour( ) ) {
				for ( ViewState s : List.of( new ViewState( 0, null ), new ViewState( middle, "Ship" ), new ViewState( 10_000, null ) ) ) {
					assertTrue( ViewTestSupport.renderAll( v.build( t, s ) ) >= 1, v.title( ) );
					assertFalse( v.explanation( t, s ).isBlank( ), v.title( ) );
				}
			}
		}
	}

	@Test
	void explanationsCarryTheKeyNumbers( ) throws Exception {
		RecognitionTrace t = TraceFixtures.recognized( );
		assertTrue( new WaveformStep( ).explanation( t, new ViewState( 0, null ) ).contains( "σ" ) );
		assertTrue( new FramingStep( ).explanation( t, new ViewState( 4, null ) ).contains( "Frame 5/" ) );
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
