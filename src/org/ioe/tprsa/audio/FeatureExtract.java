/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.audio;

import org.ioe.tprsa.audio.feature.Delta;
import org.ioe.tprsa.audio.feature.Energy;
import org.ioe.tprsa.audio.feature.FeatureVector;
import org.ioe.tprsa.audio.feature.MFCC;
import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.MfccFrame;

/**
 * Feature extraction, cepstral mean substraction, and merging with deltas
 * 
 * @author Ganesh Tiwari
 */
public class FeatureExtract {

	private final float[][]		framedSignal;
	private final float[][]		rawFramedSignal;
	private final int				noOfFrames;
	/**
	 * how many mfcc coefficients per frame
	 */
	private final int				numCepstra	= 12;

	private final double[][]		featureVector;
	private final double[][]		mfccFeature;
	private final double[][]		magnitudeSpectra;
	private final double[][]		logMelEnergies;
	private final double[][]		mfccBeforeCmn;
	private double[][]		deltaMfcc;
	private double[][]		deltaDeltaMfcc;
	private double[]		energyVal;
	private double[]		deltaEnergy;
	private double[]		deltaDeltaEnergy;
	private final FeatureVector	fv;
	private final MFCC			mfcc;
	private final Delta			delta;
	private final Energy			en;

	// FeatureVector fv;
	/**
	 * constructor of feature extract
	 * 
	 * @param framedSignal
	 *            2-D audio signal obtained after framing
	 * @param samplePerFrame
	 *            number of samples per frame
	 */
	public FeatureExtract( float[][] framedSignal, int samplingRate, int samplePerFrame ) {
		this( framedSignal, framedSignal, samplingRate, samplePerFrame );
	}

	/**
	 * @param framedSignal
	 *            pre-emphasised, windowed frames for the MFCCs
	 * @param rawFramedSignal
	 *            the same frames before pre-emphasis and windowing, for the log energy (HTK RAWENERGY = T)
	 */
	public FeatureExtract( float[][] framedSignal, float[][] rawFramedSignal, int samplingRate, int samplePerFrame ) {
		this.framedSignal = framedSignal;
		this.rawFramedSignal = rawFramedSignal;
		this.noOfFrames = framedSignal.length;
		mfcc = new MFCC(samplePerFrame, samplingRate, numCepstra );
		en = new Energy(samplePerFrame);
		fv = new FeatureVector( );
		mfccFeature = new double[ noOfFrames ][ numCepstra ];
		magnitudeSpectra = new double[ noOfFrames ][ ];
		logMelEnergies = new double[ noOfFrames ][ ];
		mfccBeforeCmn = new double[ noOfFrames ][ ];
		deltaMfcc = new double[ noOfFrames ][ numCepstra ];
		deltaDeltaMfcc = new double[ noOfFrames ][ numCepstra ];
		energyVal = new double[ noOfFrames ];
		deltaEnergy = new double[ noOfFrames ];
		deltaDeltaEnergy = new double[ noOfFrames ];
		featureVector = new double[ noOfFrames ][ 3 * numCepstra + 3 ];
		delta = new Delta( );
	}

	public FeatureVector getFeatureVector( ) {
		return fv;
	}

	/**
	 * generates feature vector by combining mfcc, and its delta and delta deltas also contains energy and its deltas
	 */
	public void makeMfccFeatureVector( ) {
		calculateMFCC( );
		doCepstralMeanNormalization( );
		// delta
		delta.setRegressionWindow( 2 );// 2 for delta
		deltaMfcc = delta.performDelta2D( mfccFeature );
		// delta delta
		delta.setRegressionWindow( 1 );// 1 for delta delta
		deltaDeltaMfcc = delta.performDelta2D( deltaMfcc );
		// energy
		energyVal = en.calcEnergy( rawFramedSignal );

		delta.setRegressionWindow( 1 );
		// energy delta
		deltaEnergy = delta.performDelta1D( energyVal );
		delta.setRegressionWindow( 1 );
		// energy delta delta
		deltaDeltaEnergy = delta.performDelta1D( deltaEnergy );
		for ( int i = 0; i < framedSignal.length; i++ ) {
            if (numCepstra >= 0) System.arraycopy(mfccFeature[i], 0, featureVector[i], 0, numCepstra);
            if (2 * numCepstra - numCepstra >= 0)
                System.arraycopy(deltaMfcc[i], numCepstra - numCepstra, featureVector[i], numCepstra, 2 * numCepstra - numCepstra);
            if (3 * numCepstra - 2 * numCepstra >= 0)
                System.arraycopy(deltaDeltaMfcc[i], 2 * numCepstra - 2 * numCepstra, featureVector[i], 2 * numCepstra, 3 * numCepstra - 2 * numCepstra);
			featureVector[ i ][ 3 * numCepstra ] = energyVal[ i ];
			featureVector[ i ][ 3 * numCepstra + 1 ] = deltaEnergy[ i ];
			featureVector[ i ][ 3 * numCepstra + 2 ] = deltaDeltaEnergy[ i ];
		}
		fv.setMfccFeature( mfccFeature );
		fv.setFeatureVector( featureVector );
	}

	/**
	 * calculates MFCC coefficients of each frame
	 */
	private void calculateMFCC( ) {
		for ( int i = 0; i < noOfFrames; i++ ) {
			// for each frame i, make mfcc from current framed signal
			MfccFrame frame = mfcc.computeFrame( framedSignal[ i ] );
			magnitudeSpectra[ i ] = frame.magnitudeSpectrum( );
			logMelEnergies[ i ] = frame.logMelEnergies( );
			mfccFeature[ i ] = frame.cepstra( );
			mfccBeforeCmn[ i ] = frame.cepstra( ).clone( );
		}
	}

	/**
	 * performs cepstral mean substraction. <br>
	 * it removes channel effect...
	 */
	private void doCepstralMeanNormalization( ) {
		double sum;
		double mean;
		// 1.loop through each mfcc coeff
		for ( int i = 0; i < numCepstra; i++ ) {
			// calculate mean
			sum = 0.0;
			for ( int j = 0; j < noOfFrames; j++ ) {
				sum += mfccFeature[ j ][ i ];// ith coeff of all frame
			}
			mean = sum / noOfFrames;
			// subtract
			for ( int j = 0; j < noOfFrames; j++ ) {
				mfccFeature[ j ][ i ] -= mean;
			}
		}
	}

	/**
	 * all intermediates of {@link #makeMfccFeatureVector()}
	 */
	public FeatureTrace toTrace( ) {
		if ( fv.getFeatureVector( ) == null ) {
			throw new IllegalStateException( "call makeMfccFeatureVector() first" );
		}
		return new FeatureTrace( magnitudeSpectra, logMelEnergies, mfccBeforeCmn, mfccFeature, deltaMfcc, deltaDeltaMfcc, energyVal,
				deltaEnergy, deltaDeltaEnergy, featureVector, mfcc.getMelCentreBins( ) );
	}
}
