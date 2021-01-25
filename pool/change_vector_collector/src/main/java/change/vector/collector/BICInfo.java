package change.vector.collector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.IterableUtils;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public class BICInfo implements Comparable<BICInfo> {
	public final static String[] headers = { "BISha1", "oldPath", "Path", "FixSha1", "BIDate", "FixDate", "LineNumInBI",
			"LineNumInPreFix", "isAddedLine", "Line" };

	String BISha1;
	String biPath;
	String path;
	String FixSha1;
	String BIDate;
	String FixDate;
	int lineNum; // line num in BI file
	int lineNumInPrevFixRev; // line num in previous commit of the fix commit
	boolean isAddedLine;
	String line = "";
	boolean isNoise;
	Edit edit;
	EditList editList;

	String filteredDueTo;

	public static ArrayList<BICInfo> collectFrom(CLIOptions input, ArrayList<String> bfcList) throws RevisionSyntaxException,
			AmbiguousObjectException, IncorrectObjectTypeException, IOException, NoHeadException, GitAPIException {
		List<RevCommit> commitList = getCommitListFrom(input.gitDir);
		Git git = input.git;
		Repository repo = git.getRepository();

		System.out.println("bfcList.size():");
		System.out.println(bfcList.size());

		List<BICInfo> lstBIChanges = new ArrayList<BICInfo>();
		for (RevCommit commit : commitList) {

			if (!Utils.isBFC(commit, bfcList)) {
				continue;
			}

			RevCommit parent = commit.getParent(0);

			List<DiffEntry> diffs = Utils.diff(parent, commit, repo);

			// check the change size in a patch
			String id = commit.name();
			for (DiffEntry diff : diffs) {
				String oldPath = diff.getOldPath();
				String newPath = diff.getNewPath();

				// ignore when no previous revision of a file, Test files, and non-java files.
				if (oldPath.equals("/dev/null") || newPath.indexOf("Test") >= 0 || !newPath.endsWith(".java"))
					continue;
			}

			// actual loop to get BI Changes
			for (DiffEntry diff : diffs) {
				ArrayList<Integer> lstIdxOfDeletedLinesInPrevFixFile = new ArrayList<Integer>();
				ArrayList<Integer> lstIdxOfOnlyInsteredLinesInFixFile = new ArrayList<Integer>();
				String oldPath = diff.getOldPath();
				String newPath = diff.getNewPath();

				// ignore when no previous revision of a file, Test files, and non-java files.
				if (oldPath.equals("/dev/null") || newPath.indexOf("Test") >= 0 || !newPath.endsWith(".java"))
					continue;

				// get preFixSource and fixSource without comments
				String prevFileSource = Utils.removeComments(Utils.fetchBlob(repo, id + "~1", oldPath));
				String fileSource = Utils.removeComments(Utils.fetchBlob(repo, id, newPath));

				EditList editList = Utils.getEditListFromDiff(prevFileSource, fileSource);

				// get line indices that are related to BI lines.
				for (Edit edit : editList) {

					if (edit.getType() != Edit.Type.INSERT) {

						int beginA = edit.getBeginA();
						int endA = edit.getEndA();

						for (int i = beginA; i < endA; i++)
							lstIdxOfDeletedLinesInPrevFixFile.add(i);

					} else {
						int beginB = edit.getBeginB();
						int endB = edit.getEndB();

						for (int i = beginB; i < endB; i++)
							lstIdxOfOnlyInsteredLinesInFixFile.add(i);
					}
				}

				// get BI commit from lines in lstIdxOfOnlyInsteredLines
				lstBIChanges.addAll(getBIChangesFromBILineIndices(id, commit, newPath, oldPath, prevFileSource,
						lstIdxOfDeletedLinesInPrevFixFile, input));
//					if(!unTrackDeletedBIlines)
//						lstBIChanges.addAll(getBIChangesFromDeletedBILine(id,rev.getCommitTime(),mapDeletedLines,fileSource,lstIdxOfOnlyInsteredLinesInFixFile,oldPath,newPath));
			}
		}
		Collections.sort(lstBIChanges);

		ArrayList<BICInfo> csvInfoList = new ArrayList<>();
		csvInfoList.addAll(lstBIChanges);

		return csvInfoList;
	}

	private static ArrayList<BICInfo> getBIChangesFromBILineIndices(String fixSha1, RevCommit fixCommit, String path,
			String prevPath, String prevFileSource, ArrayList<Integer> lstIdxOfDeletedLinesInPrevFixFile, CLIOptions input) {
		Repository repo = input.repo;
		ArrayList<BICInfo> biChanges = new ArrayList<BICInfo>();

		// do Blame
		BlameCommand blamer = new BlameCommand(repo);
		ObjectId commitID;
		try {
			commitID = repo.resolve(fixSha1 + "~1");
			blamer.setStartCommit(commitID);
			blamer.setFilePath(prevPath);
			BlameResult blame = blamer.setDiffAlgorithm(Utils.diffAlgorithm).setTextComparator(Utils.diffComparator)
					.setFollowFileRenames(true).call();

			ArrayList<Integer> arrIndicesInOriginalFileSource = lstIdxOfDeletedLinesInPrevFixFile; // getOriginalLineIndices(origPrvFileSource,prevFileSource,lstIdxOfDeletedLines);
			for (int lineIndex : arrIndicesInOriginalFileSource) {
				RevCommit commit = blame.getSourceCommit(lineIndex);

				String BISha1 = commit.name();
				String biPath = blame.getSourcePath(lineIndex);
				String FixSha1 = fixSha1;
				String BIDate = Utils.getStringDateTimeFromCommit(commit);
				String FixDate = Utils.getStringDateTimeFromCommit(fixCommit);
				int lineNum = blame.getSourceLine(lineIndex) + 1;
				int lineNumInPrevFixRev = lineIndex + 1;

				String[] splitLinesSrc = prevFileSource.split("\n");

				// split("\n") ignore last empty lines so lineIndex can be out-of-bound and
				// ignore empty line (this happens as comments are removed)
				if (splitLinesSrc.length <= lineIndex || splitLinesSrc[lineIndex].trim().equals(""))
					continue;

				BICInfo biChange = new BICInfo(BISha1, biPath, FixSha1, path, BIDate, FixDate, lineNum,
						lineNumInPrevFixRev, prevFileSource.split("\n")[lineIndex].trim(), true);
				biChanges.add(biChange);
			}

		} catch (RevisionSyntaxException | IOException | GitAPIException e) {
			e.printStackTrace();
		}

		return biChanges;
	}

	public BICInfo(String BISha1, String biPath, String FixSha1, String path, String BIDate, String FixDate,
			int lineNum, int lineNumInPrevFixRev, String line, boolean isAddedLine) {
		this.BISha1 = BISha1;
		this.biPath = biPath;
		this.FixSha1 = FixSha1;
		this.path = path;
		this.BIDate = BIDate;
		this.FixDate = FixDate;
		this.lineNum = lineNum;
		this.lineNumInPrevFixRev = lineNumInPrevFixRev;
		this.line = line;
		this.isAddedLine = isAddedLine;
	}

	public BICInfo(String changeInfo, boolean forSenitizer) {
		String[] splitString = changeInfo.split("\t");

		if (splitString.length == 2) {

			BISha1 = splitString[0];
			path = splitString[1];

		} else {

			BISha1 = splitString[0];
			biPath = splitString[1];
			path = splitString[2];
			FixSha1 = splitString[3];
			BIDate = splitString[4];
			FixDate = splitString[5];
			lineNum = Integer.parseInt(splitString[6]); // if applying Sanitizer, this will be line num in BI code.
			if (!forSenitizer) {
				lineNumInPrevFixRev = Integer.parseInt(splitString[7]); // lineNum in the prv. of fix revision.
				isAddedLine = splitString[8].equals("t") || splitString[8].toLowerCase().equals("true") ? true : false;

				// if raw line data contains tab, the line data is splitted. In this case,
				// replace tab with 5 white spaces
				for (int i = 9; i < splitString.length; i++)
					line += splitString[i] + "     ";
				line = line.trim();
			} else {
				lineNumInPrevFixRev = Integer.parseInt(splitString[6]); // lineNum in the prv. of fix revision.
				isAddedLine = splitString[7].equals("t") || splitString[7].toLowerCase().equals("true") ? true : false;
				line = splitString[8];
			}
		}

		filteredDueTo = "";
	}

	public void setIsNoise(boolean isNoise) {
		this.isNoise = isNoise;
	}

	public void setBIPath(String biPath) {
		this.biPath = biPath;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public void setBIDate(String date) {
		this.BIDate = date;
	}

	public void setFixDate(String date) {
		this.FixDate = date;
	}

	public void setFilteredDueTo(String filterName) {
		filteredDueTo = filterName;
	}

	public boolean isNoise() {
		return isNoise;
	}

	public String getBISha1() {
		return BISha1;
	}

	public String getBIPath() {
		return biPath;
	}

	public String getPath() {
		return path;
	}

	public String getFixSha1() {
		return FixSha1;
	}

	public String getBIDate() {
		return BIDate;
	}

	public String getFixDate() {
		return FixDate;
	}

	public int getLineNum() {
		return lineNum;
	}

	public String getLine() {
		return line;
	}

	public boolean getIsAddedLine() {
		return isAddedLine;
	}

	public String getFilteredDueTo() {
		return filteredDueTo;
	}

	public void setLineNum(Integer lineNum) {
		this.lineNum = lineNum;
	}

	public void setEdit(Edit edit) {
		this.edit = edit;
	}

	public void setEditList(EditList editListFromDiff) {
		this.editList = editListFromDiff;
	}

	public Edit getEdit() {
		return edit;
	}

	public EditList getEditListFromDiff() {
		return editList;
	}

	public void setBISha1(String biSha1) {
		BISha1 = biSha1;
	}

	public String toString() {
		return getBISha1() + "\t" + getBIPath() + "\t" + getPath() + "\t" + getFixSha1() + "\t" + getIsAddedLine()
				+ "\t" + getLineNum() + "\t" + getLine();
	}

	public String getRecord() {
		return getBISha1() + "\t" + getBIPath() + "\t" + getPath() + "\t" + getFixSha1() + "\t" + getBIDate() + "\t"
				+ getFixDate() + "\t" + getLineNum() + "\t" + getLineNumInPrevFixRev() + "\t" + getIsAddedLine() + "\t"
				+ getLine();
	}

	public String getRecordWithoutLineNumInPrevFix() {
		return getBISha1() + "\t" + getBIPath() + "\t" + getPath() + "\t" + getFixSha1() + "\t" + getBIDate() + "\t"
				+ getFixDate() + "\t" + getLineNum() + "\t" + getIsAddedLine() + "\t" + getLine();
	}

	public int getLineNumInPrevFixRev() {
		return lineNumInPrevFixRev;
	}

	public void setLineNumInPrevFixRev(int lineNum) {
		lineNumInPrevFixRev = lineNum;
	}

	public boolean equals(BICInfo compareWith) {
		if (!BISha1.equals(compareWith.BISha1))
			return false;
		if (!biPath.equals(compareWith.biPath))
			return false;
		if (!path.equals(compareWith.path))
			return false;
		if (!FixSha1.equals(compareWith.FixSha1))
			return false;
		if (!BIDate.equals(compareWith.BIDate))
			return false;
		if (!FixDate.equals(compareWith.FixDate))
			return false;
		if (lineNum != compareWith.lineNum)
			return false;
		if (lineNumInPrevFixRev != compareWith.lineNumInPrevFixRev)
			return false;
		if (isAddedLine != compareWith.isAddedLine)
			return false;
		if (!line.equals(compareWith.line))
			return false;
		;
		if (isNoise != compareWith.isNoise)
			return false;

		return true;
	}

	@Override
	public int compareTo(BICInfo o) {

		// order by BIDate, path, FixDate, lineNum
		if (BIDate.compareTo(o.BIDate) < 0)
			return -1;
		else if (BIDate.compareTo(o.BIDate) > 0)
			return 1;
		else {
			if (path.compareTo(o.path) < 0)
				return -1;
			else if (path.compareTo(o.path) > 0)
				return 1;
			else {
				if (FixDate.compareTo(o.FixDate) < 0)
					return -1;
				else if (FixDate.compareTo(o.FixDate) > 0)
					return 1;
				else {
					if (lineNum < o.lineNum)
						return -1;
					else if (lineNum > o.lineNum)
						return 1;
				}
			}
		}

		return 0;
	}

	public static List<RevCommit> getCommitListFrom(File gitDir) throws IOException, NoHeadException, GitAPIException {
		Git git = Git.open(gitDir);
		Iterable<RevCommit> walk = git.log().call();
		List<RevCommit> commitList = IterableUtils.toList(walk);

		return commitList;
	}
}