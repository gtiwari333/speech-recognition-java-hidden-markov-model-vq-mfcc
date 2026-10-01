package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.PreprocessTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;

/** step 2: frames of the trimmed signal, pre-emphasis and the Hamming window for the selected frame */
public final class FramingStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "2. Framing & windowing";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		int f = state.frameIn( p.frameCount( ) );
		return String.format( "The trimmed signal is cut into frames of %d samples (%.1f ms), a new frame every %d samples (50 %% overlap): %d frames. "
				+ "Frame %d/%d: pre-emphasis s'(n) = s(n) − %.2f·s(n−1) boosts the high frequencies, then the Hamming window tapers the frame edges.",
				p.frameSize( ), 1000.0 * p.frameSize( ) / p.sampleRate( ), p.hop( ), p.frameCount( ), f + 1, p.frameCount( ), p.preEmphasis( ) );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		int f = state.frameIn( p.frameCount( ) );
		double rate = p.sampleRate( );
		JFreeChart signal = Charts.line( "Trimmed signal", "time (s)", "amplitude", Charts.envelope( "trimmed", p.trimmedSignal( ), 1 / rate, 4000 ) );
		Charts.addInterval( signal, f * p.hop( ) / rate, ( f * p.hop( ) + p.frameSize( ) ) / rate, Charts.SELECTED );
		JFreeChart frame = Charts.line( "Frame " + ( f + 1 ), "sample", "amplitude", Charts.Series.of( "raw", Charts.toDouble( p.rawFrames( )[ f ] ) ),
				Charts.Series.of( "pre-emphasised", Charts.toDouble( p.preEmphasisedFrames( )[ f ] ) ),
				Charts.Series.of( "windowed", Charts.toDouble( p.windowedFrames( )[ f ] ) ) );
		JFreeChart window = Charts.line( "Hamming window", "sample", "weight", Charts.Series.of( "w(n)", Charts.toDouble( p.hammingWindow( ) ) ) );
		return Charts.stack( Charts.panel( signal ), Charts.panel( frame ), Charts.panel( window ) );
	}
}
