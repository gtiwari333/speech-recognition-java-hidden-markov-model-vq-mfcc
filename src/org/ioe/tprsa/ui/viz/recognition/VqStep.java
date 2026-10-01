package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.VqTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Arrays;

/** step 6: codeword index and quantization distance per frame */
public final class VqStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "6. Vector quantization";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		VqTrace vq = trace.vq( );
		return String.format( "Each 39-value feature vector is replaced by the index of the nearest of the %d codewords (Euclidean distance); the HMMs "
				+ "only see this sequence of %d symbols. %d distinct codewords are used; mean distance to the chosen codeword %.2f.",
				vq.codebookSize( ), vq.codewords( ).length, vq.distinctCodewords( ), vq.meanDistance( ) );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		VqTrace vq = trace.vq( );
		int f = state.frameIn( vq.codewords( ).length );
		double[] codewords = Arrays.stream( vq.codewords( ) ).asDoubleStream( ).toArray( );
		JFreeChart symbols = Charts.stepLine( "Codeword per frame", "frame", "codeword", Charts.Series.of( "codeword", codewords ) );
		JFreeChart distances = Charts.line( "Distance to the chosen codeword", "frame", "distance", Charts.Series.of( "distance", vq.distances( ) ) );
		Charts.addFrameCursor( symbols, f );
		Charts.addFrameCursor( distances, f );
		return Charts.stack( Charts.panel( symbols ), Charts.panel( distances ) );
	}
}
