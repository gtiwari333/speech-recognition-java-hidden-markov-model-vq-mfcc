package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.CodebookTrace;
import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.List;

/** training step 1: LBG splitting and k-means distortion, codeword usage */
public final class CodebookStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "1. Codebook (LBG)";
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		CodebookTrace c = session.codebook( );
		if ( c == null ) {
			return "Run Generate CodeBook to see how the codebook is built.";
		}
		List< CodebookTrace.SplitTrace > splits = c.splits( );
		double[] last = splits.get( splits.size( ) - 1 ).distortions( );
		return String.format( "LBG: starting from the mean of all %d training feature vectors, every codeword c is split into c·(1 + 0.01) and c·(1 − 0.01), "
				+ "then k-means assigns each vector to its nearest codeword and moves each codeword to the mean of its vectors until the distortion "
				+ "(sum of distances) stops improving. Repeated until there are %d codewords; final distortion %.1f, %d codewords unused.",
				c.trainingVectors( ), c.vectorsPerCodeword( ).length, last[ last.length - 1 ], c.unusedCodewords( ) );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		CodebookTrace c = session.codebook( );
		if ( c == null ) {
			return Charts.message( "Run Generate CodeBook to see this step." );
		}
		Charts.Series[] series = c.splits( ).stream( ).map( s -> Charts.Series.of( s.codebookSize( ) + " codewords", s.distortions( ) ) )
				.toArray( Charts.Series[]::new );
		JFreeChart distortion = Charts.line( "k-means distortion after each split", "k-means iteration (0 = right after the split)", "distortion", series );
		JFreeChart usage = Charts.xyBars( "Training vectors per codeword", "codeword", "vectors",
				Arrays.stream( c.vectorsPerCodeword( ) ).asDoubleStream( ).toArray( ) );
		return Charts.stack( Charts.panel( distortion ), Charts.panel( usage ) );
	}
}
