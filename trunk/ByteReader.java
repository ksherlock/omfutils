/*
 * Created on Dec 11, 2006
 * Dec 11, 2006 6:34:11 PM
 */

public final class ByteReader
{
    public static final int Read8(byte[] data, int offset, int numsex)
    {
        return data[offset] & 0xff;
    }
    public static final int Read8(byte[] data, int offset)
    {
        return data[offset] & 0xff;
    }
    
    public static final int Read16(byte[] data, int offset, int numsex)
    {
        int x1 = data[offset++] & 0x00ff;
        int x2 = data[offset++] & 0x00ff;

        if (numsex == 0)
            return x1 | (x2 << 8);
        
        return (x1 << 8) | x2;
        
    }
    public static final int Read24(byte[] data, int offset, int numsex)
    {
        int x1 = data[offset++] & 0x00ff;
        int x2 = data[offset++] & 0x00ff;
        int x3 = data[offset++] & 0x00ff;
        
        if (numsex == 0)
            return x1 | (x2 << 8) | (x3 << 16);
        
        return (x1 << 16) | (x2 << 8) | x3;
    }
    public static final int Read32(byte[] data, int offset, int numsex)
    {
        int x1 = data[offset++] & 0x00ff;
        int x2 = data[offset++] & 0x00ff;
        int x3 = data[offset++] & 0x00ff;
        int x4 = data[offset++] & 0x00ff;
        
        if (numsex == 0)
            return x1 | (x2 << 8) | (x3 << 16) | (x4 << 24);
        
        return (x1 << 24) | (x2 << 16) | (x3 << 8) | x4;
    }
    
private ByteReader() {}
}
