package util.isel.csee.edu.handong;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.*;

public class JavaCode2String {
	
	public String FiletoString (File file) {
		String source = null;
		try {
			 source = FileUtils.readFileToString(file, "UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return source;
	}
}
