package scraper.debugger.tree;

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public interface PrefixTree<V> {

    /* Gets the value associated with this key */
    V get(String key);


    /* Maps given key with given value */
    void put(String key, V value);


    /* Removes the value associated with given key */
    void remove(String key);


    /* Saves all keys to given array, array not null. */
    void keys(String[] array);


    /* Saves all keys with given prefix to given set, set not null. */
    void prefixKeys(String prefix, Set<String> set);


    /* Saves all values, whose prefix starts with given prefix, to given set, set not null. */
    void prefixValues(String prefix, Set<V> set);


    /* Checks, if there is an associated value for given key. */
    boolean contains(String key);


    boolean isEmpty();


    /* Returns the list of values that associated with all prefixes of given key. */
    List<V> getValuesOn(String key);


    /* Returns the longest prefix of given key that is contained, and its value. If no match, null. */
    Entry<String, V> getLongestMatchedEntry(String key);


    int size();
}
