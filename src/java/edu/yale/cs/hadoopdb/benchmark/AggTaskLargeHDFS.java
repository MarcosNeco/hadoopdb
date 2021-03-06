/**
 * Copyright 2009 HadoopDB Team (http://db.cs.yale.edu/hadoopdb/hadoopdb.html)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.yale.cs.hadoopdb.benchmark;


import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.util.ToolRunner;

import edu.yale.cs.hadoopdb.exec.HDFSJobBase;
import edu.yale.cs.hadoopdb.util.BenchmarkUtils;
import edu.yale.cs.hadoopdb.util.HDFSUtil;

/**
 * Adapted from Andy Pavlo's code 
 * http://database.cs.brown.edu/projects/mapreduce-vs-dbms/
 */
public class AggTaskLargeHDFS extends HDFSJobBase {

	public static void main(String[] args) throws Exception {
		int res = ToolRunner.run(new Configuration(), new AggTaskLargeHDFS(), args);
		System.exit(res);
	}

	@Override
	protected JobConf configureJob(String... args) throws IOException {

		JobConf conf = new JobConf(getConf(), this.getClass());
		conf.setJobName("aggregation_hdfs_large");

		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(DoubleWritable.class);

		conf.setMapperClass(AggTaskLargeHDFS.Map.class);
		conf.setCombinerClass(AggTaskLargeHDFS.Reduce.class);
		conf.setReducerClass(AggTaskLargeHDFS.Reduce.class);

		conf.setInputFormat(TextInputFormat.class);
		conf.setOutputFormat(TextOutputFormat.class);

		if (args.length < 2) {
			throw new RuntimeException("Incorrect arguments provided for "
					+ this.getClass());
		}

		FileInputFormat.setInputPaths(conf, new Path(args[0]));

		// OUTPUT properties
		Path outputPath = new Path(args[1]);
		HDFSUtil.deletePath(outputPath);
		FileOutputFormat.setOutputPath(conf, outputPath);

		return conf;

	}

	static class Map extends MapReduceBase implements
			Mapper<LongWritable, Text, Text, DoubleWritable> {

		protected Text outputKey = new Text();
		protected DoubleWritable outputValue = new DoubleWritable();

		public void map(LongWritable key, Text value,
				OutputCollector<Text, DoubleWritable> output, Reporter reporter)
				throws IOException {

			// UserVisits: sourceIP | destURL | visitDate | adRevenue |
			// userAgent | countryCode | langCode | searchWord | duration
			String fields[] = BenchmarkUtils.DELIMITER_PATTERN.split(value
					.toString());

			String newKey = fields[0];
			Double revenue = Double.parseDouble(fields[3]);

			outputKey.set(newKey);
			outputValue.set(revenue);
			output.collect(outputKey, outputValue);
		}
	}

	public static class Reduce extends MapReduceBase implements
			Reducer<Text, DoubleWritable, Text, DoubleWritable> {

		protected DoubleWritable outputValue = new DoubleWritable();

		public void reduce(Text key, Iterator<DoubleWritable> values,
				OutputCollector<Text, DoubleWritable> output, Reporter reporter)
				throws IOException {
			
			double sum = 0;
			while (values.hasNext()) {
				sum += values.next().get();
			}
			
			outputValue.set(sum);
			output.collect(key, outputValue);
		}
	}

	@Override
	protected int printUsage() {
		System.out.println("<input_dir> <output_dir>");
		return -1;
	}

}
