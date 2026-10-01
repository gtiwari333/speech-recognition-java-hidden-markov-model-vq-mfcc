package org.ioe.tprsa.db;

import org.ioe.tprsa.classify.speech.HMMModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectIODataBaseTest {

	private static ObjectIODataBase hmmDbIn( Path dir ) {
		ObjectIODataBase db = new ObjectIODataBase( );
		db.setType( "hmm" );
		db.CURRENTFOLDER = dir.toString( ); // folder is otherwise hard-coded relative to the working directory
		return db;
	}

	@Test
	void savesReadsAndListsModels( @TempDir Path dir ) throws Exception {
		ObjectIODataBase db = hmmDbIn( dir );
		HMMModel m = new HMMModel( );
		m.setPi( new double[] { 1 } );
		db.saveModel( m, "apple" );
		db.saveModel( m, "zebra" );

		assertTrue( Files.exists( dir.resolve( "apple.hmm" ) ) );
		assertArrayEquals( new double[] { 1 }, ( ( HMMModel ) db.readModel( "apple" ) ).getPi( ) );
		assertEquals( List.of( "apple", "zebra" ), db.readRegistered( ).stream( ).sorted( ).toList( ) );
	}

	@Test
	void readRegisteredIgnoresUnrelatedFiles( @TempDir Path dir ) throws Exception {
		ObjectIODataBase db = hmmDbIn( dir );
		db.saveModel( new HMMModel( ), "apple" );
		Files.createFile( dir.resolve( ".DS_Store" ) );
		Files.createFile( dir.resolve( "README" ) );
		assertEquals( List.of( "apple" ), db.readRegistered( ) );
	}
}
