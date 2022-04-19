package scraper.debugger.frontend.core;

import javafx.application.Platform;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.*;


import javafx.scene.paint.Paint;
import javafx.util.Callback;
import scraper.debugger.dto.FlowDTO;

import java.util.*;
import java.util.concurrent.Executors;

public class ValuesViewModel {

    private final FrontendModel MODEL;

    // Flow value table
    private final TableView<FlowDTO> VALUES;

    private final Service<Deque<QuasiStaticNode>> VALUE_VIEW;
    private Deque<QuasiStaticNode> currentSelectedNodes = null;

    // Flow map
    private final ListView<String> MAP;
    private final Label MAP_LABEL = new Label();

    // Registered columns, not modified once columns registered
    private final Map<QuasiStaticNode, TableColumn<FlowDTO, String>> valueColumns = new HashMap<>();

    // Static columns
    private final TableColumn<FlowDTO, String> waitingColumn = new TableColumn<>();
    private final TableColumn<FlowDTO, String> processedColumn = new TableColumn<>();

    // Properties
    private final StringProperty waitingFlowNumber = new SimpleStringProperty("Waiting: 0");
    private final StringProperty processedFlowNumber = new SimpleStringProperty("Processed: 0");
    private final ObservableList<FlowDTO> currentViewedFlows = FXCollections.observableArrayList();
    private final ObservableList<String> currentViewedMap = FXCollections.observableArrayList();


    ValuesViewModel(FrontendModel MODEL, TableView<FlowDTO> valueTable, ListView<String> flowMap) {
        this.MODEL = MODEL;
        VALUES = valueTable;
        MAP = flowMap;
        VALUES.setItems(currentViewedFlows);
        MAP.setItems(currentViewedMap);

        String style = "-fx-background-color: burlywood; -fx-border-color:  #896436; -fx-border-width: 2";
        waitingColumn.setStyle(style);
        waitingColumn.setPrefWidth(154);
        waitingColumn.textProperty().bind(waitingFlowNumber);
        processedColumn.setStyle(style);
        processedColumn.setPrefWidth(154);
        processedColumn.textProperty().bind(processedFlowNumber);

        VALUE_VIEW = createViewService();
        VALUE_VIEW.setExecutor(Executors.newSingleThreadExecutor());

        // rows
        VALUES.setRowFactory(new Callback<>() {
            @Override
            public TableRow<FlowDTO> call(TableView<FlowDTO> view) {
                return new TableRow<>() {
                    @Override
                    protected void updateItem(FlowDTO f, boolean empty) {
                        super.updateItem(f, empty);
                        if (empty) {
                            setStyle(null);
                            setPrefWidth(0);
                            setOnMouseClicked(null);
                        } else {
                            setStyle("-fx-background-color: darksalmon");
                            setPrefWidth(103);
                            setOnMouseClicked(e -> {
                                setTextFill(Paint.valueOf("darksalmon"));
                                currentViewedMap.clear();
                                f.getContent().forEach((key, value) -> {
                                    currentViewedMap.add(key + ":  " + value.toString());
                                });
                                MODEL.takeSelectedFlow(f);
                            });
                        }
                    }
                };
            }
        });
    }

    void createValueColumns(Set<QuasiStaticNode> NODES) {
        NODES.forEach(node -> {
            node.dataStreamKey().ifPresent(key -> {
                TableColumn<FlowDTO, String> valueColumn = new TableColumn<>(key);

                // Cell value of this column is the value of data stream key
                valueColumn.setCellValueFactory(features ->
                        new SimpleStringProperty(features.getValue().getContent().get(key).toString())
                );
                valueColumns.put(node, valueColumn);
            });
        });
    }

    void viewValues(Deque<QuasiStaticNode> selectedNodes) {
        currentSelectedNodes = Objects.requireNonNull(selectedNodes);
        VALUE_VIEW.start();
    }

    private Service<Deque<QuasiStaticNode>> createViewService() {
        return new Service<>() {
            @Override
            protected Task<Deque<QuasiStaticNode>> createTask() {
                return new Task<>() {
                    @Override
                    protected Deque<QuasiStaticNode> call() {
                        if (!currentSelectedNodes.isEmpty()) {
                            // at least one node must be marked and on screen
                            QuasiStaticNode node = currentSelectedNodes.getLast();

                            if (node.isOnScreen()) {

                                // new value factories for static columns
                                waitingColumn.setCellValueFactory(features -> {
                                    FlowDTO f = features.getValue();
                                    if (!node.departed(f)) {
                                        return new SimpleStringProperty(f.getIdent());
                                    }
                                    return new SimpleStringProperty("");
                                });
                                processedColumn.setCellValueFactory(features -> {
                                    FlowDTO f = features.getValue();
                                    if (node.departed(f)) {
                                        return new SimpleStringProperty(f.getIdent());
                                    }
                                    return new SimpleStringProperty("");
                                });

                                // find usable columns for this marking
                                currentSelectedNodes.pollLast();
                                List<TableColumn<FlowDTO, String>> usableColumns = currentSelectedNodes.stream()
                                        .map(valueColumns::get)
                                        .filter(Objects::nonNull)
                                        .toList();

                                // Renew items and add columns
                                ObservableList<TableColumn<FlowDTO, ?>> currentViewedColumns = VALUES.getColumns();

                                Platform.runLater(() -> {
                                    currentViewedFlows.clear();
                                    currentViewedColumns.clear();
                                    currentViewedColumns.addAll(List.of(waitingColumn, processedColumn));
                                    currentViewedColumns.addAll(usableColumns);
                                });
                                Set<FlowDTO> arrivals = node.arrivals();
                                Set<FlowDTO> departures = node.departures();

                                Platform.runLater(() -> {
                                    currentViewedFlows.addAll(arrivals);
                                    currentViewedFlows.addAll(departures);
                                });


                                Platform.runLater(() -> {
                                    waitingFlowNumber.setValue("Waiting: " + arrivals.size());
                                    processedFlowNumber.setValue("Processed: " + departures.size());
                                });

                            }
                        }
                        return currentSelectedNodes;
                    }
                };
            }

            @Override
            protected void succeeded() {
                reset();
            }

            @Override
            protected void failed() {
                reset();
            }
        };
    }
}
