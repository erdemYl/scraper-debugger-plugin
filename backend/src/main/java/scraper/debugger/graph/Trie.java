package scraper.debugger.graph;

import java.util.*;

/**
 * Implementation of a trie a.k.a. radix tree.
 *
 * Primarily used for storing flows efficiently within a tree structure.
 * Since debugger generates for each flow a unique IDENT (string) that is related to
 * its parent IDENT, storing these IDENTs generates a radix tree by itself.
 *
 * Whole implementation belongs to me, which I have implemented in "Programmierpraktikum 2021" in TU KL.
 * Other data retrieval methods are added like "getValuesOn", "getDirectChildValuesOf", "getLongestMatchedEntry"
 * to get a flow's lifecycle, that is IDENT_1,...,IDENT_n, where IDENT_i are the keys of its parents.
 *
 * @param <V> is the type of the stored values.
 */
public class Trie<V> implements MapI<String, V> {
    private final TrieNode headNode;
    private final Set<String> keys;
    private int size;

    /* Whether keys will be stored explicitly in a set or not. */
    private final boolean storeKeys;

    private final Object mutex = new Object();


    public Trie() {
        headNode = new TrieNode(new HashMap<>());
        keys = null;
        storeKeys = false;
    }

    public Trie(boolean storeKeys) {
        headNode = new TrieNode(new HashMap<>());
        keys = storeKeys ? new HashSet<>() : null;
        size = 0;
        this.storeKeys = storeKeys;
    }

    @Override
    public V get(String key) {
        synchronized (mutex) {
            // first check the size
            if (size() == 0 || key == null) {
                return null;
            }

            // convert string to chars
            char[] chars = key.toCharArray();

            // start from beginning state
            Node<V> node = headNode;

            // give chars to automaton
            int i = 0;
            try {
                // shifting chars one by one, changing states
                while (i < chars.length) {
                    node = node.shift(chars[i]);
                    i++;
                }
                //return value of the end state, if automaton stops in an end state
                return node.isEndNode()
                        ? ((EndNode) node).getValue()
                        : null;

            } catch (NoStateFound e) {
                return null;
            }
        }
    }

    @Override
    public void put(String key, V value) {
        synchronized (mutex) {
            if (key.isBlank()) {
                // do nothing
                return;
            }

            if (storeKeys) keys.add(key);
            size++;

            char[] chars = key.toCharArray();
            int length = chars.length;

            // begin with start state
            Node<V> n = headNode;
            Node<V> prevNode;
            int i = 0;
            try {
                // shifting can throw NoStateFound exception
                while (i < length - 1) {
                    n = n.shift(chars[i]);
                    i++;
                }
                prevNode = n;
                n = n.shift(chars[i]);

                if (n.isEndNode()) {
                    // change value of the node
                    ((EndNode) n).setValue(value);
                } else {
                    // change TrieNode to EndNode and store value
                    n = ((TrieNode) n).toEndNode(value);
                    // then add new transition to previous node
                    prevNode.transitionAdd(chars[i], n);
                }
            } catch (NoStateFound e) {
                while (i < length - 1) {
                    Node<V> node = new TrieNode(new HashMap<>());
                    n.transitionAdd(chars[i], node);
                    n = node;
                    i++;
                }
                EndNode end = new EndNode(value, new HashMap<>());
                n.transitionAdd(chars[i], end);
            }
        }
    }

    @Override
    public void remove(String key) {
        synchronized (mutex) {
            char[] input = key.toCharArray();
            int i = 0;
            // set start state
            Node<V> node = headNode;
            Node<V> prevNode;

            // put input to automaton
            try {
                // begin shifting the input chars
                while (i < input.length - 1) {
                    // throws exception, when shifting is not possible
                    node = node.shift(input[i]);
                    i++;
                }
                prevNode = node;
                node = node.shift(input[i]);

                // check, if the automaton has reached an end state
                if (node.isEndNode()) {
                    if (storeKeys) {
                        assert keys != null;
                        keys.remove(key);
                    }
                    size--;

                    if (node.getTransitions().isEmpty()) {
                        // remove node completely
                        prevNode.transitionDelete(input[i]);
                    } else {
                        // convert end node to normal trie node and
                        // change the transition in previous state
                        prevNode.transitionAdd(
                                input[i],
                                ((EndNode) node).toTrieNode()
                        );
                    }
                }
            } catch (NoStateFound e) {
                // do nothing, since the automaton does not accept given string.
            }
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void keys(String[] array) {
        synchronized (mutex) {
            if (array == null || array.length < size) {
                throw new IllegalArgumentException();
            } else {
                if (!storeKeys) {
                    Set<String> keys = gatherKeysFor("");
                    int i = 0;
                    for (String k : keys) {
                        array[i] = k;
                        i++;
                    }
                    return;
                }

                int i = 0;
                int length = array.length;
                assert keys != null;
                Iterator<String> iter = keys.iterator();

                while (i < length && iter.hasNext()) {
                    array[i] = iter.next();
                    i++;
                }
            }
        }
    }

    /**
     * Fills given set with keys, which have
     * as prefix the given prefix.
     *
     * TODO: implement a more efficient algorithm
     */
    public void prefixKeys(String prefix, Set<String> set) {
        synchronized (mutex) {
            if (set != null) {
                if (!storeKeys) {
                    set.addAll(gatherKeysFor(prefix));
                    return;
                }

                // create a new automaton
                Trie<Boolean> automaton = new Trie<>();

                // automaton only accepts strings with given prefix
                automaton.put(prefix, true);

                // now put all keys one by one to automaton
                assert keys != null;
                for (String examinedKey : keys) {
                    char[] keyChars = examinedKey.toCharArray();

                    // starting with start state
                    Node<V> node = (Node<V>) automaton.headNode;
                    try {
                        // shifting chars one by one
                        for (char c : keyChars) {
                            node = node.shift(c);

                            // check if the end state is reached
                            if (node.isEndNode()) {
                                set.add(examinedKey);
                                break;
                            }
                        }
                    } catch (NoStateFound e) {
                        // do nothing, since the examined key
                        // hasn't got the wanted prefix.
                    }
                }
            }
        }
    }

    public void prefixValues(String prefix, Set<V> set) {
        synchronized (mutex) {
            if (set != null) {
                if (!storeKeys) {
                    set.addAll(gatherValuesFor(prefix));
                    return;
                }

                // create a new automaton
                Trie<Boolean> automaton = new Trie<>();

                // automaton only accepts strings with given prefix
                automaton.put(prefix, true);

                // now put all keys one by one to automaton

                assert keys != null;
                for (String key : keys) {
                    char[] keyChars = key.toCharArray();

                    // starting with start state
                    Node<V> node = (Node<V>) automaton.headNode;
                    try {
                        // shifting chars one by one
                        for (char c : keyChars) {
                            node = node.shift(c);

                            // check if the end state is reached
                            if (node.isEndNode()) {
                                set.add(((EndNode) node).value);
                                break;
                            }
                        }
                    } catch (NoStateFound e) {
                        // do nothing, since the examined key
                        // hasn't got the wanted prefix.
                    }
                }
            }
        }
    }


    /**
     * Gets all values which are saved on the way of given key.
     * If key is not available or null, returns empty list.
     */
    public List<V> getValuesOn(String key) {
        synchronized (mutex) {
            if (key == null || key.isEmpty()) return List.of();
            List<V> values = new LinkedList<>();

            char[] input = key.toCharArray();
            Node<V> n = headNode;
            for (char c : input) {
                try {
                    n = n.shift(c);
                    if (n.isEndNode()) {
                        values.add(((EndNode) n).getValue());
                    }
                } catch (NoStateFound e) {
                    // given key is not available
                    return List.of();
                }
            }
            return values.stream().toList();
        }
    }

    @SuppressWarnings("Duplicates")
    public List<String> getDirectChildKeysOf(String key) {
        synchronized (mutex) {
            List<String> children = new LinkedList<>();
            if (key == null) return children;

            char[] input = key.toCharArray();
            Node<V> n = headNode;
            for (char c : input) {
                try {
                    n = n.shift(c);
                } catch (NoStateFound e) {
                    return List.of();
                }
            }
            if (n.isEndNode()) {
                n.getTransitions().forEach((ch, node) -> {
                    if (node.isEndNode()) children.add(String.valueOf(ch));
                    else {
                        findDirectEndNodesPOSTFIX(node).forEach(str -> {
                            children.add(ch + str);
                        });
                    }
                });
                return children.stream().toList();
            }
            return List.of();
        }
    }

    @SuppressWarnings("Duplicates")
    public List<V> getDirectChildValuesOf(String key) {
        synchronized (mutex) {
            List<V> children = new ArrayList<>();
            if (key == null) return children;

            char[] input = key.toCharArray();
            Node<V> n = headNode;
            for (char c : input) {
                try {
                    n = n.shift(c);
                } catch (NoStateFound e) {
                    return List.of();
                }
            }
            if (n.isEndNode()) {
                n.getTransitions().forEach((ch, node) -> {
                    if (node.isEndNode()) children.add(((EndNode) node).value);
                    else {
                        children.addAll(findDirectEndNodesVALUE(node));
                    }
                });
                return children.stream().toList();
            }
            return List.of();
        }
    }

    public Map.Entry<String, V> getLongestMatchedEntry(String key) {
        synchronized (mutex) {
            if (key == null) return null;

            char[] input = key.toCharArray();
            Node<V> n = headNode;
            EndNode longest = null;
            int last = 0;
            for (int i = 0; i < input.length; i++) {
                try {
                    n = n.shift(input[i]);
                    if (n.isEndNode()) {
                        longest = (EndNode) n;
                        last = i;
                    }
                } catch (NoStateFound e) {
                    break;
                }
            }

            if (longest == null) return null;
            else {
                return new AbstractMap.SimpleImmutableEntry<>(
                        Arrays.toString(Arrays.copyOf(input, last + 1)),
                        longest.getValue());
            }
        }
    }


    public boolean isEmpty() {
        synchronized (mutex) {
            return size == 0;
        }
    }


    public boolean containsKey(String key) {
        return get(key) != null;
    }


    private List<String> findDirectEndNodesPOSTFIX(Node<V> n) {
        List<String> ends = new LinkedList<>();
        n.getTransitions().forEach((ch, node) -> {
            if (node.isEndNode()) ends.add(String.valueOf(ch));
            else {
                findDirectEndNodesPOSTFIX(node).forEach(str -> {
                    ends.add(ch + str);
                });
            }
        });
        return ends.stream().toList();
    }

    private List<V> findDirectEndNodesVALUE(Node<V> n) {
        List<V> ends = new ArrayList<>();
        n.getTransitions().forEach((ch, node) -> {
            if (node.isEndNode()) ends.add(((EndNode) node).value);
            else ends.addAll(findDirectEndNodesVALUE(node));
        });
        return ends.stream().toList();
    }

    @SuppressWarnings("Duplicates")
    private Set<String> gatherKeysFor(String prefix) {
        Node<V> start = headNode;

        if (!prefix.isEmpty()) {
            try {
                char[] chars = prefix.toCharArray();
                for (char c : chars) {
                    start = start.shift(c);
                }
            } catch (NoStateFound e) {
                return new HashSet<>();
            }
        }

        return gatherKeysHelper(prefix, start);
    }

    @SuppressWarnings("Duplicates")
    private Set<V> gatherValuesFor(String prefix) {
        Node<V> start = headNode;

        if (!prefix.isEmpty()) {
            try {
                char[] chars = prefix.toCharArray();
                for (char c : chars) {
                    start = start.shift(c);
                }
            } catch (NoStateFound e) {
                return new HashSet<>();
            }
        }

        return gatherValuesHelper(prefix, start);
    }

    private Set<String> gatherKeysHelper(String prefix, Node<V> start) {
        Set<String> set = new HashSet<>();
        if (start.isEndNode()) {
            set.add(prefix);
        }
        start.getTransitions().forEach((ch, n) -> set.addAll(gatherKeysHelper(prefix + ch, n)));
        return set;
    }

    private Set<V> gatherValuesHelper(String prefix, Node<V> start) {
        Set<V> set = new HashSet<>();
        if (start.isEndNode()) {
            set.add(((EndNode) start).value);
        }
        start.getTransitions().forEach((ch, n) -> set.addAll(gatherValuesHelper(prefix + ch, n)));
        return set;
    }




    // ---------------------------
    //          Utils
    // ---------------------------


    /**
     * Interface for TrieMap nodes.
     * <p>
     * There are two kinds of nodes: EndNode and TrieNode.
     * The fist one stores the value, while the other
     * is not.
     *
     * @param <V> is a generic type.
     */
    private interface Node<V> {
        boolean isEndNode();

        void transitionAdd(Character ch, Node<V> next);

        void transitionDelete(Character ch);

        Map<Character, Node<V>> getTransitions();

        Node<V> shift(Character ch) throws NoStateFound;
    }


    /**
     * Represents the end nodes in TrieMap.
     */
    private final class EndNode implements Node<V> {
        private V value;

        /**
         * A map of character transitions from this node.
         */
        private final Map<Character, Node<V>> transitions;

        public EndNode(V value, Map<Character, Node<V>> transitions) {
            this.value = value;
            this.transitions = transitions;
        }

        @Override
        public boolean isEndNode() {
            return true;
        }

        @Override
        public void transitionAdd(Character ch, Node<V> next) {
            transitions.put(ch, next);
        }

        @Override
        public void transitionDelete(Character ch) {
            transitions.remove(ch);
        }

        @Override
        public Map<Character, Node<V>> getTransitions() {
            return transitions;
        }

        @Override
        public Node<V> shift(Character ch) throws NoStateFound {
            Node<V> nextState = transitions.get(ch);
            if (nextState != null) {
                return nextState;
            } else {
                throw new NoStateFound();
            }
        }

        /**
         * Converts this EndNode object to TrieNode.
         *
         * @return a new TrieNode.
         */
        TrieNode toTrieNode() {
            return new TrieNode(transitions);
        }

        V getValue() {
            return value;
        }

        void setValue(V value) {
            this.value = value;
        }
    }


    /**
     * Represents nodes except end nodes in TrieMap.
     */
    private final class TrieNode implements Node<V> {

        /**
         * Map of character transitions from this node to other nodes.
         */
        private final Map<Character, Node<V>> transitions;

        /**
         * Constructing a node with its transitions.
         *
         * @param transitions is given.
         */
        TrieNode(Map<Character, Node<V>> transitions) {
            this.transitions = transitions;
        }

        @Override
        public boolean isEndNode() {
            return false;
        }

        @Override
        public void transitionAdd(Character ch, Node<V> next) {
            transitions.put(ch, next);
        }

        @Override
        public void transitionDelete(Character ch) {
            transitions.remove(ch);
        }

        @Override
        public Map<Character, Node<V>> getTransitions() {
            return transitions;
        }

        @Override
        public Node<V> shift(Character ch) throws NoStateFound {
            Node<V> nextState = transitions.get(ch);
            if (nextState != null) {
                return nextState;
            } else {
                throw new NoStateFound();
            }
        }

        /**
         * Converts this TrieNode object to EndNode.
         *
         * @param value is the given value to store with key.
         * @return a new EndNode.
         */
        EndNode toEndNode(V value) {
            return new EndNode(value, this.transitions);
        }
    }
}

class NoStateFound extends Exception {
}

interface MapI<K, V> {
    V get(K key);
    void put(K key, V value);
    void remove(K key);
    int size();
    void keys(K[] array);
}