import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

import omf.OMF;
import omf.OMF_Segment;
import omf.OMF_LConst;
import omf.OMF_Eof;

/*
 *
 * makeobj [options] file
 * 
 * convert a binary file to an OMF object file.
 * 
 * Tim Meekins had a mkobj utility which essentially did
 * the same thing; this version has more options.
 */

public class makeobj
{

    private static void usage()
    {
        System.out.println("makeobj v 0.1");
        System.out.println("Convert a file to an object file");
        System.out.println("usage: makeobj [options]");
        System.out.println("options:");

        System.out.println("\t-a align       alignment (must be power of 2)");
        System.out.println("\t-n name        segment name");
        System.out.println("\t-l name        load name");
        System.out.println("\t-k kind        segment kind");
        System.out.println("\t-o objfile     object file name");
        System.out.println("\t-h             help");        
        System.out.println();
        System.out.println("kind may be: CODE, DATA, INIT, or STACK.");
    }
    
    
    /*
     * returns null on bad input data, an Integer on success.
     * 
     */
    private static Integer toInt(String s)
    {
        int c;
        if (s == null || s.length() == 0) return null;
        
        int base = 10;
        c = s.charAt(0);
        if (c == '$')
        {
            s = s.substring(1);
            base = 16;
        }
            
        else if (c == '0' 
            && s.length() > 2 
            && s.charAt(1) == 'x')
        {
            s = s.substring(2);
            base = 16;
        }
        try
        {
            int i = Integer.parseInt(s, base);
            return new Integer(i);
        }
        catch (Exception e)
        {
            return null;
        }

    }
    
    private static int toKind(String s)
    {
        if (s.compareToIgnoreCase("CODE") == 0)
            return OMF.KIND_CODE;
        if (s.compareToIgnoreCase("DATA") == 0)
            return OMF.KIND_DATA;
        if (s.compareToIgnoreCase("DP") == 0)
            return OMF.KIND_DP;
        if (s.compareToIgnoreCase("INIT") == 0)
            return OMF.KIND_INIT;
        if (s.compareToIgnoreCase("STACK") == 0)
            return OMF.KIND_DP;
        return -1;
    }
    
    public static void main(String[] args)
    {
        GetOpt go = new GetOpt(args, "a:k:l:n:o:h");

        String fSegname = null;
        String fLoadname = null;
        String fOutfile = null;
        int fKind = OMF.KIND_DATA;
        int fAlign = 0;
        
        int c;
        while ((c = go.Next()) != -1)
        {
            switch(c)
            {
            case 'a':
                {
                    String s = go.Argument();
                    Integer i = toInt(s);
                    if (i == null)
                    {
                        System.err.printf("makeobj: invalid number format : %1$s.\n",
                                s);
                        return;
                    }
                    int j = i.intValue();
                    if (j == 1)
                    {
                        fAlign = 0;
                        break;
                    }
                    while ((j & 0x01) == 0) j = j >> 1;
                    if (j == 1)
                        fAlign = i.intValue();
                    else
                    {
                        System.err.println("makeobj: alignment must be a power of 2.");
                        return;
                    }    
                }
                break;
                
            case 'k':
                {
                    int i = toKind(go.Argument());
                    if (i == -1)
                    {
                        usage();
                        return;
                    }
                    fKind = i;
                }
                break;
                
            case 'l':
                fLoadname = go.Argument();
                break;
                
            case 'n':
                fSegname = go.Argument();
                break;
                
            case 'o':
                fOutfile = go.Argument();
                break;
                
            case 'h':
            case '?':
                usage();
                return;
            }
        }        

        args = go.CommandLine();
        if (args.length != 1)
        {
            usage();
            return;
        }

        String base;
        File f = new File(args[0]);

        if (!f.exists() || !f.isFile())
        {
            System.err.printf("makeobj: %1$s is not a valid file.\n",
                    f.getName());
            return;
        }
        
        base = f.getName();
        {
            int i = base.lastIndexOf('.');
            
            if (i > 0)
                base = base.substring(0, i);
            
            if (fSegname == null)
                fSegname = base;
            if (fOutfile == null)
                fOutfile = base + ".o";
        }
        
        try
        {
            FileOutputStream fout;
            RandomAccessFile file = new RandomAccessFile(args[0], "r");
            long length = file.length();
            byte[] data = new byte[(int)length];
        
            file.readFully(data);
            
            OMF_Segment seg = new OMF_Segment();
            seg.SetKind(fKind);
            seg.SetAlignment(fAlign);

            seg.SetSegmentName(fSegname);
            if (fLoadname != null) 
                seg.SetLoadName(fLoadname);
            
            seg.AddOpcode(new OMF_LConst(data));
            seg.AddOpcode(new OMF_Eof());
            
            fout = new FileOutputStream(fOutfile);
            
            seg.Save(fout); // should probably check if this worked.
        }
        catch(Exception e)
        {
            System.err.printf("makeobj: error processing %1$s.\n",
                    f.getName());
        }
        
        
    }

}
