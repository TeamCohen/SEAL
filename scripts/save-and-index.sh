echo run this from the top-level SEAL directory as 'sh scripts save-and-index.sh'
echo you should edit the directories in these scripts and config files before running this:
echo
echo - scripts/seal.properties.saving-wrappers 
echo -- change stopwordsList from /usr0/wcohen/code/seal/lib/stopwords.txt
echo -- optionally change cacheDir from /usr2/bigdata/seal/cache/kdd 
echo -- change savedWrapperDir from /usr2/bigdata/seal/test/wrappers to SOMEWHERE/wrappers
echo -- ... and make sure that the directory SOMEWHERE exists
echo 
echo - scripts/seal.properties.indexing-wrappers
echo -- change stopwordsList from /usr0/wcohen/code/seal/lib/stopwords.txt
echo -- change localRoot from /usr2/bigdata/seal/test to to SOMEWHERE
echo -- ... and make sure that directory exists but SOMEWHERE/index does NOT exist
echo 
echo - scripts/save-wrappers.pl
echo -- change SEAL_DIR to be your seal cvs directory
echo -- optionally, change INPUT_FILE to be an appropriate list of concepts
echo -- change OUTPUT_LOG_DIR from '/usr2/bigdata/seal/test/kdd-wrapper-outputs/' to SOMEWHERE/something
echo -- ... and make sure that directory exists
echo
echo - scripts/index-wrappers.pl
echo -- change SEAL_DIR to be your seal cvs directory
echo -- change LOCAL_ROOT to the value of localRoot in seal.properties.indexing-wrappers
echo

cp scripts/seal.properties.saving-wrappers config/seal.properties
perl scripts/save-wrappers.pl

cp scripts/seal.properties.indexing-wrappers config/seal.properties
perl scripts/index-wrappers.pl
