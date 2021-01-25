package change.vector.collector;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class Main {
	CLIOptions arguments = null;
	boolean is_help = false;
	public static boolean is_repo = false;
	public static boolean is_local = false;
	public static boolean is_correlation = false;
	public static boolean is_all = false;
	public static boolean is_precfix = false;
	public static boolean is_gumtree = false;
	public static boolean is_string = false;
	public static boolean is_defects4j = false;
	public static boolean is_clean = false;
	public static boolean is_remove_zero = false;

	public static void main(String[] args) throws Exception {
		Main cvc = new Main();
		cvc.run(args);
	}

	public void run(String[] args) throws Exception {
		Options options = createOptions();
		ArrayList<BeforeBIC> bbics = new ArrayList<BeforeBIC>();

		if (parseOptions(options, args)) {
			if (is_help)
				printHelp(options);
			
			// remove zero from x instances -z
			if (is_remove_zero) {
				Gumtree.rm_dups(arguments);	
				return;
			}

			// colllect all clean changes in a repository -q
			if (is_clean) {
				bbics = Collector.getAllCleanCommits(arguments);
				Gumtree.runGumtree(arguments, bbics);
				return;
			}

			// collects all changes in a repository -a
			if (is_all) {
				bbics = Collector.getAllCommits(arguments);
				BeforeBIC.writeBBICsOnCSV(arguments, bbics);
				return;
			}


			// collect bbic from git repository -r
			if (is_repo) {
				bbics = Collector.collectBeforeBIC(arguments);
				bbics = Collector.rmDups(bbics, arguments);
			}

			// get AST vectors with ordering using GumTree -g
			if (is_gumtree) {
				String bbic_F = arguments.inputDir;
				File bbicFile = new File(bbic_F);

				// TE&YH 프로젝트에서는 collectBeforeBICFromLocalFile 만 사용한다. 
				// 이 프로젝트는 BFC가 없으므로 BBIC만 사용하여 change vector를 만들어야 하기 때문에 FIC로 BBIC를 얻는다.
				bbics = Collector.collectBeforeBICFromLocalFile(arguments);

				Gumtree.runGumtree(arguments, bbics);
				return;
			}

			// get gumtree vectors from defects4j instances -d
			if (is_defects4j) {
				File bicFile = new File(arguments.inputDir + "BIC_d4j_" + arguments.projectName + ".csv");
				if (!bicFile.exists()) {
					Gumtree.runD4j3(arguments);
				}

				File bbicFile = new File(arguments.inputDir + "BBIC_d4j_" + arguments.projectName + ".csv");
				if (bbicFile.exists()) {
					bbics = Collector.collectBeforeBICFromLocalFile(arguments);
				} else {
					bbics = Collector.collectBeforeBIC(arguments);
				}
				Gumtree.runGumtree(arguments, bbics);

				System.out.println("run d4j complete!");
				return;
			}

			// get string data of commit -s
			if (is_string) {
				ArrayList<BeforeBIC> new_bbics = new ArrayList<BeforeBIC>();
				FileWriter writer = new FileWriter(arguments.outputDir + "S_" + arguments.projectName + ".txt");

				String inputFile = arguments.inputDir + "BBIC_" + arguments.projectName + ".csv";
				File bbicFile = new File(inputFile);
				if (bbicFile.exists()) {
					bbics = Collector.collectBeforeBICFromLocalFile(arguments);
				} else {
					bbics = Collector.collectBeforeBIC(arguments);
				}
				for (BeforeBIC bbic : bbics) {
					String bic = DefectPatchPair.getBICcode(arguments.repo, bbic, arguments);
					if (bic == null)
						continue;
					new_bbics.add(bbic);
					writer.write(bic + "\n");
				}

				BeforeBIC.writeBBICsOnCSV(arguments, new_bbics);

				writer.close();

				System.out.println("writing strings done");
				return;
			}
		}
	}

	private boolean parseOptions(Options options, String[] args) {
		CommandLineParser parser = new DefaultParser();
		String in;
		String out;
		String url;

		try {
			CommandLine cmd = parser.parse(options, args);
			try {
				if (cmd.hasOption("c"))
					is_correlation = true;
				else if (cmd.hasOption("r"))
					is_repo = true;
				else if (cmd.hasOption("l"))
					is_local = true;
				else if (cmd.hasOption("a"))
					is_all = true;
				else if (cmd.hasOption("p"))
					is_precfix = true;
				else if (cmd.hasOption("g"))
					is_gumtree = true;
				else if (cmd.hasOption("s"))
					is_string = true;
				else if (cmd.hasOption("d"))
					is_defects4j = true;
				else if (cmd.hasOption("q"))
					is_clean = true;
				else if (cmd.hasOption("z"))
					is_remove_zero = true;

				in = cmd.getOptionValue("i");
				out = cmd.getOptionValue("o");
				url = cmd.getOptionValue("u");

			} catch (Exception e) {
				System.out.println(e.getMessage());
				printHelp(options);
				return false;
			}

			arguments = new CLIOptions(url, in, out);
		} catch (Exception e) {
			e.printStackTrace();
			printHelp(options);
			return false;
		}
		return true;
	}

	private Options createOptions() {
		Options options = new Options();

		options.addOption(Option.builder("z").longOpt("zero remove").desc("remove gv instances with zero length").build());
		
		options.addOption(Option.builder("q").longOpt("clean").desc("get all clean commits").build());

		options.addOption(Option.builder("s").longOpt("string retrieval").desc("mining commit as string").build());

		options.addOption(
				Option.builder("d").longOpt("defects4j").desc("run Gumtree vectors for defects4j instances").build());

		options.addOption(Option.builder("g").longOpt("gumtree").desc("run Gumtree").build());

		options.addOption(Option.builder("p").longOpt("precfix").desc("run PRECFIX").build());

		options.addOption(Option.builder("a").longOpt("all").desc("Collects all changes in a repo").build());

		options.addOption(
				Option.builder("c").longOpt("correlation").desc("Computes correlation of each vectors").build());

		options.addOption(Option.builder("r").longOpt("repo")
				.desc("Collect change vectors straight from Git repository").build());

		options.addOption(
				Option.builder("l").longOpt("local").desc("Collect change vectors with BBIC file in local").build());

		options.addOption(Option.builder("u").longOpt("url").desc("url of the git repo").hasArg().argName("git_url")
				.build());

		options.addOption(Option.builder("i").longOpt("input").desc("directory of the input file to parse").hasArg()
				.argName("input_path").required().build());

		options.addOption(Option.builder("o").longOpt("output").desc("directory will have result file").hasArg()
				.argName("output_path").required().build());

		options.addOption(Option.builder("h").longOpt("help").desc("Help").build());

		return options;
	}

	private void printHelp(Options options) {
		// automatically generate the help statement
		HelpFormatter formatter = new HelpFormatter();
		String header = "Collects change vectors from 2 files";
		String footer = "\nPlease report issues at https://github.com/HGUISEL/ChangeVectorCollector/issues";
		formatter.printHelp("ChangeVectorCollector", header, options, footer, true);
	}
}
