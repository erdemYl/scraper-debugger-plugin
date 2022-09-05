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
import scraper.debugger.dto.FlowMapDTO;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ValuesViewModel {

    // Flow value table
    private final TableView<FlowMapDTO> VALUE_TABLE;

    // On demand, views flows in some node n
    private final Service<Void> VIEW_SERVICE;

    // Flow map
    private final ListView<String> MAP;
    private final Label MAP_LABEL;

    // Registered columns, not modified once columns registered
    private final Map<QuasiStaticNode, TableColumn<FlowMapDTO, String>> valueColumns = new HashMap<>();

    // Static columns
    private final TableColumn<FlowMapDTO, String> waitingColumn = new TableColumn<>();
    private final TableColumn<FlowMapDTO, String> processedColumn = new TableColumn<>();

    // Properties
    private final StringProperty waitingFlowNumber = new SimpleStringProperty("Waiting: 0");
    private final StringProperty processedFlowNumber = new SimpleStringProperty("Processed: 0");
    private final ObservableList<FlowMapDTO> currentViewedFlows = FXCollections.observableArrayList();
    private final ObservableList<String> currentViewedMap = FXCollections.observableArrayList();


    ValuesViewModel(FrontendModel MODEL, TableView<FlowMapDTO> valueTable, ListView<String> flowMap, Label mapLabel) {
        VALUE_TABLE = valueTable;
        MAP = flowMap;
        MAP_LABEL = mapLabel;
        VALUE_TABLE.setItems(currentViewedFlows);
        MAP.setItems(currentViewedMap);

        String style = "-fx-background-color: burlywood; -fx-border-color:  #896436; -fx-border-width: 2";

        // cell factory for static columns
        Callback<TableColumn<FlowMapDTO, String>, TableCell<FlowMapDTO, String>> cells = column -> new TableCell<>() {
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

        VIEW_SERVICE = createViewService(MODEL);
        VIEW_SERVICE.setExecutor(Executors.newSingleThreadExecutor());

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
        VALUE_TABLE.setRowFactory(new Callback<>() {
            @Override
            public TableRow<FlowMapDTO> call(TableView<FlowMapDTO> view) {
                return new TableRow<>() {
                    @Override
                    protected void updateItem(FlowMapDTO f, boolean empty) {
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
                                    currentViewedMap.add(key + "::  " + value.toString());
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
                TableColumn<FlowMapDTO, String> valueColumn = new TableColumn<>(key);

                // Cell value of this column is the value of data stream key
                valueColumn.setCellValueFactory(features ->
                        new SimpleStringProperty(features.getValue().getContent().get(key).toString())
                );
                valueColumns.put(node, valueColumn);
            });
        });
    }

    void viewValues() {
        VIEW_SERVICE.restart();
    }

    void visibleMap(boolean v) {
        MAP.setVisible(v);
        MAP_LABEL.setVisible(v);
    }

    private Service<Void> createViewService(FrontendModel MODEL) {
        return new Service<>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<>() {
                    @Override
                    protected Void call() {
                        MODEL.currentSelectedNodes().ifPresent(currentNodes -> {

                            // at least one node is selected
                            QuasiStaticNode node = currentNodes.getLast();

                            if (node.isOnScreen()) {

                                // new value factories for static columns
                                waitingColumn.setCellValueFactory(features -> {
                                    FlowMapDTO f = features.getValue();
                                    CharSequence ident = f.getIdent();
                                    if (!node.departed(ident)) {
                                        return new SimpleStringProperty(ident.toString().intern());
                                    }
                                    return new SimpleStringProperty("");
                                });
                                processedColumn.setCellValueFactory(features -> {
                                    FlowMapDTO f = features.getValue();
                                    CharSequence ident = f.getIdent();
                                    if (node.departed(ident)) {
                                        return new SimpleStringProperty(ident.toString().intern());
                                    }
                                    return new SimpleStringProperty("");
                                });

                                // find usable columns for this marking
                                //---last node is not taken
                                int last = currentNodes.size() - 1;
                                List<TableColumn<FlowMapDTO, String>> usableColumns = currentNodes.stream()
                                        .limit(last)
                                        .map(valueColumns::get)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList());

                                // Renew items and add columns
                                ObservableList<TableColumn<FlowMapDTO, ?>> currentViewedColumns = VALUE_TABLE.getColumns();

                                List<FlowMapDTO> arrivals = node.arrivals()
                                        .parallelStream()
                                        .map(MODEL.ACTIONS::requestDataflowQuery)
                                        .collect(Collectors.toList());

                                List<FlowMapDTO> departures = node.departures()
                                        .parallelStream()
                                        .map(MODEL.ACTIONS::requestDataflowQuery)
                                        .collect(Collectors.toList());

                                Platform.runLater(() -> {
                                    currentViewedFlows.clear();
                                    currentViewedColumns.clear();
                                    currentViewedColumns.addAll(List.of(waitingColumn, processedColumn));
                                    currentViewedColumns.addAll(usableColumns);
                                    currentViewedFlows.addAll(arrivals);
                                    currentViewedFlows.addAll(departures);
                                    waitingFlowNumber.setValue("Waiting: " + arrivals.size());
                                    processedFlowNumber.setValue("Processed: " + departures.size());
                                });
                            }
                        });
                        return null;
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
