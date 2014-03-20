package com.rcwang.seal.fetch;

/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 *
 * and William Cohen (wcohen@cs.cmu.edu)
 *
 * 
 * Based on the org.apache.lucene.demo.IndexHTML main program
 **************************************************************************/

import java.io.File;
import java.io.BufferedReader;
import java.io.LineNumberReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.Date;
import java.util.Arrays;

import org.apache.log4j.Logger;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.demo.HTMLDocument;
import org.apache.lucene.document.*;

import com.rcwang.seal.util.GlobalVar;

public class OfflineSearchIndexer {
    public static Logger log = Logger.getLogger(OfflineSearchIndexer.class);

    private static IndexReader reader;		  // existing index
    private static IndexWriter writer;		  // new index being built
    private static int numIndexed;
    private static int numFiles;

    public static void main(String[] argv) {

        try {
            GlobalVar gv = GlobalVar.getGlobalVar();

            // get args
            File indexDir = gv.getIndexDir();
            File localDir = gv.getLocalDir();
            File root = gv.getLocalRoot();
            boolean hasWrappers = false;
            String usage = OfflineSearchIndexer.class.getName() + " [-wrappers]";
            for (int i = 0; i < argv.length; i++) {
                if (argv[i].equals("-wrappers")) { // parse -wrappers option
                    log.info("wrappers set true");
                    hasWrappers = true;
                } else {
                    log.error("Incorrect arguments in the command line");
                    System.err.println(usage);
                    System.err.println(" -wrappers means the directory contains wrappers saved in earlier run of seal");
                    return;
                }
            }

            // check args
            if (root!=null && !System.getenv("PWD").equals(root.getPath())) {
                log.error("to build an index relative to "+root+" run OfflineSearchIndexer from that directory, and make localDir a relative path");
                System.exit(-1);
            }
            if (root==null && !localDir.isAbsolute()) {
                log.warn("to build an absolute index make localDir an absolute path - this index will be relative to "+System.getenv("PWD"));
            }
            if (indexDir.exists()) {
                log.error("Cannot save index to '" +indexDir+ "' directory, please delete it first");
                System.exit(-1);
            }
            if (!localDir.exists() || !localDir.canRead()) {
                System.out.println("Document directory '" +localDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
                System.exit(-1);
            }
            Date start = new Date();
            IndexWriter writer = new IndexWriter(FSDirectory.open(indexDir), new StandardAnalyzer(Version.LUCENE_30), true, IndexWriter.MaxFieldLength.LIMITED);
            System.out.println("Indexing to directory '" +indexDir+ "'...");
            indexDocs(writer, localDir, hasWrappers);
            System.out.println("Optimizing...");
            writer.optimize();
            writer.close();

            Date end = new Date();
            log.info("indexed "+numIndexed+" of "+numFiles+" files");
            log.info((end.getTime() - start.getTime())+" total milliseconds");

        } catch (Exception e) {
            log.error(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* Walk directory hierarchy in uid order, while keeping uid iterator from
       /* existing index in sync.  Mismatches indicate one of: (a) old documents to
       /* be deleted; (b) unchanged documents, to be left alone; or (c) new
       /* documents, to be indexed.
    */

    private static void indexDocs(IndexWriter writer, File file,boolean hasWrappers) throws Exception {
        if (file.isDirectory()) {			  // if a directory
            String[] files = file.list();		  // list its files
            Arrays.sort(files);			  // sort the files
            log.info("so far, indexed "+numIndexed+" of "+numFiles+" files");
            log.info("indexing "+files.length+" files in directory "+file);
            if (files != null) {
                for (int i = 0; i < files.length; i++) {	  // recursively index them
                    indexDocs(writer,new File(file, files[i]),hasWrappers);
                }
            }
        } else if (file.getPath().endsWith(".html") || // index .html files
                   file.getPath().endsWith(".htm") || // index .htm files
                   file.getPath().endsWith(".txt")) { 
            try {
                numFiles++;
                if (hasWrappers) {
                    log.debug("indexing wrapper "+file);
                    Document doc = wrapperDoc(file);
                    writer.addDocument(doc);
                } else {
                    log.debug("indexing html file "+file);
                    Document doc = HTMLDocument.Document(file);
                    // 11 nov 2011 kmr: lucene demo 3.0.2 dropped the url field on HTMLDocument.Document
                    doc.add(new Field("url", file.getPath(), Field.Store.YES, Field.Index.NOT_ANALYZED));
                    writer.addDocument(doc);		  // add docs unconditionally
                }
                numIndexed++;
            } catch(Exception e) {
                e.printStackTrace();
                log.debug("error "+e.getClass()+" indexing "+file);
                throw(e);
            }
        } else {
            log.info("skipping "+file);
        }
    }
    public static Document wrapperDoc(File file) throws java.io.FileNotFoundException, java.io.IOException {
        // parse doc in format: first line = header,
        // then rest of lines are: left <TAB> content <TAB> right 
        Document doc = new Document();
        StringBuffer buf = new StringBuffer();
        LineNumberReader in = new LineNumberReader(new FileReader(file));
        // skip header
        String line = in.readLine();
        while ((line = in.readLine())!=null) {
            try {
                String[] parts = line.split("\t");
                buf.append(parts[1] + "\n");
            } catch(ArrayIndexOutOfBoundsException e) {
                System.out.println("error parsing wrapper file!");
                System.out.println("file: '"+file+"'");
                System.out.println("line#: "+in.getLineNumber());
                System.out.println("line: '"+line+"'");
                System.out.println("#parts: "+line.split("\t").length);
                throw(e);
            }
        }
        in.close();
        doc.add(new Field("contents",new StringReader(buf.toString())));
        doc.add(new Field("url", file.getPath(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        return doc;
    }
}
