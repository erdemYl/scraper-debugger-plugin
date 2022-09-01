package scraper.debugger.frontend.core;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;

import scraper.debugger.dto.FlowMapDTO;

public class FrontendController {

    private String style = "-fx-background-color: cornsilk";
    private boolean executionStarted = false;

    private FrontendModel MODEL;

    public void setModel(FrontendModel MODEL) { this.MODEL = MODEL; }

    public void initialize() {
        flowMapList.visibleProperty().addListener((changed, oldVal, newVal) -> {
            if (newVal) {
                buttonStepSelected.setOpacity(0.3);
                buttonContinueSelected.setOpacity(0.3);
            } else {
                buttonStepSelected.setOpacity(1);
                buttonContinueSelected.setOpacity(1);
            }
        });
        logTextArea.textProperty().addListener((value, oldVal, newVal) -> logTextArea.setScrollTop(Double.MAX_VALUE));
    }


    //===========
    // BUTTONS
    //===========

    @FXML Label buttonScraper;
    @FXML void buttonScraperClicked() {
        style = "-fx-background-color: " + (style.contains("cornsilk") ? "lightblue" : "cornsilk");
        buttonScraper.setStyle(style);
        dynamicFlowTree.setStyle(style);
    }


    @FXML Label buttonExit;
    @FXML void buttonExitClicked() {
        Platform.exit();
        System.exit(0);
    }


    @FXML Label buttonConnect;
    @FXML void buttonConnectClicked() {
        MODEL.ACTIONS.connectToBackend();
        buttonConnect.setText("Connected");
    }


    @FXML Pane buttonContinueExecution;
    @FXML void buttonContinueExecutionClicked() {
        if (executionStarted) {
            MODEL.ACTIONS.requestResumeAllContinueExecution();
        } else {
            executionStarted = true;
            MODEL.ACTIONS.requestStartExecution();
            buttonStepAllContinueExecution.setOpacity(1);
            buttonContinueFlowsInNode.setOpacity(1);
        }
        buttonContinueExecution.setVisible(false);
        buttonStopExecution.setVisible(true);
    }


    @FXML Pane buttonStopExecution;
    @FXML void buttonStopExecutionClicked() {
        MODEL.ACTIONS.requestStopExecution();
        buttonStopExecution.setVisible(false);
        buttonContinueExecution.setVisible(true);
    }


    @FXML AnchorPane buttonStepAllContinueExecution;
    @FXML void buttonStepAllContinueExecutionClicked() {
        MODEL.ACTIONS.requestStepAllContinueExecution();
    }


    @FXML AnchorPane buttonContinueFlowsInNode;
    @FXML void buttonContinueFlowsInNodeClicked() {
        MODEL.currentSelectedNodes().ifPresent(nodes -> {
            if (!nodes.isEmpty()) {
                QuasiStaticNode node = nodes.getLast();
                if (node.isOnScreen()) {
                    node.arrivals().forEach(ident -> MODEL.ACTIONS.requestResumeSelected(ident));
                    MODEL.ACTIONS.requestContinueExecution();
                }
            }
        });
    }


    @FXML AnchorPane buttonLog;
    @FXML void buttonLogClicked() {
        if (logScrollPane.isVisible()) {
            logScrollPane.setVisible(false);
            specificationTreeView.setVisible(true);
        } else {
            specificationTreeView.setVisible(false);
            logScrollPane.setVisible(true);
        }
    }


    @FXML Pane buttonStepSelected;
    @FXML void buttonStepSelectedClicked() {
        if (buttonStepSelected.getOpacity() == 1) {
            MODEL.currentSelectedMap().ifPresent(f -> {
                MODEL.ACTIONS.requestStepSelected(f.getIdent());
                MODEL.ACTIONS.requestContinueExecution();
            });
        }
    }


    @FXML Pane buttonContinueSelected;
    @FXML void buttonContinueSelectedClicked() {
        if (buttonContinueSelected.getOpacity() == 1) {
            MODEL.currentSelectedMap().ifPresent(f -> {
                MODEL.ACTIONS.requestResumeSelected(f.getIdent());
                MODEL.ACTIONS.requestContinueExecution();
            });
        }
    }




    //=================
    // Static Specification
    //================

    @FXML TreeView<QuasiStaticNode> specificationTreeView;


    //================
    // Dynamic Flow Tree
    //================

    @FXML AnchorPane dynamicFlowTree;


    //================
    // Flow Value Table
    //================

    @FXML TableView<FlowMapDTO> valueTable;


    //================
    // Flow Map List
    //================

    @FXML ListView<String> flowMapList;
    @FXML Label flowMapLabel;


    //===============
    // Incoming Logs
    //===============

    @FXML ScrollPane logScrollPane;
    @FXML TextArea logTextArea;

}
