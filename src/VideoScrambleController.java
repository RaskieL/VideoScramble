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
import javafx.scene.Scene;
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

    final Random random = new Random();

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


        sliderR.valueProperty().addListener((observable, oldValue, newValue) -> r = (byte) newValue.intValue());
        sliderS.valueProperty().addListener((observable, oldValue, newValue) -> s = (byte) newValue.intValue());
        sliderP.valueProperty().addListener((observable, oldValue, newValue) -> p = newValue.intValue());

    }

    /**
     * Initialise les tailles des ImageView en fonction de la taille de la fenêtre
     */
    public void initializeImageSizes(Scene scene) {
        // Lier la largeur de chaque ImageView à environ 1/3 de la largeur de la scène
        // En tenant compte des marges et espacements
        originalFrame.fitWidthProperty().bind(scene.widthProperty().divide(3.3));
        scrambledFrame.fitWidthProperty().bind(scene.widthProperty().divide(3.3));
        unscrambledFrame.fitWidthProperty().bind(scene.widthProperty().divide(3.3));
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
            startCameraCapture();
        }
        else
        {
            stopCameraCapture();
        }
    }

    /**
     * Démarre la capture vidéo depuis la caméra
     */
    private void startCameraCapture() {
        this.capture.open(cameraId);
        if (this.capture.isOpened())
        {
            this.cameraActive = true;
            Runnable frameGrabber = this::processFrame;
            this.timer = Executors.newSingleThreadScheduledExecutor();
            this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
            this.button.setText("Stop Camera");

            int fourcc = VideoWriter.fourcc('H','2','6','4');
            double fps = capture.get(Videoio.CAP_PROP_FPS);
            Size size =  new Size((int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH), (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT));
            this.videoWriter = new VideoWriter("/home/maiken/Videos/test.avi", fourcc, fps, size, true);
        }
        else
        {
            System.err.println("Impossible to open the camera connection...");
        }
    }

    /**
     * Arrête la capture vidéo depuis la caméra
     */
    private void stopCameraCapture() {
        this.cameraActive = false;
        this.button.setText("Start Camera");
        resetFPSCounters();
        this.stopAcquisition();
    }

    /**
     * Traite une frame de la caméra
     */
    private void processFrame() {
        try {
            Mat frame = grabFrame();
            if (frame.empty()) {
                return;
            }

            updateRandomKeysIfNeeded();
            updateKeyLabels();

            Image imageToShow = mat2Image(frame.clone());
            Mat scrambled = Scrambler.scramble(frame, r, s);
            Image scrambledImageToShow = mat2Image(scrambled);

            Mat unscrambled = Unscrambler.unscramble(scrambled, p);
            Image unscrambledImageToShow = mat2Image(unscrambled);

            updateFPS();
            updateAllImageViews(imageToShow, scrambledImageToShow, unscrambledImageToShow);
            writeToVideoIfNeeded(scrambled);

            releaseMatrices(frame, scrambled, unscrambled);
        } catch (Exception e) {
            System.err.println("Erreur dans la boucle vidéo : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Met à jour les clés aléatoires si nécessaire
     */
    private void updateRandomKeysIfNeeded() {
        if (randomKey.isSelected()) {
            r = (byte) Math.abs(random.nextInt(4, 255) & 0xFF);
            s = (byte) Math.abs(random.nextInt(4, 128) & 0xFF);
        }
    }

    /**
     * Met à jour les labels affichant les clés
     */
    private void updateKeyLabels() {
        Platform.runLater(() -> {
            rValue.setText(String.format("r: %d", r & 0xFF));
            sValue.setText(String.format("s: %d", s & 0xFF));
        });
    }

    /**
     * Met à jour le calcul et l'affichage du FPS
     */
    private void updateFPS() {
        long currentTime = System.currentTimeMillis();
        if (fpsStartTime == 0) {
            fpsStartTime = currentTime;
        }
        fpsFrameCount++;

        if (fpsFrameCount >= 30) {
            long elapsed = currentTime - fpsStartTime;
            if (elapsed > 0) {
                currentFPS = (fpsFrameCount * 1000.0) / elapsed;
            }
            fpsFrameCount = 0;
            fpsStartTime = currentTime;

            final double fps = currentFPS;
            Platform.runLater(() -> lblFPS.setText(String.format("FPS: %.1f", fps)));
        }
    }

    /**
     * Réinitialise les compteurs FPS
     */
    private void resetFPSCounters() {
        fpsFrameCount = 0;
        fpsStartTime = 0;
        currentFPS = 0;
        Platform.runLater(() -> lblFPS.setText("FPS: --"));
    }

    /**
     * Met à jour toutes les ImageView
     */
    private void updateAllImageViews(Image original, Image scrambled, Image unscrambled) {
        updateImageView(originalFrame, original);
        updateImageView(scrambledFrame, scrambled);
        updateImageView(unscrambledFrame, unscrambled);
    }

    /**
     * Écrit dans le fichier vidéo si un VideoWriter est actif
     */
    private void writeToVideoIfNeeded(Mat scrambled) {
        if (videoWriter != null && videoWriter.isOpened()) {
            videoWriter.write(scrambled);
        }
    }

    /**
     * Libère les ressources des matrices
     */
    private void releaseMatrices(Mat... matrices) {
        for (Mat mat : matrices) {
            if (mat != null) {
                mat.release();
            }
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
        Platform.runLater(() -> property.set(value));
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
            lblCurrentR.setText("R: " + (r & 0xFF));
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

        Task<Void> task = createVideoProcessingTask(inputPath, outputPath, isScrambling, keyR, keyS);
        bindTaskToUI(task, progressBar, statusLabel);
        new Thread(task).start();
    }

    /**
     * Crée une tâche de traitement vidéo
     */
    private Task<Void> createVideoProcessingTask(String inputPath, String outputPath,
                                                 boolean isScrambling, byte keyR, byte keyS) {
        return new Task<>() {
            @Override
            protected Void call() {
                VideoCapture fileCapture = new VideoCapture(inputPath);
                VideoWriter fileWriter = null;

                if (!fileCapture.isOpened()) {
                    updateMessage("Erreur : Impossible d'ouvrir la vidéo source.");
                    return null;
                }

                try {
                    VideoMetadata metadata = extractVideoMetadata(fileCapture);
                    fileWriter = createVideoWriter(outputPath, metadata);

                    if (!fileWriter.isOpened()) {
                        updateMessage("Erreur : Impossible de créer le fichier de sortie.");
                        return null;
                    }

                    processAllFramesInTask(fileCapture, fileWriter, metadata.totalFrames,
                                          isScrambling, keyR, keyS);
                    updateMessage("Terminé ! Fichier : " + new File(outputPath).getName());
                    updateProgress(1, 1);

                } catch (Exception e) {
                    updateMessage("Erreur : " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    releaseVideoResources(fileCapture, fileWriter);
                }
                return null;
            }

            /**
             * Traite toutes les frames de la vidéo (méthode interne à Task)
             */
            private void processAllFramesInTask(VideoCapture capture, VideoWriter writer, int totalFrames,
                                                boolean isScrambling, byte keyR, byte keyS) {
                Mat frame = new Mat();
                int frameCounter = 0;
                updateMessage("Traitement en cours...");

                while (capture.read(frame)) {
                    if (frame.empty()) break;

                    Mat processedFrame = processFrame(frame, isScrambling, keyR, keyS);
                    writer.write(processedFrame);

                    frameCounter++;
                    updateProgress(frameCounter, totalFrames);

                    if (frameCounter % (totalFrames / 10 + 1) == 0) {
                        updateMessage(String.format("Traitement : %d %%", (frameCounter * 100 / totalFrames)));
                    }
                }
            }
        };
    }

    /**
     * Extrait les métadonnées de la vidéo
     */
    private VideoMetadata extractVideoMetadata(VideoCapture capture) {
        double fps = capture.get(Videoio.CAP_PROP_FPS);
        int width = (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int height = (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        int totalFrames = (int) capture.get(Videoio.CAP_PROP_FRAME_COUNT);

        if (fps <= 0) fps = 30.0; // Fallback

        return new VideoMetadata(fps, width, height, totalFrames);
    }

    /**
     * Crée un VideoWriter pour l'écriture de la vidéo de sortie
     */
    private VideoWriter createVideoWriter(String outputPath, VideoMetadata metadata) {
        int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
        return new VideoWriter(outputPath, fourcc, metadata.fps,
                              new Size(metadata.width, metadata.height), true);
    }

    /**
     * Traite une frame selon le mode (scramble ou unscramble)
     */
    private Mat processFrame(Mat frame, boolean isScrambling, byte keyR, byte keyS) {
        if (isScrambling) {
            return processScrambleFrame(frame, keyR, keyS);
        } else {
            return Unscrambler.unscramble(frame, p);
        }
    }

    /**
     * Traite une frame en mode scramble
     */
    private Mat processScrambleFrame(Mat frame, byte keyR, byte keyS) {
        if (scrambleVideoWithRandomKey.isSelected()) {
            byte randomR = (byte) Math.abs(random.nextInt(4, 255) & 0xFF);
            byte randomS = (byte) Math.abs(random.nextInt(4, 128) & 0xFF);
            return Scrambler.scramble(frame, randomR, randomS);
        } else {
            return Scrambler.scramble(frame, keyR, keyS);
        }
    }

    /**
     * Lie les propriétés de la tâche à l'interface utilisateur
     */
    private void bindTaskToUI(Task<Void> task, ProgressBar progressBar, Label statusLabel) {
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
    }

    /**
     * Libère les ressources vidéo
     */
    private void releaseVideoResources(VideoCapture capture, VideoWriter writer) {
        if (capture != null) capture.release();
        if (writer != null) writer.release();
    }

    /**
     * Classe interne pour stocker les métadonnées vidéo
     */
    private static class VideoMetadata {
        final double fps;
        final int width;
        final int height;
        final int totalFrames;

        VideoMetadata(double fps, int width, int height, int totalFrames) {
            this.fps = fps;
            this.width = width;
            this.height = height;
            this.totalFrames = totalFrames;
        }
    }
}