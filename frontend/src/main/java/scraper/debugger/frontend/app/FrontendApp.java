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
            CONTROL.setModel(new FrontendModel(CONTROL, bindingIp, port));
            CONTROL.initialize();

            stage.setScene(new Scene(loaded));
            stage.initStyle(StageStyle.UNDECORATED);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
