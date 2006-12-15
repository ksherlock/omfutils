/*
 * Created on Feb 25, 2006
 * Feb 25, 2006 5:25:41 PM
 */

/*
 * somewhat based on bsd and the original public domain att source code.
 */
public class GetOpt
{
    private String[] fArgs;
    private String fOptions;
    private String fPlace;
    
    private int fIndex;
    private int fOffset;
    
    private String fOptarg;
    
    public GetOpt(String[] args, String options)
    {
        fIndex = 0;
        fOffset = 0;
        fOptarg = null;
        fPlace = null;
        
        fArgs = args;
        fOptions = options;       
    }
    
    // will replace option strings with null in the data.
    public int Next()
    {
       fOptarg = null;
 
       if (fPlace == null || fOffset >= fPlace.length())
       {
           // go to the next one.
           fOffset = 0;
       }       
       
       
       if (fOffset == 0)
       {
           fOffset = 1;
           
           while (true)
           {
               int oldi = fIndex;
               
               
               if (fIndex >= fArgs.length) return -1;
               fPlace = fArgs[fIndex++];
               if (fPlace == null) continue;
               
               if (fPlace.charAt(0) != '-') continue;
               // option string... hurray!!
                            
               // "-" allow set fOffset=0 so that it will be
               // treated as a flag or an error.
               if (fPlace.length() == 1)
               {
                   fOffset = 0;
               }
               
               // "--" end of options.
               if (fPlace.compareTo("--") == 0)
               {
                   fArgs[oldi] = null;
                   return -1;
               }
               fArgs[oldi] = null;
               
               break;              
           }
       }
       // fPlace is the option string, fOffset is the index in it to check.
       int c = fPlace.charAt(fOffset++);
       
       // check if it's a valid value.
       int loc = fOptions.indexOf(c);
       if (c == ':' || loc == -1)
       {
           System.err.printf("Illegal option -- %1$c", c);
           return '?';
       }
       
       // valid option.  check for :
       if ((loc + 1) < fOptions.length() && fOptions.charAt(loc + 1) == ':')
       {
           // if more data in this option, that's the arg.
           if (fOffset < fPlace.length())
           {
               fOptarg = fPlace.substring(fOffset);
               fOffset = 0;
           }
           // next argv is the option argument
           else if (fArgs.length > fIndex)
           {
               fOptarg = fArgs[fIndex];
               fArgs[fIndex] = null;
               fIndex++;
           }
           else
           {
               System.err.printf("Option requires an argument -- %1$c", c);
               return '?';
           }
       }
       
       return c;
       
    }
    public String Argument()
    {
        return fOptarg;
    }
    
    // return the orginal arguments, compacted.
    public String[] CommandLine()
    {
        int i;
        int count = 0;
        for (i = 0; i < fArgs.length; i++)
        {
            if (fArgs[i] != null) count++;
        }
        if (count == i) return fArgs;
        String[] tmp = new String[count];
        count = 0;
        for (i = 0; i < fArgs.length; i++)
        {
            if (fArgs[i] != null) tmp[count++] = fArgs[i];
        }
        
        return tmp;
    }
}
