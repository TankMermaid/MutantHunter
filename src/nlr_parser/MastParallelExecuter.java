package nlr_parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import supportClasses.BioSequence;
import supportClasses.FastaReader;
import supportClasses.FastqReader;

public class MastParallelExecuter {

	File mastExe;
	File memeXML;
	File tempdir;
	int numThreads;
	int numSequencesPerCall;
	
	
	public MastParallelExecuter(File mastExe, File memeXML, int numThreads, int numSequencesPerCall) {
		this.mastExe = mastExe;
		this.memeXML = memeXML;
		
		this.numSequencesPerCall = numSequencesPerCall;
		this.numThreads = numThreads;
		
		
		this.tempdir = new File( "temp_mast1" );
		while(tempdir.exists()){
			tempdir = new File("temp_mast" + ((int) Math.round( Math.random() *100000000  )));
		}
		tempdir.mkdir();
		
		
		System.err.println("DEBUG: Temporary directory is "+tempdir.getAbsolutePath() );
		
	}

	public boolean close(){
		
		System.err.println("DEBUG: Temporary directory "+tempdir.getAbsolutePath() +" is now being deleted.");
		
		return recoursiveDelete(tempdir);
	}
	
	
	public File getTempDir(){
		return this.tempdir;
	}
	
	
	
	public Hashtable<String,MastMotifHitList> executeMastDNA(File sequenceFile, double pvalue_threshold)throws IOException, InterruptedException, ExecutionException{
		ExecutorService executor = Executors.newFixedThreadPool(this.numThreads);
		List<Future<File>> list = new ArrayList<Future<File>>();
		
		int fileCounta = 1;
		int seqCounta = 0;
		File tempSeq = new File(tempdir, "tempSeq"+fileCounta+".fasta");
		BufferedWriter out = new BufferedWriter(new FileWriter( tempSeq ));
		FastaReader reader = new FastaReader(sequenceFile);
		for (BioSequence seq = reader.readEntry(); seq != null; seq = reader.readEntry()) {
			
			BioSequence[] prots = seq.translate2Protein();
			for( int i = 0; i < prots.length; i++ ){
				out.write(prots[i].getFastaString(100));
				seqCounta++;
			}
		
						
			if(seqCounta>=this.numSequencesPerCall){
				seqCounta = 0;
				out.close();
				MastThread mt = new MastThread( tempSeq, tempdir ,  this.mastExe, memeXML);
				Callable<File> worker = mt;
				Future <File> submit = executor.submit(worker);
				list.add(submit);
				fileCounta++;
				tempSeq = new File(tempdir, "tempSeq"+fileCounta+".fasta");
				out = new BufferedWriter(new FileWriter( tempSeq ));
			}
		}	
		
		reader.close();
		out.close();
		
		//last call with remaining sequences
		MastThread mt = new MastThread( tempSeq, tempdir ,  mastExe, memeXML);
		Callable<File> worker = mt;
		Future <File> submit = executor.submit(worker);
		list.add(submit);
		
		
		Hashtable<String,MastMotifHitList> h = new Hashtable<String,MastMotifHitList>();
		
		for(Future<File> future : list){
			File file = future.get();
			MastFile mastFile = new MastFile(file, pvalue_threshold);
			mastFile.mergeDNAEntries();
			Hashtable<String,MastMotifHitList> hh = mastFile.getNibblers();
			//System.out.println("DEBUG: <MastParallelExecuter> num hits: "+hh.size());
			for(Enumeration<String> myenum = hh.keys(); myenum.hasMoreElements();){
				String key = myenum.nextElement();
				MastMotifHitList  mmhl = hh.get(key);
				
				if(mmhl.isNibbler()){
					//System.out.println("DEBUG <MastParallelExecuter>; motif list: " + mmhl.getMotifListString());
					h.put(key, hh.get(key));
				}
			}
		}
		
				
		executor.shutdown();
			
		return h;
	}

	
	
	public Hashtable<String,MastMotifHitList> executeMastProtein(File inputFile,  double pvalue_threshold)throws IOException, InterruptedException, ExecutionException{
		
		
		ExecutorService executor = Executors.newFixedThreadPool(this.numThreads);
		List<Future<File>> list = new ArrayList<Future<File>>();
		
		int fileCounta = 1;
		int seqCounta = 0;
		File tempSeq = new File(tempdir, "tempSeq"+fileCounta+".fasta");
		BufferedWriter out = new BufferedWriter(new FileWriter( tempSeq ));
		
		FastaReader reader = new FastaReader(inputFile);
		for (BioSequence seq = reader.readEntry(); seq != null; seq = reader.readEntry()) {
			
			out.write(seq.getFastaString(100));
			seqCounta++;
			
			if(seqCounta>=this.numSequencesPerCall){
				seqCounta = 0;
				out.close();
				MastThread mt = new MastThread( tempSeq, tempdir ,  this.mastExe, memeXML);
				Callable<File> worker = mt;
				Future <File> submit = executor.submit(worker);
				list.add(submit);
				fileCounta++;
				tempSeq = new File(tempdir, "tempSeq"+fileCounta+".fasta");
				out = new BufferedWriter(new FileWriter( tempSeq ));
			}
			
		}
		
		out.close();
		MastThread mt = new MastThread( tempSeq, tempdir ,  mastExe, memeXML);
		Callable<File> worker = mt;
		Future <File> submit = executor.submit(worker);
		list.add(submit);
		
		
		Hashtable<String,MastMotifHitList> h = new Hashtable<String,MastMotifHitList>();
		
		for(Future<File> future : list){
			File file = future.get();
			MastFile mastFile = new MastFile(file,  pvalue_threshold);
			mastFile.prepareProteinEntries();
			Hashtable<String,MastMotifHitList> hh = mastFile.getNibblers();
			
			for(Enumeration<String> myenum = hh.keys(); myenum.hasMoreElements();){
				String key = myenum.nextElement();
				
				MastMotifHitList  mmhl = hh.get(key);
				if(mmhl.isNibbler()){
					h.put(key, hh.get(key));
				}
				
			}
		}
		
				
		executor.shutdown();
	
		
		
		
		return h;
		
	}
	
	
	
	public Hashtable<String,MastMotifHitList> executeMastDNAFastq(File sequenceFile, double pvalue_threshold)throws IOException, InterruptedException, ExecutionException{
		ExecutorService executor = Executors.newFixedThreadPool(this.numThreads);
		List<Future<File>> list = new ArrayList<Future<File>>();
		
		
		System.err.println("Loading sequences:");
		
		int fileCounta = 1;
		int seqCounta = 0;
		File tempSeq = new File(tempdir, "tempSeq"+fileCounta+".fasta");
		BufferedWriter out = new BufferedWriter(new FileWriter( tempSeq ));
		FastqReader reader = new FastqReader(sequenceFile);
		for (BioSequence read = reader.readEntry(); read != null; read = reader.readEntry()) {
			
			BioSequence[] prots = read.translate2Protein();
			for( int i = 0; i < prots.length; i++ ){
				out.write(prots[i].getFastaString(100));
				
				seqCounta++;
				if( seqCounta%1000==0){
					System.err.print(".");
				}
				if(seqCounta%100000==0){
					System.err.println();
				}
			}
		
						
			if(seqCounta>=this.numSequencesPerCall){
				seqCounta = 0;
				out.close();
				MastThread mt = new MastThread( tempSeq, tempdir ,  this.mastExe, memeXML);
				Callable<File> worker = mt;
				Future <File> submit = executor.submit(worker);
				list.add(submit);
				fileCounta++;
				tempSeq = new File(tempdir, "tempSeq"+fileCounta+".fasta");
				out = new BufferedWriter(new FileWriter( tempSeq ));
			}
		}	
		System.err.println();
		reader.close();
		out.close();
		System.err.println("Calling MAST");
		//last call with remaining sequences
		MastThread mt = new MastThread( tempSeq, tempdir ,  mastExe, memeXML);
		Callable<File> worker = mt;
		Future <File> submit = executor.submit(worker);
		list.add(submit);
		
		
		
		System.err.println("Reading mast output");
		
		Hashtable<String,MastMotifHitList> h = new Hashtable<String,MastMotifHitList>();
		
		for(Future<File> future : list){
			File file = future.get();
			MastFile mastFile = new MastFile(file, pvalue_threshold);
			mastFile.mergeDNAEntries();
			Hashtable<String,MastMotifHitList> hh = mastFile.getNibblers();
			for(Enumeration<String> myenum = hh.keys(); myenum.hasMoreElements();){
				String key = myenum.nextElement();
				MastMotifHitList  mmhl = hh.get(key);
				if(mmhl.isNibbler()){
					h.put(key, hh.get(key));
				}
			}
		}
		
				
		executor.shutdown();
	
		
		
		return h;
	}


	
	
	
	
	
	
	private boolean recoursiveDelete(File dir){
		File[] files = dir.listFiles();
		for( int i = 0; i< files.length; i++){
			File file = files[i];
			if(file.isDirectory()){
				recoursiveDelete(file);
			}else{
				file.delete();
			}
		}
		
		return dir.delete();
	}
	
	
	
	
}
