package krati.cds.impl.segment;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.Logger;

/**
 * SegmentManager
 * 
 * <pre>
 *    SegmentManager manager = new SegmentManager(...);
 *    Segment segment = manager.bootstrap();
 *    
 *    while(...)
 *    {
 *        try
 *        {
 *            segment.append(...);
 *        }
 *        catch(SegmentOverflowException e)
 *        {
 *           segment.force();
 *           manager.updateMeta();
 *           segment = manger.nextSegment();
 *        }
 *    }
 *    
 * </pre>
 * 
 * @author jwu
 *
 */
public final class SegmentManager implements Cloneable
{
    private final static Logger _log = Logger.getLogger(SegmentManager.class);
    private final static Map<String, SegmentManager> _segManagerMap = new HashMap<String, SegmentManager>();
    
    private final List<Segment> _segList = new ArrayList<Segment>(100);
    private final SegmentFactory _segFactory;
    private final SegmentMeta _segMeta;
    private final String _segHomePath;
    private final int _segFileSizeMB;
    
    private Segment _segCurrent;
    
    private SegmentManager(String segmentHomePath) throws IOException
    {
        this(segmentHomePath, new MappedSegmentFactory());
    }
    
    private SegmentManager(String segmentHomePath, SegmentFactory segmentFactory) throws IOException
    {
        this(segmentHomePath, segmentFactory, Segment.defaultSegmentFileSizeMB);
    }
    
    private SegmentManager(String segmentHomePath, SegmentFactory segmentFactory, int segmentFileSizeMB) throws IOException
    {
        _log.info("init segHomePath=" + segmentHomePath + " segFileSizeMB=" + segmentFileSizeMB);
        
        this._segFactory = segmentFactory;
        this._segHomePath = segmentHomePath;
        this._segFileSizeMB = segmentFileSizeMB;
        this._segMeta = new SegmentMeta(new File(_segHomePath, ".meta"));
        this.init();
    }
    
    /**
     * Only used for cloning this SegmentManager.
     * 
     * @param cloneTarget
     */
    private SegmentManager(SegmentManager cloneTarget)
    {
        this._segFactory = cloneTarget.getSegmentFactory();
        this._segHomePath = cloneTarget.getSegmentHomePath();
        this._segFileSizeMB = cloneTarget.getSegmentFileSizeMB();
        this._segMeta = cloneTarget._segMeta;
        this._segCurrent = cloneTarget._segCurrent;
        this._segList.addAll(cloneTarget._segList);
    }
    
    public int getSegmentFileSizeMB()
    {
        return _segFileSizeMB;
    }
    
    public String getSegmentHomePath()
    {
        return _segHomePath;
    }
    
    public SegmentFactory getSegmentFactory()
    {
        return _segFactory;
    }
    
    public Segment getSegment(int index)
    {
        return _segList.get(index);
    }
    
    public int getSegmentCount()
    {
        return _segList.size();
    }
    
    public int getLiveSegmentCount()
    {
        int num = 0;
        
        for(int i = 0; i < _segList.size(); i++)
        {
            if(_segList.get(i) != null) num++;
        }
        
        return num;
    }
    
    public synchronized void clear()
    {
        _segList.clear();
    }
    
    /**
     * Frees a segment.
     */
    public synchronized boolean freeSegment(Segment seg) throws IOException
    {
        if(seg == null) return false;
        
        int segId = seg.getSegmentId();
        if (segId < _segList.size() && _segList.get(segId) == seg)
        {
            _segList.set(segId, null);
            seg.close(false);
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets the next segment available for read and write.
     */
    public synchronized Segment nextSegment(Segment seg) throws IOException
    {
        if(seg != _segCurrent)
        {
            return (_segCurrent != null) ? _segCurrent : nextSegment(); 
        }
        
        return nextSegment();
    }
    
    /**
     * Gets the next segment available for read and write.
     */
    public synchronized Segment nextSegment() throws IOException
    {
        _segCurrent = nextSegment(false);
        return _segCurrent;
    }
    
    /**
     * Gets the next segment available for read and write.
     * 
     * @param newOnly  If true, create a new segment from scratch.
     *                 Otherwise, reuse the first free segment.
     * @return 
     * @throws IOException
     */
    private synchronized Segment nextSegment(boolean newOnly) throws IOException
    {
        int index;
        
        if (newOnly)
        {
            index = _segList.size();
        }
        else
        {
            for(index = 0; index < _segList.size(); index++)
            {
                if(_segList.get(index) == null) break;
            }
        }
        
        // Always create next segment as READ_WRITE
        File segFile = new File(_segHomePath, index + ".seg");
        Segment s = getSegmentFactory().createSegment(index, segFile, _segFileSizeMB, Segment.Mode.READ_WRITE);
        
        if(index < _segList.size()) _segList.set(index, s);
        else _segList.add(s);
        
        _log.info("Segment " + s.getSegmentId() + ": " + segFile.getCanonicalPath());
        
        return s;
    }
    
    protected synchronized void init() throws IOException
    {
        File[] segFiles = listSegmentFiles();
        if(segFiles.length == 0) {
            return;
        }
        
        try
        {
            for(int i = 0; i < segFiles.length; i++)
            {
                File segFile = segFiles[i];
                int segId = Integer.parseInt(segFile.getName().substring(0, segFile.getName().indexOf('.')));
                if(segId != i)
                {
                    throw new IOException("Segment file " + i + ".seg missing");
                }
                
                if (getMeta().hasSegmentInService(segId))
                {
                    // Always load a live segment as READ_ONLY
                    Segment s = getSegmentFactory().createSegment(segId, segFile, _segFileSizeMB, Segment.Mode.READ_ONLY);
                    s.incrLoadSize(getMeta().getSegmentLoadSize(segId));
                    _segList.add(s);
                }
                else
                {
                    // Segment is not live and is free for reuse
                    _segList.add(null);
                }
            }
        }
        catch(IOException e)
        {
            _log.error(e.getMessage());
            
            clear();
            throw e;
        }
        
        // TODO
        // Validate that all live segments from meta are loaded
        
        _log.info("init done");
    }
    
    protected File[] listSegmentFiles()
    {
        File segDir = new File(_segHomePath);
        File[] segFiles = segDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File filePath)
            {
                String fileName = filePath.getName();
                if(fileName.matches("^[0-9]+\\.seg$")) {
                    return true;
                }
                return false;
            }
        });
        
        if (segFiles == null)
        {
            segFiles = new File[0];
        }
        else if(segFiles.length > 0)
        {
            Arrays.sort(segFiles, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2)
                {
                    int segId1 = Integer.parseInt(f1.getName().substring(0, f1.getName().indexOf('.')));
                    int segId2 = Integer.parseInt(f2.getName().substring(0, f2.getName().indexOf('.')));
                    return (segId1 < segId2) ? -1 : ((segId1 == segId2) ? 0 : 1);
                }
            });
        }
        
        return segFiles;
    }
    
    @Override
    public Object clone()
    {
        synchronized(this)
        {
            return new SegmentManager(this);
        }
    }
    
    public SegmentMeta getMeta()
    {
        return _segMeta;
    }
    
    public synchronized void updateMeta() throws IOException
    {
        FileLock lock = null;
        FileChannel channel = null;
        
        try
        {
            channel = new RandomAccessFile(getMeta().getMetaFile(), "rw").getChannel();
            lock = channel.lock(0, Long.MAX_VALUE, false);  // get exclusive file lock
            _segMeta.wrap(this);
        }
        finally
        {
            if(lock != null) lock.release();
            if(channel != null) channel.close();
        }
    }
    
    public synchronized static SegmentManager getInstance(String segmentHomePath) throws IOException
    {
        return getInstance(new MappedSegmentFactory(), segmentHomePath);
    }
    
    public synchronized static SegmentManager getInstance(SegmentFactory segmentFactory, String segmentHomePath) throws IOException
    {
        return getInstance(segmentFactory, segmentHomePath, Segment.defaultSegmentFileSizeMB);
    }
    
    public synchronized static SegmentManager getInstance(SegmentFactory segmentFactory, String segmentHomePath, int segmentFileSizeMB) throws IOException
    {
        if(segmentFileSizeMB < Segment.minSegmentFileSizeMB)
        {
            throw new IllegalArgumentException("Invalid argument segmentFileSizeMB " + segmentFileSizeMB + ", smaller than " + Segment.minSegmentFileSizeMB);
        }
        
        if(segmentFileSizeMB > Segment.maxSegmentFileSizeMB)
        {
            throw new IllegalArgumentException("Invalid argument segmentFileSizeMB " + segmentFileSizeMB + ", greater than " + Segment.maxSegmentFileSizeMB);
        }
        
        File segDir = new File(segmentHomePath);
        if(!segDir.exists())
        {
            if(!segDir.mkdirs())
            {
                throw new IOException("Failed to create directory " + segmentHomePath);
            }
        }
        
        if(segDir.isFile())
        {
            throw new IOException("File " + segmentHomePath + " is not a directory");
        }
        
        String key = segDir.getCanonicalPath();
        SegmentManager mgr = _segManagerMap.get(key);
        if(mgr == null)
        {
            mgr = new SegmentManager(key, segmentFactory, segmentFileSizeMB);
            _segManagerMap.put(key, mgr);
        }
        
        return mgr;
    }
    
}
