#!/bin/bash

# Based on https://tinyapps.org/blog/201701240700_convert_asciidoc_to_markdown.html

simpleName=$(basename $1)
inputFile=$1
xmlFile=${inputFile/.adoc/.xml}
markDownFile=${inputFile/.adoc/.md}

logDir=$2
log=$logDir/$simpleName.log

printf "AsciiDoc to XML conversion for %s\n" "${simpleName}"
asciidoc -b docbook "${inputFile}" > "$log" 2>&1
if [ $? -ne 0 ] ; then
  printf "Failed AsciiDoc to XML conversion for %s\n" "${inputFile}" | tee -a "$log"
fi

printf "XML to Markdown conversion for %s\n" "${simpleName}"
pandoc -f docbook -t markdown_strict "${xmlFile}" -o "${markDownFile}" >> "$log" 2>&1
if [ $? -ne 0 ] ; then
  printf "Failed XML to Markdown conversion for %s\n" "$1" | tee -a "$log"
fi

