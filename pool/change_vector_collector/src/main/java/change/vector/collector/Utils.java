package change.vector.collector;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public class Utils {

	static public DiffAlgorithm diffAlgorithm = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS);
	static public RawTextComparator diffComparator = RawTextComparator.WS_IGNORE_ALL;

	static public String fetchBlob(Repository repo, String revSpec, String path)
			throws RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, IOException {

		// Resolve the revision specification
		final ObjectId id = repo.resolve(revSpec);

		// Makes it simpler to release the allocated resources in one go
		ObjectReader reader = repo.newObjectReader();

		// Get the commit object for that revision
		RevWalk walk = new RevWalk(reader);
		RevCommit commit = walk.parseCommit(id);
		walk.close();

		// Get the revision's file tree
		RevTree tree = commit.getTree();
		// .. and narrow it down to the single file's path
		TreeWalk treewalk = TreeWalk.forPath(reader, path, tree);

		if (treewalk != null) {
			// use the blob id to read the file's data
			byte[] data = reader.open(treewalk.getObjectId(0)).getBytes();
			reader.close();
			return new String(data, "utf-8");
		} else {
			return "";
		}

	}

	public static boolean isBFC(RevCommit commit, List<String> bfcList) {

		return bfcList.contains(commit.getId().getName());
	}


	public static String getProjectName(String URI) {

		Pattern p = Pattern.compile(".*/(.+)\\.git");
		Matcher m = p.matcher(URI);
		m.find();
//		System.out.println(m.group(1));
		return m.group(1);

	}

	public static String getReferencePath(CLIOptions input) {

		// if(input.is_defects4j)
		// 	return "/home/aprweb/APR_Projects/APR/pool/target" ;
		// else
		//return "/home/aprweb/APR_Projects/data/AllBIC/reference/repositories";

		String path = (input.inputDir.split("/outputs"))[0]; // target/hash의 값이 나온다

		return path ;
	}
	//where all the data is stored

	public static File getGitDirectory(CLIOptions input) {
		String referencePath = getReferencePath(input);
		File clonedDirectory = new File(referencePath + File.separator + input.projectName);
		//File clonedDirectory = new File("/home/aprweb/APR_Projects/data/AllBIC/reference/repositories/closure-compiler");
		return clonedDirectory;
	}

	public static boolean isCloned(CLIOptions input) {
		File clonedDirectory = getGitDirectory(input);
		return clonedDirectory.exists();
	}

	public static String removeComments(String code) {

		JavaASTParser codeAST = new JavaASTParser(code);
		@SuppressWarnings("unchecked")
		List<Comment> lstComments = codeAST.cUnit.getCommentList();
		for (Comment comment : lstComments) {
			code = replaceCommentsWithWS(code, comment.getStartPosition(), comment.getLength());
		}

		return code;
	}

	private static String replaceCommentsWithWS(String code, int startPosition, int length) {

		String pre = code.substring(0, startPosition);
		String post = code.substring(startPosition + length, code.length());

		String comments = code.substring(startPosition, startPosition + length);

		comments = comments.replaceAll("\\S", " ");

		code = pre + comments + post;

		return code;
	}

	public static List<DiffEntry> diff(RevCommit parent, RevCommit commit, Repository repo) {

		DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
		df.setRepository(repo);
		df.setDiffAlgorithm(diffAlgorithm);
		df.setDiffComparator(diffComparator);
		df.setDetectRenames(true);
		List<DiffEntry> diffs = null;
		try {
			diffs = df.scan(parent.getTree(), commit.getTree());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			df.close();
		}

		return diffs;
	}

	static public EditList getEditListFromDiff(String file1, String file2) {
		RawText rt1 = new RawText(file1.getBytes());
		RawText rt2 = new RawText(file2.getBytes());
		EditList diffList = new EditList();

		diffList.addAll(diffAlgorithm.diff(diffComparator, rt1, rt2));
		return diffList;
	}

	public static String getStringDateTimeFromCommit(RevCommit commit) {

		SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date commitDate = commit.getAuthorIdent().getWhen();

		TimeZone GMT = commit.getCommitterIdent().getTimeZone();
		ft.setTimeZone(GMT);

		return ft.format(commitDate);
	}

}