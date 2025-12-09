import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.application.Application;
import javafx.scene.Scene;

import javafx.stage.WindowEvent;
import org.opencv.core.Core;

public class VideoScramble extends Application {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // load the FXML resource
            FXMLLoader loader = new FXMLLoader(getClass().getResource("VideoScramble.fxml"));
            // store the root element so that the controllers can use it
            BorderPane rootElement = (BorderPane) loader.load();

            Scene scene = new Scene(rootElement, 1400, 800);

            primaryStage.setTitle("Video scramble");
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);
            primaryStage.show();

            // set the proper behavior on closing the application
            VideoScrambleController controller = loader.getController();
            controller.initializeImageSizes(scene);
            primaryStage.setOnCloseRequest(we -> controller.setClosed());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        launch(args);
    }

}

