#top level SEAL directory
$SEAL_DIR = '/usr0/wcohen/code/seal/';
#input list of concepts to learn wrappers for
$INPUT_FILE = "$SEAL_DIR/scripts/kdd-frequent-concepts.txt";
#where to save wrappers - must exist
$OUTPUT_LOG_DIR = '/usr2/bigdata/seal/test/kdd-wrapper-outputs/';
# wait time between calls
$WAIT_TIME = 10;

#don't change this stuff
$ASIA_BIN = 'com.rcwang.seal.asia.WrapperSavingAsia';
$CP = join(':', 
	   "$SEAL_DIR/config", "$SEAL_DIR/bin", "$SEAL_DIR/lib/log4j-1.2.14.jar",
	   "$SEAL_DIR/lib/json.jar",
	   "$SEAL_DIR/lib/lucene-core-3.0.1.jar","$SEAL_DIR/lib/lucene-demos-3.0.3-dev.jar");
$MAX_RUNS = 0;
$MIN_SCORE = 1.0;

my $n=0;
open(F,$INPUT_FILE) || die "can't open $INPUT_FILE";
while (my $line=<F>) {
    $n++;
    chop($line);
    my($count,$concept) = split(/\t/,$line);
    last if $count < $MIN_SCORE;
    last if $MAX_RUNS && ($n>$MAX_RUNS);
    print "===== Running ASIA on concept $n: $concept ====\n";
    $concept =~ s/^'//;
    $concept =~ s/'$//;
    $concept =~ s/\s/-/g;
    while (1) {
	open(G,"<seal-block.txt");
	chop($status = <G>);
	close(G);
	last unless $status=~/\S/;
	print "blocking 10s because seal-block contains a non-null value: [$status]...\n";
	sleep 10;
    }
    print "===== Exec Seal on concept $concept ====\n";
    print "executing: java -cp $CP $ASIA_BIN $concept >& $OUTPUT_LOG_DIR/$concept.log","\n";
    system("java -cp $CP $ASIA_BIN $concept >& $OUTPUT_LOG_DIR/$concept.log") && die;
    print "blocking $WAIT_TIME sec...\n";
    sleep $WAIT_TIME;
}
