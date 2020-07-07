package util.isel.csee.edu.handong;

public class CodePreprocessor {
		private String file;
		public CodePreprocessor(String file){
			this.file = file;
		}
		
		/* delete single and multi-line comments from given string */
		public void deleteComments(){
			file = file.replaceAll("//.*", ""); 
			file = file.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");
		}
		
		/* delete annotations */
		public void deleteAnnotations() {
			file = file.replaceAll("(@).*\\s", "");
		}
		
		/* delete single braces*/
		public void deleteBraces(){
			file = file.replaceAll("\\{", "");
			file = file.replaceAll("\\}", "");
		}
		
		/* delete empty new lines */
		public void deleteNewlineSpaces() {
			file = file.replaceAll("\n+", "");
		}
		
		public String run() {
			deleteComments();
			//deleteAnnotations(); we are not going to use annotation processor in this project.
			deleteBraces();
			deleteNewlineSpaces();
			return file;
		}
}
