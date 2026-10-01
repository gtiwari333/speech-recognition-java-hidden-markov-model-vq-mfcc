package org.ioe.tprsa.trace;

/**
 * MFCC intermediates of one frame
 *
 * @param magnitudeSpectrum
 *            |X(k)| for k = 0..N/2
 * @param logMelEnergies
 *            log of the 30 mel filter bank outputs
 * @param cepstra
 *            the 12 MFCCs (before cepstral mean normalisation)
 */
public record MfccFrame( double[] magnitudeSpectrum, double[] logMelEnergies, double[] cepstra ) {
}
