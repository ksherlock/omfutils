This is a collection of miscellaneous command-line java utilities for dealing with Apple IIgs OMF files.  (These depend on libomf (code.google.com/p/libomf/)).

dumpobj: inspired by the Apple/Orca utility of the same name.  Lists all the header and segment data in an OMF file.

lseg:  inspired by the GNO/ME utility of the same name.  Similar to dumpobj, but the information is more concise.

makedirect:  inspired by the Apple/APW utility of the same name.    Creates a direct-page/stack segment of the specified size.

makeobj:  inspired by the Tim Meekins utility ``mkobj''.  Converts a binary file to an OMF segment, which can then be linked.

Status:  All the utilities are usable, functional, and free of known bugs, though more functionality will be added as appropriate.