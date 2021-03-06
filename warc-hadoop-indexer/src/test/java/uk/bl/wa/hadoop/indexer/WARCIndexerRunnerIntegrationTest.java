package uk.bl.wa.hadoop.indexer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputLogFilter;
import org.junit.Assert;
import org.junit.Test;

import uk.bl.wa.hadoop.mapreduce.MapReduceTestBaseClass;

/**
 * 
 * 
 * @see https://wiki.apache.org/hadoop/HowToDevelopUnitTests
 * @see http://blog.pfa-labs.com/2010/01/unit-testing-hadoop-wordcount-example.html
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class WARCIndexerRunnerIntegrationTest extends MapReduceTestBaseClass {
	
	private static final Log log = LogFactory.getLog(WARCIndexerRunnerIntegrationTest.class);

	@SuppressWarnings( "deprecation" )
	@Test
	public void testFullIndexerJob() throws Exception {
		// prepare for test
		//createTextInputFile();

		log.info("Checking input file is present...");
		// Check that the input file is present:
		Path[] inputFiles = FileUtil.stat2Paths(getFileSystem().listStatus(
				input, new OutputLogFilter()));
		Assert.assertEquals(2, inputFiles.length);
		
		// Set up arguments for the job:
		// FIXME The input file could be written by this test.
		String[] args = { "-i", "src/test/resources/test-inputs.txt", "-o", this.output.getName()};
		
		// Set up the WARCIndexerRunner
		WARCIndexerRunner wir = new WARCIndexerRunner();

		// run job
		log.info("Setting up job config...");
		JobConf conf = this.mrCluster.createJobConf();
		wir.createJobConf(conf, args);
		log.info("Running job...");
		JobClient.runJob(conf);
		log.info("Job finished, checking the results...");

		// check the output
		Path[] outputFiles = FileUtil.stat2Paths(getFileSystem().listStatus(
				output, new OutputLogFilter()));
		//Assert.assertEquals(1, outputFiles.length);
		
		// Check contents of the output:
		for( Path output : outputFiles ) {
			log.info(" --- output : "+output);
			if( getFileSystem().isFile(output) ) {
				InputStream is = getFileSystem().open(output);
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				String line = null;
				while( ( line = reader.readLine()) != null ) {
					log.info(line);
				}
				reader.close();
			} else {
				log.info(" --- ...skipping directory...");
			}
		}
	}

}
