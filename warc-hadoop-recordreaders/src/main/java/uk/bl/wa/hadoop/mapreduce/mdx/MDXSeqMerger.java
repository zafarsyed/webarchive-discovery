package uk.bl.wa.hadoop.mapreduce.mdx;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapred.lib.IdentityMapper;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.zookeeper.KeeperException;

/**
 * 
 * This takes multiple MDX sequence files and merges them using the
 * re-duplicating reducer.
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 */

@SuppressWarnings({ "deprecation" })
public class MDXSeqMerger extends Configured implements Tool {
	private static final Log LOG = LogFactory.getLog(MDXSeqMerger.class);
	private static final String CLI_USAGE = "[-i <input file>] [-o <output dir>] [-r <#reducers>] [-w] [Wait for completion.]";
	private static final String CLI_HEADER = "MapReduce job for merging MDX sequence files.";

	private String inputPath;
	private String outputPath;
	private boolean wait;

	// Reducer count:
	int numReducers = 10;

	/**
	 * 
	 * @param args
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws InterruptedException
	 * @throws KeeperException
	 */
    public void createJobConf(JobConf conf, String[] args)
			throws IOException, ParseException, KeeperException,
			InterruptedException {
		// Parse the command-line parameters.
		this.setup(args, conf);

		// Add input paths:
		LOG.info("Reading input files...");
		String line = null;
		BufferedReader br = new BufferedReader(new FileReader(this.inputPath));
		while ((line = br.readLine()) != null) {
			FileInputFormat.addInputPath(conf, new Path(line));
		}
		br.close();
		LOG.info("Read " + FileInputFormat.getInputPaths(conf).length
				+ " input files.");

		FileOutputFormat.setOutputPath(conf, new Path(this.outputPath));

		conf.setJobName(this.inputPath + "_" + System.currentTimeMillis());
		// Input
		conf.setInputFormat(SequenceFileInputFormat.class);
		// M-R
        conf.setMapperClass(IdentityMapper.class);
		conf.setReducerClass(MDXReduplicatingReducer.class);
		// Map outputs
		conf.setMapOutputKeyClass(Text.class);
        conf.setMapOutputValueClass(Text.class);
		// Job outputs
		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(Text.class);
		conf.setOutputFormat(SequenceFileOutputFormat.class);
		SequenceFileOutputFormat.setOutputCompressionType(conf,
				CompressionType.BLOCK);
		LOG.info("Used " + numReducers + " reducers.");
		conf.setNumReduceTasks(numReducers);

		// Compress the output from the maps, to cut down temp space
		// requirements between map and reduce.
		conf.setBoolean("mapreduce.map.output.compress", true); // Wrong syntax
																// for 0.20.x ?
		conf.set("mapred.compress.map.output", "true");
		// conf.set("mapred.map.output.compression.codec",
		// "org.apache.hadoop.io.compress.GzipCodec");
		// Ensure the JARs we provide take precedence over ones from Hadoop:
		conf.setBoolean("mapreduce.task.classpath.user.precedence", true);

	}

	/**
	 * 
	 * Run the job:
	 * 
	 * @throws InterruptedException
	 * @throws KeeperException
	 * 
	 */
	public int run(String[] args) throws IOException, ParseException,
			KeeperException, InterruptedException {
		// Set up the base conf:
		JobConf conf = new JobConf(getConf(), MDXSeqMerger.class);

		// Get the job configuration:
		this.createJobConf(conf, args);

		// Submit it:
		if (this.wait) {
			JobClient.runJob(conf);
		} else {
			JobClient client = new JobClient(conf);
			client.submitJob(conf);
		}
		return 0;
	}

	private void setup(String[] args, JobConf conf) throws ParseException {
		// Process Hadoop args first:
		String[] otherArgs = new GenericOptionsParser(conf, args)
				.getRemainingArgs();

		// Process remaining args list this:
		Options options = new Options();
		options.addOption("i", true, "input file list");
		options.addOption("o", true, "output directory");
		options.addOption("w", false, "wait for job to finish");
		options.addOption("r", true, "number of reducers");

		CommandLineParser parser = new PosixParser();
		CommandLine cmd = parser.parse(options, otherArgs);
		if (!cmd.hasOption("i") || !cmd.hasOption("o")) {
			HelpFormatter helpFormatter = new HelpFormatter();
			helpFormatter.setWidth(80);
			helpFormatter.printHelp(CLI_USAGE, CLI_HEADER, options, "");
			System.exit(1);
		}
		this.inputPath = cmd.getOptionValue("i");
		this.outputPath = cmd.getOptionValue("o");
		this.wait = cmd.hasOption("w");
		if (cmd.hasOption("r")) {
			this.numReducers = Integer.parseInt(cmd.getOptionValue("r"));
		}
	}

	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		int ret = ToolRunner.run(new MDXSeqMerger(), args);
		System.exit(ret);
	}

}
