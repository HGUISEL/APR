package com.github.thwak.confix.tree.compiler;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;

import java.util.ArrayList;
import java.util.List;

public class CompilerRequestorImpl implements ICompilerRequestor {

    private final List<IProblem> problems;
    private final List<ClassFile> classes;

    public CompilerRequestorImpl() {
		problems = new ArrayList<IProblem>();
		classes = new ArrayList<ClassFile>();
    }

    @Override
    public void acceptResult(CompilationResult result) {
        boolean errors = false;
        if (result.hasProblems()) {
            IProblem[] problems = result.getProblems();
            for (int i = 0; i < problems.length; i++) {
                if (problems[i].isError()) {
					errors = true;
				}

                this.problems.add(problems[i]);
            }
        }
        if (!errors) {
            ClassFile[] classFiles = result.getClassFiles();
            for (int i = 0; i < classFiles.length; i++) {
				classes.add(classFiles[i]);
			}
        }
    }

    public List<IProblem> getProblems() {
        return problems;
    }

    public List<ClassFile> getClasses() {
        return classes;
    }
}
