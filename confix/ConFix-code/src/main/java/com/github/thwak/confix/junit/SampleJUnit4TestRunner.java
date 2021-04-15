package com.github.thwak.confix.junit;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.List;

public class SampleJUnit4TestRunner extends BlockJUnit4ClassRunner {

    private final List<String> sampledTests;
    private final boolean sampleOnly;

    public SampleJUnit4TestRunner(Class<?> testClass, List<String> sampledTests, boolean sampleOnly) throws InitializationError {
        super(testClass);
        this.sampledTests = sampledTests;
        this.sampleOnly = sampleOnly;
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        List<FrameworkMethod> sampleMethods;
        if (sampledTests == null) {
            return super.computeTestMethods();
        } else {
            sampleMethods = new ArrayList<FrameworkMethod>();
            for (FrameworkMethod method : super.computeTestMethods()) {
                String name = getTestClass().getName() + "#"
                        + method.getName();
                if (sampleOnly && !sampledTests.contains(name)) {
                    continue;
                }
                sampleMethods.add(method);

            }
            return sampleMethods;
        }
    }


}
