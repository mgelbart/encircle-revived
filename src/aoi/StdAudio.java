package aoi;


/*************************************************************************
    adapted from PRINCETON COS 126, 2006
 *  
 *  Simple library for reading, writing, and manipulationg .wav files.

 *
 *  Limitations
 *  -----------
 *    - Does not seem to work properly when reading .wav files from a .jar file.
 *    - Assumes the audio is monaural, and 44,100 samples per second
 *
 *************************************************************************/

import java.io.*;
import javax.sound.sampled.*;
import java.net.*;
import java.applet.*;

public abstract class StdAudio {
    private final static int FPS = 44100;   // samples per second

    public static String AUDIO_DIRECTORY = File.separator + "sound" + File.separator;
    
    public static AudioClip takeShot  = getClip(AUDIO_DIRECTORY + "Bow_Fire_Arrow-Stephan_Schutze-2133929391.wav");
    public static AudioClip score     = getClip(AUDIO_DIRECTORY + "Metroid_Door-Brandino480-995195341.wav");
    public static AudioClip winGame   = getClip(AUDIO_DIRECTORY + "Electrical_Sweep-Sweeper-1760111493.wav");
    public static AudioClip crossPath = null;
    public static AudioClip getShot   = null;
    public static AudioClip crash     = null;
    
    // return data as a double array, with values scaled to be between -1 and 1
    public static double[] read(String filename) {
        byte[] data = readByte(filename);
        int N = data.length;
        double[] d = new double[N/2];
        for (int i = 0; i < N/2; i++) {
            d[i] = ((short) (((data[2*i+1] & 0xFF) << 8) + (data[2*i] & 0xFF))) / 32768.0;
        }
        return d;
    }



    // play the input array
    // 44,100 samples per second, 16-bit audio, mono, signed PCM, little Endian
    // the array values are assumed to be between -1 and 1
    public static void play(double[] input) {
        AudioFormat format = new AudioFormat(FPS, 16, 1, true, false);
        byte[] data = new byte[2 * input.length];
        for (int i = 0; i < input.length; i++) {
            int temp = (int) (input[i] * 32768.0);
            data[2*i + 0] = (byte) temp;
            data[2*i + 1] = (byte) (temp >> 8);
        }
        play(format, data);
    }

    // play the sound represented by filename
   /* public static void play(String filename) {
        play(format(filename), readByte(filename));
    }*/

//    // play a wav or midi sound in the background
//    public static void play(String filename) {
//        URL url = null;
//        try {
//            File fil = new File(filename);
//            if(fil.canRead()) url = fil.toURL();
//        }
//        catch (MalformedURLException e) { e.printStackTrace(); }
//        // URL url = StdAudio.class.getResource(filename);
//        if (url == null) throw new RuntimeException("audio " + filename + " not found");
//        AudioClip clip = Applet.newAudioClip(url);
//        clip.play();
//    }

    ///MINE!!
    public static void stop(AudioClip clip) {
    	clip.stop();
    }
    
    
    public static AudioClip getClip(String filename) {
   /*     URL url = null;
        try {
            File fil = new File(filename);
            if(fil.canRead()) url = fil.toURL();
        }
        catch (MalformedURLException e) { e.printStackTrace(); }
    */     URL url = StdAudio.class.getResource(filename);  //this version is used when making JAR files
        if (url == null) throw new RuntimeException("audio " + filename + " not found");
        AudioClip clip = Applet.newAudioClip(url);
        return clip;
    }
    
    public static void playClip(AudioClip c) {
    	c.play();
    }
    //////////// end mine


    // save double array as .wav or .au file
    public static void save(double[] input, String filename) {

        // assumes 44,100 samples per second
        // use 16-bit audio, mono, signed PCM, little Endian
        AudioFormat format = new AudioFormat(FPS, 16, 1, true, false);
        byte[] data = new byte[2 * input.length];
        for (int i = 0; i < input.length; i++) {
            int temp = (int) (input[i] * 32768.0);
            data[2*i + 0] = (byte) temp;
            data[2*i + 1] = (byte) (temp >> 8);
        }

        // now save the file
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            AudioInputStream ais = new AudioInputStream(bais, format, input.length);
            if (filename.endsWith(".wav") || filename.endsWith(".WAV")) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(filename));
            }
            else if (filename.endsWith(".au") || filename.endsWith(".AU")) {
                AudioSystem.write(ais, AudioFileFormat.Type.AU, new File(filename));
            }
            else {
                throw new RuntimeException("File format not supported: " + filename);
            }
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    // play the sound represented by filename
    private static void play(AudioFormat format, byte[] data) {
        
    	SourceDataLine line = null;
        DataLine.Info info  = new DataLine.Info(SourceDataLine.class, format);
        try {
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }

        line.start();
        line.write(data, 0, data.length);
        line.drain();
        line.close();
    }

/*    // return audio format
	private static AudioFormat format(String filename) {
    	AudioInputStream ais = ais(filename);
        AudioFormat format = ais.getFormat();
        return format;
    }
*/   //COMMENTED OUT BY ME BECAUSE NOT USED!!!
    
    // return audio input stream
    private static AudioInputStream ais(String filename) {
        AudioInputStream ais = null;
        try {
            URL url = StdAudio.class.getResource(filename);
            ais = AudioSystem.getAudioInputStream(url);
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(0);
        }
        return ais;
    }


    // return data as a byte array
    private static byte[] readByte(String filename) {
        AudioInputStream ais = ais(filename);
        byte[] data = null;
        try {
            data = new byte[ais.available()];
            ais.read(data);
        }
        catch (IOException e) { throw new RuntimeException("Could not read " + filename); }

        return data;
    }





    // create a note (sine wave) of the given frequency (Hz), for the given
    // duration (seconds) scaled to the given volume (amplitude)
    public static double[] note(double hz, double duration, double amplitude) {
        int N = (int) (FPS * duration);
        double[] tone = new double[N];
        for (int i = 0; i < N; i++)
            tone[i] = amplitude * Math.sin(2 * Math.PI * i * hz / FPS);
        return tone;
    }


    // test client - A major scale
    public static void main(String[] args) {

        // scale increments
        int[] steps = { 0, 2, 4, 5, 7, 9, 11, 12 };
        for (int i = 0; i < steps.length; i++) {
            double hz = 440.0 * Math.pow(2, steps[i] / 12.0);
            StdAudio.play(note(hz, 1.0, 0.5));
        }

        // needed because of the way Java handles audio
        System.exit(0);
    }
}
