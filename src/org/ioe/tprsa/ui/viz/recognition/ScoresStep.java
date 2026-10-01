package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.WordScore;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;

import javax.swing.JComponent;
import java.util.List;

/** step 7: every word model's Viterbi score, ranked */
public final class ScoresStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "7. Word scores";
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		List< WordScore > s = trace.scores( );
		StringBuilder text = new StringBuilder( String.format( "Each word's HMM scores the codeword sequence with the Viterbi algorithm (log probability of "
				+ "the best state path; higher is better). Recognised: %s (%.1f).", s.get( 0 ).word( ), s.get( 0 ).score( ) ) );
		if ( s.size( ) > 1 ) {
			text.append( String.format( " Runner-up: %s (%.1f), margin %.1f.", s.get( 1 ).word( ), s.get( 1 ).score( ), trace.margin( ) ) );
		} else {
			text.append( " Only one word model is trained." );
		}
		if ( trace.isVerification( ) ) {
			text.append( trace.verified( ) ? String.format( " Expected %s: ✔ verified.", trace.expectedWord( ) )
					: trace.scoreOf( trace.expectedWord( ) ).map( e -> String.format( " Expected %s: ✘ not verified, it scored %.1f (rank %d).",
							trace.expectedWord( ), e.score( ), trace.rankOf( trace.expectedWord( ) ) ) )
							.orElse( String.format( " Expected %s: ✘ no model for this word.", trace.expectedWord( ) ) ) );
		}
		return text.toString( );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		List< WordScore > s = trace.scores( );
		String[] labels = new String[ s.size( ) ];
		double[] values = new double[ s.size( ) ];
		int mark = -1;
		for ( int i = 0; i < s.size( ); i++ ) {
			boolean expected = trace.isVerification( ) && s.get( i ).word( ).equalsIgnoreCase( trace.expectedWord( ) );
			labels[ i ] = s.get( i ).word( ) + ( expected ? " (expected)" : "" );
			values[ i ] = s.get( i ).score( );
			mark = expected && i > 0 ? i : mark;
		}
		return Charts.panel( Charts.bar( "Viterbi log score per word", "word", "log score", labels, values, 0, mark ) );
	}
}
