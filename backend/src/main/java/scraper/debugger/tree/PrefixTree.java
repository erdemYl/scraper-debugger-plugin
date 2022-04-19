package scraper.debugger.tree;

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public interface PrefixTree<V> {

    V get(CharSequence key);


    void put(CharSequence key, V value);


    void remove(CharSequence key);


    void keys(CharSequence[] array);


    void prefixKeys(CharSequence prefix, Set<String> set);


    void prefixValues(CharSequence prefix, Set<String> set);


    boolean contains(CharSequence key);


    boolean isEmpty();


    List<V> getValuesOn(CharSequence key);


    Entry<CharSequence, V> getLongestMatchedEntry(CharSequence key);
}
