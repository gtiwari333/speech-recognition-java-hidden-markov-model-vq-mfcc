package org.ioe.tprsa.audio.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FastFourierTransformTest {

	@ParameterizedTest
	@ValueSource( ints = { 2, 4, 8, 64, 512 } )
	void matchesNaiveDft( int n ) {
		Random r = new Random( n );
		float[] signal = new float[ n ];
		for ( int i = 0; i < n; i++ ) {
			signal[ i ] = ( float ) r.nextGaussian( );
		}
		float[] copy = signal.clone( );

		FastFourierTransform fft = new FastFourierTransform( );
		fft.computeFFT( signal );

		for ( int k = 0; k < n; k++ ) {
			double re = 0, im = 0;
			for ( int t = 0; t < n; t++ ) {
				double angle = -2 * Math.PI * k * t / n;
				re += copy[ t ] * Math.cos( angle );
				im += copy[ t ] * Math.sin( angle );
			}
			assertEquals( re, fft.real[ k ], 1e-3 * n, "real[" + k + "]" );
			assertEquals( im, fft.imag[ k ], 1e-3 * n, "imag[" + k + "]" );
		}
	}

	@Test
	void impulseHasFlatSpectrum( ) {
		float[] impulse = new float[ 16 ];
		impulse[ 0 ] = 1;
		FastFourierTransform fft = new FastFourierTransform( );
		fft.computeFFT( impulse );
		for ( int k = 0; k < 16; k++ ) {
			assertEquals( 1.0, fft.real[ k ], 1e-6 );
			assertEquals( 0.0, fft.imag[ k ], 1e-6 );
		}
	}

	@Test
	void doesNotModifyInput( ) {
		float[] signal = { 1, 2, 3, 4, 5, 6, 7, 8 };
		float[] copy = signal.clone( );
		new FastFourierTransform( ).computeFFT( signal );
		assertArrayEquals( copy, signal );
	}
}
