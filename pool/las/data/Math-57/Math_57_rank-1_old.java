/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.flink.streaming.api;

import java.io.IOException;

import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.collector.OutputSelector;
import org.apache.flink.streaming.api.invokable.StreamComponentInvokable;
import org.apache.flink.streaming.api.streamcomponent.StreamComponentException;
import org.apache.flink.streaming.partitioner.ShufflePartitioner;
import org.apache.flink.streaming.partitioner.StreamPartitioner;

public class StreamConfig {
	private static final String INPUT_TYPE = "inputType_";
	private static final String NUMBER_OF_OUTPUTS = "numberOfOutputs";
	private static final String NUMBER_OF_INPUTS = "numberOfInputs";
	private static final String OUTPUT_NAME = "outputName_";
	private static final String PARTITIONER_OBJECT = "partitionerObject_";
	private static final String USER_DEFINED_NAME = "userDefinedName";
	private static final String NUMBER_OF_OUTPUT_CHANNELS = "numOfOutputs_";
	private static final String ITERATION_ID = "iteration-id";
	private static final String OUTPUT_SELECTOR = "outputSelector";
	private static final String DIRECTED_EMIT = "directedEmit";
	private static final String FUNCTION_NAME = "operatorName";
	private static final String FUNCTION = "operator";
	private static final String COMPONENT_NAME = "componentName";
	private static final String SERIALIZEDUDF = "serializedudf";
	private static final String USER_FUNCTION = "userfunction";
	private static final String BUFFER_TIMEOUT = "bufferTimeout";

	// DEFAULT VALUES

	private static final boolean DEFAULT_IS_MUTABLE = false;

	private static final long DEFAULT_TIMEOUT = 0;

	// STRINGS

	private static final String MUTABILITY = "isMutable";

	private Configuration config;

	public StreamConfig(Configuration config) {
		this.config = config;
	}

	public Configuration getConfiguration() {
		return config;
	}

	// CONFIGS

	public void setMutability(boolean isMutable) {
		config.setBoolean(MUTABILITY, isMutable);
	}

	public boolean getMutability() {
		return config.getBoolean(MUTABILITY, DEFAULT_IS_MUTABLE);
	}

	public void setBufferTimeout(long timeout) {
		config.setLong(BUFFER_TIMEOUT, timeout);
	}

	public long getBufferTimeout() {
		return config.getLong(BUFFER_TIMEOUT, DEFAULT_TIMEOUT);
	}

	public void setUserInvokableClass(Class<? extends StreamComponentInvokable> clazz) {
		config.setClass(USER_FUNCTION, clazz);
	}

	@SuppressWarnings("unchecked")
	public <T extends StreamComponentInvokable> Class<? extends T> getUserInvokableClass() {
		return (Class<? extends T>) config.getClass(USER_FUNCTION, null);
	}

	public void setUserInvokableObject(StreamComponentInvokable invokableObject) {
		try {
			config.setBytes(SERIALIZEDUDF, SerializationUtils.serialize(invokableObject));
		} catch (SerializationException e) {
			throw new RuntimeException("Cannot serialize invokable object "
					+ invokableObject.getClass(), e);
		}
	}

	public <T extends StreamComponentInvokable> T getUserInvokableObject() {
		try {
			return deserializeObject(config.getBytes(SERIALIZEDUDF, null));
		} catch (Exception e) {
			new StreamComponentException("Cannot instantiate user function");
		}
		return null;
	}

	public void setComponentName(String componentName) {
		config.setString(COMPONENT_NAME, componentName);
	}

	public String getComponentName() {
		return config.getString(COMPONENT_NAME, null);
	}

	public void setFunction(byte[] serializedFunction) {
		config.setBytes(FUNCTION, serializedFunction);
	}

	public Object getFunction() {
		try {
			return SerializationUtils.deserialize(config
					.getBytes(FUNCTION, null));
		} catch (SerializationException e) {
			throw new RuntimeException("Cannot deserialize invokable object", e);
		}
	}

	public void setFunctionName(String functionName) {
		config.setString(FUNCTION_NAME, functionName);
	}

	public String getFunctionName() {
		return config.getString(FUNCTION_NAME, "");
	}
	
	public void setUserDefinedName(String userDefinedName) {
		if (userDefinedName != null) {
			config.setString(USER_DEFINED_NAME, userDefinedName);
		}
	}

	public void setDirectedEmit(boolean directedEmit) {
		config.setBoolean(DIRECTED_EMIT, directedEmit);
	}

	public boolean getDirectedEmit() {
		return config.getBoolean(DIRECTED_EMIT, false);
	}

	public void setOutputSelector(byte[] outputSelector) {
		config.setBytes(OUTPUT_SELECTOR, outputSelector);

	}

	public <T extends Tuple> OutputSelector<T> getOutputSelector() {
		try {
			return deserializeObject(config.getBytes(OUTPUT_SELECTOR, null));
		} catch (Exception e) {
			throw new StreamComponentException("Cannot deserialize and instantiate OutputSelector",
					e);
		}
	}

	public void setIterationId(String iterationId) {
		config.setString(ITERATION_ID, iterationId);
	}
	
	public String getIterationId() {
		return config.getString(ITERATION_ID, "iteration-0");
	}

	public void setNumberOfOutputChannels(int outputIndex, Integer numberOfOutputChannels) {
		config.setInteger(NUMBER_OF_OUTPUT_CHANNELS + outputIndex, numberOfOutputChannels);
	}

	public int getNumberOfOutputChannels(int outputIndex) {
		return config.getInteger(NUMBER_OF_OUTPUT_CHANNELS + outputIndex, 0);
	}

	public <T extends Tuple> void setPartitioner(int outputIndex,
			StreamPartitioner<T> partitionerObject) {

		config.setBytes(PARTITIONER_OBJECT + outputIndex,
				SerializationUtils.serialize(partitionerObject));
	}

	public <T extends Tuple> StreamPartitioner<T> getPartitioner(int outputIndex)
			throws ClassNotFoundException, IOException {
		return deserializeObject(config.getBytes(PARTITIONER_OBJECT + outputIndex,
				SerializationUtils.serialize(new ShufflePartitioner<T>())));
	}

	public void setOutputName(int outputIndex, String outputName) {
		if (outputName != null) {
			config.setString(OUTPUT_NAME + outputIndex, outputName);
		}
	}

	public String getOutputName(int outputIndex) {
		return config.getString(OUTPUT_NAME + outputIndex, null);
	}

	public void setNumberOfInputs(int numberOfInputs) {
		config.setInteger(NUMBER_OF_INPUTS, numberOfInputs);
	}

	public int getNumberOfInputs() {
		return config.getInteger(NUMBER_OF_INPUTS, 0);
	}

	public void setNumberOfOutputs(int numberOfOutputs) {
		config.setInteger(NUMBER_OF_OUTPUTS, numberOfOutputs);
	}

	public int getNumberOfOutputs() {
		return config.getInteger(NUMBER_OF_OUTPUTS, 0);
	}

	public void setInputType(int inputNumber, Integer inputTypeNumber) {
		config.setInteger(INPUT_TYPE + inputNumber++, inputTypeNumber);
	}

	public int getInputType(int inputNumber) {
		return config.getInteger(INPUT_TYPE + inputNumber, 0);
	}
	
	@SuppressWarnings("unchecked")
	protected static <T> T deserializeObject(byte[] serializedObject) throws IOException,
			ClassNotFoundException {
		return (T) SerializationUtils.deserialize(serializedObject);
	}
}
