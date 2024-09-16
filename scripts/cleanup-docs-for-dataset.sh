CODE_DIR=${1:-"$HOME/code/java/camel"}

# Cleanup the files (in 2 steps out of precaution)
find $CODE_DIR -type f -iname '*-component.xml' -ipath '*src/main/docs*' -exec rm -f {} \;
find $CODE_DIR -type f -iname '*-component.xml.md' -ipath '*src/main/docs*' -exec rm -f {} \;