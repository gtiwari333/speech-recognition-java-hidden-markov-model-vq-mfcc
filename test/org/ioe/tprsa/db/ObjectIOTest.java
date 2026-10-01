package org.ioe.tprsa.db;

import org.ioe.tprsa.classify.speech.CodeBookDictionary;
import org.ioe.tprsa.classify.speech.HMMModel;
import org.ioe.tprsa.classify.speech.vq.Centroid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ObjectIOTest {

	@Test
	void hmmModelRoundTrip( @TempDir Path dir ) throws Exception {
		HMMModel m = new HMMModel( );
		m.setPi( new double[] { 1, 0 } );
		m.setTransition( new double[][] { { 0.5, 0.5 }, { 0, 1 } } );
		m.setOutput( new double[][] { { 0.1, 0.9 }, { 0.8, 0.2 } } );

		String file = dir.resolve( "nested/dir/word.hmm" ).toString( );
		ObjectIO< HMMModel > io = new ObjectIO<>( );
		io.setModel( m );
		io.saveModel( file ); // creates missing parent directories

		HMMModel read = new ObjectIO< HMMModel >( ).readModel( file );
		assertArrayEquals( m.getPi( ), read.getPi( ) );
		assertArrayEquals( m.getTransition( ), read.getTransition( ) );
		assertArrayEquals( m.getOutput( ), read.getOutput( ) );
	}

	@Test
	void codebookRoundTrip( @TempDir Path dir ) throws Exception {
		CodeBookDictionary cbd = new CodeBookDictionary( );
		cbd.setDimension( 2 );
		cbd.setCent( new Centroid[] { new Centroid( new double[] { 1, 2 } ), new Centroid( new double[] { 3, 4 } ) } );

		String file = dir.resolve( "codebook.cbk" ).toString( );
		ObjectIO< CodeBookDictionary > io = new ObjectIO<>( );
		io.setModel( cbd );
		io.saveModel( file );

		CodeBookDictionary read = new ObjectIO< CodeBookDictionary >( ).readModel( file );
		assertEquals( 2, read.getDimension( ) );
		assertArrayEquals( new double[] { 3, 4 }, read.getCent( )[ 1 ].getAllCo( ) );
	}
}
