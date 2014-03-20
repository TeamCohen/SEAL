##############################################################################
#set the classpath for seal
##############################################################################

#for seal.properties, log4j.properties
export CP=`pwd`/config
#for seal classes
export CP=$CP:`pwd`/bin
#libraries used by seal
export CP=$CP:`pwd`/lib/log4j-1.2.14.jar
export CP=$CP:`pwd`/lib/json.jar
#lucene stuff used in offline seal
export CP=$CP:`pwd`/lib/lucene-core-3.0.1.jar
export CP=$CP:`pwd`/lib/lucene-demos-3.0.3-dev.jar
# httpclient used in cluewebsearcher
export CP=$CP:`pwd`/lib/httpmime-4.1.2.jar
export CP=$CP:`pwd`/lib/httpclient-4.1.2.jar
export CP=$CP:`pwd`/lib/httpcore-4.1.2.jar
export CP=$CP:`pwd`/lib/commons-codec-1.4.jar
export CP=$CP:`pwd`/lib/commons-logging-1.1.1.jar
export CP=$CP:`pwd`/lib/commons-io-2.0.1.jar

export CLASSPATH=$CP
