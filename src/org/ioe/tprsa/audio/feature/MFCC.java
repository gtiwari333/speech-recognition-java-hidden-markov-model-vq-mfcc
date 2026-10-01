/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.audio.feature;

import java.util.Arrays;

import org.ioe.tprsa.trace.MfccFrame;

/**
 * 
 * @author Ganesh Tiwari
 * 
 */
public class MFCC {

	private final int numMelFilters = 30;// how much
	private final double lowerFilterFreq = 80.00;// FmelLow
	private final double samplingRate;
	private final double upperFilterFreq;
	private final int samplePerFrame;
	// /////
	final FastFourierTransform fastFourierTransform;
	final DCT dct;

	public MFCC(int samplePerFrame, int samplingRate, int numCepstra) {
		this.samplePerFrame = samplePerFrame;
		this.samplingRate = samplingRate;
		// number of mfcc coeffs
		upperFilterFreq = samplingRate / 2.0;
		fastFourierTransform = new FastFourierTransform();
		dct = new DCT(numCepstra, numMelFilters);
	}

	public double[] doMFCC(float[] framedSignal) {
		return computeFrame(framedSignal).cepstra();
	}

	/**
	 * MFCC of one (pre-emphasised, windowed) frame, keeping the intermediate results
	 */
	public MfccFrame computeFrame(float[] framedSignal) {
		// Magnitude Spectrum (pre-emphasis and windowing are done in PreProcess)
		double[] bin = magnitudeSpectrum(framedSignal);
		// process Mel Filterbank
		double[] fbank = melFilter(bin, fftBinIndices());
		// Non-linear transformation
		double[] f = nonLinearTransformation(fbank);
		// Cepstral coefficients, by DCT
		double[] cepc = dct.performDCT(f);
		return new MfccFrame(Arrays.copyOf(bin, samplePerFrame / 2 + 1), f, cepc);
	}

	/**
	 * FFT bins of the mel filter edges / centres: filter k (1..30) rises from bin[k-1] to bin[k] and falls to bin[k+1]
	 */
	public int[] getMelCentreBins() {
		return fftBinIndices();
	}

	private double[] magnitudeSpectrum(float[] frame) {
		double[] magSpectrum = new double[frame.length];
		// calculate FFT for current frame
		fastFourierTransform.computeFFT(frame);
		// System.err.println("FFT SUCCEED");
		// calculate magnitude spectrum
		for (int k = 0; k < frame.length; k++) {
			magSpectrum[k] = Math.sqrt(fastFourierTransform.real[k] * fastFourierTransform.real[k] + fastFourierTransform.imag[k] * fastFourierTransform.imag[k]);
		}
		return magSpectrum;
	}

	private int[] fftBinIndices() {
		int[] cbin = new int[numMelFilters + 2];
		cbin[0] = (int) Math.round(lowerFilterFreq / samplingRate * samplePerFrame);// cbin0
		cbin[cbin.length - 1] = (samplePerFrame / 2);// cbin24
		for (int i = 1; i <= numMelFilters; i++) {// from cbin1 to cbin23
			double fc = centerFreq(i);// center freq for i th filter
			cbin[i] = (int) Math.round(fc / samplingRate * samplePerFrame);
		}
		return cbin;
	}

	/**
	 * performs mel filter operation
	 * 
	 * @param bin
	 *            magnitude spectrum (| |)^2 of fft
	 * @param cbin
	 *            mel filter coeffs
	 * @return mel filtered coeffs--> filter bank coefficients.
	 */
	private double[] melFilter(double[] bin, int[] cbin) {
		double[] temp = new double[numMelFilters + 2];
		for (int k = 1; k <= numMelFilters; k++) {
			double num1 = 0.0, num2 = 0.0;
			for (int i = cbin[k - 1]; i <= cbin[k]; i++) {
				// System.out.println("Inside filter loop");
				// rising edge: 0 at the previous centre, 1 at this centre
				num1 += (cbin[k] == cbin[k - 1] ? 1.0 : (i - cbin[k - 1]) / (double) (cbin[k] - cbin[k - 1])) * bin[i];
			}

			for (int i = cbin[k] + 1; i <= cbin[k + 1]; i++) {
				// System.out.println("Inside filter loop 222222");
				// falling edge: 1 at this centre, 0 at the next centre
				num2 += ((cbin[k + 1] - i) / (double) (cbin[k + 1] - cbin[k])) * bin[i];
			}

			temp[k] = num1 + num2;
		}
		double[] fbank = new double[numMelFilters];
		// System.out.println(fbank[i]);
		System.arraycopy(temp, 1, fbank, 0, numMelFilters);
		return fbank;
	}

	/**
	 * performs nonlinear transformation
	 * 
	 * @param fbank
	 * @return f log of filter bac
	 */
	private double[] nonLinearTransformation(double[] fbank) {
		double[] f = new double[fbank.length];
		final double FLOOR = -50;
		for (int i = 0; i < fbank.length; i++) {
			f[i] = Math.log(fbank[i]);
			// check if ln() returns a value less than the floor
			if (f[i] < FLOOR) {
				f[i] = FLOOR;
			}
		}
		return f;
	}

	private double centerFreq(int i) {
		double melFLow, melFHigh;
		melFLow = freqToMel(lowerFilterFreq);
		melFHigh = freqToMel(upperFilterFreq);
		double temp = melFLow + ((melFHigh - melFLow) / (numMelFilters + 1)) * i;
		return inverseMel(temp);
	}

	private double inverseMel(double x) {
		double temp = Math.pow(10, x / 2595) - 1;
		return 700 * (temp);
	}

	protected double freqToMel(double freq) {
		return 2595 * log10(1 + freq / 700);
	}

	private double log10(double value) {
		return Math.log(value) / Math.log(10);
	}
}
