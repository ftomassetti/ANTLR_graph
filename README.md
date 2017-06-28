# ANTLR_graph

Generate graphs for ANTLR grammars.

You can run the program specifying the ANTLR grammar files to consider. It will generate DOT files that can be transformed
in images using graphviz.

It generate different files for the whole grammar:

![](examples/atn.png)

For single rules:

![](examples/atn_5.png)

Or for single rules inclusing all related states:

![](examples/clusters_for_expression.png)
