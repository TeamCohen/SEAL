SEAL
====

Set Expander for Any Language

ABOUT THIS VERSION OF SEAL
--------------------------

This is a branch of Richard's thesis version of SEAL, created by
William Cohen. The development repository resides on a private lab
server, but we push stable revisions here.

SOME EXAMPLES
-------------

1) Set yourself up to run things

```
    % cd [this directory]
    % ant 
    % . user/init.sh         #set java classpath
```
2) Configure SEAL for online use (this properties file puts the cache and output in local subdirectories)

```
    % cp config/seal.properties.online config/seal.properties
    % [edit seal.properties to set path to stopword list]
    % java -Xmx512M -Dfile.encoding=UTF-8 com.rcwang.seal.expand.Seal "Hugo Chavez" "Fidel Castro"
```
        
Output is stored in the directory ./result in xml format --- there are also log files stored in ./log.
To see the first 20 lines of the most recent result:
    
```
  % xml_pp result/`ls -t result/ | head -1` | head -20  

  [output]
  <?xml version="1.0" encoding="UTF-8"?>
  <response elapsedTimeInMS="959">
    <setting>
      <seeds>
	<seed>Hugo Chavez</seed>
	<seed>Fidel Castro</seed>
      </seeds>
      <extract-language>un</extract-language>
      <fetch-language>un</fetch-language>
    </setting>
    <entities numEntities="1223">
      <entity WLW="15.02177983989703" rank="1" score="0.9999999903382715">Fidel Castro</entity>
      <entity WLW="15.02177983989703" rank="2" score="0.9999999903203863">Hugo Chavez</entity>
      <entity WLW="4.419369262270404" rank="3" score="0.29162216422379256">Venezuela</entity>
      <entity WLW="3.2272026451721794" rank="4" score="0.2119700520378352">Honduras</entity>
      <entity WLW="3.0190643378798216" rank="5" score="0.19806372746885645">Cuba</entity>
      <entity WLW="2.6883916054897687" rank="6" score="0.17597052255044113">evo-morales</entity>
      <entity WLW="2.6846663273172364" rank="7" score="0.17572162592034407">Barack Obama</entity>
      <entity WLW="1.7202723621669884" rank="8" score="0.11128766567479131">Bolivia</entity>
      <entity WLW="1.4805898357010734" rank="9" score="0.09527378048626035">manuel-zelaya</entity>
```

3) Configure SEAL for offline use.  

First, you need to get yourself an local directory of useful files for
seal, and index it. There is tarball of one with an index in this
distribution under lib/offline-seal-data.tgz.  Unpack this

```
 % cp lib/offline-seal-data.tgz SOMEWHERE
 % (cd SOMEWHERE; tar -xzf offline-seal-data.tgz)
```

Under your directory SOMEWHERE you will find one directory,
offline-seal-data, with two subdirectories, kdd/ and kdd-index/.
You need to tell seal where this is

```
  % cp config/seal.properties.offline config/seal.properties
```

Now edit `config/seal.properties`. SOMEWHERE should be a relative path, or an absolute path from / (don't use ~).

```
localDir = kdd
localRoot = SOMEWHERE/offline-seal-data
indexDir = SOMEWHERE/offline-seal-data/kdd-index
```

Now run OfflineSeal:

```
  % java -Xmx512M -Dfile.encoding=UTF-8 com.rcwang.seal.expand.OfflineSeal "Hugo Chavez" "Fidel Castro"

  [output]
  ...
	  --------------------------------------------------------------------------------
  INFO  	----------------------[ Listing 100 out of 5282 Entities ]----------------------
  1.	WLW:13.678790	1.000000 fidel castro
  2.	WLW:13.678790	1.000000 hugo chavez
  3.	WLW:10.537825	0.763887 kim jong il
  4.	WLW:9.856922	0.712702 barack obama
  5.	WLW:9.856922	0.712702 dick cheney
  6.	WLW:9.856922	0.712702 john mccain
  7.	WLW:9.451379	0.682217 hillary clinton
  8.	WLW:9.420185	0.679872 jimmy carter
  9.	WLW:9.420185	0.679872 mahmoud ahmadinejad
  ... 
```

4) Create your own indexed local directory of file for SEAL.  If you
have a nice healthy chunk of data for seal to run over, you can move
it to your SOMEWHERE directory and point the `localDir` at it:

```
localDir = MYDATA
localRoot = SOMEWHERE/offline-seal-data
indexDir = SOMEWHERE/offline-seal-data/MYDATA-index
```

Then you can build an index

```
  % (cd SOMEWHERE/offline-seal-data; java com.rcwang.seal.fetch.OfflineSearchIndexer)
```

The directory MYDATA being indexed (here `/usr2/bigdata/seal/cache/kdd`)
may be a cacheDir used in previous on-line runs of seal, or may be any
other directory tree containing indexable html files.  What's actually
indexed are any files with the extension *.html, *.htm, or *.txt.

MYDATA can also be a directory of wrappers that were saved previously
by OfflineSeal or WrapperSavingAsia, by running them using the option
`wrapperSaving=2`.  In this case you should use the option `-wrappers`
when you run the OfflineSearchIndexer.

The Indexer is likely to print a whole pile of errors, including 
"`Parse Aborted: Lexical error`" and "`java.io.IOException: Pipe closed`". These 
don't appear to have an adverse effect on the index, and you can probably
ignore them.


==============================================================================
CHANGELOG:
----------

**Changes in Fall 2011-Spring 2012**
 - Mostly by Katie Rivard

Added ability to use the ClueWeb search service at http://boston.lti.cs.cmu.edu/NELL
and some associated features for batch query processing. Note that you have to
be added to the whitelist before you can use this service.


**Changes from Richard's thesis up through June 2011**
 - Mostly by William Cohen

The main changes are: 
    
1) removal of some files code that used external repositories.  Most of these are minor, but not using GHIRL does mean that the RWR and PageRank rankers no longer work.


```
       [ghirl]
       /usr1/cvsroot/seal/src/com/rcwang/seal/rank/Attic/GraphLearner.java,v
       /usr1/cvsroot/seal/src/com/rcwang/seal/rank/Attic/GraphBuilder.java,v
       /usr1/cvsroot/seal/src/com/rcwang/seal/eval/Attic/LearnExperiment.java,v
        
       [javamail-1.4.jar]
       /usr1/cvsroot/seal/src/com/rcwang/seal/util/Attic/Mailer.java,v
        
       [wordnet]
       /usr1/cvsroot/seal/src/com/rcwang/seal/wordnet/Attic/WordNetHelper.java,v
       /usr1/cvsroot/seal/src/com/rcwang/seal/wordnet/Attic/SynReplace.java,v
       /usr1/cvsroot/seal/src/com/rcwang/seal/wordnet/Attic/LeafParentFinder.java,v
       /usr1/cvsroot/seal/src/com/rcwang/seal/wordnet/Attic/LeafFinder.java,v
       /usr1/cvsroot/seal/src/com/rcwang/seal/wordnet/Attic/WordNetDemo.java,v
```

2) edits to make the code consistent with the deletions
     
3) addition of 
 - the OffLineSeal main program in src/com/rcwang/seal/expand/OfflineSeal.java, which runs Seal directly off a lucene searcher and a cache
 - support for saving wrappers in OfflineSeal
 - additon of WrapperSavingAsia, which adds wrapper-saving support to Asia.
 - additon of an index-building program for OfflineSeal

4) adding lucene jars for indexing and such

5) moving the seal.properties and log4j.properties file to config

