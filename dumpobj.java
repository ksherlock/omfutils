import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import omf.*;

/*
 * Created on Feb 23, 2006
 * Feb 23, 2006 10:23:14 PM
 */

public class dumpobj
{
    private HashMap<Integer, String> fTools;
    private HashMap<Integer, String> fProdos;
    private HashMap<Integer, String> fGSOS;
    
    
    private dumpobj()
    {
        fTools = load_tools();
        fGSOS = load_gsos();
        fProdos = null;       
    }

    private static void usage()
    {
        System.out.println("dumpobj v 0.1");
        System.out.println("usage: dumpobj [options] file [segments]");
        
        System.out.println("options:");
        System.out.println("\t-H             dump headers only");
        System.out.println("\t-D             don't do hexdump");
        System.out.println("\t-d             disassemble CODE segments");
        System.out.println("\t-dd            disassemble all segments");
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
       GetOpt go = new GetOpt(args, "DHdh");
       
       dumpobj self = null;
       
       
       int fDisasm = 0;
       
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
           case 'd':
               fDisasm++;
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
           
           if (fDisasm > 0) self = new dumpobj();
           
           for (Iterator<OMF_Segment> iter = segments.iterator(); iter.hasNext(); )
           {
               OMF_Segment segment = iter.next();
               
               if ((fSegments.size() > 0) 
                       && (!fSegments.contains(segment.SegmentName().toUpperCase())))
                       continue;
               
               dumpheader(segment);
               
               
               
               if (!fHeaderOnly)
               {
                   int kind = segment.Kind();
                   if (fDisasm > 1 || fDisasm == 1 && (kind == OMF.KIND_CODE || kind == OMF.KIND_INIT))
                   {
                       self.disasm(segment);
                   }
                   else
                   {
                       dumpbody(segment, fHexDump);
                   }
               }
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
    
    private static String format_x2(int n, int length)
    {
        String s = Integer.toHexString(n);
        while(s.length() < length)
            s = "0" + s;
        
        return s;
    }
    private static String format_x(int n, int length)
    {
        return "$" + format_x2(n, length);
    }
    
    private static String format_arg(int operand, int size, int mode)
    {
        switch (mode & 0xfff0)
        {
        case mRelative:
            switch(size)
            {
            case 1:
                if ((operand & 0x80) == 0x80)
                    return "*-" + format_x(256 - operand, 2);
                return "*+" + format_x(2 + operand, 2);
                
            case 2:
                if ((operand & 0x8000) == 0x8000)
                    return "*-" + format_x(65536 - operand, 2);
                return "*+" + format_x(3 + operand, 2);                
            }
            return "";
            
        case mBlockMove:
            return format_x(operand >> 8,2) + "," + format_x(operand & 0xff, 2);

        default:
            return format_arg(format_x(operand, size * 2), mode);
        }
    }
    
    private static String format_arg(String base, int mode)
    {
        String args = "";
        
        switch(mode & 0xf000)
        {
        case mImmediate:
            args = "#" + base;
            break;
            
        case mDP:
        case mAbsolute:
        case mAbsoluteLong:                                
            args = base;
            break;

        case mRelative:
            args = base;
            break;
        case mBlockMove:
            args = base; // 
            break;
           
        case mDPIL:  
        case mAbsoluteIL:
            args = "[" + base + "]";
            break;
            
        case mDPI:
        case mAbsoluteI:
            args = "(" + base;
            if ((mode & m_S) == m_S)
            {
                mode &= (~m_S);
                args += ",s";
            }
            if ((mode & m_X) == m_X)
            {
                mode &= (~m_X);
                args += ",x";
            }
            args += ")";
            break;                                                
        
        }
        switch(mode & 0x0f00)
        {
        case m_X:
            args += ",x";
            break;
        case m_Y:
            args += ",y";
            break;
        case m_S:
            args += ",s";
        }                           
        
        return args;
    }
    
    
    private void disasm(OMF_Segment seg)
    {
        int pc = 0;
        boolean fX = true;
        boolean fM = true;

        BitSet branches = new BitSet();
        
        String type;
        
        if (seg.Private())
        {
            type = seg.Kind() == OMF.KIND_DATA ? "PRIVDATA"  : "PRIVATE";
        }
        else
        {
            type = seg.Kind() == OMF.KIND_DATA ? "DATA"  : "START";
                       
        }
        
        System.out.printf("%1$s\t%2$s\t%3$s\n",
                seg.SegmentName().trim(), type, seg.LoadName().trim());
        
        if ((seg.Kind() & (~1)) != 0 || (seg.Attributes() & (~OMF.KIND_PRIVATE)) != 0)
        {
            System.out.printf("\tKIND $%1$04x\n",
                    seg.Kind() | seg.Attributes());
        }
        
        
        ListIterator<OMF_Opcode> iter = seg.Opcodes();

       
        
        while(iter.hasNext())
        {
            OMF_Opcode op = iter.next();
            switch(op.Opcode())
            {
            
            case OMF.OMF_DS:
                print_line(pc, "ds", format_x(op.CodeSize(), 2), "");
                pc += op.CodeSize();
                break;
            case OMF.OMF_EQU:
                {
                    OMF_Equ e = (OMF_Equ)op;
                    print_line(e.toString(), "equ", format_expr(e.Expression()));
                }
                break;

            case OMF.OMF_GEQU:
            {
                OMF_Equ e = (OMF_Equ)op;
                print_line(e.toString(), "gequ", format_expr(e.Expression()));
            }
            break;                
                
                
            case OMF.OMF_GLOBAL:
                print_line(op.toString(), "entry", "");
                break;
            case OMF.OMF_LOCAL:
                print_line(op.toString(), "anop", "");
                break;
            case OMF.OMF_ALIGN:
                print_line("", "align", format_x( ((OMF_Align)op).Value(), 4));
                break;
                
            case OMF.OMF_LCONST:
                {
                    int size = op.CodeSize();
                    int i = 0;
                    byte[] data =((OMF_Const)op).Data();
                    
loop:                    while (i < size)
                    {
                        int opcode = data[i] & 0xff;
                        
                       
                        int mode = modes[opcode];
                        int argsize = mode & 0x000f;

                        if (branches.get(pc))
                            System.out.println();
                        
                        // adjust operand size if m/x bits apply.
                        if ( ((mode & m_I) == m_I) && fX)
                        {
                            argsize++;
                        }
                        if ( ((mode & m_M) == m_M) && fM)
                        {
                            argsize++;
                        }
                        
                        int arg = 0;
                        String args = "";
                                                
                        if (i + argsize >= size)
                        {
                            // TODO  -- check for mvn/mvp, handle as special case.
                            if (i +1 == size)
                            {
                                boolean process = true;
                                op = iter.next();
                                if (op.CodeSize() == argsize) switch(op.Opcode())
                                {
                                case OMF.OMF_EXPR:
                                case OMF.OMF_ZPEXPR:
                                case OMF.OMF_BKEXPR:  
                                case OMF.OMF_LEXPR:
                                    args = format_expr((OMF_Expr)op);
                                    break;

                                case OMF.OMF_RELEXPR:
                                    args = format_expr((OMF_RelExpr)op);
                                    break;

                                default : 
                                    iter.previous();
                                    process = false;
                                }
                                
                                if (process)
                                {
                                    print_line(pc, to_opcode(opcode), args, format_x2(opcode, 2));
                                    pc += 1 + argsize;
                                    i++;
                                    continue;
                                }
                                
                            }

                            for(;;)
                            {
                                print_line(pc, "dc", "i1'" + format_x(opcode,2) + "'", format_x2(opcode, 2));
                                pc++;
                                i++;
                                if (i >= size) break;
                                opcode = data[i]; 
                            }
                            continue;
                        }
                        
                        
                        switch(argsize)
                        {
                        case 1: 
                            arg = OMF.Read8(data, i + 1,0);
                            break;
                        case 2:
                            arg = OMF.Read16(data, i + 1,0);
                            break;
                        case 3:
                            arg = OMF.Read24(data, i + 1,0);
                            break;
                        }
                                               
                        switch(opcode)
                        {
                        case 0x40: // rti
                        case 0x60: // rts
                        case 0x6b: // rtl
                            branches.set(pc +1);
                            break;
                            
                        case 0x90: // bxx
                        case 0xb0:
                        case 0xf0:
                        case 0x30:
                        case 0xd0:
                        case 0x10:
                        case 0x80:
                        case 0x50:
                        case 0x70:
                            if (arg < 0x80) branches.set(pc + arg + 2);
                            else branches.set(pc + 0x100 - arg);
                            break;
                            
                        case 0x82: //brl
                            // TODO -- check for debug names.
                            if (arg < 0x8000) branches.set(pc + 3 + arg);
                            else branches.set(pc + 0x10000 - arg);
                            break;
                            
                        case 0xc2: // rep
                            if ((arg & 0x20) == 0x20)
                            {
                                System.out.println("\tLONGA ON");
                                fM = true;
                            }
                            if ((arg & 0x10) == 0x10)  
                            {
                                System.out.println("\tLONGI ON");
                                fX = true;
                            }  
                            break;
                        case 0xe2:
                            if ((arg & 0x20) == 0x20)
                            {
                                System.out.println("\tLONGA OFF");
                                fM = false;
                            }
                            if ((arg & 0x10) == 0x10)  
                            {
                                System.out.println("\tLONGI OFF");
                                fX = false;
                            }
                            break;
                        case 0xfb: //xce
                            if (i > 0)
                            {
                                if (data[i-1] == 0x18)
                                {
                                    // clc/xce --> go to native, but still 8 bit mx registers.
                                    
                                }
                                else if (data[i-1] == 0x38)
                                {
                                    //sec/xce --> go emulation
                                    System.out.println("\tLONGA OFF");
                                    System.out.println("\tLONGI OFF");
                                    fM = fX = false;
                                }
                            }
                            break;
                            
                        case 0xa2: // ldx #
                            if (fX && fTools != null)
                            {
                                // check for 22 00 00 e1
                                if (size > i + 2 + 4)
                                {
                                    if (data[i + 3] == 0x22 && OMF.Read24(data, i + 4, 0) == kTOOL_STACK)
                                    {
                                        String s = fTools.get(new Integer(arg));
                                        if (s != null)
                                        {
                                            print_line(pc, s, "", "");
                                            pc += 7;
                                            i += 7;
                                            continue loop;   
                                        }
                                        
                                        
                                    }
                                }
                            }
                            break;
                        case 0x22: // JSL ... check if GS/OS call...
                            if (arg == kGSOS_INLINE)
                            {
                                // TODO check for 6 bytes or 2 bytes + 1 expression or 0 bytes + 2 expression
                                if (size > i + 3 + 6)
                                {
                                    int a,b;
                                    String s = null;
                                    a = OMF.Read16(data, i + 4, 0);
                                    b = OMF.Read32(data, i + 6, 0);
                                    if (fGSOS != null) s = fGSOS.get(a);
                                    
                                    if (s == null) s = "_GSOS_" + format_x2(a, 4);
                                     
                                    print_line(pc, s, format_x(b, 8),"");
                                    pc += 10;
                                    i += 10;
                                    continue loop;

                                }
                                else if (size > i + 3 + 2)
                                {
                                    
                                }
                                else if (size == i + 1)
                                {
                                    
                                }
                                
                                
                            }
                            break;
                         
                        case 0x00:  // BRK -- treat long runs of 0s as a DS
                            if (arg == 0)
                            {
                            
                                int j = i + 2 ;
                                for (; j < size; j++)
                                {
                                    if (data[j] != 0) break;
                                }
                                int dsize = j - i;
                                if (dsize > 2)
                                {
                                    print_line(pc, "ds", format_x(dsize, 4),"");
                                    i += dsize;
                                    pc += dsize;
                                    continue loop;
                                }
                            }
                            break;

                        }
                        
                        args = format_arg(arg, argsize, mode);
                        
                        String hexbytes = format_x2(opcode, 2);
                        switch(argsize)
                        {
                        case 3:
                            hexbytes += " " +format_x2(arg & 0xff ,2);
                            arg = arg >> 8;
                        case 2:
                            hexbytes += " " +format_x2(arg & 0xff ,2);
                            arg = arg >> 8;
                        case 1:
                            hexbytes += " " +format_x2(arg & 0xff ,2);
                        }
                        
                        print_line(pc, to_opcode(opcode), args, hexbytes);
                        


                        pc += 1 + argsize;
                        i += 1 + argsize;
                    }
                }
                break;
                
            }
        }
        
        
        System.out.printf("\tEND\n\n");
        
        
        
        
        
    }
    
    private static String to_opcode(int opcode)
    {
        return opcodes.substring(opcode * 3, opcode * 3 + 3);
    }
    
    private static String format_expr(OMF_Expr expr)
    {
        return format_expr(expr.Expression());
    }
    private static String format_expr(OMF_RelExpr expr)
    {        
        return format_expr(expr.Expression());
    }
    private static String format_expr(ArrayList expr)
    {
        // TODO -- support all ops, make sure in correct order of operation.
        
       ArrayList<String> stack = new ArrayList<String>();
       
       int size = expr.size();
       int i;
       int top = 0;
       for (i = 0; i < size; i++)
       {
           Object o = expr.get(i);
           if (o instanceof OMF_Label)
           {
               stack.add(o.toString());
               top++;
               continue;
           }
           else if (o instanceof OMF_Number)
           {
               int v = ((OMF_Number)o).Value();
               
               if (v < 0)
               {
                   Object o2 = expr.get(i + 1);
                   if ((o2 instanceof Integer) && ((Integer)o2).intValue() == OMF_Expression.EXPR_SHIFT)
                   {
                       stack.add(Integer.toString(v));
                       top++;
                       continue;
                   }
               }
               
               
               stack.add(format_x(v, 4) );
               top++;
               continue;
           }
           else if (o instanceof Integer)
           {
               Integer mathop = (Integer)o;
               switch(mathop.intValue())
               {
               case OMF_Expression.EXPR_ADD:
                   {
                       String a,b;
                       a = stack.remove(--top);
                       b = stack.remove(--top);
                       stack.add(b + "+" + a);
                       top++;
                       break;
                   }
               case OMF_Expression.EXPR_SUB:
                   {
                       String a,b;
                       a = stack.remove(--top);
                       b = stack.remove(--top);
                       stack.add(b + "-" + a);
                       top++;
                       break;
                   }
               case OMF_Expression.EXPR_MUL:
                   {
                       String a,b;
                       a = stack.remove(--top);
                       b = stack.remove(--top);
                       stack.add(b + "*" + a);
                       top++;
                       break;
                   }               
               case OMF_Expression.EXPR_SHIFT:
                   {
                       String a,b;
                       a = stack.remove(--top);
                       b = stack.remove(--top);
                       stack.add(b + "|" + a);
                       top++;
                       break;                   
                   }
               }
               
           }
       }
       
       return stack.get(top - 1);

    }
    
    private static void print_line(int pc, String opcode, String operand, String bytes)
    {
        System.out.printf("\t%1$-4s %2$-30s ; %3$06x:  %4$s\n",
                opcode, operand, pc, bytes);       
    }
    
    private static void print_line(String lab, String opcode, String operand)
    {
        System.out.printf("%1$s\t%2$-4s %3$s\n",
                lab, opcode, operand);
    }
    
    
    private static HashMap<Integer, String>load_file(File f)
    {
        HashMap<Integer, String> map = null;
        boolean ok;
        
        try
        {
            RandomAccessFile raf = new RandomAccessFile(f, "r");

            map = new HashMap<Integer, String>();
            
            for(;;)
            {
                int c;
                String line = raf.readLine();
                if (line == null) break;
                c = line.charAt(0);
                if (c == ';') continue;
                if (c == '#') continue;
                line = line.trim();
                if (line.length() == 0) continue;
                /*
                 * xxxx [a-zA-Z0-9]
                 * 
                 */
                int tn = 0;
                ok = true;
                int i;
                for (i = 0; i < 4; i++)
                {
                    c = line.charAt(i);
                    switch(c)
                    {
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        tn = tn << 4 | (c - '0');
                        break;
                    case 'a':
                    case 'b':
                    case 'c':
                    case 'd':
                    case 'e':
                    case 'f':
                        tn = tn << 4 | (c - 'a' + 10);
                        break;
                    case 'A':
                    case 'B':
                    case 'C':
                    case 'D':
                    case 'E':
                    case 'F':
                        tn = tn << 4 | (c - 'A' + 10);
                        break;
                        
                    default: ok = false;
                    }
                    if (!ok) break;
                    
                }
                if (!ok) continue;
                
                for (;;)
                {
                    c = line.charAt(i);
                    if (c == ' ' || c == '\t') i++;
                    else break;
                }
                String s = line.substring(i);
                if (s.length() == 0) continue;
                
                map.put(new Integer(tn), "_" + s);
            }           
        }
        catch (Exception e)
        {
        }
        
        if (map.size() == 0) map = null;
        return map;        
    }
    
    private static HashMap<Integer, String>load_gsos()
    {
        String paths[] =
        {
            "/etc/gsos.txt",
            "/usr/etc/gsos.txt",
            "/usr/local/etc/gsos.txt",
            "gsos.txt"
        };
        
        HashMap<Integer, String> map = null;
               
        File f;
        
        for (String s: paths)
        {
            f = new File(s);
            if (f.exists())
            {
                map = load_file(f);
                if (map != null) return map;
            }
        }
        return null;
    }
    
    
    private static HashMap<Integer, String>load_tools()
    {
        String paths[] =
        {
            "/etc/tools.txt",
            "/usr/etc/tools.txt",
            "/usr/local/etc/tools.txt",
            "tools.txt"
        };
        
        HashMap<Integer, String> map = null;
               
        File f;
        
        for (String s: paths)
        {
            f = new File(s);
            if (f.exists())
            {
                map = load_file(f);
                if (map != null) return map;
            }
        }
        return null;
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
    
    private static final String opcodes = 
      "brkoracoporatsboraaslora" +
      "phporaaslphdtsboraaslora" +
      "bploraoraoratrboraaslora" +
      "clcorainctcstrboraaslora" +
      "jsrandjslandbitandroland" +
      "plpandrolpldbitandroland" +
      "bmiandandandbitandroland" +
      "secanddectscbitandroland" +
      "rtieorwdmeormvpeorlsreor" +
      "phaeorlsrphkjmpeorlsreor" +
      "bvceoreoreormvneorlsreor" +
      "clieorphytcdjmleorlsreor" +
      "rtsadcperadcstzadcroradc" +
      "plaadcrorrtljmpadcroradc" +
      "bvsadcadcadcstzadcroradc" +
      "seiadcplytdcjmpadcroradc" +
      "brastabrlstastystastxsta" +
      "deybittxaphbstystastxsta" +
      "bccstastastastystastxsta" +
      "tyastatxstxystzstastzsta" +
      "ldyldaldxldaldyldaldxlda" +
      "tayldataxplbldyldaldxlda" +
      "bcsldaldaldaldyldaldxlda" +
      "clvldatsxtyxldyldaldxlda" +
      "cpycmprepcmpcpycmpdeccmp" +
      "inycmpdexwaicpycmpdeccmp" +
      "bnecmpcmpcmppeicmpdeccmp" +
      "cldcmpphxstpjmlcmpdeccmp" +
      "cpxsbcsepsbccpxsbcincsbc" +
      "inxsbcnopxbacpxsbcincsbc" +
      "beqsbcsbcsbcpeasbcincsbc" +
      "sedsbcplxxcejsrsbcincsbc"
      ;
    
    private static final int mImplied =      0x0000;
    private static final int mImmediate =    0x1000;
    private static final int mAbsolute =     0x2000;
    private static final int mAbsoluteI =    0x3000;
    private static final int mAbsoluteIL =   0x4000;
    private static final int mAbsoluteLong = 0x5000;
    private static final int mDP =           0x6000;
    private static final int mDPI =          0x7000;
    private static final int mDPIL =         0x8000;
    private static final int mRelative =     0x9000;
    private static final int mBlockMove =    0xa000;
    
    private static final int m_S =          0x0100;
    private static final int m_X =          0x0200;
    private static final int m_Y =          0x0400;
    
    private static final int m_M =          0x0010;
    private static final int m_I =          0x0020;
                                          
    private static int modes[] =
    {
        1 | mAbsolute,              // 00 brk #imm
        1 | mDPI | m_X,             // 01 ora (dp,x)
        1 | mAbsolute,              // 02 cop #imm
        1 | mDP | m_S,              // 03 ora ,s
        1 | mDP,                    // 04 tsb <dp
        1 | mDP,                    // 05 ora <dp
        1 | mDP,                    // 06 asl <dp
        1 | mDPIL,                  // 07 ora [dp]
        0 | mImplied,               // 08 php
        1 | mImmediate | m_M,       // 09 ora #imm
        0 | mImplied,               // 0a asl a
        0 | mImplied,               // 0b phd
        2 | mAbsolute,              // 0c tsb |abs
        2 | mAbsolute,              // 0d ora |abs
        2 | mAbsolute,              // 0e asl |abs
        3 | mAbsoluteLong,          // 0f ora >abs

        1 | mRelative,              // 10 bpl
        1 | mDPI | m_Y,             // 11 ora (dp),y
        1 | mDPI,                   // 12 ora (dp)
        1 | mDPI | m_S | m_Y,       // 13 ora ,s,y
        1 | mDP,                    // 14 trb <dp
        1 | mDP | m_X,              // 15 ora <dp,x
        1 | mDP | m_X,              // 16 asl <dp,x
        1 | mDPIL | m_Y,            // 17 ora [dp],y
        0 | mImplied,               // 18 clc
        2 | mAbsolute | m_Y,        // 19 ora |abs,y
        0 | mImplied,               // 1a inc a
        0 | mImplied,               // 1b tcs
        2 | mAbsolute,              // 1c trb |abs
        2 | mAbsolute | m_X,        // 1d ora |abs,x
        2 | mAbsolute | m_X,        // 1e asl |abs,x
        3 | mAbsoluteLong | m_X,    // 1f ora >abs,x
        
        2 | mAbsolute,              // 20 jsr |abs
        1 | mDPI | m_X,             // 21 and (dp,x)
        3 | mAbsoluteLong,          // 22 jsl >abs
        1 | mDP | m_S,              // 23 and ,s
        1 | mDP,                    // 24 bit <dp
        1 | mDP,                    // 25 and <dp
        1 | mDP,                    // 26 rol <dp
        1 | mDPIL,                  // 27 and [dp]
        0 | mImplied,               // 28 plp
        1 | mImmediate | m_M,       // 29 and #imm
        0 | mImplied,               // 2a rol a
        0 | mImplied,               // 2b pld
        2 | mAbsolute,              // 2c bit |abs
        2 | mAbsolute,              // 2d and |abs
        2 | mAbsolute,              // 2e rol |abs
        3 | mAbsoluteLong,          // 2f and >abs
        
        1 | mRelative,              // 30 bmi 
        1 | mDPI | m_Y,             // 31 and (dp),y
        1 | mDPI,                   // 32 and (dp)
        1 | mDPI | m_S | m_Y,       // 33 and ,s,y
        1 | mDP | m_X,              // 34 bit dp,x
        1 | mDP | m_X,              // 35 and dp,x
        1 | mDP | m_X,              // 36 rol <dp,x
        1 | mDPIL | m_Y,            // 37 and [dp],y
        0 | mImplied,               // 38 sec
        2 | mAbsolute | m_Y,        // 39 and |abs,y
        0 | mImplied,                 // 3a dec a
        0 | mImplied,                 // 3b tsc
        2 | mAbsolute | m_X,                 // 3c bits |abs,x
        2 | mAbsolute | m_X,                  // 3d and |abs,x
        2 | mAbsolute | m_X,                  // 3e rol |abs,x
        3 | mAbsoluteLong | m_X,                 // 3f and >abs,x
        
        0 | mImplied,                  // 40 rti
        1 | mDPI | m_X,                  // 41 eor (dp,x)
        1 | mAbsolute,                 // 42 wdm #imm
        1 | mDP | m_S,                 // 43 eor ,s
        2 | mBlockMove,                 // 44 mvp x,x
        1 | mDP,                  // 45 eor dp
        1 | mDP,                  // 46 lsr dp
        1 | mDPIL | m_Y,                 // 47 eor [dp],y
        0 | mImplied,                  // 48 pha
        1 | mImmediate | m_M,             // 49 eor #imm
        0 | mImplied,                  // 4a lsr a
        0 | mImplied,                 // 4b phk
        2 | mAbsolute,                  // 4c jmp |abs
        2 | mAbsolute,                  // 4d eor |abs
        2 | mAbsolute,                  // 4e lsr |abs
        3 | mAbsoluteLong,                 // 4f eor >abs      
        
        1 | mRelative,        // 50 bvc
        1 | mDPI | m_Y,                  // 51 eor (dp),y
        1 | mDPI,                 // 52 eor (dp)
        1 | mDPI | m_S | m_Y,                 // 53 eor ,s,y
        2 | mBlockMove,                 // 54 mvn x,x
        1 | mDP | m_X,                  // 55 eor dp,x
        1 | mDP | m_X,                  // 56 lsr dp,x
        1 | mDPIL | m_Y,                 // 57 eor [dp],y
        0 | mImplied,                  // 58 cli
        2 | mAbsolute | m_Y,                  // 59 eor |abs,y
        0 | mImplied,                 // 5a phy
        0 | mImplied,                 // 5b tcd
        3 | mAbsoluteLong,                 // 5c jml >abs
        2 | mAbsolute | m_X,                  // 5d eor |abs,x
        2 | mAbsolute | m_X,                  // 5e lsr |abs,x
        3 | mAbsoluteLong | m_X,                 // 5f eor >abs,x

        0 | mImplied,                  // 60 rts
        1 | mDPI | m_X,                  // 61 adc (dp,x)
        2 | mRelative,       // 62 per |abs
        1 | mDP | m_S,                 // 63 adc ,s
        1 | mDP,                 // 64 stz <dp
        1 | mDP,                  // 65 adc <dp
        1 | mDP,                  // 66 ror <dp
        1 | mDPIL,                 // 67 adc [dp]
        0 | mImplied,                  // 68 pla
        1 | mImmediate | m_M,              // 69 adc #imm
        0 | mImplied,                  // 6a ror a 
        0 | mImplied,                 // 6b rtl
        2 | mAbsoluteI,                  // 6c jmp (abs)
        2 | mAbsolute,                  // 6d adc |abs
        2 | mAbsolute,                  // 6e ror |abs
        3 | mAbsoluteLong,                 // 6f adc >abs
  
        1 | mRelative,        // 70 bvs
        1 | mDPI | m_Y,                  // 71 adc (dp),y
        1 | mDPI,                 // 72 adc (dp)
        1 | mDPI | m_S | m_Y,                 // 73 adc ,s,y
        1 | mDP | m_X,                 // 74 stz dp,x
        1 | mDP | m_X,                  // 75 adc dp,x
        1 | mDP | m_X,                  // 76 ror dp,x
        1 | mDPIL | m_Y,                 // 77 adc [dp],y
        0 | mImplied,                  // 78 sei
        2 | mAbsolute | m_Y,                  // 79 adc |abs,y
        0 | mImplied,                 // 7a ply
        0 | mImplied,                 // 7b tdc
        2 | mAbsoluteI | m_X,                 // 7c jmp (abs,x)
        2 | mAbsolute | m_X,                  // 7d adc |abs,x
        2 | mAbsolute | m_X,                  // 7e ror |abs,x
        3 | mAbsoluteLong | m_X,                 // 7f adc >abs,x
        
        1 | mRelative,       // 80 bra 
        1 | mDPI | m_X,                  // 81 sta (dp,x)
        2 | mRelative,       // 82 brl |abs
        1 | mDP | m_S,                 // 83 sta ,s
        1 | mDP,                  // 84 sty <dp
        1 | mDP,                  // 85 sta <dp
        1 | mDP,                  // 86 stx <dp
        1 | mDPIL,                 // 87 sta [dp]
        0 | mImplied,                  // 88 dey
        1 | mImmediate | m_M,            // 89 bit #imm
        0 | mImplied,                  // 8a txa
        0 | mImplied,                 // 8b phb
        2 | mAbsolute,                  // 8c sty |abs
        2 | mAbsolute,                  // 8d sta |abs
        2 | mAbsolute,                  // 8e stx |abs
        3 | mAbsoluteLong,                 // 8f sta >abs
        
        1 | mRelative,        // 90 bcc
        1 | mDPI | m_Y,                  // 91 sta (dp),y
        1 | mDPI,                 // 92 sta (dp)
        1 | mDPI | m_S | m_Y,                 // 93 sta ,s,y
        1 | mDP | m_X,                  // 94 sty dp,x
        1 | mDP | m_X,                  // 95 sta dp,x
        1 | mDP | m_Y,                  // 96 stx dp,y
        1 | mDPIL | m_Y,                 // 97 sta [dp],y
        0 | mImplied,                  // 98 tya
        2 | mAbsolute | m_Y,                  // 99 sta |abs,y
        0 | mImplied,                  // 9a txs
        0 | mImplied,                 // 9b txy
        2 | mAbsolute,                 // 9c stz |abs
        2 | mAbsolute | m_X,                  // 9d sta |abs,x
        2 | mAbsolute | m_X,                 // 9e stz |abs,x
        3 | mAbsoluteLong | m_X,                 // 9f sta >abs,x
        
        1 | mImmediate | m_I,             // a0 ldy #imm
        1 | mDPI | m_X,                  // a1 lda (dp,x)
        1 | mImmediate | m_I,             // a2 ldx #imm
        1 | mDP | m_S,                 // a3 lda ,s
        1 | mDP,                  // a4 ldy <dp
        1 | mDP,                  // a5 lda <dp
        1 | mDP,                  // a6 ldx <dp
        1 | mDPIL,                 // a7 lda [dp]
        0 | mImplied,                  // a8 tay
        1 | mImmediate | m_M,             // a9 lda #imm
        0 | mImplied,                  // aa tax
        0 | mImplied,                 // ab plb
        2 | mAbsolute,                  // ac ldy |abs
        2 | mAbsolute,                  // ad lda |abs
        2 | mAbsolute,                  // ae ldx |abs
        3 | mAbsoluteLong,                 // af lda >abs   
        
        1 | mRelative,        // b0 bcs
        1 | mDPI | m_Y,                  // b1 lda (dp),y
        1 | mDPI,                 // b2 lda (dp)
        1 | mDPI | m_S | m_Y,                 // b3 lda ,s,y
        1 | mDP | m_X,                  // b4 ldy <dp,x
        1 | mDP | m_X,                  // b5 lda <dp,x
        1 | mDP | m_Y,                  // b6 ldx <dp,y
        1 | mDPIL | m_Y,                 // b7 lda [dp],y
        0 | mImplied,                  // b8 clv
        2 | mAbsolute | m_Y,                  // b9 lda |abs,y
        0 | mImplied,                  // ba tsx
        0 | mImplied,                 // bb tyx
        2 | mAbsolute | m_X,                  // bc ldy |abs,x
        2 | mAbsolute | m_X,                  // bd lda |abs,x
        2 | mAbsolute | m_Y,                  // be ldx |abs,y
        3 | mAbsoluteLong | m_X,                 // bf lda >abs,x
        
        1 | mImmediate | m_I,             // c0 cpy #imm
        1 | mDPI | m_X,                  // c1 cmp (dp,x)
        1 | mAbsolute,                 // c2 rep #
        1 | mDP | m_S,                 // c3 cmp ,s
        1 | mDP,                  // c4 cpy <dp
        1 | mDP,                  // c5 cmp <dp
        1 | mDP,                  // c6 dec <dp
        1 | mDPIL,                 // c7 cmp [dp]
        0 | mImplied,                  // c8 iny
        1 | mImmediate | m_M,             // c9 cmp #imm
        0 | mImplied,                  // ca dex
        0 | mImplied,                 // cb WAI
        2 | mAbsolute,                  // cc cpy |abs
        2 | mAbsolute,                  // cd cmp |abs
        2 | mAbsolute,                  // ce dec |abs
        3 | mAbsoluteLong,                 // cf cmp >abs
        
        1 | mRelative,                  // d0 bne
        1 | mDPI | m_Y,                 // d1 cmp (dp),y
        1 | mDPI,                       // d2 cmp (dp)
        1 | mDPI | m_S | m_Y,           // d3 cmp ,s,y
        1 | mDPI,                       // d4 pei (dp)
        1 | mDP | m_X,                  // d5 cmp dp,x
        1 | mDP | m_X,                  // d6 dec dp,x
        1 | mDPIL | m_Y,                // d7 cmp [dp],y
        0 | mImplied,                   // d8 cld
        2 | mAbsolute | m_Y,            // d9 cmp |abs,y
        0 | mImplied,                   // da phx
        0 | mImplied,                   // db stp
        2 | mAbsoluteIL,                // dc jml [abs]
        2 | mAbsolute | m_X,            // dd cmp |abs,x
        2 | mAbsolute | m_X,            // de dec |abs,x
        3 | mAbsoluteLong | m_X,        // df cmp >abs,x
        
        1 | mImmediate | m_I,           // e0 cpx #imm
        1 | mDPI | m_X,                 // e1 sbc (dp,x)
        1 | mAbsolute,                  // e2 sep #imm
        1 | mDP | m_S,                  // e3 sbc ,s
        1 | mDP,                        // e4 cpx <dp
        1 | mDP,                        // e5 sbc <dp
        1 | mDP,                        // e6 inc <dp
        1 | mDPIL,                      // e7 sbc [dp]
        0 | mImplied,                   // e8 inx
        1 | mImmediate| m_M,            // e9 sbc #imm
        0 | mImplied,                   // ea nop
        0 | mImplied,                   // eb xba
        2 | mAbsolute,                  // ec cpx |abs
        2 | mAbsolute,                  // ed abc |abs
        2 | mAbsolute,                  // ee inc |abs
        3 | mAbsoluteLong,              // ef sbc >abs
        
        1 | mRelative,                  // f0 beq
        1 | mDPI | m_Y,                 // f1 sbc (dp),y
        1 | mDPI,                       // f2 sbc (dp)
        1 | mDPI | m_S | m_Y,           // f3 sbc ,s,y
        2 | mAbsolute,                  // f4 pea |abs
        1 | mDP | m_X,                  // f5 sbc dp,x
        1 | mDP | m_X,                  // f6 inc dp,x
        1 | mDPIL | m_Y,                // f7 sbc [dp],y
        0 | mImplied,                   // f8 sed
        2 | mAbsolute | m_Y,            // f9 sbc |abs,y
        0 | mImplied,                   // fa plx
        0 | mImplied,                   // fb xce
        2 | mAbsoluteI,                 // fc jsr (abs)
        2 | mAbsolute | m_X,            // fd sbc |abs,x
        2 | mAbsolute | m_X,            // fe inc |abs,x
        3 | mAbsoluteLong | m_X,        // ff sbc >abs,x      

    };
    
    private static final String paths[] = 
    {
        "",
        "/usr/local/etc",
        "/usr/etc",
        "/etc"
    };
    
    
    private static final int kGSOS_INLINE =     0xe100a8;    /* gs/os inline entry */
    private static final int kGSOS_STACK =      0xe100b0;    /* gs/os stack entry */
    private static final int kPRODOS_MLI =      0xbf00;      /* prodos 8 mli inline */
    private static final int kTOOL_STACK =      0xe10000;    /* toolbox stack entry */
    private static final int kTOOL_STACK_ALT =  0xe10004;    /* 2nd toolbox stack entry */


    
}
