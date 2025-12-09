import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;

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
    @FXML private Slider sliderP;
    private byte r = (byte)177;
    private byte s = 67;
    private int p = 1;

    @FXML private Text rValue;
    @FXML private Text sValue;

    @FXML
    private CheckBox randomKey;

    @FXML private Label lblFPS;
    @FXML private Label lblScrambleSource;
    @FXML private Label lblCurrentR;
    @FXML private Label lblCurrentS;
    @FXML private Button btnStartScramble;
    @FXML private ProgressBar progressScramble;
    @FXML private Label lblStatusScramble;
    @FXML private CheckBox scrambleVideoWithRandomKey;

    @FXML private Label lblUnscrambleSource;
    @FXML private Button btnStartUnscramble;
    @FXML private ProgressBar progressUnscramble;
    @FXML private Label lblStatusUnscramble;

    // Fichiers sélectionnés
    private File fileToScramble;
    private File fileToUnscramble;

    private ScheduledExecutorService timer;
    private VideoCapture capture = new VideoCapture();
    private boolean cameraActive = false;
    private static int cameraId = 0;

    // Variables pour le calcul du FPS
    private double currentFPS = 0;
    private int fpsFrameCount = 0;
    private long fpsStartTime = 0;

    VideoWriter videoWriter;

    @FXML
    public void initialize() {
        sliderR.setValue(r & 0xFF);
        sliderS.setValue(s);

        randomKey.setSelected(false);
        scrambleVideoWithRandomKey.setSelected(false);

        sliderR.disableProperty().bind(randomKey.selectedProperty());
        sliderS.disableProperty().bind(randomKey.selectedProperty());


        sliderR.valueProperty().addListener((observable, oldValue, newValue) -> {
            r = (byte) newValue.intValue();
        });
        sliderS.valueProperty().addListener((observable, oldValue, newValue) -> {
            s = (byte) newValue.intValue();
        });
        sliderP.valueProperty().addListener((observable, oldValue, newValue) -> {
            p = newValue.intValue();
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
            this.capture.open(cameraId);
            // is the video stream available?
            if (this.capture.isOpened())
            {
                this.cameraActive = true;
                int fourcc = VideoWriter.fourcc('H','2','6','4');
                double fps = capture.get(Videoio.CAP_PROP_FPS);
                Size size =  new Size((int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH), (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT));

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
                            Image imageToShow = mat2Image(frame.clone());

                            if(randomKey.isSelected()){
                                r = (byte)Math.abs(new Random().nextInt(4, 255) & 0xFF);
                                s = (byte)Math.abs(new Random().nextInt(4, 128) & 0xFF);
                            }

                            Platform.runLater(() -> {
                                rValue.setText(String.format("r: %d", (int) r & 0xFF));
                                sValue.setText(String.format("s: %d", (int) s));
                            });

                            Mat scrambled = Scrambler.scramble(frame, r, s);
                            Image scrambledImageToShow = mat2Image(scrambled);

                            Mat unscrambled = Unscrambler.unscramble(scrambled, p);
                            Image unscrambledImageToShow = mat2Image(unscrambled);

                            // Calcul du FPS
                            long currentTime = System.currentTimeMillis();
                            if (fpsStartTime == 0) {
                                fpsStartTime = currentTime;
                            }
                            fpsFrameCount++;

                            // Mettre à jour le FPS toutes les 30 frames
                            if (fpsFrameCount >= 30) {
                                long elapsed = currentTime - fpsStartTime;
                                if (elapsed > 0) {
                                    currentFPS = (fpsFrameCount * 1000.0) / elapsed;
                                }
                                fpsFrameCount = 0;
                                fpsStartTime = currentTime;

                                // Afficher le FPS
                                final double fps = currentFPS;
                                Platform.runLater(() -> {
                                    lblFPS.setText(String.format("FPS: %.1f", fps));
                                });
                            }

                            updateImageView(originalFrame, imageToShow);
                            updateImageView(scrambledFrame, scrambledImageToShow);
                            updateImageView(unscrambledFrame, unscrambledImageToShow);

                            if (videoWriter != null && videoWriter.isOpened()) {
                                videoWriter.write(scrambled);
                            }

                            scrambled.release();
                            unscrambled.release();
                            frame.release();
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

            // Réinitialiser les variables FPS
            fpsFrameCount = 0;
            fpsStartTime = 0;
            currentFPS = 0;
            Platform.runLater(() -> lblFPS.setText("FPS: --"));

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

    @FXML
    public void selectVideoToScramble(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une vidéo à chiffrer");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.avi", "*.mkv")
        );
        File selectedFile = fileChooser.showOpenDialog(button.getScene().getWindow());
        if (selectedFile != null) {
            this.fileToScramble = selectedFile;
            lblScrambleSource.setText(selectedFile.getName());
            btnStartScramble.setDisable(false);

            // Mise à jour visuelle des clés qui seront utilisées
            lblCurrentR.setText("R: " + (int)(r & 0xFF));
            lblCurrentS.setText("S: " + (int)s);
        }
    }

    @FXML
    public void startScrambleProcess(ActionEvent event) {
        if (fileToScramble == null) return;

        // Création du nom de fichier de sortie (ex: video.mp4 -> video_scrambled.avi)
        String inputPath = fileToScramble.getAbsolutePath();
        String outputPath = inputPath.substring(0, inputPath.lastIndexOf('.')) + "_scrambled.avi";

        // Capture des clés actuelles (pour éviter qu'elles changent si l'user touche aux sliders pendant le traitement)
        final byte currentR = this.r;
        final byte currentS = this.s;

        // Lancement de la tâche
        processVideoTask(inputPath, outputPath, progressScramble, lblStatusScramble, true, currentR, currentS);
    }

    // ==========================================
    // LOGIQUE DÉCHIFFREMENT FICHIER
    // ==========================================

    @FXML
    public void selectVideoToUnscramble(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une vidéo à déchiffrer");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Vidéos AVI", "*.avi"),
                new FileChooser.ExtensionFilter("Toutes vidéos", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(button.getScene().getWindow());
        if (selectedFile != null) {
            this.fileToUnscramble = selectedFile;
            lblUnscrambleSource.setText(selectedFile.getName());
            btnStartUnscramble.setDisable(false);
        }
    }

    @FXML
    public void startUnscrambleProcess(ActionEvent event) {
        if (fileToUnscramble == null) return;

        String inputPath = fileToUnscramble.getAbsolutePath();
        String outputPath = inputPath.substring(0, inputPath.lastIndexOf('.')) + "_unscrambled.avi";

        final byte manualR = this.r; // Si pas auto-crack, on prend les sliders
        final byte manualS = this.s;

        processVideoTask(inputPath, outputPath, progressUnscramble, lblStatusUnscramble, false, manualR, manualS);
    }

    // ==========================================
    // MOTEUR GÉNÉRIQUE DE TRAITEMENT VIDÉO
    // ==========================================

    private void processVideoTask(String inputPath, String outputPath,
                                  ProgressBar progressBar, Label statusLabel,
                                  boolean isScrambling, byte keyR, byte keyS) {

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                VideoCapture fileCapture = new VideoCapture(inputPath);
                VideoWriter fileWriter = null;

                if (!fileCapture.isOpened()) {
                    updateMessage("Erreur : Impossible d'ouvrir la vidéo source.");
                    return null;
                }

                try {
                    // Récupération des métadonnées
                    double fps = fileCapture.get(Videoio.CAP_PROP_FPS);
                    int width = (int) fileCapture.get(Videoio.CAP_PROP_FRAME_WIDTH);
                    int height = (int) fileCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
                    int totalFrames = (int) fileCapture.get(Videoio.CAP_PROP_FRAME_COUNT);

                    if (fps <= 0) fps = 30.0; // Fallback

                    // Codec MJPG pour compatibilité maximale .avi
                    int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
                    fileWriter = new VideoWriter(outputPath, fourcc, fps, new Size(width, height), true);

                    if (!fileWriter.isOpened()) {
                        updateMessage("Erreur : Impossible de créer le fichier de sortie.");
                        return null;
                    }

                    Mat frame = new Mat();
                    int frameCounter = 0;
                    byte finalR = keyR;
                    byte finalS = keyS;

                    updateMessage("Traitement en cours...");

                    while (fileCapture.read(frame)) {
                        if (frame.empty()) break;

                        Mat processedFrame;

                        if (isScrambling) {
                            if(scrambleVideoWithRandomKey.isSelected()){
                                final Random random = new Random();
                                r = (byte)Math.abs(random.nextInt(4, 255) & 0xFF);
                                s = (byte)Math.abs(random.nextInt(4, 128) & 0xFF);
                            }
                            processedFrame = Scrambler.scramble(frame, finalR, finalS);
                        } else {
                            processedFrame = Unscrambler.unscramble(frame, p);
                        }

                        // Écriture
                        fileWriter.write(processedFrame);

                        // Nettoyage
                        // processedFrame.release(); // Attention: si scramble retourne une nouvelle Mat, release.
                        // frame.release() est inutile ici car réutilisé par read(), mais bon de savoir.

                        frameCounter++;
                        updateProgress(frameCounter, totalFrames);

                        // Mise à jour texte tous les 10% pour ne pas spammer l'UI
                        if (frameCounter % (totalFrames / 10 + 1) == 0) {
                            updateMessage(String.format("Traitement : %d %%", (frameCounter * 100 / totalFrames)));
                        }
                    }

                    updateMessage("Terminé ! Fichier : " + new File(outputPath).getName());
                    updateProgress(1, 1);

                } catch (Exception e) {
                    updateMessage("Erreur : " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    fileCapture.release();
                    if (fileWriter != null) fileWriter.release();
                }
                return null;
            }
        };

        // Liaison des propriétés de la tâche à l'UI
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        // Lancer dans un thread séparé
        new Thread(task).start();
    }
}