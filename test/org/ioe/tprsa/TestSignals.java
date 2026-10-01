package org.ioe.tprsa;

import java.util.Random;

/**
 * deterministic synthetic signals shared by the tests
 */
public final class TestSignals {

	public static final int SAMPLING_RATE = 22050;

	private TestSignals( ) {
	}

	public static float[] sine( int length, double freqHz, double amplitude ) {
		float[] s = new float[ length ];
		for ( int i = 0; i < length; i++ ) {
			s[ i ] = ( float ) ( amplitude * Math.sin( 2 * Math.PI * freqHz * i / SAMPLING_RATE ) );
		}
		return s;
	}

	public static float[] noise( int length, double amplitude, long seed ) {
		Random r = new Random( seed );
		float[] s = new float[ length ];
		for ( int i = 0; i < length; i++ ) {
			s[ i ] = ( float ) ( amplitude * r.nextGaussian( ) );
		}
		return s;
	}

	public static float[] concat( float[]... parts ) {
		int len = 0;
		for ( float[] p : parts ) {
			len += p.length;
		}
		float[] out = new float[ len ];
		int pos = 0;
		for ( float[] p : parts ) {
			System.arraycopy( p, 0, out, pos, p.length );
			pos += p.length;
		}
		return out;
	}

	/** quiet noise, then a loud tone, then quiet noise again */
	public static float[] silenceToneSilence( int silence, int tone, long seed ) {
		float[] tn = sine( tone, 440, 1000 );
		float[] bg = noise( tone, 5, seed + 2 );
		for ( int i = 0; i < tone; i++ ) {
			tn[ i ] += bg[ i ];
		}
		return concat( noise( silence, 5, seed ), tn, noise( silence, 5, seed + 1 ) );
	}
}
