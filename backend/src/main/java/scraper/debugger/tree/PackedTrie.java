package scraper.debugger.tree;

import java.util.*;


public class PackedTrie<V> implements PrefixTree<V> {

    private Node<V> headNode = null;
    private final Object putMutex = new Object();

    @Override
    public V get(CharSequence key) {
        if (headNode == null || key == null || key.isEmpty())
            return null;

        int keyLen = key.length();

        // while loop only depends on these
        int i = 0;
        Map<Character, Node<V>> transitions = headNode.getTransitions();
        Node<V> node = headNode;
        CharSequence partial = headNode.getPartialKey();

        while (i < keyLen) {
            if (partial == null) {
                Node<V> next = transitions.get(key.charAt(i));
                if (next == null) return null;
                else {
                    i++;
                    transitions = next.getTransitions();
                    node = next;
                    partial = next.getPartialKey();
                }
            } else {
                CharSequence kKey = key.subSequence(i, keyLen);
                int kKeyLen = kKey.length();
                int partLen = partial.length();
                int min = Math.min(partLen, kKeyLen);

                int last = 0;
                while (last < min && partial.charAt(last) == kKey.charAt(last)) {
                    last++;
                }

                if (partLen == min) {
                    if (last == partLen) {
                        if (kKeyLen == last) {
                            // this node
                            return node.value;
                        }
                        else {
                            // search in the next node
                            Node<V> next = transitions.get(kKey.charAt(last));
                            if (next == null) {
                                return null;
                            } else {
                                // check len
                                if (last + 1 == kKeyLen) {
                                    return next.value;
                                }

                                i = i + last + 1;
                                transitions = next.getTransitions();
                                node = next;
                                partial = next.getPartialKey();
                            }
                        }
                    } else {
                        // last < partLen
                        System.out.println("here1");
                        return null;
                    }
                } else {
                    System.out.println("here2: " + node.partialKey);
                    // kKeyLen == min
                    // also kKeyLen > 0
                    return null;
                }
            }
        }
        System.out.println("here3");
        return null;
    }

    @Override
    @SuppressWarnings("Duplicates")
    public void put(CharSequence key, V value) {
        synchronized (putMutex) {
            if (headNode == null) {
                // first entry to tree
                headNode = new Node<>(key, value);
            } else {
                int keyLen = key.length();
                int i = 0; // char index for key

                // check start partial
                CharSequence startPart = headNode.partialKey;
                if (startPart != null) {
                    int last = 0;
                    int partLen = startPart.length();
                    int min = Math.min(partLen, keyLen);

                    while (last < min && startPart.charAt(last) == key.charAt(last)) {
                        last++;
                    }

                    if (partLen == min) {
                        if (last != min) {
                            // divide node
                            Node<V> nForOld = new Node<>(
                                    partLen == last + 1 ? null : startPart.subSequence(last + 1, partLen),
                                    headNode.value,
                                    headNode.transitions
                            );
                            Node<V> nForNew = new Node<>(
                                    keyLen == last + 1 ? null : key.subSequence(last + 1, keyLen),
                                    value
                            );
                            headNode.setUpdate(
                                    last == 0 ? null : startPart.subSequence(0, last),
                                    null,
                                    new HashMap<>(Map.of(
                                            startPart.charAt(last), nForOld,
                                            key.charAt(last), nForNew)
                                    )
                            );
                            return;
                        }
                        if (partLen == keyLen) {
                            // start node
                            headNode.setValue(value);
                            return;
                        } else {
                            // forward to the while loop
                            i = last;
                        }
                    } else {
                        // keyLen < partLen
                        // divide one
                        // transfer value
                        Map<Character, Node<V>> headNewTr = new HashMap<>();
                        Node<V> nForOld = new Node<>(
                                partLen == last + 1 ? null : startPart.subSequence(last + 1, partLen),
                                headNode.value,
                                headNode.transitions
                        );
                        if (last == 0) {
                            // divide one another for new
                            Node<V> nForNew = new Node<>(
                                    keyLen == 1 ? null : key.subSequence(1, keyLen),
                                    value
                            );
                            headNewTr.put(key.charAt(0), nForNew);
                        }
                        headNewTr.put(startPart.charAt(last), nForOld);
                        headNode.setUpdate(
                                last == 0 ? null : startPart.subSequence(0, last),
                                null,
                                headNewTr
                        );
                        return;
                    }
                }

                // check other nodes
                Node<V> node;

                Map<Character, Node<V>> transitions = headNode.transitions;
                while (i < keyLen) {
                    node = transitions.get(key.charAt(i));
                    if (node == null) {
                        // add new transition
                        headNode.transitionAdd(key.charAt(i), new Node<>(
                                i + 1 == keyLen ? null : key.subSequence(i + 1, keyLen),
                                value
                        ));
                        break;
                    } else {
                        CharSequence kKey = key.subSequence(i, keyLen);
                        int kKeyLen = kKey.length();
                        CharSequence partial = node.partialKey;

                        if (partial != null) {
                            int partLen = partial.length();
                            int last = 0;
                            int min = Math.min(kKeyLen, partLen);
                            do {
                                last++;
                            }
                            while (last < min && kKey.charAt(last) == partial.charAt(last));
                            boolean equalLen = kKeyLen == partLen;

                            if (partLen == min) {
                                if (last == partLen) {
                                    if (!equalLen) {
                                        // kKeyLen > partLen
                                        // whole partial key remains
                                        Map<Character, Node<V>> nodeTr = node.transitions;
                                        if (nodeTr.isEmpty()) {
                                            if (node.value == null) {
                                                // append to partial key
                                                node.setPartialKey(
                                                        String.valueOf(partial)
                                                                + kKey.subSequence(1, kKeyLen));
                                            } else {
                                                node.transitionAdd(
                                                        kKey.charAt(last + 1),
                                                        new Node<>(
                                                                last + 2 == kKeyLen ? null : kKey.subSequence(last + 2, kKeyLen),
                                                                value
                                                        )
                                                );
                                            }
                                            break;
                                        } else {
                                            // not append to partial key, create new transition instead
                                            // or, continue with the existing transition
                                            char toAdd = kKey.charAt(last);
                                            Node<V> existing = nodeTr.get(toAdd);
                                            if (existing == null) {
                                                // add new node to transitions
                                                node.transitionAdd(toAdd, new Node<>(
                                                        kKey.subSequence(last + 1, kKeyLen),
                                                        value
                                                ));
                                                break;
                                            } else {
                                                if (kKeyLen == last + 1) {
                                                    // get node and change its value
                                                    existing.setValue(value);
                                                    break;
                                                } else {
                                                    // continue with the existing transition
                                                    transitions = nodeTr;
                                                    i = last;
                                                }
                                            }
                                        }
                                    } else {
                                        // kKeyLen == partLen
                                        // this node takes given value
                                        node.setValue(value);
                                        break;
                                    }
                                } else {
                                    // last < partLen
                                    // change partial key of the node
                                    CharSequence partLast = partial.subSequence(last, partLen);
                                    Map<Character, Node<V>> nodeTr = node.transitions;

                                    if (nodeTr.isEmpty()) {
                                        // transferring node's value
                                        // TODO: write update method in node
                                        node.transitionAdd(partLast.charAt(0),
                                                new Node<>(
                                                        partLast.length() == 1 ? null : partLast.subSequence(1, partLast.length()),
                                                        node.value)
                                        );
                                        node.setValue(null);
                                    } else {
                                        // creating two nodes
                                        // transferring node's value
                                        // transferring transitions
                                        CharSequence kKeyLast = kKey.subSequence(last, kKeyLen);
                                        CharSequence forOldPartial = partLast.length() == 1
                                                ? null
                                                : partLast.subSequence(1, partLast.length());
                                        Node<V> nForOld = new Node<>(forOldPartial, node.value);
                                        Node<V> nForNew = new Node<>(
                                                kKeyLast.length() == 1 ? null : kKeyLast.subSequence(1, kKeyLast.length()),
                                                null
                                        );
                                        // TODO: collide maybe
                                        nForOld.transitions = nodeTr;
                                        node.setUpdate(
                                                partial.subSequence(0, last),
                                                null,
                                                new HashMap<>(Map.of(
                                                        partLast.charAt(0), nForOld,
                                                        kKeyLast.charAt(0), nForNew)
                                                )
                                        );
                                    }
                                    break;
                                }
                            } else {
                                // partLen > min
                                // kKeyLen == min
                                // divide partLen always
                                Map<Character, Node<V>> nodeTr = node.transitions;
                                CharSequence firstPart = partial.subSequence(0, last);
                                CharSequence secondPart = partial.subSequence(last + 1, partLen);

                                Node<V> newNode = new Node<>(
                                        secondPart.isEmpty() ? null : secondPart,
                                        node.value,
                                        nodeTr
                                );

                                // TODO: add partial key colliding if necessary

                                node.setUpdate(
                                        firstPart.isEmpty() ? null : firstPart,
                                        null,
                                        new HashMap<>(Map.of(partial.charAt(last), newNode))
                                );
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void remove(CharSequence key) {

    }

    @Override
    public void keys(CharSequence[] array) {

    }

    @Override
    public void prefixKeys(CharSequence prefix, Set<String> set) {

    }

    @Override
    public void prefixValues(CharSequence prefix, Set<String> set) {

    }

    @Override
    public boolean contains(CharSequence key) {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<V> getValuesOn(CharSequence key) {
        return null;
    }

    @Override
    public Map.Entry<CharSequence, V> getLongestMatchedEntry(CharSequence key) {
        return null;
    }


    @SuppressWarnings("Duplicates")
    private void putHelper(CharSequence key, Node<V> startNode, V value) {
        int keyLen = key.length();
        int i = 0; // char index for key

        // check start partial
        CharSequence startPart = startNode.partialKey;
        if (startPart != null) {
            int last = 0;
            int partLen = startPart.length();
            int min = Math.min(partLen, keyLen);

            while (last < min && startPart.charAt(last) == key.charAt(last)) {
                last++;
            }

            if (partLen == min) {
                if (last != min) {
                    // divide node
                    Node<V> nForOld = new Node<>(
                            partLen == last + 1 ? null : startPart.subSequence(last + 1, partLen),
                            startNode.value,
                            startNode.transitions
                    );
                    Node<V> nForNew = new Node<>(
                            keyLen == last + 1 ? null : key.subSequence(last + 1, keyLen),
                            value
                    );
                    startNode.setUpdate(
                            last == 0 ? null : startPart.subSequence(0, last),
                            null,
                            new HashMap<>(Map.of(
                                    startPart.charAt(last), nForOld,
                                    key.charAt(last), nForNew)
                            )
                    );
                    return;
                }
                if (partLen == keyLen) {
                    // start node
                    startNode.setValue(value);
                    return;
                } else {
                    // forward to the while loop
                    i = last;
                }
            } else {
                // keyLen < partLen
                // divide one
                // transfer value
                Node<V> nForOld = new Node<>(
                        partLen == last + 1 ? null : startPart.subSequence(last + 1, partLen),
                        startNode.value,
                        startNode.transitions
                );
                startNode.setUpdate(
                        last == 0 ? null : startPart.subSequence(0, last),
                        value,
                        new HashMap<>(Map.of(
                                startPart.charAt(last), nForOld
                        ))
                );
                return;
            }
        }

        // check other nodes
        Node<V> node;

        Map<Character, Node<V>> transitions = startNode.transitions;
        while (i < keyLen) {
            node = transitions.get(key.charAt(i));
            if (node == null) {
                // add new transition
                startNode.transitionAdd(key.charAt(i), new Node<>(
                        i + 1 == keyLen ? null : key.subSequence(i + 1, keyLen),
                        value
                ));
                break;
            } else {
                CharSequence kKey = key.subSequence(i, keyLen);
                int kKeyLen = kKey.length();
                CharSequence partial = node.partialKey;

                if (partial != null) {
                    int partLen = partial.length();
                    int last = 0;
                    int min = Math.min(kKeyLen, partLen);
                    do {
                        last++;
                    }
                    while (last < min && kKey.charAt(last) == partial.charAt(last));
                    boolean equalLen = kKeyLen == partLen;

                    if (partLen == min) {
                        if (last == partLen) {
                            if (!equalLen) {
                                // kKeyLen > partLen
                                // whole partial key remains
                                Map<Character, Node<V>> nodeTr = node.transitions;
                                if (nodeTr.isEmpty()) {
                                    if (node.value == null) {
                                        // append to partial key
                                        node.setPartialKey(
                                                String.valueOf(partial)
                                                        + kKey.subSequence(1, kKeyLen));
                                    } else {
                                        node.transitionAdd(
                                                kKey.charAt(last + 1),
                                                new Node<>(
                                                        last + 2 == kKeyLen ? null : kKey.subSequence(last + 2, kKeyLen),
                                                        value
                                                )
                                        );
                                    }
                                    break;
                                } else {
                                    // not append to partial key, create new transition instead
                                    // or, continue with the existing transition
                                    char toAdd = kKey.charAt(last);
                                    Node<V> existing = nodeTr.get(toAdd);
                                    if (existing == null) {
                                        // add new node to transitions
                                        node.transitionAdd(toAdd, new Node<>(
                                                kKey.subSequence(last + 1, kKeyLen),
                                                value
                                        ));
                                        break;
                                    } else {
                                        if (kKeyLen == last + 1) {
                                            // get node and change its value
                                            existing.setValue(value);
                                            break;
                                        } else {
                                            // continue with the existing transition
                                            transitions = nodeTr;
                                            i = last;
                                        }
                                    }
                                }
                            } else {
                                // kKeyLen == partLen
                                // this node takes given value
                                node.setValue(value);
                                break;
                            }
                        } else {
                            // last < partLen
                            // change partial key of the node
                            CharSequence partLast = partial.subSequence(last, partLen);
                            Map<Character, Node<V>> nodeTr = node.transitions;

                            if (nodeTr.isEmpty()) {
                                // transferring node's value
                                // TODO: write update method in node
                                node.transitionAdd(partLast.charAt(0),
                                        new Node<>(
                                                partLast.length() == 1 ? null : partLast.subSequence(1, partLen),
                                                node.value)
                                );
                                node.setValue(null);
                            } else {
                                // creating two nodes
                                // transferring node's value
                                // transferring transitions
                                CharSequence kKeyLast = kKey.subSequence(last, keyLen);
                                CharSequence forOldPartial = partLast.length() == 1
                                        ? null
                                        : partLast.subSequence(1, partLen);
                                Node<V> nForOld = new Node<>(forOldPartial, node.value);
                                Node<V> nForNew = new Node<>(
                                        kKeyLast.length() == 1 ? null : kKeyLast.subSequence(1, kKeyLast.length()),
                                        null
                                );
                                // TODO: collide maybe
                                nForOld.transitions = nodeTr;
                                node.setUpdate(
                                        partial.subSequence(0, last),
                                        null,
                                        new HashMap<>(Map.of(
                                                partLast.charAt(0), nForOld,
                                                kKeyLast.charAt(0), nForNew)
                                        )
                                );
                            }
                            break;
                        }
                    } else {
                        // partLen > min
                        // kKeyLen == min
                        // divide partLen always
                        Map<Character, Node<V>> nodeTr = node.transitions;
                        CharSequence firstPart = partial.subSequence(0, last);
                        CharSequence secondPart = partial.subSequence(last + 1, partLen);

                        Node<V> newNode = new Node<>(
                                secondPart.isEmpty() ? null : secondPart,
                                node.value,
                                nodeTr
                        );

                        // TODO: add partial key colliding if necessary

                        node.setUpdate(
                                firstPart.isEmpty() ? null : firstPart,
                                null,
                                new HashMap<>(Map.of(partial.charAt(last), newNode))
                        );
                        break;
                    }
                }
            }
        }
    }



    private static class Node<V> {
        CharSequence partialKey;
        V value;
        Map<Character, Node<V>> transitions;

        Node(CharSequence partialKey, V value) {
            this.partialKey = partialKey;
            this.value = value;
            transitions = new HashMap<>();
        }

        Node(CharSequence partialKey, V value, Map<Character, Node<V>> transitions) {
            this.partialKey = partialKey;
            this.value = value;
            this.transitions = transitions;
        }

        synchronized void transitionAdd(Character ch, Node<V> next) {
            transitions.put(ch, next);
        }

        synchronized void transitionDelete(Character ch) {
            transitions.remove(ch);
        }

        Optional<Node<V>> shift(Character ch) {
            return Optional.ofNullable(transitions.get(ch));
        }

        synchronized void transitionsChange(Map<Character, Node<V>> other) {
            transitions = other;
        }

        synchronized Map<Character, Node<V>> getTransitions() {
            return transitions;
        }

        synchronized void setPartialKey(CharSequence partialKey) {
            this.partialKey = partialKey;
        }

        synchronized CharSequence getPartialKey() {
            return partialKey;
        }

        synchronized V getValue() {
            return value;
        }

        synchronized void setValue(V other) {
            this.value = other;
        }

        synchronized void setUpdate(CharSequence partialKey, V value, Map<Character, Node<V>> transitions) {
            this.partialKey = partialKey;
            this.value = value;
            this.transitions = transitions;
        }
    }
}
