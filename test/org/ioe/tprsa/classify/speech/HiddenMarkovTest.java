package org.ioe.tprsa.classify.speech;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HiddenMarkovTest {

	private static final double[][] A = { { 0.6, 0.3, 0.1 }, { 0.0, 0.7, 0.3 }, { 0.0, 0.0, 1.0 } };
	private static final double[][] B = { { 0.7, 0.2, 0.1 }, { 0.1, 0.8, 0.1 }, { 0.2, 0.2, 0.6 } };

	private static HiddenMarkov model( double[] pi ) {
		HiddenMarkov hmm = new HiddenMarkov( 3, 3 );
		hmm.transition = A;
		hmm.output = B;
		hmm.pi = pi;
		return hmm;
	}

	/** enumerate every state path: returns {sum over paths, max over paths} */
	private static double[] bruteForce( double[] pi, int[] obs ) {
		int n = pi.length, len = obs.length;
		double sum = 0, max = 0;
		int[] path = new int[ len ];
		int total = ( int ) Math.pow( n, len );
		for ( int code = 0; code < total; code++ ) {
			for ( int t = 0, c = code; t < len; t++, c /= n ) {
				path[ t ] = c % n;
			}
			double p = pi[ path[ 0 ] ] * B[ path[ 0 ] ][ obs[ 0 ] ];
			for ( int t = 1; t < len; t++ ) {
				p *= A[ path[ t - 1 ] ][ path[ t ] ] * B[ path[ t ] ][ obs[ t ] ];
			}
			sum += p;
			max = Math.max( max, p );
		}
		return new double[] { sum, max };
	}

	@Test
	void forwardAlgorithmMatchesBruteForce( ) {
		double[] pi = { 1, 0, 0 };
		int[] obs = { 0, 0, 1, 1, 2, 2, 1 };
		double expected = Math.log( bruteForce( pi, obs )[ 0 ] );
		assertEquals( expected, model( pi ).getProbability( obs ), 1e-9 );
	}

	@Test
	void viterbiMatchesBruteForce( ) {
		double[] pi = { 0.5, 0.3, 0.2 }; // non-zero so the MIN_PROBABILITY substitution does not kick in
		int[] obs = { 0, 1, 1, 2, 0, 2 };
		double expected = Math.log( bruteForce( pi, obs )[ 1 ] );
		assertEquals( expected, model( pi ).viterbi( obs ), 1e-9 );
	}

	@Test
	void viterbiStateSequenceIsLeftToRight( ) {
		HiddenMarkov hmm = model( new double[] { 1, 0, 0 } );
		hmm.viterbi( new int[] { 0, 0, 1, 1, 1, 2, 2 } );
		assertArrayEquals( new int[] { 0, 0, 1, 1, 1, 2, 2 }, hmm.q );
	}

	@Test
	void randomInitIsAValidModel( ) {
		HiddenMarkov hmm = new HiddenMarkov( 6, 10, new Random( 3 ) );
		for ( double[][] m : new double[][][] { hmm.transition, hmm.output } ) {
			for ( double[] row : m ) {
				assertEquals( 1.0, java.util.Arrays.stream( row ).sum( ), 1e-9 );
			}
		}
	}

	@Test
	void trainingIsReproducibleWithTheSameSeed( ) {
		int[][] seqs = utterances( new int[] { 0, 1, 2, 3 }, 6, 8, 21 );
		HiddenMarkov a = new HiddenMarkov( 4, 8, new Random( 5 ) ), b = new HiddenMarkov( 4, 8, new Random( 5 ) );
		a.setTrainSeq( seqs );
		b.setTrainSeq( seqs );
		a.train( );
		b.train( );
		assertArrayEquals( a.transition, b.transition );
		assertArrayEquals( a.output, b.output );
	}

	@Test
	void forbiddenTransitionsStayZeroAfterTraining( ) {
		HiddenMarkov hmm = trained( utterances( new int[] { 0, 1, 2, 3 }, 6, 8, 8 ), 8 );
		for ( int i = 0; i < 4; i++ ) {
			for ( int j = 0; j < 4; j++ ) {
				if ( j < i || j > i + hmm.delta ) {
					assertEquals( 0.0, hmm.transition[ i ][ j ] );
				}
			}
		}
		assertEquals( 1.0, hmm.transition[ 3 ][ 3 ], 1e-12, "last state can only loop" );
	}

	@Test
	void randomInitIsLeftToRightWithJumpLimit( ) {
		HiddenMarkov hmm = new HiddenMarkov( 6, 10 );
		assertEquals( 1.0, hmm.pi[ 0 ] );
		for ( int i = 0; i < 6; i++ ) {
			for ( int j = 0; j < 6; j++ ) {
				if ( j < i || j > i + hmm.delta ) {
					assertEquals( 0.0, hmm.transition[ i ][ j ], "a[" + i + "][" + j + "]" );
				}
			}
		}
	}

	// ---- training ------------------------------------------------------------------------

	/** left-to-right "word": each symbol of the pattern repeated a random number of times, with some noise */
	private static int[] utterance( int[] pattern, int numSymbols, Random r ) {
		java.util.List< Integer > seq = new java.util.ArrayList<>( );
		for ( int s : pattern ) {
			int reps = 3 + r.nextInt( 4 );
			for ( int k = 0; k < reps; k++ ) {
				seq.add( r.nextDouble( ) < 0.1 ? r.nextInt( numSymbols ) : s );
			}
		}
		return seq.stream( ).mapToInt( Integer::intValue ).toArray( );
	}

	private static int[][] utterances( int[] pattern, int count, int numSymbols, long seed ) {
		Random r = new Random( seed );
		int[][] seqs = new int[ count ][];
		for ( int i = 0; i < count; i++ ) {
			seqs[ i ] = utterance( pattern, numSymbols, r );
		}
		return seqs;
	}

	private static HiddenMarkov trained( int[][] seqs, int numSymbols ) {
		HiddenMarkov hmm = new HiddenMarkov( 4, numSymbols, new Random( 99 ) );
		hmm.setTrainSeq( seqs );
		hmm.train( );
		return hmm;
	}

	private static double totalLogLikelihood( HiddenMarkov hmm, int[][] seqs ) {
		double sum = 0;
		for ( int[] s : seqs ) {
			sum += hmm.getProbability( s );
		}
		return sum;
	}

	@Test
	void trainingIncreasesLikelihoodOfTrainingData( ) {
		int[][] seqs = utterances( new int[] { 0, 1, 2, 3 }, 8, 8, 11 );
		HiddenMarkov hmm = new HiddenMarkov( 4, 8, new Random( 1 ) );
		hmm.setTrainSeq( seqs );
		double before = totalLogLikelihood( hmm, seqs );
		hmm.train( );
		double after = totalLogLikelihood( hmm, seqs );
		assertTrue( after > before, "before " + before + " after " + after );
	}

	@Test
	void trainedModelsDiscriminateBetweenWords( ) {
		int symbols = 8;
		int[] wordA = { 0, 1, 2, 3 }, wordB = { 4, 5, 6, 7 };
		HiddenMarkov hmmA = trained( utterances( wordA, 8, symbols, 1 ), symbols );
		HiddenMarkov hmmB = trained( utterances( wordB, 8, symbols, 2 ), symbols );

		for ( int[] test : utterances( wordA, 5, symbols, 100 ) ) {
			assertTrue( hmmA.viterbi( test ) > hmmB.viterbi( test ) );
		}
		for ( int[] test : utterances( wordB, 5, symbols, 200 ) ) {
			assertTrue( hmmB.viterbi( test ) > hmmA.viterbi( test ) );
		}
	}

	@Test
	void trainedModelHasNoNaNs( ) {
		HiddenMarkov hmm = trained( utterances( new int[] { 0, 1, 2, 3 }, 6, 8, 5 ), 8 );
		for ( double[] row : hmm.transition ) {
			for ( double v : row ) {
				assertFalse( Double.isNaN( v ) );
			}
		}
		for ( double[] row : hmm.output ) {
			for ( double v : row ) {
				assertFalse( Double.isNaN( v ) );
			}
		}
	}

	@Test
	void trainedRowsAreProbabilityDistributions( ) {
		HiddenMarkov hmm = trained( utterances( new int[] { 0, 1, 2, 3 }, 6, 8, 5 ), 8 );
		for ( double[][] m : new double[][][] { hmm.transition, hmm.output } ) {
			for ( double[] row : m ) {
				assertEquals( 1.0, java.util.Arrays.stream( row ).sum( ), 1e-9 );
			}
		}
	}
}
