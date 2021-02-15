/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ctakes.dictionary.lookup.ae;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.core.resource.FileResourceImpl;
import org.apache.ctakes.core.resource.JdbcConnectionResourceImpl;
import org.apache.ctakes.utils.env.EnvironmentVariable;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;

/**
 * UIMA annotator that identified entities based on lookup.
 * 
 * @author Mayo Clinic
 */
public class UmlsDictionaryLookupAnnotator extends DictionaryLookupAnnotator
{
	/* Special implementation to pre bundle the UMLS SnowmedCT/RxNorm dictionaries
	 * Performs a check for user's UMLS licence at init time via their RESTful API
	 * User's will need to configure their UMLS username/password in their config
	 */
	public final static String UMLSADDR_PARAM = "ctakes.umlsaddr";
	public final static String UMLSVENDOR_PARAM = "ctakes.umlsvendor";
	public final static String UMLSUSER_PARAM = "ctakes.umlsuser";
	public final static String UMLSPW_PARAM = "ctakes.umlspw";
	
	private Logger iv_logger = Logger.getLogger(getClass().getName());

	private String UMLSAddr;
	private String UMLSVendor;
	private String UMLSUser;
	private String UMLSPW;

	@Override
  public void initialize(UimaContext aContext)
			throws ResourceInitializationException
	{
		super.initialize(aContext);

		try {
			UMLSAddr = EnvironmentVariable.getEnv(UMLSADDR_PARAM, aContext);
			UMLSVendor = EnvironmentVariable.getEnv(UMLSVENDOR_PARAM, aContext);
			UMLSUser = EnvironmentVariable.getEnv(UMLSUSER_PARAM, aContext);
			UMLSPW = EnvironmentVariable.getEnv(UMLSPW_PARAM, aContext);
			
			iv_logger.info("Using " + UMLSADDR_PARAM + ": " + UMLSAddr + ": " + UMLSUser);
			if(!isValidUMLSUser(UMLSAddr, UMLSVendor, UMLSUser, UMLSPW))
			{
				iv_logger.error("Error: Invalid UMLS License.  A UMLS License is required to use the UMLS dictionary lookup. \n" +
						"Error: You may request one at: https://uts.nlm.nih.gov/license.html \n" +
						"Please verify your UMLS license settings in the DictionaryLookupAnnotatorUMLS.xml configuration.");
				throw new Exception("Failed to initilize.  Invalid UMLS License");
			}
			
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}	
	}

	public static boolean isValidUMLSUser(String umlsaddr, String vendor, String username, String password) throws Exception {
		String data = URLEncoder.encode("licenseCode", "UTF-8") + "="
				+ URLEncoder.encode(vendor, "UTF-8");
		data += "&" + URLEncoder.encode("user", "UTF-8") + "="
				+ URLEncoder.encode(username, "UTF-8");
		data += "&" + URLEncoder.encode("password", "UTF-8") + "="
				+ URLEncoder.encode(password, "UTF-8");
		URL url = new URL(umlsaddr);
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		try(OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream())){
      wr.write(data);
      wr.flush();
		}
		try(BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))){
		  boolean result = false;
		  String line;
		  while ((line = rd.readLine()) != null) {
		    if(line.trim().length()>0)
		    {
		      result = line.trim().equalsIgnoreCase("<Result>true</Result>");
		    }
		  }
		  return result;
		}
	}
	
	public static AnalysisEngineDescription createAnnotatorDescription() throws ResourceInitializationException{
	  try {
      return AnalysisEngineFactory.createEngineDescription(UmlsDictionaryLookupAnnotator.class,
          UMLSADDR_PARAM,
          "https://uts-ws.nlm.nih.gov/restful/isValidUMLSUser",
          UMLSVENDOR_PARAM,
          "NLM-6515182895",
          "LookupDescriptor",
          ExternalResourceFactory.createExternalResourceDescription(
              FileResourceImpl.class,
              FileLocator.locateFile("org/apache/ctakes/dictionary/lookup/LookupDesc_Db.xml")),
          "DbConnection",
          ExternalResourceFactory.createExternalResourceDescription(
              JdbcConnectionResourceImpl.class,
              "",
              JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS,
              "org.hsqldb.jdbcDriver",
              JdbcConnectionResourceImpl.PARAM_URL,
              // Should be the following but it's WAY too slow
               "jdbc:hsqldb:res:/org/apache/ctakes/dictionary/lookup/umls2011ab/umls"),
              //"jdbc:hsqldb:file:target/unpacked/org/apache/ctakes/dictionary/lookup/umls2011ab/umls"),
          "RxnormIndexReader",
          ExternalResourceFactory.createExternalResourceDescription(
              JdbcConnectionResourceImpl.class,
              "",
              JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS,
              "org.hsqldb.jdbcDriver",
              JdbcConnectionResourceImpl.PARAM_URL,
              "jdbc:hsqldb:res:/org/apache/ctakes/dictionary/lookup/rxnorm-hsqldb/umls"),
          "OrangeBookIndexReader",
          ExternalResourceFactory.createExternalResourceDescription(
              JdbcConnectionResourceImpl.class,
              "",
              JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS,
              "org.hsqldb.jdbcDriver",
              JdbcConnectionResourceImpl.PARAM_URL,
              "jdbc:hsqldb:res:/org/apache/ctakes/dictionary/lookup/orange_book_hsqldb/umls")
              );
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      throw new ResourceInitializationException(e);
    }
            
	}
}
