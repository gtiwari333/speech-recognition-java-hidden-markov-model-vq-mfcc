/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.audio;

import org.ioe.tprsa.audio.preProcessings.EndPointDetection;

/**
 * pre-processing steps
 * 
 * @author Ganesh Tiwari
 */
public class PreProcess {

	final float[]				originalSignal;		// initial extracted PCM,
	final float[]				afterEndPtDetection;// after endPointDetection
	public int			noOfFrames;			// calculated total no of frames
	final int					samplePerFrame;		// how many samples in one frame
	int					framedArrayLength;	// how many samples in framed array
	public float[][]	framedSignal;
	/**
	 * frames before pre-emphasis and windowing, used for the log energy (HTK RAWENERGY = T)
	 */
	public float[][]	rawFramedSignal;
	float[]				hammingWindow;
	final EndPointDetection	epd;
	final int					samplingRate;
	static final float			PRE_EMPHASIS	= 0.95f;

	/**
	 * constructor, all steps are called frm here
	 * 
	 * @param samplePerFrame
	 *            how many samples in one frame,=660 << frameDuration, typically 30; samplingFreq, typically 22Khz
	 */
	public PreProcess( float[] originalSignal, int samplePerFrame, int samplingRate ) {
		this.originalSignal = originalSignal.clone( ); // normalised in place, keep the caller's array intact
		this.samplePerFrame = samplePerFrame;
		this.samplingRate = samplingRate;

		normalizePCM( );
		epd = new EndPointDetection( this.originalSignal, this.samplingRate );
		afterEndPtDetection = epd.doEndPointDetection( );
		// ArrayWriter.printFloatArrayToFile(afterEndPtDetection, "endPt.txt");
		doFraming( );
		rawFramedSignal = new float[ noOfFrames ][ ];
		for ( int i = 0; i < noOfFrames; i++ ) {
			rawFramedSignal[ i ] = framedSignal[ i ].clone( );
		}
		doPreEmphasis( );
		doWindowing( );
	}

	private void normalizePCM( ) {
		float max = 0;
		for ( float sample : originalSignal ) {
			max = Math.max( max, Math.abs( sample ) );
		}
		if ( max == 0 ) {
			return; // all silent
		}
		for ( int i = 0; i < originalSignal.length; i++ ) {
			originalSignal[ i ] = originalSignal[ i ] / max;
		}
	}

	/**
	 * divides the whole signal into frames of samplerPerFrame
	 */
	private void doFraming( ) {
		// calculate no of frames, for framing

		// 50% overlapping frames; a signal shorter than one frame becomes a single zero padded frame
		noOfFrames = Math.max( 1, 2 * afterEndPtDetection.length / samplePerFrame - 1 );
		framedSignal = new float[ noOfFrames ][ samplePerFrame ];
		for ( int i = 0; i < noOfFrames; i++ ) {
			int startIndex = ( i * samplePerFrame / 2 );
			int length = Math.min( samplePerFrame, afterEndPtDetection.length - startIndex );
			System.arraycopy( afterEndPtDetection, startIndex, framedSignal[ i ], 0, length );
		}
	}

	/**
	 * first order pre-emphasis of each frame, s'_n = s_n - k s_(n-1) (HTK Book eq. 5.1),
	 * applied before windowing; the first sample uses s'_1 = (1 - k) s_1 as in HTK
	 */
	private void doPreEmphasis( ) {
		for ( float[] frame : framedSignal ) {
			for ( int n = frame.length - 1; n >= 1; n-- ) {
				frame[ n ] -= PRE_EMPHASIS * frame[ n - 1 ];
			}
			frame[ 0 ] *= 1 - PRE_EMPHASIS;
		}
	}

	/**
	 * does hamming window on each frame
	 */
	private void doWindowing( ) {
		// prepare hammingWindow
		hammingWindow = new float[ samplePerFrame + 1 ];
		// prepare for through out the data
		for ( int i = 1; i <= samplePerFrame; i++ ) {

			// HTK Book eq. 5.2: 0.54 - 0.46 cos(2 pi (n - 1) / (N - 1)), n = 1..N
			hammingWindow[ i ] = ( float ) ( 0.54 - 0.46 * ( Math.cos( 2 * Math.PI * ( i - 1 ) / ( samplePerFrame - 1 ) ) ) );
		}
		// do windowing
		for ( int i = 0; i < noOfFrames; i++ ) {
			for ( int j = 0; j < samplePerFrame; j++ ) {
				framedSignal[ i ][ j ] = framedSignal[ i ][ j ] * hammingWindow[ j + 1 ];
			}
		}
	}
}
