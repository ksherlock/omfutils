## NAME ##

makeobj -- convert a binary file to an OMF segment.

## SYNOPSIS ##

`makeobj [options] infile`

## OPTIONS ##
| `-a align` | Set alignment (must be power of 2). |
|:-----------|:------------------------------------|
| `-n name` | Set segment name. Default is null. |
| `-l name`  | Set load name.  Default is null. |
| `-t kind` | Set segment kind  (CODE, DATA, INIT, or STACK).  Default is DATA. |
| `-o objfile` | Set output file name.  Default is _infile_.o. |
| `-h` | Display help information. |