package Part3;

import Part3.waveaccess.WaveFileReader;
import org.quifft.QuiFFT;
import org.quifft.output.FFTFrame;
import org.quifft.output.FFTResult;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author duxiaoyuan
 */
public class Receiver {
    private final float transmitTime;
    private List<Integer> outputList = new ArrayList<>();
    private int frameNum;
    private float[] allInputs;
    private int inputIndex = 0;
    private int fileOutIndex = 0;

    public Receiver() {
        frameNum = Util.SoundUtil.inputSize * Util.SoundUtil.symbolLength / Util.SoundUtil.dataLength;
        if (((Util.SoundUtil.inputSize * Util.SoundUtil.symbolLength) % Util.SoundUtil.dataLength) != 0) {
            frameNum++;
        }
        transmitTime = (frameNum * Util.SoundUtil.symbolsPerFrame * Util.SoundUtil.symbolLength * 1000) / (Util.SoundUtil.sampleRate);
    }

    public void receive() {
        try {
            FFTResult fftResult = new QuiFFT("src/main/java/Part1/o.wav").normalized(true).fullFFT();
            FileWriter fileWriter = new FileWriter("src/main/java/Part3/waves.txt");
            double maxFrequency = -9999999;
            for (FFTFrame f : fftResult.fftFrames) {
                for (int i = 0; i < f.bins.length; i++) {
                    if (f.bins[i].frequency > maxFrequency) {
                        maxFrequency = f.bins[i].frequency;
                    }
                }
                break;
            }
            double[] fft = new double[(int) maxFrequency+1];
            for (FFTFrame f : fftResult.fftFrames) {
                for (int i = 0; i < f.bins.length; i++) {
                    fft[(int) f.bins[i].frequency] -= f.bins[i].amplitude;
                }
                break;
            }
            for (double d : fft) {
                fileWriter.write(String.valueOf(d));
                fileWriter.write(",");
            }

        } catch (IOException | UnsupportedAudioFileException e) {
            e.printStackTrace();
        }

//        int[][] data = waveFileReader.getData();
//        allInputs = new float[data[0].length];
//        for(int i = 0; i < data[0].length;i++){
//            allInputs[i] = (float) data[0][i] / 10000;
    }
//        Transmitter transmitter = new Transmitter();
//        allInputs = transmitter.getOutput();


    public void decode() {
        float max = 0;
        float power = 0;
        int count = 0;
        Queue<Float> syncFIFO = new ArrayDeque<>(Util.SoundUtil.headerLength);
        Queue<Float> frameBuffer = new ArrayDeque<>(Util.SoundUtil.frameLength);

        // initialize syncFIFO
        for (int i = 0; i < Util.SoundUtil.frameLength; i++) {
            if (i < Util.SoundUtil.headerLength) {
                syncFIFO.add(allInputs[i]);
            }
            frameBuffer.add(allInputs[i]);
        }
        max = Util.MathUtil.correlation(syncFIFO, Util.SoundUtil.getHeader());
//        if (Util.MathUtil.correlation(syncFIFO, Util.SoundUtil.getHeader()) >= 1.726) {
//            // detect a frame
//            count++;
//            decodeFrame(frameBuffer);
//        }

        float currentSample;
        for (int i = Util.SoundUtil.frameLength - 1; i < allInputs.length; ) {
            if (Util.MathUtil.correlation(syncFIFO, Util.SoundUtil.getHeader()) >= 0.5) {
                // detect a frame
                count++;
                decodeFrame(frameBuffer);
                fillInNewBuffer(frameBuffer, syncFIFO, i + 1);
                i += Util.SoundUtil.frameLength;
                continue;
            }
            if (i + 1 >= allInputs.length) {
                break;
            }
            currentSample = allInputs[++i];
            int headerIndex = i - (Util.SoundUtil.frameLength - Util.SoundUtil.headerLength);
            syncFIFO.remove();
            syncFIFO.add(allInputs[headerIndex]);

            frameBuffer.remove();
            frameBuffer.add(currentSample);
        }
        System.out.println(max);
        System.out.println(count);
        Util.FileUtil.writeOutput("OUTPUT.txt", outputList);
    }

    private void decodeFrame(Queue<Float> frame) {
        //decode 100 bits of a frame
        for (int i = 0; i < Util.SoundUtil.headerLength; i++) {
            frame.remove();
        }
        float[] bitBuffer = new float[Util.SoundUtil.symbolLength];
        for (int i = 0; i < Util.SoundUtil.symbolsPerFrame; i++, fileOutIndex++) {
            if (fileOutIndex >= Util.SoundUtil.inputSize) {
                break;
            }
            for (int j = 0; j < Util.SoundUtil.symbolLength; j++) {
                //fill in bit buffer
                bitBuffer[j] = frame.remove();
            }
            outputList.add(decodeOneSymbol(bitBuffer));
        }
    }

    private void fillInNewBuffer(Queue<Float> frame, Queue<Float> syncFIFO, int index) {
        syncFIFO.clear();
        for (int i = index; i < index + Util.SoundUtil.frameLength; i++) {
            if (i < allInputs.length) {
                if (i < index + Util.SoundUtil.headerLength) {
                    syncFIFO.add(allInputs[i]);
                }
                frame.add(allInputs[i]);
            } else {
                break;
            }
        }
    }

    private int decodeOneSymbol(float[] symbol) {
        return Util.MathUtil.correlation(symbol, Util.SoundUtil.one) > 0 ? 1 : 0;
    }

    public static void main(String[] args) {
        Receiver receiver = new Receiver();
        receiver.receive();
    }
}