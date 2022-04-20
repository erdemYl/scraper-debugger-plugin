package scraper.debugger.frontend.core;

import javafx.application.Platform;
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

    // Flow value table
    private final TableView<FlowDTO> VALUES;

    private final Service<Deque<QuasiStaticNode>> VALUE_VIEW;
    private Deque<QuasiStaticNode> currentSelectedNodes = null;

    // Flow map
    private final ListView<String> MAP;
    private final Label MAP_LABEL;

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


    ValuesViewModel(FrontendModel MODEL, TableView<FlowDTO> valueTable, ListView<String> flowMap, Label mapLabel) {
        VALUES = valueTable;
        MAP = flowMap;
        MAP_LABEL = mapLabel;
        VALUES.setItems(currentViewedFlows);
        MAP.setItems(currentViewedMap);

        String style = "-fx-background-color: burlywood; -fx-border-color:  #896436; -fx-border-width: 2";

        // cell factory for static columns
        Callback<TableColumn<FlowDTO, String>, TableCell<FlowDTO, String>> cells = column -> new TableCell<>() {
            @Override
            protected void updateItem(String content, boolean empty) {
                super.updateItem(content, empty);
                if (empty) {
                    setStyle(null);
                    setText(null);
                } else {
                    setStyle(style);
                    setText(content);
                }
            }
        };
        waitingColumn.setCellFactory(cells);
        processedColumn.setCellFactory(cells);

        // styling static columns
        waitingColumn.setStyle(style);
        waitingColumn.setPrefWidth(154);
        waitingColumn.textProperty().bind(waitingFlowNumber);
        processedColumn.setStyle(style);
        processedColumn.setPrefWidth(154);
        processedColumn.textProperty().bind(processedFlowNumber);

        VALUE_VIEW = createViewService();
        VALUE_VIEW.setExecutor(Executors.newSingleThreadExecutor());

        // flow map elements
        MAP.setCellFactory(new Callback<>() {
            @Override
            public ListCell<String> call(ListView<String> view) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(String content, boolean empty) {
                        super.updateItem(content, empty);
                        if (empty) {
                            setStyle(null);
                            setText(null);
                        } else {
                            setStyle(style);
                            setText(content);
                        }
                    }
                };
            }
        });

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
                            setOnMouseClicked(null);
                        } else {
                            setStyle("-fx-background-color: darksalmon");
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

    void visibleMap(boolean v) {
        MAP.setVisible(v);
        MAP_LABEL.setVisible(v);
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
                                List<TableColumn<FlowDTO, String>> usableColumns = new LinkedList<>(currentSelectedNodes){{pollLast();}}
                                        .stream()
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
