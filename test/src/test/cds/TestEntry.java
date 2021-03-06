package test.cds;

import java.io.File;
import java.util.Random;

import krati.cds.impl.array.entry.Entry;
import krati.cds.impl.array.entry.EntryUtility;
import krati.cds.impl.array.entry.EntryValueInt;
import krati.cds.impl.array.entry.EntryValueIntFactory;

/**
 * Test Entry
 * 
 * @author jwu
 *
 */
public class TestEntry extends AbstractTest
{
    static Random random = new Random(System.currentTimeMillis());
    
    public TestEntry()
    {
        super(TestEntry.class.getCanonicalName());
    }
    
    /**
     * Write an entry to disk and then load it into a new entry.
     * Verify that these two entries are the same.
     */
    public void testWriteReadEntry() throws Exception
    {
        cleanTestOutput();
        
        for(int run = 1; run <= 10; run++) {
            File file = new File(TEST_OUTPUT_DIR, "entry_test" + run + ".dat");
            Entry<EntryValueInt> entry = new Entry<EntryValueInt>(new EntryValueIntFactory(), 1000);
            
            for (int i = 0; i < 1000; i++)
            {
                entry.add(new EntryValueInt(i, random.nextInt(50000), i));
            }
            entry.save(file);
            
            Entry<EntryValueInt> entryRead = new Entry<EntryValueInt>(new EntryValueIntFactory(), 1000);
            entryRead.load(file);
            
            assertTrue("Entry minSCNs don't match at run " + run,
                                 entry.getMinScn() == entryRead.getMinScn());
            assertTrue("Entry maxSCNs don't match at run " + run,
                                 entry.getMaxScn() == entryRead.getMaxScn());
            assertTrue("Arrays don't match at run " + run,
                                 entry.getValueList().equals(entryRead.getValueList()));
        }
        
        cleanTestOutput();
    }
    
    /**
     * Test SortEntriesByPos by having it sort values then 
     * iterate over them to insure they're sorted.
     */
    public void testSortedEntryValues()
    {
        int c;
        EntryValueIntFactory valFactory = new EntryValueIntFactory();
        
        c = 0;
        Entry<EntryValueInt> e1 = new Entry<EntryValueInt>(valFactory, 10);
        for (int i = 9; i >= 0; i--)
        {
            e1.add(new EntryValueInt(i, c, c++));
        }
        
        c = 0;
        Entry<EntryValueInt> e2 = new Entry<EntryValueInt>(valFactory, 10);
        for (int i = 19; i >= 10; i--)
        {
            e2.add(new EntryValueInt(i, c, c++));
        }
        
        @SuppressWarnings("unchecked")
        Entry<EntryValueInt>[] entries = new Entry[] {e1, e2};
        EntryValueInt[] values = EntryUtility.sortEntriesToValues(entries);
        
        // verify the values are in sorted order
        long pos = 0;
        for (EntryValueInt value : values)
        {
            assertEquals(value.pos, pos);
            pos++;
        }
    }
}
