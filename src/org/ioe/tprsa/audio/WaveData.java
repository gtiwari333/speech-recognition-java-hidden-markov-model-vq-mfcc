/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.audio;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * saving and extracting PCM data from wavefile byteArray
 * 
 * @author Ganesh Tiwari
 */
public class WaveData {

	private byte[]					audioBytes;
	private float[]					audioData;
	private AudioFormat				format;
	private double					durationSec;

	public WaveData( ) {
	}

	public byte[] getAudioBytes( ) {
		return audioBytes;
	}

	public double getDurationSec( ) {
		return durationSec;
	}

	public float[] getAudioData( ) {
		return audioData;
	}

	public AudioFormat getFormat( ) {
		return format;
	}

	public float[] extractAmplitudeFromFile( File wavFile ) throws Exception {
		// create file input stream
		FileInputStream fis = new FileInputStream( wavFile );
		// create bytearray from file
		byte[] arrFile = new byte[(int) wavFile.length()];
		fis.read(arrFile);
		return extractAmplitudeFromFileByteArray(arrFile);
	}

	public float[] extractAmplitudeFromFileByteArray( byte[] arrFile ) throws Exception {
		// System.out.println("File :  "+wavFile+""+arrFile.length);
		ByteArrayInputStream bis = new ByteArrayInputStream(arrFile);
		return extractAmplitudeFromFileByteArrayInputStream(bis);
	}

	/**
	 * for extracting amplitude array the format we are using :16bit, 22khz, 1 channel, littleEndian,
	 * 
	 * @return PCM audioData
	 * @throws Exception
	 */
	public float[] extractAmplitudeFromFileByteArrayInputStream( ByteArrayInputStream bis ) throws Exception {
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bis);
		float milliseconds = ( long ) ( ( audioInputStream.getFrameLength( ) * 1000 ) / audioInputStream.getFormat( ).getFrameRate( ) );
		durationSec = milliseconds / 1000.0;
		return extractFloatDataFromAudioInputStream(audioInputStream);
	}

	public float[] extractFloatDataFromAudioInputStream( AudioInputStream audioInputStream ) throws Exception {
		format = audioInputStream.getFormat( );
		audioBytes = new byte[ ( int ) ( audioInputStream.getFrameLength( ) * format.getFrameSize( ) ) ];
		// calculate durationSec
		float milliseconds = ( long ) ( ( audioInputStream.getFrameLength( ) * 1000 ) / audioInputStream.getFormat( ).getFrameRate( ) );
		durationSec = milliseconds / 1000.0;
		// System.out.println("The current signal has duration "+durationSec+" Sec");
		audioInputStream.read( audioBytes );
		return extractFloatDataFromAmplitudeByteArray( format, audioBytes );
	}

	public float[] extractFloatDataFromAmplitudeByteArray( AudioFormat format, byte[] audioBytes ) {
		// convert
		audioData = null;
		if ( format.getSampleSizeInBits( ) == 16 ) {
			int nlengthInSamples = audioBytes.length / 2;
			audioData = new float[ nlengthInSamples ];
			if ( format.isBigEndian( ) ) {
				for ( int i = 0; i < nlengthInSamples; i++ ) {
					/* First byte is MSB (high order) */
					int MSB = audioBytes[ 2 * i ];
					/* Second byte is LSB (low order) */
					int LSB = audioBytes[ 2 * i + 1 ];
					audioData[ i ] = MSB << 8 | ( 255 & LSB );
				}
			} else {
				for ( int i = 0; i < nlengthInSamples; i++ ) {
					/* First byte is LSB (low order) */
					int LSB = audioBytes[ 2 * i ];
					/* Second byte is MSB (high order) */
					int MSB = audioBytes[ 2 * i + 1 ];
					audioData[ i ] = MSB << 8 | ( 255 & LSB );
				}
			}
		} else if ( format.getSampleSizeInBits( ) == 8 ) {
			int nlengthInSamples = audioBytes.length;
			audioData = new float[ nlengthInSamples ];
			if ( format.getEncoding( ).toString( ).startsWith( "PCM_SIGN" ) ) {
				for ( int i = 0; i < audioBytes.length; i++ ) {
					audioData[ i ] = audioBytes[ i ];
				}
			} else {
				for ( int i = 0; i < audioBytes.length; i++ ) {
					audioData[ i ] = audioBytes[ i ] - 128;
				}
			}
		} // end of if..else
			// System.out.println("PCM Returned===============" +
			// audioData.length);
		return audioData;
	}

	/**
	 * Save to file.
	 * 
	 * @param name
	 *            the name
	 * @param fileType
	 *            the file type
	 */
	public void saveToFile( String name, AudioFileFormat.Type fileType, AudioInputStream audioInputStream ) throws Exception {

		System.out.println( "WaveData.saveToFile() " + name );

		File myFile = new File( name );
		if ( !myFile.exists( ) )
			myFile.mkdirs( );

		if ( audioInputStream == null ) {
			return;
		}
		// reset to the beginnning of the captured data
		audioInputStream.reset( );
		myFile = new File( name + ".wav" );
		int i = 0;
		while ( myFile.exists( ) ) {
			String temp = String.format( name + "%d", i++ );
			myFile = new File( temp + ".wav" );
		}
		AudioSystem.write(audioInputStream, fileType, myFile);
		System.out.println( myFile.getAbsolutePath( ) );
	}

	/**
	 * saving the file's bytearray
	 * 
	 * @param fileName
	 *            the name of file to save the received byteArray of File
	 */
	public void saveFileByteArray( String fileName, byte[] arrFile ) throws Exception {
		FileOutputStream fos = new FileOutputStream(fileName);
		fos.write( arrFile );
		fos.close( );
		System.out.println( "WAV Audio data saved to " + fileName );
	}
}
