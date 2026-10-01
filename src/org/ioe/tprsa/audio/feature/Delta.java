/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.audio.feature;

/**
 * calculates delta by linear regression for 2D data
 * 
 * @author Ganesh Tiwari
 * @reference Spectral Features for Automatic Text-Independent Speaker
 *            Recognition @author Tomi Kinnunen, @fromPage 83
 */
public class Delta {
	/**
	 * @param M
	 *            regression window size <br>
	 *            i.e.,number of frames to take into account while taking delta
	 */
	int M;

	public Delta() {
	}

	/**
	 * @param M
	 *            length of regression window
	 */
	public void setRegressionWindow(int M) {
		this.M = M;
	}

	/**
	 * denominator of the regression formula: 2 * sum(m^2), m = 1..M
	 */
	private double denominator() {
		double mSqSum = 0;
		for (int m = 1; m <= M; m++) {
			mSqSum += m * m;
		}
		return 2 * mSqSum;
	}

	/**
	 * regression deltas (HTK Book eq. 5.16): d_t = sum_{m=1..M} m (c_{t+m} - c_{t-m}) / (2 sum_{m=1..M} m^2);
	 * at the start and end the first / last vector is replicated to fill the window (HTK default)
	 */
	public double[][] performDelta2D(double[][] data) {
		int noOfMfcc = data[0].length;
		int frameCount = data.length;
		double mSqSum = denominator();
		double[][] delta = new double[frameCount][noOfMfcc];
		for (int t = 0; t < frameCount; t++) {
			for (int i = 0; i < noOfMfcc; i++) {
				double sum = 0;
				for (int m = 1; m <= M; m++) {
					sum += m * (data[clamp(t + m, frameCount)][i] - data[clamp(t - m, frameCount)][i]);
				}
				delta[t][i] = sum / mSqSum;
			}
		}
		return delta;
	}

	public double[] performDelta1D(double[] data) {
		int frameCount = data.length;
		double mSqSum = denominator();
		double[] delta = new double[frameCount];
		for (int t = 0; t < frameCount; t++) {
			double sum = 0;
			for (int m = 1; m <= M; m++) {
				sum += m * (data[clamp(t + m, frameCount)] - data[clamp(t - m, frameCount)]);
			}
			delta[t] = sum / mSqSum;
		}
		return delta;
	}

	/**
	 * index of a frame inside [0, frameCount), i.e. replicate the first / last frame
	 */
	private static int clamp(int t, int frameCount) {
		return Math.max(0, Math.min(frameCount - 1, t));
	}
}
