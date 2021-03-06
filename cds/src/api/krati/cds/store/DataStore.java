package krati.cds.store;

import java.io.IOException;

/**
 * Key Value Store.
 * 
 * @author jwu
 *
 * @param <K> key
 * @param <V> value
 */
public interface DataStore<K, V>
{
    public V get(K key);
    
    public boolean put(K key, V value) throws Exception;
    
    public boolean delete(K key) throws Exception;
    
    public long hash(K key);
    
    public void persist() throws IOException;
}
