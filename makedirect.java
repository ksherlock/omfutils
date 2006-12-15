import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import omf.OMF_Eof;
import omf.OMF_Segment;
import omf.OMF;

/*
 * Created on Dec 7, 2006
 * Dec 7, 2006 7:29:03 PM
 */

/*
 * makedirect [-o outfile] [-n name] [-p size]
 * 
 * create a direct page segment 
 * -o outfile: name of file (direct.o is default)
 * -n name: segment name (STACKMIN is default)
 * -p size: size of segment (required)
 * 
 */

public class makedirect
{

    private static void usage()
    {
        System.out.println("makedirect v 0.1");
        System.out.println("Create a direct page/stack segment.");
        System.out.println("usage: makedirect [options]");
        System.out.println("options:");

        System.out.println("\t-n name        segment name [STACKMIN]");
        System.out.println("\t-o objfile     object file name [direct.o]");
        System.out.println("\t-p size        size of segment (required)");
        System.out.println("\t-h             help");        
    }
    
    private static int ParseInt(String s)
    {
        int c;
        if (s == null || s.length() == 0) return 0;
        
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
        return Integer.parseInt(s, base);

    }
    
    public static void main(String[] args)
    {
        GetOpt go = new GetOpt(args, "n:o:p:h");
        String fSegname = "STACKMIN";
        String fOutfile = "direct.o";
        int fSize = -1;
        
        int c;
        while ((c = go.Next()) != -1)
        {
            switch(c)
            {
            case 'n':
                fSegname = go.Argument();
                break;
            case 'o':
                fOutfile = go.Argument();
                break;
            case 'p':
                fSize = makedirect.ParseInt(go.Argument());
                break;
                
            case 'h':
            case '?':
                usage();
                return;
            }
        }
        
        if (fSize == 0)
        {
            usage();
            return;            
        }
        if (fSize < 0x0100 || fSize > 0xffff)
        {
            System.err.println("makedirect: size is out of range.  Must be $100--$ffff.");
        }
        
        OMF_Segment s = new OMF_Segment();
        
        s.SetSegmentName(fSegname);
        s.SetLoadName("~Direct");
        s.SetReservedSpace(fSize);
        s.SetKind(OMF.KIND_DP);
        s.SetSegmentNumber(1);
        s.AddOpcode(new OMF_Eof());
        
        try
        {
            FileOutputStream fout = new FileOutputStream(fOutfile);

            s.Save(fout);           
        }
        catch (FileNotFoundException e)
        {
            System.err.println("makedirect: unable to open file " 
                    + fOutfile + ".");
        }
    }

}
