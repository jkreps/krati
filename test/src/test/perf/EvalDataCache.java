package test.perf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import krati.cds.DataCache;
import krati.cds.DataCacheLoader;
import krati.cds.impl.DataCacheImpl;
import krati.cds.impl.DataCacheLoaderImpl;
import krati.zoie.impl.ZoieDataCache;
import krati.zoie.impl.ZoieInterpreter;

import org.apache.lucene.index.IndexReader;

import proj.zoie.impl.indexing.DefaultIndexReaderDecorator;
import proj.zoie.impl.indexing.ZoieSystem;

public class EvalDataCache
{
    static List<String> _lineSeedData = new ArrayList<String>(3000);
    
    public void loadSeedData(File dataFile) throws IOException
    {
        String line;
        FileReader reader = new FileReader(dataFile);
        BufferedReader in = new BufferedReader(reader);
        
        while((line = in.readLine()) != null)
        {
            _lineSeedData.add(line);
        }
        
        in.close();
        reader.close();
    }
    
    public void populate(DataCache cache) throws IOException
    {
        String line;
        int lineCnt = _lineSeedData.size();
        int index = cache.getIdStart();
        int stopIndex = index + cache.getIdCount();
        
        long scn = cache.getHWMark();
        long startTime = System.currentTimeMillis();
        
        while(index < stopIndex)
        {
            try
            {   line = _lineSeedData.get(index % lineCnt);
                cache.setData(index, line.getBytes(), scn++);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            index++;
        }
        
        cache.persist();

        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        System.out.printf("elapsedTime=%d ms (init)%n", elapsedTime);
        
        double rate = cache.getIdCount()/(double)elapsedTime;
        System.out.printf("writeCount=%d rate=%6.2f per ms%n", cache.getIdCount(), rate);
    }
    
    public static void checkData(DataCache cache, int index)
    {
        byte[] b = cache.getData(index);
        if (b != null)
        {
            String s = new String(b);
            System.out.printf("[%8d] %s%n", index, s);
        }
        else
        {
            System.out.printf("[%8d] %s%n", index, null);
        }
    }
    
    public static void checkData(DataCache cache)
    {
        checkData(cache, 0);
        checkData(cache, 10);
        checkData(cache, 100);
        checkData(cache, 1000);
        checkData(cache, 10000);
        checkData(cache, 100000);
        checkData(cache, 1000000);
        checkData(cache, 2000000);
        checkData(cache, 3000000);
        checkData(cache, 4000000);
    }
    
    static class Reader implements Runnable
    {
        DataCache _cache;
        Random _rand = new Random();
        byte[] _data = new byte[1 << 13];
        boolean _running = true;
        int _indexStart;
        int _length;
        long _cnt = 0;
        
        public Reader(DataCache cache)
        {
            this._cache = cache;
            this._length = cache.getIdCount();
            this._indexStart = cache.getIdStart();
        }
        
        public long getReadCount()
        {
            return this._cnt;
        }

        public void stop()
        {
            _running = false;
        }
        
        int read(int index)
        {
            return _cache.getData(index, _data);
        }
        
        @Override
        public void run()
        {
            while(_running)
            {
                read(_indexStart + _rand.nextInt(_length));
                _cnt++;
            }
        }
    }
    
    static class Writer implements Runnable
    {
        DataCache _cache;
        Random _rand = new Random();
        boolean _running = true;
        
        int _indexStart;
        int _length;
        long _cnt = 0;
        long _scn = 0;
        
        public Writer(DataCache cache)
        {
            this._cache = cache;
            this._length = cache.getIdCount();
            this._indexStart = cache.getIdStart();
            this._scn = cache.getHWMark();
        }
        
        public long getWriteCount()
        {
            return this._cnt;
        }

        public void stop()
        {
            _running = false;
        }
        
        void write(int index)
        {
            try
            {
                byte[] b = _lineSeedData.get(index%_lineSeedData.size()).getBytes();
                _cache.setData(index, b, _scn++);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        
        @Override
        public void run()
        {
            while(_running)
            {
                write(_indexStart + _rand.nextInt(_length));
                _cnt++;
            }
        }
    }
    
    public static double testWrite(DataCache cache, int runTimeSeconds) throws Exception
    {
        try
        {
            // Start writer
            Writer writer = new Writer(cache);
            Thread writerThread = new Thread(writer);
            writerThread.start();
            System.out.println("Writer started");
            
            long startTime = System.currentTimeMillis();
            long writeCount = 0;
            int heartBeats = runTimeSeconds/10;
            for(int i = 0; i < heartBeats; i++)
            {
                Thread.sleep(10000);
                long newWriteCount = writer.getWriteCount();
                System.out.printf("writeCount=%d%n", newWriteCount - writeCount);
                writeCount = newWriteCount;
            }
            
            writer.stop();
            writerThread.join();
            
            long endTime = System.currentTimeMillis();

            long elapsedTime = endTime - startTime;
            System.out.printf("elapsedTime=%d ms%n", elapsedTime);
            
            double rate = writer.getWriteCount()/(double)elapsedTime;
            System.out.printf("writeCount=%d rate=%6.2f per ms%n", writer.getWriteCount(), rate);
            
            return rate;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }
    
    public static void testRead(DataCache cache, int readerCnt, int runTimeSeconds) throws Exception
    {
        try
        {
            // Start readers
            Reader[] readers = new Reader[readerCnt];
            for(int i = 0; i < readers.length; i++)
            {
                readers[i] = new Reader(cache);
            }
            
            Thread[] threads = new Thread[readers.length];
            for(int i = 0; i < threads.length; i++)
            {
                threads[i] = new Thread(readers[i]);
                threads[i].start();
                System.out.println("Reader " + i + " started");
            }
            
            long startTime = System.currentTimeMillis();
            
            // Sleep until run time is exhausted
            Thread.sleep(runTimeSeconds * 1000);
            
            for(int i = 0; i < readers.length; i++)
            {
                readers[i].stop();
            }
            for(int i = 0; i < threads.length; i++)
            {
                threads[i].join();
            }
            
            long endTime = System.currentTimeMillis();
            
            double sumReadRate = 0;
            long elapsedTime = endTime - startTime;
            System.out.printf("elapsedTime=%d ms%n", elapsedTime);
            for(int i = 0; i < readers.length; i++)
            {
                double rate = readers[i].getReadCount()/(double)elapsedTime;
                System.out.printf("readCount[%d]=%d rate=%6.2f per ms%n", i, readers[i].getReadCount(), rate);
                sumReadRate += rate;
            }
            
            System.out.printf("Total Read Rate=%6.2f per ms%n", sumReadRate);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }
    
    public static void testReadWrite(DataCache cache, int readerCnt, int runTimeSeconds) throws Exception
    {
        try
        {
            // Start readers
            Reader[] readers = new Reader[readerCnt];
            for(int i = 0; i < readers.length; i++)
            {
                readers[i] = new Reader(cache);
            }

            Thread[] threads = new Thread[readers.length];
            for(int i = 0; i < threads.length; i++)
            {
                threads[i] = new Thread(readers[i]);
                threads[i].start();
                System.out.println("Reader " + i + " started");
            }
            
            // Start writer
            Writer writer = new Writer(cache);
            Thread writerThread = new Thread(writer);
            writerThread.start();
            System.out.println("Writer started");
            
            long startTime = System.currentTimeMillis();
            
            long readCount = 0;
            long writeCount = 0;
            int heartBeats = runTimeSeconds/10;
            for(int i = 0; i < heartBeats; i++)
            {
                Thread.sleep(10000);

                int newReadCount = 0;
                for(int r = 0; r < readers.length; r++)
                {
                    newReadCount += readers[r].getReadCount();
                }
                
                long newWriteCount = writer.getWriteCount();
                
                System.out.printf("write=%d read=%d%n", newWriteCount-writeCount, newReadCount-readCount);
                
                readCount = newReadCount;
                writeCount = newWriteCount;
            }
            
            // Stop reader
            for(int i = 0; i < readers.length; i++)
            {
                readers[i].stop();
            }
            for(int i = 0; i < threads.length; i++)
            {
                threads[i].join();
            }
            
            // Stop writer
            writer.stop();
            writerThread.join();
            
            long endTime = System.currentTimeMillis();

            long elapsedTime = endTime - startTime;
            System.out.printf("elapsedTime=%d ms%n", elapsedTime);
            
            double rate = writer.getWriteCount()/(double)elapsedTime;
            System.out.printf("writeCount=%d rate=%6.2f per ms%n", writer.getWriteCount(), rate);
            
            double sumReadRate = 0;
            for(int i = 0; i < readers.length; i++)
            {
                rate = readers[i].getReadCount()/(double)elapsedTime;
                System.out.printf("readCount[%d]=%d rate=%6.2f per ms%n", i, readers[i].getReadCount(), rate);
                sumReadRate += rate;
            }
            
            System.out.printf("Total Read Rate=%6.2f per ms%n", sumReadRate);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }
    
    static DataCache getKratiDataCache(File cacheDir) throws Exception
    {
    	int idStart = 0;
        int idCount = 500000;
        int segFileSizeMB = 256;
        
        DataCache cache = new DataCacheImpl(idStart,
                                            idCount,
                                            cacheDir,
                                            new krati.cds.impl.segment.MemorySegmentFactory(),
                                            segFileSizeMB);
        return cache;
    }
    
    @SuppressWarnings("unchecked")
    static DataCache getZoieDataCacheZoie(File cacheDir) throws Exception
    {
        int idStart = 0;
        int idCount = 500000;
        
    	ZoieSystem zs = new ZoieSystem(cacheDir,
    	                               new ZoieInterpreter(),
    	                               new DefaultIndexReaderDecorator(),
    	                               null, null, 10000, 60000, true);
    	zs.start();
    	
    	DataCache cache = new ZoieDataCache<IndexReader>(zs, idStart, idCount);
        return cache;
    }
    
    public static void main(String[] args)
    {
        EvalDataCache eval = new EvalDataCache();
        
        try
        {
            File seedDataFile = new File("test/seed/seed.dat");
            eval.loadSeedData(seedDataFile);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return;
        }
        
        try
        {
            DataCache cache;
            DataCacheLoader cacheLoader = new DataCacheLoaderImpl();
            
            File cacheDir = new File("test/cache");
            
            cache = getKratiDataCache(cacheDir);
            //cache = getZoieDataCache(cacheDir);
            
            int runTimeSeconds = 60;
            File initFile = new File("test/cache.init.dat");
            File dumpFile = new File("test/cache.dump.dat");
            
            System.out.println("---checkData---");
            checkData(cache);
            
            if (cache.getLWMark() == 0)
            {
                System.out.println("---populate---");
                eval.populate(cache);
                
                System.out.println("---dump---");
                cacheLoader.dump(cache, initFile);
                
                System.out.println("---checkData---");
                checkData(cache);
            }
            
            System.out.println("---testWrite---");
            testWrite(cache, runTimeSeconds);
            cache.persist();
            
            System.out.println("---checkData---");
            checkData(cache);
            
            System.out.println("---testRead---");
            testRead(cache, 4, runTimeSeconds);
            
            System.out.println("---checkData---");
            checkData(cache);
            
            System.out.println("---testReadWrite---");
            testReadWrite(cache, 4, runTimeSeconds);
            cache.persist();
            
            System.out.println("---checkData---");
            checkData(cache);
            
            System.out.println("---dump---");
            cacheLoader.dump(cache, dumpFile);
            
            System.out.println("done");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
