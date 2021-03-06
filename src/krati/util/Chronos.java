package krati.util;

import java.io.PrintStream;

/**
 * Chronos
 * 
 * @author jwu
 *
 */
public class Chronos
{
  /**
   * Where to print information
   */
  private final PrintStream _out;
  
  /**
   * The last tick
   */
  private long _lastTick;
  
  /**
   * When this Chronos is started
   */
  private long _startTime;
  
  /**
   * Constructor : initialize the tick and sets no stream
   */
  public Chronos()
  {
    this(null);
  }
  
  /**
   * Constructor : initialize the tick and set the stream to the one given
   *
   * @param out the print stream for the output
   */
  public Chronos(PrintStream out)
  {
    this._startTime = System.currentTimeMillis();
    this._lastTick = _startTime;
    this._out = out;
  }
  
  /**
   * Restarts this Chronos.
   */
  public void restart()
  {
    _startTime = System.currentTimeMillis();
    _lastTick = _startTime;
  }
  
  /**
   * Returns the number of milliseconds elapsed since the last call to
   * this function.
   *
   * @return the number of milliseconds since last call*/
  public long tick()
  {
    long tick = System.currentTimeMillis();
    long diff = tick - _lastTick;
    _lastTick = tick;
    
    return diff;
  }

  /**
   * @return the total time since start of this chronos
   */
  public long getTotalTime()
  {
    return System.currentTimeMillis() - _startTime;
  }
  
  /**
   * Returns a string that represents the time elapsed
   *
   * @return the elapsed time as a string */
  public String getElapsedTime()
  {
    StringBuffer sb = new StringBuffer("Time: ");
    sb.append(tick());
    sb.append(" ms");
    
    return sb.toString();
  }
  
  /**
   * Display the time elapsed since the last call (with no message)
   */
  public void displayElapsedTime()
  {
    this.displayElapsedTime("");
  }

  /**
   * Display the time elapsed since the last call (with an additional message)
   *
   * @param msg the message to display first
   */
  public void displayElapsedTime(String msg)
  {
    if(_out != null)
    {
      _out.println(msg + getElapsedTime());
    }
  }
  
  /**
   * Flushes the underlying writer
   */
  public void flush()
  {
    if(_out != null)
    {
      _out.flush();
    }
  }
}