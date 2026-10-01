package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.WordScore;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.Optional;

/** step 8: best state path and Viterbi score grid of the chosen word, compared with the expected / recognised word */
public final class BestPathStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "8. Best path";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public boolean usesWord( ) {
		return true;
	}

	private static WordScore chosen( RecognitionTrace trace, ViewState state ) {
		return state.word( ) == null ? trace.scores( ).get( 0 ) : trace.scoreOf( state.word( ) ).orElse( trace.scores( ).get( 0 ) );
	}

	/** when verification failed: the other one of {expected, recognised} */
	private static Optional< WordScore > comparison( RecognitionTrace trace, WordScore chosen ) {
		if ( !trace.isVerification( ) || trace.verified( ) ) {
			return Optional.empty( );
		}
		String other = chosen.word( ).equalsIgnoreCase( trace.recognizedWord( ) ) ? trace.expectedWord( ) : trace.recognizedWord( );
		return trace.scoreOf( other ).filter( s -> !s.word( ).equalsIgnoreCase( chosen.word( ) ) );
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		WordScore w = chosen( trace, state );
		int states = w.viterbiGrid( )[ 0 ].length;
		String text = String.format( "The Viterbi algorithm finds the most likely sequence of the %d states of %s's left-to-right model (each frame a state "
				+ "may stay, move to the next state or skip one). Score %.1f, rank %d of %d.", states, w.word( ), w.score( ), trace.rankOf( w.word( ) ),
				trace.scores( ).size( ) );
		return text + comparison( trace, w ).map( o -> String.format( " Also shown: the %s model's path (score %.1f).", o.word( ), o.score( ) ) ).orElse( "" );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		WordScore w = chosen( trace, state );
		int f = state.frameIn( w.statePath( ).length );
		Optional< WordScore > other = comparison( trace, w );
		Charts.Series mine = Charts.Series.of( w.word( ), Arrays.stream( w.statePath( ) ).asDoubleStream( ).toArray( ) );
		JFreeChart path = other.isPresent( )
				? Charts.stepLine( "Best state path", "frame", "state", mine,
						Charts.Series.of( other.get( ).word( ), Arrays.stream( other.get( ).statePath( ) ).asDoubleStream( ).toArray( ) ) )
				: Charts.stepLine( "Best state path", "frame", "state", mine );
		JFreeChart symbols = Charts.stepLine( "Codeword per frame", "frame", "codeword",
				Charts.Series.of( "codeword", Arrays.stream( trace.vq( ).codewords( ) ).asDoubleStream( ).toArray( ) ) );
		JFreeChart grid = Charts.heatMap( "Viterbi log scores, " + w.word( ), "frame", "state", w.viterbiGrid( ) );
		for ( JFreeChart c : new JFreeChart[] { path, symbols, grid } ) {
			Charts.addFrameCursor( c, f );
		}
		return Charts.stack( Charts.panel( path ), Charts.panel( symbols ), Charts.panel( grid ) );
	}
}
