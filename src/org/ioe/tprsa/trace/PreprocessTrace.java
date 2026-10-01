package org.ioe.tprsa.trace;

/**
 * what pre-processing did to one recording: normalisation, end point detection, framing, pre-emphasis, windowing
 *
 * @param normalisedSignal
 *            the recording divided by its peak amplitude
 * @param noiseFrames
 *            frames of {@code epdFrameSize} samples the noise statistics were computed from (the quietest ones)
 * @param voicedFrames
 *            end point detection decision per frame of {@code epdFrameSize} samples
 * @param wholeSignalUsed
 *            true when no speech was detected and the whole signal was kept
 * @param trimmedSignal
 *            the signal after silence removal
 */
public record PreprocessTrace( int sampleRate, float[] normalisedSignal, double noiseMean, double noiseSd, double voicedThreshold,
		int noiseSamples, int epdFrameSize, boolean[] noiseFrames, boolean[] voicedFrames, boolean wholeSignalUsed, float[] trimmedSignal, int frameSize,
		int hop, float preEmphasis, float[] hammingWindow, float[][] rawFrames, float[][] preEmphasisedFrames, float[][] windowedFrames ) {

	public double durationSec( ) {
		return normalisedSignal.length / ( double ) sampleRate;
	}

	/** fraction of the signal kept by end point detection */
	public double keptFraction( ) {
		return normalisedSignal.length == 0 ? 0 : trimmedSignal.length / ( double ) normalisedSignal.length;
	}

	public int frameCount( ) {
		return windowedFrames.length;
	}
}
