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

	public String url = "";
	public Repository repo = null;
	public String REMOTE_URI = "";
	public String projectName = "train";
	public Git git = null;
	public File gitDir = null;

// comment by TE
// url will be project name
	public CLIOptions(String url, String in, String out)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		if (!Main.is_remove_zero) {
			this.url = url;
			this.REMOTE_URI = url + ".git";
			this.projectName = url ; //Utils.getProjectName(REMOTE_URI);
			if (Utils.isCloned(this)) {
				this.gitDir = Utils.getGitDirectory(this);
			} else {
				this.gitDir = Utils.GitClone(this);
			}
			this.git = Git.open(gitDir);
			this.repo = git.getRepository();
		}
		this.url = url;
		this.REMOTE_URI = url + ".git";
		this.projectName =  url ; //Utils.getProjectName(REMOTE_URI);
		this.inputDir = in;
		this.outputDir = out;
		
	}

}
