package scraper.debugger.frontend.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import scraper.debugger.frontend.core.*;

import java.io.IOException;

public class FrontendApp extends Application {

    private final String bindingIp;
    private final int port;

    private FrontendModel MODEL;

    public FrontendApp(String bindingIp, int port) {
        this.bindingIp = bindingIp;
        this.port = port;
    }

    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(FrontendApp.class.getResource("/FrontendView.fxml"));
            Parent loaded = loader.load();
            FrontendController CONTROL = loader.getController();
            MODEL = new FrontendModel(CONTROL, bindingIp, port);
            CONTROL.setModel(MODEL);
            CONTROL.initializeLogTextArea();

            stage.setScene(new Scene(loaded));
            stage.initStyle(StageStyle.UNDECORATED);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
