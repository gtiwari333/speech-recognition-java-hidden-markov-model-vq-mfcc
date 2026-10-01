package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.Optional;

/** training step 2: codeword sequence of each recording of the selected word */
public final class TrainingSequencesStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "2. Training sequences";
	}

	@Override
	public boolean usesWord( ) {
		return true;
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		return WordSteps.word( session, state ).filter( w -> !w.isSkipped( ) )
				.map( w -> String.format( "Each of the %d recordings of %s is quantised with the codebook into a sequence of codewords (one row per recording: %s). "
						+ "Baum-Welch trains the HMM on these sequences.", w.files( ).size( ), w.word( ), String.join( ", ", w.files( ) ) ) )
				.orElse( "Run Train HMM to see the training sequences." );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		Optional< WordTrainingTrace > word = WordSteps.word( session, state );
		JComponent unavailable = WordSteps.unavailable( word );
		if ( unavailable != null ) {
			return unavailable;
		}
		WordTrainingTrace w = word.get( );
		int longest = w.codewordSequences( ).stream( ).mapToInt( s -> s.length ).max( ).orElse( 0 );
		double[][] z = new double[ longest ][ w.codewordSequences( ).size( ) ];
		for ( double[] column : z ) {
			Arrays.fill( column, Double.NaN );
		}
		for ( int r = 0; r < w.codewordSequences( ).size( ); r++ ) {
			int[] seq = w.codewordSequences( ).get( r );
			for ( int t = 0; t < seq.length; t++ ) {
				z[ t ][ r ] = seq[ t ];
			}
		}
		return Charts.panel( Charts.heatMap( "Codeword sequences of " + w.word( ), "frame", "recording", z ) );
	}
}
