package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Optional;

/** training step 4: the learned transition and output matrices */
public final class ModelStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "4. Learned model";
	}

	@Override
	public boolean usesWord( ) {
		return true;
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		return WordSteps.word( session, state ).filter( w -> !w.isSkipped( ) )
				.map( w -> String.format( "%s's left-to-right model: a[i][j] (top) is the probability of moving from state i to state j; only j = i, i+1, i+2 "
						+ "are allowed. b[j][k] (bottom) is the probability that state j emits codeword k; after each Baum-Welch step 1e-4 is added to every allowed probability and the rows are renormalised, so no codeword or allowed transition has probability 0; disallowed transitions stay exactly 0.",
						w.word( ) ) )
				.orElse( "Run Train HMM to see the learned models." );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		Optional< WordTrainingTrace > word = WordSteps.word( session, state );
		JComponent unavailable = WordSteps.unavailable( word );
		if ( unavailable != null ) {
			return unavailable;
		}
		WordTrainingTrace w = word.get( );
		double[][] a = Charts.transpose( w.finalTransition( ) ); // x = to state, y = from state
		JFreeChart transition = Charts.heatMap( "Transition probabilities a[from][to], " + w.word( ), "to state", "from state", a );
		Charts.annotateCells( transition, a, "%.2f" );
		JFreeChart output = Charts.heatMap( "Output probabilities b[state][codeword]", "codeword", "state", Charts.transpose( w.finalOutput( ) ) );
		return Charts.stack( Charts.panel( transition ), Charts.panel( output ) );
	}
}
