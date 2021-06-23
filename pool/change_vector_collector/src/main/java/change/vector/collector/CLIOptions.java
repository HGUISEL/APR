package change.vector.collector;

import java.io.File;
import java.io.IOException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Repository;

public class CLIOptions {

	public String inputDir = "";
	public String outputDir = "";

	public String project = "";
	public Repository repo = null;
	public String projectName = "train";
	public Git git = null;
	public File gitDir = null;
	public boolean is_defects4j = false;

// comment by TE
	public CLIOptions(String project, String in, String out)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		
		this.project = project;
		this.projectName =  project ;
		this.inputDir = in;
		this.outputDir = out;


		if (!Main.is_remove_zero) {
			this.project = project;
			this.projectName = project ;
			this.gitDir = Utils.getGitDirectory(this);

			// this.projectName = Utils.getProjectName(REMOTE_URI);
			// if (Utils.isCloned(this)) {
			// 	this.gitDir = Utils.getGitDirectory(this);
			// } else {
			// 	this.gitDir = Utils.GitClone(this);
			// }
			this.git = Git.open(gitDir);
			this.repo = git.getRepository();
		}

		
	}

}