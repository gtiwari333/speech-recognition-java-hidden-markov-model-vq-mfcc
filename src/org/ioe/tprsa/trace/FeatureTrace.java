package org.ioe.tprsa.trace;

/**
 * per frame feature extraction intermediates of one recording; every 2-D array is [frame][coefficient]
 *
 * @param melCentreBins
 *            FFT bin of each mel filter edge / centre (32 entries for 30 filters)
 */
public record FeatureTrace( double[][] magnitudeSpectra, double[][] logMelEnergies, double[][] mfccBeforeCmn, double[][] mfcc,
		double[][] deltaMfcc, double[][] deltaDeltaMfcc, double[] logEnergy, double[] deltaLogEnergy, double[] deltaDeltaLogEnergy,
		double[][] featureVectors, int[] melCentreBins ) {

	public int frameCount( ) {
		return featureVectors.length;
	}
}
