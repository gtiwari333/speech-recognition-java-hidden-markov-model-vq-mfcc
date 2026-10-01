package org.ioe.tprsa.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WaveDataTest {

	private static final short[] SAMPLES = { 0, 1, -1, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE };

	private static byte[] pcm16( boolean bigEndian ) {
		byte[] b = new byte[ SAMPLES.length * 2 ];
		for ( int i = 0; i < SAMPLES.length; i++ ) {
			byte lo = ( byte ) SAMPLES[ i ], hi = ( byte ) ( SAMPLES[ i ] >> 8 );
			b[ 2 * i ] = bigEndian ? hi : lo;
			b[ 2 * i + 1 ] = bigEndian ? lo : hi;
		}
		return b;
	}

	private static AudioFormat format16( boolean bigEndian ) {
		return new AudioFormat( 22050f, 16, 1, true, bigEndian );
	}

	private static void assertSamples( float[] actual ) {
		assertEquals( SAMPLES.length, actual.length );
		for ( int i = 0; i < SAMPLES.length; i++ ) {
			assertEquals( SAMPLES[ i ], actual[ i ], 0.0, "sample " + i );
		}
	}

	@Test
	void decodes16BitLittleEndian( ) {
		assertSamples( new WaveData( ).extractFloatDataFromAmplitudeByteArray( format16( false ), pcm16( false ) ) );
	}

	@Test
	void decodes16BitBigEndian( ) {
		assertSamples( new WaveData( ).extractFloatDataFromAmplitudeByteArray( format16( true ), pcm16( true ) ) );
	}

	@Test
	void decodes8BitSigned( ) {
		byte[] bytes = { 0, 127, ( byte ) 128, ( byte ) 255 };
		float[] signed = new WaveData( ).extractFloatDataFromAmplitudeByteArray( new AudioFormat( 8000f, 8, 1, true, false ), bytes );
		assertArrayEquals( new float[] { 0, 127, -128, -1 }, signed );
	}

	@Test
	void decodes8BitUnsigned( ) {
		byte[] bytes = { 0, 127, ( byte ) 128, ( byte ) 255 };
		float[] unsigned = new WaveData( ).extractFloatDataFromAmplitudeByteArray( new AudioFormat( 8000f, 8, 1, false, false ), bytes );
		assertArrayEquals( new float[] { -128, -1, 0, 127 }, unsigned );
	}

	@Test
	void readsWavFileRoundTrip( @TempDir Path dir ) throws Exception {
		File wav = dir.resolve( "t.wav" ).toFile( );
		byte[] data = pcm16( false );
		AudioFormat fmt = format16( false );
		AudioSystem.write( new AudioInputStream( new ByteArrayInputStream( data ), fmt, SAMPLES.length ), AudioFileFormat.Type.WAVE, wav );

		WaveData wd = new WaveData( );
		assertSamples( wd.extractAmplitudeFromFile( wav ) );
		assertSamples( wd.extractAmplitudeFromFileByteArray( Files.readAllBytes( wav.toPath( ) ) ) );
		assertEquals( 22050f, wd.getFormat( ).getSampleRate( ) );
	}
}
