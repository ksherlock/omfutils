## NAME ##

dumpobj -- dump an OMF file.

## SYNOPSIS ##

`dumpobj [options] file [segment ...]`

## OPTIONS ##

| `-H` | Dump headers only. |
|:-----|:-------------------|
| `-D` | Suppress hex dump. |
| `-d` | Disassemble CODE segments. |
| `-dd` | Disassemble all segments. |
| `-h` | Show help information. |

If no segments are specified, all segments will be dumped.

## FILES ##

`dumpobj` will attempt to load GS/OS information from the following locations:

  * `/etc/gsos.txt`
  * `/usr/etc/gsos.txt`
  * `/usr/local/etc/gsos.txt`
  * `gsos.txt`



`dumpobj` will attempt to load toolbox information from the following locations:

  * `/etc/tools.txt`
  * `/usr/etc/tools.txt`
  * `/usr/local/etc/tools.txt`
  * `tools.txt`