/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.stubbing.defaultanswers;

import java.io.Serializable;

import org.mockito.Mockito;
import org.mockito.internal.MockHandlerInterface;
import org.mockito.internal.stubbing.InvocationContainerImpl;
import org.mockito.internal.stubbing.StubbedInvocationMatcher;
import org.mockito.internal.util.MockCreationValidator;
import org.mockito.internal.util.MockUtil;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ReturnsDeepStubs implements Answer<Object>, Serializable {
    
    private static final long serialVersionUID = -6926328908792880098L;
    
    private Answer<Object> delegate = new ReturnsEmptyValues();

    public Object answer(InvocationOnMock invocation) throws Throwable {
        Class<?> clz = invocation.getMethod().getReturnType();
        if (!new MockCreationValidator().isTypeMockable(clz))
            return delegate.answer(invocation);
        return getMock(invocation);
    }

    private Object getMock(InvocationOnMock invocation) throws Throwable {
    	MockHandlerInterface<Object> handler = new MockUtil().getMockHandler(invocation.getMock());
    	InvocationContainerImpl container = (InvocationContainerImpl) handler.getInvocationContainer();

        // matches invocation for verification
        for (StubbedInvocationMatcher stubbedInvocationMatcher : container.getStubbedInvocations()) {
    		if(container.getInvocationForStubbing().matches(stubbedInvocationMatcher.getInvocation())) {
    			return stubbedInvocationMatcher.answer(invocation);
    		}
		}

        // deep stub
        return recordDeepStubMock(invocation, container);
    }

    private Object recordDeepStubMock(InvocationOnMock invocation, InvocationContainerImpl container) {
        Class<?> clz = invocation.getMethod().getReturnType();
        final Object mock = Mockito.mock(clz, this);

        container.addAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return mock;
            }
        }, false);

        return mock;
    }
}
