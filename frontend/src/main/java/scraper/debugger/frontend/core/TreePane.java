package scraper.debugger.frontend.core;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("Duplicates")
public class TreePane {

    // Pane, upon which our tree will build itself
    private final AnchorPane TREE_PANE;

    // Tree levels
    private final List<VBox> nodeBoxes = new ArrayList<>();

    private final Map<Circle, VBox> circleToLevel = new HashMap<>();

    // How nodes are connected
    private final Map<VBox, Map<Integer, List<Circle>>> nodeBoxSeparation = new HashMap<>();

    // How lines are situated
    private final Map<Line, Entry<Circle, Circle>> lines = new HashMap<>();
    private final Map<Line, Entry<VBox, VBox>> lines2 = new HashMap<>();

    public TreePane(AnchorPane pane) {
        TREE_PANE = pane;
        Platform.runLater(() -> {
            TREE_PANE.setPrefWidth(2000);
            TREE_PANE.setPrefHeight(1000);
        });
    }

    public void putInitial(Circle circle) {
        if (nodeBoxes.isEmpty()) {
            VBox firstLevel = createLevel(50);

            nodeBoxes.add(firstLevel);

            circleToLevel.put(circle, firstLevel);

            Platform.runLater(() -> {
                firstLevel.getChildren().add(circle);
                TREE_PANE.getChildren().add(firstLevel);
            });
        }
    }

    public Line put(Circle parent, Circle childToPut) {
        VBox pLevel = circleToLevel.get(parent);
        assert pLevel != null;
        assert !exists(childToPut);

        // create or find level
        VBox level;
        int pLevelNumber = nodeBoxes.indexOf(pLevel);
        if (pLevelNumber == nodeBoxes.size() - 1) {
            // child introduces new level
            level = createLevel(pLevel.getLayoutX() + 100);
            nodeBoxSeparation.put(level, new HashMap<>(4));
            nodeBoxes.add(level);
            circleToLevel.put(childToPut, level);

            Platform.runLater(() -> {
                level.getChildren().add(childToPut);
                TREE_PANE.getChildren().add(level);
            });

        } else {
            // add child one level ahead
            level = nodeBoxes.get(pLevelNumber + 1);

            // in which index of level should child be added
            int addingIndex = getAddingIndex(parent, pLevel, level);

            Platform.runLater(() -> {
                level.getChildren().add(addingIndex, childToPut);
            });

            updateNodeBoxSeparation(addingIndex, level);
        }


        // update partition
        int parentIndex = pLevel.getChildren().indexOf(parent);
        Map<Integer, List<Circle>> partition = nodeBoxSeparation.get(level);
        List<Circle> goes = partition.get(parentIndex);
        if (goes == null) {
            goes = new LinkedList<>();
            goes.add(parent);
            partition.put(parentIndex, goes);
        }
        else goes.add(parent);


        // drawl line between
        Line line = new Line();
        lines.put(line, new AbstractMap.SimpleEntry<>(parent, childToPut));
        lines2.put(line, new AbstractMap.SimpleEntry<>(pLevel, level));
        double startY = getNodeCenter(pLevel, parent) + 9;
        double endY = getNodeCenter(level, childToPut) + 9;

        Platform.runLater(() -> {
            line.setLayoutX(0);
            line.setLayoutY(0);
            line.setStartX(pLevel.getLayoutX() + 9);
            line.setStartY(startY);
            line.setEndX(level.getLayoutX() + 9);
            line.setEndY(endY);
            TREE_PANE.getChildren().add(line);
        });

        updateLines(pLevel, level);
        updateLines(level);

        return line;
    }


    // Center Y of given circle in given level.
    private double getNodeCenter(VBox level, Circle circle) {
        int index = level.getChildren().indexOf(circle);
        int size = level.getChildren().size();
        if (size > 5) return getCenterHelper(level, circle);
        if (size % 2 == 0) {
            int half = size / 2;
            if (index > half - 1) {
                int k = index - half + 1;
                if (k == 0) {
                    return 165 + 30 + 9;
                } else {
                    return 165 + 30 + (k - 1) * 60 + (k - 1) * 18 + 9;
                }
            } else {
                int k = half - index;
                if (k == 1) {
                    return 165 - 30 - 9;
                }
                else {
                    return 165 - (30 + (k - 1) * 60 + (k - 1) * 18 + 9);
                }
            }
        } else {
            int centerI = size / 2;
            if (centerI == 0) {
                return 165;
            } else {
                if (index < centerI) {
                    int k = centerI - index;
                    return 165 - (9 + k * 60 + (k - 1) * 18 + 9);
                } else {
                    int k = index - centerI;
                    if (k == 0) {
                        return 165;
                    } else {
                        return 165 + 9 + k * 60 + (k - 1) * 18 + 9;
                    }
                }
            }
        }
    }

    private double getCenterHelper(VBox level, Circle circle) {
        int midI = 2;
        int i = level.getChildren().indexOf(circle);
        if (i == midI) {
            return 165;
        } else {
            if (i < midI) {
                int k = midI - i;
                return 165 - (9 + 60 * k + 18 * (k - 1) + 9);
            } else {
                int k = i - midI;
                return 165 + 9 + 60 * k + 18 * (k - 1) + 9;
            }
        }
    }


    private int getAddingIndex(Circle parent, VBox parentLevel, VBox childLevel) {
        Map<Integer, List<Circle>> partOfChildLevel = nodeBoxSeparation.get(childLevel);
        int indexC1 = parentLevel.getChildren().indexOf(parent);

        int add = 0;
        for (int i = indexC1 - 1; i >= 0; i--) {
            List<Circle> goes = partOfChildLevel.get(i);
            add += goes == null ? 0 : goes.size();
        }
        return add;
    }


    // All indexes after "afterIndex" must be updated in box separation.
    private void updateNodeBoxSeparation(int afterIndex, VBox level) {
        int thisLevel = nodeBoxes.indexOf(level);
        if (thisLevel < nodeBoxes.size() - 1) {
            VBox next = nodeBoxes.get(thisLevel + 1);
            Map<Integer, List<Circle>> part = nodeBoxSeparation.get(next);
            Map<Integer, List<Circle>> newMap = new HashMap<>();

            if (next != null) {
                part.forEach((i, list) -> {
                    if (i < afterIndex) newMap.put(i, list);
                    else newMap.put(i + 1, list);
                });
                nodeBoxSeparation.put(next, newMap);
            }
        }
    }


    private void updateLines(VBox parentLevel, VBox childLevel) {
        lines2.forEach((l, boxes) -> {
            if (boxes.getKey() == parentLevel && boxes.getValue() == childLevel) {
                Entry<Circle, Circle> circles = lines.get(l);
                l.setStartY(getNodeCenter(parentLevel, circles.getKey()) + 9);
                l.setEndY(getNodeCenter(childLevel, circles.getValue()) + 9);
            }
        });
    }

    private void updateLines(VBox level) {
        // if a box exists after this box, lines between updated
        int levelNumber = nodeBoxes.indexOf(level);
        if (levelNumber < nodeBoxes.size() - 1) {
            updateLines(level, nodeBoxes.get(levelNumber + 1));
        }
    }


    private VBox createLevel(double xGap) {
        VBox level = new VBox();
        Platform.runLater(() -> {
            level.setAlignment(Pos.CENTER);
            level.setPrefWidth(20);
            level.setPrefHeight(330);
            level.setSpacing(60);
            level.setLayoutY(9);
            level.setLayoutX(xGap);
        });
        return level;
    }

    private boolean exists(Circle circle) {
        return circleToLevel.get(circle) != null;
    }
}
