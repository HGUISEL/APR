package util.isel.csee.edu.handong;

public class CodePreprocessor {
		private String file;
		public CodePreprocessor(String file){
			this.file = file;
		}
		
		// delete single and multi-line comments from given string
		public void deleteComments(){
			file = file.replaceAll("//.*", ""); 
			file = file.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");
		}
		
		// delete annotations
		public void deleteAnnotations() {
			file = file.replaceAll("(@).*\\s", "");
		}
		public String run() {
			deleteComments();
			deleteAnnotations();
			return file;
		}
}
