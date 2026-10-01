package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.PreprocessTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

/** step 1: normalised waveform with the noise window and the frames kept by end point detection */
public final class WaveformStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "1. Waveform & end point detection";
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		String text = String.format( "The recording (%.2f s) is normalised to a peak of 1. The first 200 ms (grey) estimate the background noise: "
				+ "mean μ = %.4f, σ = %.4f. A sample is voiced when |x − μ| / σ ≥ %.0f; 10 ms frames with mostly voiced samples (green) are kept: "
				+ "%.0f %% of the signal.", p.durationSec( ), p.noiseMean( ), p.noiseSd( ), p.voicedThreshold( ), 100 * p.keptFraction( ) );
		return p.wholeSignalUsed( ) ? text + " No speech detected: the whole signal is used." : text;
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		double rate = p.sampleRate( );
		JFreeChart chart = Charts.line( "Normalised waveform", "time (s)", "amplitude", Charts.envelope( "signal", p.normalisedSignal( ), 1 / rate, 4000 ) );
		Charts.addInterval( chart, 0, p.noiseSamples( ) / rate, Charts.NOISE );
		for ( int[] run : runs( p.voicedFrames( ) ) ) {
			Charts.addInterval( chart, run[ 0 ] * p.epdFrameSize( ) / rate, run[ 1 ] * p.epdFrameSize( ) / rate, Charts.VOICED );
		}
		return Charts.panel( chart );
	}

	/** [start, endExclusive] of every run of true values */
	public static List< int[] > runs( boolean[] flags ) {
		List< int[] > runs = new ArrayList<>( );
		int start = -1;
		for ( int i = 0; i <= flags.length; i++ ) {
			boolean on = i < flags.length && flags[ i ];
			if ( on && start < 0 ) {
				start = i;
			} else if ( !on && start >= 0 ) {
				runs.add( new int[] { start, i } );
				start = -1;
			}
		}
		return runs;
	}
}
