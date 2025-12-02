import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.text.Text;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.opencv.videoio.Videoio;

/**
 * The controller for our application, where the application logic is
 * implemented. It handles the button for starting/stopping the camera and the
 * acquired video stream.
 *
 * @author <a href="mailto:luigi.derussis@polito.it">Luigi De Russis</a>
 * @author <a href="http://max-z.de">Maximilian Zuleger</a> (minor fixes)
 * @version 2.0 (2016-09-17)
 * @since 1.0 (2013-10-20)
 *
 */
public class VideoScrambleController
{
    // the FXML button
    @FXML
    private Button button;
    // the FXML image view
    @FXML
    private ImageView originalFrame;
    @FXML
    private ImageView scrambledFrame;
    @FXML
    private ImageView unscrambledFrame;

    @FXML private Slider sliderR;
    @FXML private Slider sliderS;
    @FXML
    private byte r;
    @FXML
    private byte s;

    @FXML private Text rValue;
    @FXML private Text sValue;

    @FXML
    private CheckBox randomKey;

    private ScheduledExecutorService timer;
    private VideoCapture capture = new VideoCapture();
    private boolean cameraActive = false;
    private static int cameraId = 0;

    @FXML
    public void initialize() {
        sliderR.setValue(r & 0xFF);
        sliderS.setValue(s);

        randomKey.setSelected(false);

        sliderR.disableProperty().bind(randomKey.selectedProperty());
        sliderS.disableProperty().bind(randomKey.selectedProperty());


        sliderR.valueProperty().addListener((observable, oldValue, newValue) -> {
            r = (byte) newValue.intValue();
        });
        sliderS.valueProperty().addListener((observable, oldValue, newValue) -> {
            s = (byte) newValue.intValue();
        });
    }

    /**
     * The action triggered by pushing the button on the GUI
     *
     * @param event
     *            the push button event
     */
    @FXML
    protected void startCamera(ActionEvent event)
    {

        if (!this.cameraActive)
        {
            // start the video capture
            this.capture.open(cameraId, Videoio.CAP_V4L2);
            // is the video stream available?
            if (this.capture.isOpened())
            {
                this.cameraActive = true;

                // grab a frame every 33 ms (30 frames/sec)
                Runnable frameGrabber = new Runnable() {
                    @Override
                    public void run()
                    {
                        try {
                            Mat frame = grabFrame();
                            if (frame.empty()) {
                                return;
                            }
                            // Original
                            Image imageToShow = mat2Image(frame.clone());

                            if(randomKey.isSelected()){
                                r = (byte)Math.abs(new Random().nextInt(4, 255) & 0xFF);
                                s = (byte)Math.abs(new Random().nextInt(4, 128) & 0xFF);
                            }

                            rValue.setText(String.format("r: %d", (int) r));
                            sValue.setText(String.format("s: %d", (int) s));

                            // Scrambling
                            Mat scrambled = Scrambler.scramble(frame.clone(), r, s);
                            Image scrambledImageToShow = mat2Image(scrambled);

                            // Unscrambling
                            Mat unscrambled = Unscrambler.unscramble(scrambled.clone());
                            Image unscrambledImageToShow = mat2Image(unscrambled);

                            updateImageView(originalFrame, imageToShow);
                            updateImageView(scrambledFrame, scrambledImageToShow);
                            updateImageView(unscrambledFrame, unscrambledImageToShow);

                        } catch (Exception e) {
                            System.err.println("Erreur dans la boucle vidéo : " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

                // update the button content
                this.button.setText("Stop Camera");
            }
            else
            {
                // log the error
                System.err.println("Impossible to open the camera connection...");
            }
        }
        else
        {
            // the camera is not active at this point
            this.cameraActive = false;
            // update again the button content
            this.button.setText("Start Camera");

            // stop the timer
            this.stopAcquisition();
        }
    }

    /**
     * Get a frame from the opened video stream (if any)
     *
     * @return the {@link Mat} to show
     */
    private Mat grabFrame()
    {
        // init everything
        Mat frame = new Mat();

        // check if the capture is open
        if (this.capture.isOpened())
        {
            try
            {
                // read the current frame
                this.capture.read(frame);

                // if the frame is not empty, process it
                if (!frame.empty())
                {
                    // basic single frame processing can be performed here
                }

            }
            catch (Exception e)
            {
                // log the error
                System.err.println("Exception during the image elaboration: " + e);
            }
        }

        return frame;
    }

    /**
     * Stop the acquisition from the camera and release all the resources
     */
    private void stopAcquisition()
    {
        if (this.timer!=null && !this.timer.isShutdown())
        {
            try
            {
                // stop the timer
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                // log any exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }
        }

        if (this.capture.isOpened())
        {
            // release the camera
            this.capture.release();
        }
    }

    /**
     * Update the {@link ImageView} in the JavaFX main thread
     *
     * @param view
     *            the {@link ImageView} to update
     * @param image
     *            the {@link Image} to show
     */
    private void updateImageView(ImageView view, Image image)
    {
        onFXThread(view.imageProperty(), image);
    }

    /**
     * On application close, stop the acquisition from the camera
     */
    protected void setClosed()
    {
        this.stopAcquisition();
    }

    private Image matToJavaFXImage(Mat mat) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buffer);
        return new Image(new java.io.ByteArrayInputStream(buffer.toArray()));
    }


    /**
     * Convert a Mat object (OpenCV) in the corresponding Image for JavaFX
     *
     * @param frame
     *            the {@link Mat} representing the current frame
     * @return the {@link Image} to show
     */
    public static Image mat2Image(Mat frame)
    {
        try
        {
            return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
        }
        catch (Exception e)
        {
            System.err.println("Cannot convert the Mat obejct: " + e);
            return null;
        }
    }

    /**
     * Support for the {@link mat2image()} method
     *
     * @param original
     *            the {@link Mat} object in BGR or grayscale
     * @return the corresponding {@link BufferedImage}
     */
    private static BufferedImage matToBufferedImage(Mat original)
    {
        // init
        BufferedImage image = null;
        int width = original.width(), height = original.height(), channels = original.channels();
        byte[] sourcePixels = new byte[width * height * channels];
        original.get(0, 0, sourcePixels);

        if (original.channels() > 1)
        {
            image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        }
        else
        {
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        }
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);

        return image;
    }

    /**
     * Generic method for putting element running on a non-JavaFX thread on the
     * JavaFX thread, to properly update the UI
     *
     * @param property
     *            a {@link ObjectProperty}
     * @param value
     *            the value to set for the given {@link ObjectProperty}
     */
    public static <T> void onFXThread(final ObjectProperty<T> property, final T value)
    {
        Platform.runLater(() -> {
            property.set(value);
        });
    }

}