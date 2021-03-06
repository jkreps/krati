package krati.cds.impl.segment;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.BufferOverflowException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Date;

import org.apache.log4j.Logger;

/**
 * ChannelSegment
 * 
 * @author jwu
 *
 */
public class ChannelSegment extends AbstractSegment
{
    private final static Logger _log = Logger.getLogger(ChannelSegment.class);
    private RandomAccessFile _raf = null;
    private FileChannel _channel;
    
    public ChannelSegment(int segmentId, File segmentFile, int initialSizeMB, Segment.Mode mode) throws IOException
    {
        super(segmentId, segmentFile, initialSizeMB, mode);
    }
    
    @Override
    protected void init() throws IOException
    {
        if (!getSegmentFile().exists())
        {
            if (!getSegmentFile().createNewFile())
            {
                String msg = "Failed to create " + getSegmentFile().getAbsolutePath();
                
                _log.error(msg);
                throw new IOException(msg);
            }
            
            RandomAccessFile raf = new RandomAccessFile(getSegmentFile(), "rw");
            raf.setLength(getInitialSize());
            raf.close();
        }
        
        if(getMode() == Segment.Mode.READ_ONLY)
        {
            _raf = new RandomAccessFile(getSegmentFile(), "r");
            
            if(_raf.length() != getInitialSize())
            {
                int rafSizeMB = (int)(_raf.length() / 1024L / 1024L);
                throw new SegmentFileSizeException(getSegmentFile().getCanonicalPath(), rafSizeMB, getInitialSizeMB());
            }
            
            _channel = _raf.getChannel();
            _lastForcedTime = readLong(0);
            
            _log.info("Segment " + getSegmentId() + " loaded as " + getMode() + " lastForcedTime=" + new Date(_lastForcedTime));
        }
        else
        {
            _raf = new RandomAccessFile(getSegmentFile(), "rw");
            
            if(_raf.length() != getInitialSize())
            {
                int rafSizeMB = (int)(_raf.length() / 1024L / 1024L);
                throw new SegmentFileSizeException(getSegmentFile().getCanonicalPath(), rafSizeMB, getInitialSizeMB());
            }
            
            _channel = _raf.getChannel();
            
            // update the time stamp of segment
            _lastForcedTime = System.currentTimeMillis(); 
            appendLong(_lastForcedTime);
            
            _log.info("Segment " + getSegmentId() + " initialized as " + getMode() + " at " + new Date(_lastForcedTime));
        }
    }
    
    @Override
    public long getAppendPosition() throws IOException
    {
        return _channel.position();
    }
    
    @Override
    public void setAppendPosition(long newPosition) throws IOException
    {
        _channel.position(newPosition);
    }
    
    @Override
    public int appendInt(int value) throws IOException, SegmentOverflowException, SegmentReadOnlyException
    {
        if(isReadOnly())
        {
            throw new SegmentReadOnlyException(this);
        }
        
        try
        {
            int pos = (int)_channel.position();
            if((pos + 4) >= _initSizeBytes)
            {
                throw new SegmentOverflowException(this);
            }
            
            ByteBuffer bb = ByteBuffer.wrap(new byte[4]);
            bb.putInt(value);
            bb.flip();
            _channel.write(bb);
            _loadSizeBytes += 4;
            return pos;
        }
        catch(BufferOverflowException boe)
        {
            asReadOnly();
            throw new SegmentOverflowException(this);
        }
    }

    @Override
    public int appendLong(long value) throws IOException, SegmentOverflowException, SegmentReadOnlyException
    {
        if(isReadOnly())
        {
            throw new SegmentReadOnlyException(this);
        }
        
        try
        {
            int pos = (int)_channel.position();
            if((pos + 8) >= _initSizeBytes)
            {
                throw new SegmentOverflowException(this);
            }
            
            ByteBuffer bb = ByteBuffer.wrap(new byte[8]);
            bb.putLong(value);
            bb.flip();
            _channel.write(bb);
            _loadSizeBytes += 8;
            return pos;
        }
        catch(BufferOverflowException boe)
        {
            asReadOnly();
            throw new SegmentOverflowException(this);
        }
    }

    @Override
    public int appendShort(short value) throws IOException, SegmentOverflowException, SegmentReadOnlyException
    {
        if(isReadOnly())
        {
            throw new SegmentReadOnlyException(this);
        }
        
        try
        {
            int pos = (int)_channel.position();
            if((pos + 2) >= _initSizeBytes)
            {
                throw new SegmentOverflowException(this);
            }
            
            ByteBuffer bb = ByteBuffer.wrap(new byte[2]);
            bb.putShort(value);
            bb.flip();
            _channel.write(bb);
            _loadSizeBytes += 2;
            return pos;
        }
        catch(BufferOverflowException boe)
        {
            asReadOnly();
            throw new SegmentOverflowException(this);
        }
    }
    
    @Override
    public int append(byte[] data) throws IOException, SegmentOverflowException, SegmentReadOnlyException
    {
        if(isReadOnly())
        {
            throw new SegmentReadOnlyException(this);
        }
        
        try
        {
            int pos = (int)_channel.position();
            if((pos + data.length) >= _initSizeBytes)
            {
                throw new SegmentOverflowException(this);
            }
            
            ByteBuffer bb = ByteBuffer.wrap(data);
            _channel.write(bb);
            _loadSizeBytes += data.length;
            return pos;
        }
        catch(BufferOverflowException boe)
        {
            asReadOnly();
            throw new SegmentOverflowException(this);
        }
    }
    
    @Override
    public int append(byte[] data, int offset, int length) throws IOException, SegmentOverflowException, SegmentReadOnlyException
    {
        if(isReadOnly())
        {
            throw new SegmentReadOnlyException(this);
        }
        
        try
        {
            int pos = (int)_channel.position();
            if((pos + length) >= _initSizeBytes)
            {
                throw new SegmentOverflowException(this);
            }
            
            ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
            _channel.write(bb);
            _loadSizeBytes += length;
            return pos;
        }
        catch(BufferOverflowException boe)
        {
            asReadOnly();
            throw new SegmentOverflowException(this);
        }
    }

    @Override
    public int readInt(int pos) throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap(new byte[4]);
        _channel.read(bb, pos);
        return bb.getInt(0);
    }

    @Override
    public long readLong(int pos) throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap(new byte[8]);
        _channel.read(bb, pos);
        return bb.getLong(0);
    }

    @Override
    public short readShort(int pos) throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap(new byte[2]);
        _channel.read(bb, pos);
        return bb.getShort(0);
    }
    
    @Override
    public void read(int pos, byte[] dst) throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap(dst);
        _channel.read(bb, pos);
    }
    
    @Override
    public void read(int pos, byte[] dst, int offset, int length) throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap(dst, offset, length);
        _channel.read(bb, pos);
    }
    
    @Override
    public long transferTo(long pos, int length, WritableByteChannel targetChannel) throws IOException
    {
        return _channel.transferTo(pos, length, targetChannel);
    }
    
    @Override
    public boolean isReadOnly()
    {
        return (getMode() == Segment.Mode.READ_ONLY);
    }

    @Override
    public synchronized void asReadOnly() throws IOException
    {
        if(getMode() == Segment.Mode.READ_WRITE)
        {
            force();
            _segMode = Segment.Mode.READ_ONLY;
            _log.info("Segment " + getSegmentId() + " switched to " + getMode());
        }
    }
    
    @Override
    public synchronized void load() throws IOException {}
    
    @Override
    public synchronized void force() throws IOException
    {
        if(getMode() == Segment.Mode.READ_WRITE)
        {
            long currentTime = System.currentTimeMillis();
            ByteBuffer bb = ByteBuffer.wrap(new byte[8]);
            bb.putLong(currentTime);
            bb.flip();
            _channel.write(bb, 0);
            _lastForcedTime = currentTime;
        }
        
        _channel.force(true);
        _log.info("Forced Segment " + getSegmentId());
    }
    
    @Override
    public synchronized void close(boolean force) throws IOException
    {
        if(force) force();

        if(_channel != null)
        {
            _channel.close();
            _channel = null;
        }
        
        if(_raf != null)
        {
            _raf.close();
            _raf = null;
        }
    }
}
