CODE_DIR=${1:-"$HOME/code/java/camel}"}

# Based on https://tinyapps.org/blog/201701240700_convert_asciidoc_to_markdown.html

# Step 1: convert all component documentation to a docbook file
find $CODE_DIR -type f -iname '*-component.adoc' -ipath '*src/main/docs*' -exec asciidoc -b docbook {} \;

# Step 2: convert the docbook files to Markdown
find $CODE_DIR -type f -iname '*-component.xml' -ipath '*src/main/docs*' -exec pandoc -f docbook -t markdown_strict {} -o {}.md  \;

# Step 3: cleanup the files (in 2 steps out of precaution)
find $CODE_DIR -type f -iname '*-component.xml' -ipath '*src/main/docs*' -exec rm -f {} \;
find $CODE_DIR -type f -iname '*-component.xml.md' -ipath '*src/main/docs*' -exec rm -f {} \;