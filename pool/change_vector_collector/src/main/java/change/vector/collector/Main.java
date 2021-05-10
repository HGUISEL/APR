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

			// get AST vectors with ordering using GumTree -g
			if (is_gumtree) {
				String bbic_F = arguments.inputDir;
				File bbicFile = new File(bbic_F);
				arguments.is_defects4j = this.is_defects4j ;

				// TE&YH 프로젝트에서는 collectBeforeBICFromLocalFile 만 사용한다. 
				// 이 프로젝트는 BFC가 없으므로 BBIC만 사용하여 change vector를 만들어야 하기 때문에 FIC로 BBIC를 얻는다.
				bbics = Collector.collectBeforeBICFromLocalFile(arguments);

				Gumtree.runGumtree(arguments, bbics);
				return;
			}

		}
	}

	private boolean parseOptions(Options options, String[] args) {
		CommandLineParser parser = new DefaultParser();
		String in;
		String out;
		String project;

		try {
			CommandLine cmd = parser.parse(options, args);
			try {
				if (cmd.hasOption("g"))
					is_gumtree = true;
				else if (cmd.hasOption("d"))
					is_defects4j = true;


				in = cmd.getOptionValue("i");
				out = cmd.getOptionValue("o");
				project = cmd.getOptionValue("p");

			} catch (Exception e) {
				System.out.println(e.getMessage());
				printHelp(options);
				return false;
			}

			arguments = new CLIOptions(project, in, out);
		} catch (Exception e) {
			e.printStackTrace();
			printHelp(options);
			return false;
		}
		return true;
	}

	private Options createOptions() {
		Options options = new Options();

		options.addOption(Option.builder("d").longOpt("defects4j").desc("run Gumtree vectors for defects4j instances").build());

		options.addOption(Option.builder("g").longOpt("gumtree").desc("run Gumtree").build());

		options.addOption(Option.builder("i").longOpt("input").desc("directory of the input file to parse").hasArg()
				.argName("input_path").required().build());

		options.addOption(Option.builder("o").longOpt("output").desc("directory will have result file").hasArg()
				.argName("output_path").required().build());

		options.addOption(Option.builder("p").longOpt("project").desc("name of the target project").hasArg()
				.argName("project_name").required().build());

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