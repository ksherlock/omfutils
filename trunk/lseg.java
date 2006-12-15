

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;

import omf.OMF;
import omf.OMF_Const;
import omf.OMF_Expr;
import omf.OMF_Label;
import omf.OMF_Opcode;
import omf.OMF_Segment;


public class lseg
{
    static private final String[] SegKinds = 
    {
        "Code",
        "Data",
        "Jump-table",
        "",
        "Pathname",
        "",
        "",
        "",
        "Library Dictionary",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "Initialization",
        "",
        "Direct-page/Stack"
    };        
    
    private static void usage()
    {
        System.out.println("lseg v 0.1");
        System.out.println("List segments in an OMF file.");
        System.out.println("usage: lseg file ...");
    }
    
    private static int get_stack_size(OMF_Segment seg)
    {
        /*
         * try to find the stack usage of an object file.
         * 
         * generated code is:
         * 3b   tsc
         * 38   sec
         * e9   sbc #xxxx <--
         * 1b   tcs
         * 
         * this is stored in the const record
         * 
         * if stack debugging is on, it is preceded by:
         * 
         * pea xxxx
         * jsl ~CHECKSTACK
         * 
         * if debug names is on, everything will preceded by
         * 82 xx xx 71 77 ....
         * 
         */
        
        ListIterator<OMF_Opcode> ops = seg.Opcodes();
        OMF_Opcode op = ops.next();
        if (op instanceof OMF_Const) do
        {
            OMF_Const c = (OMF_Const)op;
            byte[] data;
            int length;
            int offset;
            int i;
            
            data = c.Data();
            length = c.CodeSize();
            
            offset = 0;
            
            i = ByteReader.Read8(data, offset++, 0);
            
            if (i == 0x82) // debug name ?
            {
                if (offset + 3 > length) break;
                int branch = ByteReader.Read16(data, offset, 0);
                offset += 2;
                if ((branch & 0x8000) == 0x8000) break;
                
                if (ByteReader.Read16(data, offset, 0) != 0x7771)
                    break;
                
                offset += branch;
                
                if (offset >= length) break;
                
                i = ByteReader.Read8(data, offset++, 0);           
            }
            
            if (i == 0xf4) // stack check ?
            {
                // f4 xx xx 22 [end of const]
                // 3-byte LEXPR to ~CHECKSTACK
                if (length - offset != 3) break;
                
                if (ByteReader.Read8(data, offset +2, 0) != 0x22)
                    break;
                
                op = ops.next();
                if (op == null) break;
                if (op.Opcode() != 0xf3) break;
                if (op.CodeSize() != 3) break;
                OMF_Expr expr = (OMF_Expr)op;
                ArrayList ex = expr.Expression();
                if (ex.size() != 2) break;
                Object o = ex.get(0);
                OMF_Label lab;
                
                if (o instanceof OMF_Label)
                    lab = (OMF_Label)o;
                else break;
                String s = lab.toString();
                if (s.compareTo("~CHECKSTACK") != 0)
                    break;
                
                offset = 0;
                op = ops.next();
                if (op == null)
                    break;
                if (!(op instanceof OMF_Const)) break;
                
                c = (OMF_Const)op;
                data = c.Data();
                length = c.CodeSize();
                
                offset = 0;
                
                i = ByteReader.Read8(data, offset++, 0);
               
            }

            if (offset + 5 > length) break;

            if (i != 0x3b) break;
                
            i = ByteReader.Read8(data, offset++, 0);
            if (i != 0x38) break;
            
            i = ByteReader.Read8(data, offset++, 0);
            if (i != 0xe9) break;
            
            int stack = ByteReader.Read16(data, offset, 0);
            offset += 2;
            i = ByteReader.Read8(data, offset++, 0);
            if (i != 0x1b) break;                
            
            return stack;

        } while (false);
        
        
        return -1;
    }
    
    
    private static void list(String filename)
    {
        ArrayList<OMF_Segment> segments;
       
        
        File f = new File(filename);
        if (!f.exists() ||  !f.isFile())
        {
            System.err.printf("lseg: %1$s is not a valid file.\n",
                    filename);
            return;
        }
        segments = OMF.LoadFile(f);
        if (segments == null)
        {
            System.err.printf("lseg: %1$s is not a valid OMF file.\n",
                    filename);
            return;
        }
        for (Iterator<OMF_Segment> iter = segments.iterator(); iter.hasNext(); )
        {
            OMF_Segment segment = iter.next();               
            int kind = segment.Kind();
            
            String Kind;
            if (kind > SegKinds.length)
                Kind = "";
            else
                Kind = SegKinds[kind];
            
            int stack = -1;
            if (kind == OMF.KIND_CODE)
            {
                stack = get_stack_size(segment);
            }
            
            System.out.printf("%1$-20s %2$-18s 0x%3$06x ",
                        f.getName(),
                        Kind, 
                        segment.Length());
            if (stack == -1)
                System.out.print("      ");
            else
                System.out.printf("0x%1$04x", stack);
            
            System.out.printf(" \"%1$s\"\n", segment.SegmentName());
        }
    
    }

    public static void main(String[] args)
    {
        
        if (args.length == 0)
        {
            usage();
            return;
        }
        
        System.out.println("File                 Type               Size     Stack  Name");
        System.out.println("-------------------- ------------------ -------- ------ -----------------");
        for (int i = 0; i < args.length; i++)
        {
            list(args[i]);
        }

    }

}
