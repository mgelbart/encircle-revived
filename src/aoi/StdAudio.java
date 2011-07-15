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
  public static String AUDIO_DIRECTORY = File.separator + "sound" + File.separator;

  private static final boolean DISABLE = false;
  
  public static AudioClip takeShot  = (DISABLE)? null:getClip(AUDIO_DIRECTORY + "Bow_Fire_Arrow-Stephan_Schutze-2133929391.wav");
  public static AudioClip score     = (DISABLE)? null:getClip(AUDIO_DIRECTORY + "Metroid_Door-Brandino480-995195341.wav");
  public static AudioClip winGame   = (DISABLE)? null:getClip(AUDIO_DIRECTORY + "Electrical_Sweep-Sweeper-1760111493.wav");
  public static AudioClip crossPath = (DISABLE)? null:null;
  public static AudioClip getShot   = (DISABLE)? null:null;
  public static AudioClip crash     = (DISABLE)? null:getClip(AUDIO_DIRECTORY + "36847__EcoDTR__LaserRocket2.wav");
  
  // play the input array
  // 44,100 samples per second, 16-bit audio, mono, signed PCM, little Endian
  // the array values are assumed to be between -1 and 1
  public static void play(double[] input) {
    final int FPS = 44100;   // samples per second

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
     */     


    URL url = StdAudio.class.getResource(filename);  //this version is used when making JAR files
    if (url == null) throw new RuntimeException("audio " + filename + " not found");
    AudioClip clip = Applet.newAudioClip(url);
    return clip;

//    try
//    {
//      Clip clickClip = AudioSystem.getClip();
//      URL clipURL = new URL(filename);
//      AudioInputStream ais = AudioSystem.getAudioInputStream(clipURL);
//      clickClip.open(ais);
//      clickClip.start();
//    } catch(Exception e) { e.printStackTrace(); }

  }

  public static void playClip(AudioClip c) {
    c.play();
  }
  //////////// end mine

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

  // return audio format
  private static AudioFormat format(String filename) {
    AudioInputStream ais = ais(filename);
    AudioFormat format = ais.getFormat();
    return format;
  }

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

}
