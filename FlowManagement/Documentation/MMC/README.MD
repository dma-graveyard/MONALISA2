#!/bin/sh

#
# HTML EDITION
#

# Build the documentation with

mvn process-resources 

#(or any subsequent phase).
# outputs into target/docs/


#
# PDF EDITION
#

#You were supposed to generate the pdf version with a -Dbackend=pdf, but for some reason it fails.

#Currently you can run asciidoctor tool manually if you wish to generate the pdf version

asciidoctor -b pdf -r asciidoctor-pdf -o proposal.pdf src/main/docs/proposal.adoc 
