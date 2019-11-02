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

/**
 * Feature extraction, cepstral mean substraction, and merging with deltas
 * 
 * @author Ganesh Tiwari
 */
public class FeatureExtract {

	private final float[][]		framedSignal;
	private final int				noOfFrames;
	/**
	 * how many mfcc coefficients per frame
	 */
	private final int				numCepstra	= 12;

	private final double[][]		featureVector;
	private final double[][]		mfccFeature;
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
		this.framedSignal = framedSignal;
		this.noOfFrames = framedSignal.length;
		mfcc = new MFCC(samplePerFrame, samplingRate, numCepstra );
		en = new Energy(samplePerFrame);
		fv = new FeatureVector( );
		mfccFeature = new double[ noOfFrames ][ numCepstra ];
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
		energyVal = en.calcEnergy( framedSignal );

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
		System.gc( );
	}

	/**
	 * calculates MFCC coefficients of each frame
	 */
	private void calculateMFCC( ) {
		for ( int i = 0; i < noOfFrames; i++ ) {
			// for each frame i, make mfcc from current framed signal
			mfccFeature[ i ] = mfcc.doMFCC( framedSignal[ i ] );// 2D data
		}
	}

	/**
	 * performs cepstral mean substraction. <br>
	 * it removes channel effect...
	 */
	private void doCepstralMeanNormalization( ) {
		double sum;
		double mean;
		double[][] mCeps = new double[ noOfFrames ][ numCepstra - 1 ];// same size
																		// as mfcc
																		// 1.loop through each mfcc coeff
		for ( int i = 0; i < numCepstra - 1; i++ ) {
			// calculate mean
			sum = 0.0;
			for ( int j = 0; j < noOfFrames; j++ ) {
				sum += mfccFeature[ j ][ i ];// ith coeff of all frame
			}
			mean = sum / noOfFrames;
			// subtract
			for ( int j = 0; j < noOfFrames; j++ ) {
				mCeps[ j ][ i ] = mfccFeature[ j ][ i ] - mean;
			}
		}
	}
}
