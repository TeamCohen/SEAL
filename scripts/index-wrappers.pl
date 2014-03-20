#top level SEAL directory
$SEAL_DIR = '/usr0/wcohen/code/seal/';

#parent of directory where wrappers are saved
$LOCAL_ROOT = '/usr2/bigdata/seal/test';

#don't change this stuff
$INDEXER_BIN = 'com.rcwang.seal.fetch.OfflineSearchIndexer';
$CP = join(':', 
	   "$SEAL_DIR/config", "$SEAL_DIR/bin", "$SEAL_DIR/lib/log4j-1.2.14.jar",
	   "$SEAL_DIR/lib/json.jar",
	   "$SEAL_DIR/lib/lucene-core-3.0.1.jar","$SEAL_DIR/lib/lucene-demos-3.0.3-dev.jar");

system("(cd $LOCAL_ROOT; java -cp $CP $INDEXER_BIN -wrappers)") && die;

