package com.github.thwak.confix.tree.compiler;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ClassFile;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomClassLoader extends URLClassLoader {

    public Map<String, byte[]> classMap;

    public CustomClassLoader(URL[] urls, ClassLoader parent, List<ClassFile> classesList) {
        super(urls);
		classMap = new HashMap<String, byte[]>();
        for (int i = 0; i < classesList.size(); i++) {
            ClassFile classFile = classesList.get(i);
            String className = CharOperation.toString(classFile.getCompoundName());
			classMap.put(className, classFile.getBytes());
        }
    }

    public CustomClassLoader(ClassLoader parent, List<ClassFile> classesList) {
        super(new URL[]{});
		classMap = new HashMap<String, byte[]>();
        for (int i = 0; i < classesList.size(); i++) {
            ClassFile classFile = classesList.get(i);
            String className = CharOperation.toString(classFile.getCompoundName());
			classMap.put(className, classFile.getBytes());
        }
    }

    public Class findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classMap.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        } else {
            return super.findClass(name);
        }
    }
}
