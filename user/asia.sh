export HOME=/usr0/wcohen/code/seal/seal
export BIN=com.rcwang.seal.asia.Asia
export JVM_ARG="-Xmx512M -Dfile.encoding=UTF-8"
export CP=.
export CP=$CP:$HOME/lib
export CP=$CP:$HOME/lib/log4j-1.2.14.jar
export CP=$CP:$HOME/lib/javamail-1.4.jar
export CP=$CP:$HOME/lib/jaf-1.1.jar
export CP=$CP:$HOME/lib/seal.jar

java $JVM_ARG -cp $CP $BIN $1 $2 $3 $4 $5 $6 $7 $8 $9
