import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import omf.*;

/*
 * Created on Feb 23, 2006
 * Feb 23, 2006 10:23:14 PM
 */

public class dumpobj
{

    private static void usage()
    {
        System.out.println("dumpobj v 0.1");
        System.out.println("usage: dumpobj [options] file [segments]");
        
        System.out.println("options:");
        System.out.println("\t-H             dump headers only");
        System.out.println("\t-D             don't do hexdump");
        System.out.println("\t-h             help");
        System.out.println("if [segments] is blank then all segments will be dumped.");
    }
    /**
     * @param args
     */
    public static void main(String[] args)
    {    
       
       boolean fHeaderOnly = false;
       boolean fHexDump = true;
       GetOpt go = new GetOpt(args, "DhH");
       
       HashSet<String> fSegments = new HashSet<String>();
       
       int c;
       
       while ((c = go.Next()) != -1)
       {
           switch (c)
           {
           case 'H':
               fHeaderOnly = true;
               break;
           case 'D':
               fHexDump = false;
               break;
               
           case 'h':
           case '?':
               usage();
               return;
           }
       }
       
       args = go.CommandLine();
      
       if (args.length < 1) usage();
       else
       {
           File f = new File(args[0]);
           for (int i = 1; i < args.length; i++)
               fSegments.add(args[i].toUpperCase());

           ArrayList<OMF_Segment> segments = OMF.LoadFile(f);
           if (segments == null)
           {
               System.err.printf("Invalid OMF File: %1$s\n", args[0]);
               return;
           }
           for (Iterator<OMF_Segment> iter = segments.iterator(); iter.hasNext(); )
           {
               OMF_Segment segment = iter.next();
               
               if ((fSegments.size() > 0) 
                       && (!fSegments.contains(segment.SegmentName().toUpperCase())))
                       continue;
               
               dumpheader(segment);
               if (!fHeaderOnly) dumpbody(segment, fHexDump);
           }   
       }
    }
    
    
    static void dumpheader(OMF_Segment seg)
    {
        // write the header....
        System.out.printf("Byte count:     $%1$08x\n", seg.ByteCount());
        System.out.printf("Reserved space: $%1$08x\n", seg.ReservedSpace());
        System.out.printf("Length:         $%1$08x\n", seg.Length());
        System.out.printf("Label length:   $%1$02x\n", seg.LabelLength());
        System.out.printf("Number length:  $%1$02x\n", seg.NumberLength());
        System.out.printf("Version:        $%1$02x\n", seg.Version());
        System.out.printf("Bank size:      $%1$08x\n", seg.BankSize());
        
        System.out.printf("Kind:           $%1$04x     (%2$s)\n", 
                seg.Kind() | seg.Attributes(),
                kind(seg.Kind(), seg.Attributes()));
        System.out.printf("Org:            $%1$08x\n", seg.Org());
        System.out.printf("Alignment:      $%1$08x\n", seg.Alignment());
        System.out.printf("Number sex:     $%1$02x\n", seg.NumberSex());
        System.out.printf("Segment number: $%1$04x\n", seg.SegmentNumber());
        System.out.printf("Segment entry:  $%1$08x\n", seg.Entry());
        System.out.printf("Load name:      %1$s\n", 
                seg.LoadName().replace((char)0x00, ' '));
        System.out.printf("Segment name:   %1$s\n", seg.SegmentName());
        
        System.out.println();       
    }
    
    static String kind(int kind, int attr)
    {
        StringBuffer buff = new StringBuffer();
        
        if (kind < 0 || kind > 0x12)
            buff.append("???");
        else
            buff.append(SegKinds[kind]);
        
            
        if ((attr & OMF.KIND_BANKREL) != 0)
            buff.append(", bank relative");
        if ((attr & OMF.KIND_SKIPSEG) != 0)
            buff.append(", skip");
        if ((attr & OMF.KIND_RELOADSEG) != 0)
            buff.append(", reload");       
        if ((attr & OMF.KIND_ABSBANK) != 0)
            buff.append(", absolute");
        if ((attr & OMF.KIND_NOSPEC) != 0)
            buff.append(", nospec");
        if ((attr & OMF.KIND_POSIND) != 0)
            buff.append(", posind");
        if ((attr & OMF.KIND_PRIVATE) != 0)
            buff.append(", private");
        
        if ((attr & OMF.KIND_DYNAMIC) != 0)
            buff.append(", dynamic");
        else buff.append(", static");
        
        
        return buff.toString();
    }
    static void dumpbody(OMF_Segment seg, boolean hexdump)
    {
        int pc = 0;
        for(Iterator<OMF_Opcode> iter = seg.Opcodes(); iter.hasNext(); )
        {
            int size;
            OMF_Opcode op = iter.next();
            int opcode = op.Opcode();
            String name;
            if (opcode == OMF.OMF_EOF) name = "END";
            else if (opcode < 0xe0 || opcode > 0xf7) name = "???";
            else
            {
                name = names[opcode - 0xe0];
                if (opcode == OMF.OMF_LCONST && !(op instanceof OMF_LConst))
                {
                    opcode = op.CodeSize();
                    name = "CONST";
                }
                
            }
            
            size = op.CodeSize();
            System.out.printf("%1$08x:  %2$-10s($%3$02x)\t$%4$04x", pc, name, opcode, size);
            
            
            switch (op.Opcode())
            {
            case OMF.OMF_LCONST:
                if (hexdump) dumphex( ((OMF_Const)op).Data(), op.CodeSize());
                break;
                
            case OMF.OMF_ENTRY:
                {
                    OMF_Entry e = (OMF_Entry)op;
                    System.out.printf("\t$%1$04x $%2$08x %3$s", 
                            e.Segment(), e.Offset(), e.toString());
                    
                }
                break;
                
            case OMF.OMF_SUPER:
                {
                    OMF_Super s = (OMF_Super)op;
                    String tname;
                    int type = s.Type();
                    
                    if (type == 0) tname = "RELOC2";
                    else if (type == 1) tname = "RELOC3";
                    else if (type > 1 && type < 38) 
                        tname = "INTERSEG" + (type - 1);
                    else tname = "??? (" + type + ")";
                    
                    System.out.printf("\t%1s", tname);
                    if (hexdump) dumphex(s.Data(), s.Length() - 1);
                }
                break;
                
            case OMF.OMF_EXPR:
            case OMF.OMF_BKEXPR:
            case OMF.OMF_LEXPR:
            case OMF.OMF_ZPEXPR:
                {
                    OMF_Expr e = (OMF_Expr)op;
                    dumpexpr(e.Expression());
                }
                break;
                
                case OMF.OMF_RELEXPR:
                {
                    OMF_RelExpr e = (OMF_RelExpr)op;
                    dumpexpr(e.Expression());                   
                }
                break;
                
            case OMF.OMF_GEQU:
            case OMF.OMF_EQU:
                {
                    OMF_Equ eq = (OMF_Equ)op;
                    System.out.printf("\t%1$s", eq.toString());
                    dumpexpr(eq.Expression());
                }
                break;
            case OMF.OMF_GLOBAL:
            case OMF.OMF_LOCAL:

                System.out.printf("\t%1$s", op.toString());
                break;

                
            }
    
            System.out.println();
            pc += size;
        }
        
        System.out.println();
    }
    private static void dumphex(byte[] data, int length)
    {
        
        int i = 0;
        while (i < length)
        {
            StringBuffer hex = new StringBuffer();
            StringBuffer ascii = new StringBuffer();
            
            for(int j = 0; j < 16; j++, i++)
            {
                if (i == length) break;
                byte b = data[i];
                hex.append(hexcodes[(b >> 4) & 0x0f]);
                hex.append(hexcodes[b & 0x0f]);
                hex.append(' ');
                
                // isprint hack
                if (b >= 32 && b <= 127)    ascii.append((char)b);
                else ascii.append('.');
            }
            System.out.printf("\n           %1$-50s%2$s", hex, ascii);
            
        }
    }
    
    private static void dumpexpr(ArrayList expr)
    {
        System.out.print("\t");
        for (Iterator iter = expr.iterator(); iter.hasNext(); )
        {
            Object o = iter.next();
            if (o instanceof Integer)
            {
                int value = ((Integer)o).intValue();
                String op;
                if (value == 0) return; // end of expression.
                if (value > 0x15) op = "?";
                else op = mathops[value];
                
                System.out.print(op);
            }
            
            else if (o instanceof OMF_Number)
            {
                OMF_Number n = (OMF_Number)o;
                int value = n.Value();
                int opcode = n.Opcode();
                if (opcode == OMF_Expression.EXPR_REL)
                {
                    System.out.printf("*+$%1$04x", value);  
                }
                else if (opcode == OMF_Expression.EXPR_ABS)
                {
                    System.out.printf("$%1$04x", value);
                }
                else System.out.print("?");
            }
            else if (o instanceof OMF_Label)
            {
                OMF_Label lab = (OMF_Label)o;
                // may be weak, label, len, type, or count .. should distinguish them.
                System.out.print(lab.toString());
                
            }
            else
            {
                System.out.print("?");
            }
            System.out.print(' ');
        }
    }    
    private static final char hexcodes[] =
    {
        '0', '1', '2', '3', 
        '4', '5', '6', '7', 
        '8', '9', 'a', 'b', 
        'c', 'd', 'e', 'f'
     };
    
    static private final String[] SegKinds = 
    {
        "code",
        "data",
        "jump-table",
        "",
        "pathname",
        "",
        "",
        "",
        "library",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "init",
        "",
        "stack"
    };
    
    private static final String names[] = 
    {
        // 0xe0
        "ALIGN",
        "ORG",
        "RELOC",
        "INTERSEG",
        "USING",
        "STRONG",
        "GLOBAL",
        "GEQU",
        "MEM",
        null, 
        null, 
        "EXPR",
        "ZPEXPR",
        "BKEXPR",
        "RELEXPR",
        "LOCAL",
        // 0xf0
        "EQU",
        "DS",
        "LCONST",
        "LEXPR",
        "ENTRY",
        "cRELOC",
        "cINTERSEG",
        "SUPER",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    };
    private static final String mathops[] = 
    {
        null,
       "+",
       "-",
       "*",
       "/",
       "%",
       "(-)",   // uminus
       "<<",    // shift
       "and",
       "or",
       "eor",
       "not",
       "<=",
       ">=",
       "<>",
       "<",
       ">",
       "=",
       "&",
       "|",
       "^",
       "!"
    };    
}
