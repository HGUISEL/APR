/*

   Derby - Class org.apache.derby.client.ClientBaseDataSource

   Copyright (c) 2001, 2005 The Apache Software Foundation or its licensors, where applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

package org.apache.derby.client;

import org.apache.derby.client.am.Configuration;
import org.apache.derby.client.am.SqlException;
import org.apache.derby.client.am.LogWriter;
import org.apache.derby.client.net.NetConfiguration;

import java.util.Properties;

public abstract class ClientBaseDataSource implements java.io.Serializable
{
  private static final long serialVersionUID = -7660172643035173692L;

  // The loginTimeout jdbc 2 data source property is not supported as a jdbc 1 connection property,
  // because loginTimeout is set by the jdbc 1 api via java.sql.DriverManager.setLoginTimeout().
  // The databaseName, serverName, and portNumber data source properties are also not supported as connection properties
  // because they are extracted from the jdbc 1 database url passed on the connection request.
  // However, all other data source properties should probably also be supported as connection properties.

  //---------------------contructors/finalizers---------------------------------

  // This class is abstract, hide the default constructor
  protected ClientBaseDataSource () {}

  // ---------------------------- loginTimeout -----------------------------------
  //
  // was serialized in 1.0 release
  /**
   * The time in seconds to wait for a connection request on this data source.
   * The default value of zero indicates that either the system time out be used or no timeout limit.
   *
   * @serial
   **/
  protected int loginTimeout = propertyDefault_loginTimeout;
  public final static String propertyKey_loginTimeout = "loginTimeout";
  public static final int propertyDefault_loginTimeout = 0;
  public synchronized void setLoginTimeout (int seconds) { this.loginTimeout = seconds; }
  public int getLoginTimeout () { return this.loginTimeout; }

  // ---------------------------- logWriter -----------------------------------
  //
  /**
   * The log writer is declared transient, and is not serialized or stored under JNDI.
   *
   * @see #traceLevel
   */
  protected transient java.io.PrintWriter logWriter = null;
  public synchronized void setLogWriter (java.io.PrintWriter logWriter) { this.logWriter = logWriter; }
  public java.io.PrintWriter getLogWriter() { return this.logWriter; }

  // ---------------------------- databaseName -----------------------------------
  //
  // Stores the relational database name, RDBNAME.
  // The length of the database name may be limited to 18 bytes
  // and therefore may throw an SQLException.
  //
  //
  protected String databaseName = null;
  public final static String propertyKey_databaseName = "databaseName";
  
  // databaseName is not permitted in a properties object


  // ---------------------------- description ------------------------------
  // A description of this data source.
  protected String description = null;
  public final static String propertyKey_description = "description";

  // ---------------------------- dataSourceName -----------------------------------
  //
  // A data source name;
  // used to name an underlying XADataSource,
  // or ConnectionPoolDataSource when pooling of connections is done.
  //
  protected String dataSourceName = null;
  public final static String propertyKey_dataSourceName = "dataSourceName";
 
  // ---------------------------- portNumber -----------------------------------
  //
  protected int portNumber = propertyDefault_portNumber;
  public final static int propertyDefault_portNumber = 1527;
  public final static String propertyKey_portNumber = "portNumber";
  
  // ---------------------------- serverName -----------------------------------
  //
  //
  protected String serverName = null;
  public final static String propertyKey_serverName = "serverName";
 
  // serverName is not permitted in a properties object

  // ---------------------------- user -----------------------------------
  //
  // This property can be overwritten by specifing the
  // username parameter on the DataSource.getConnection() method
  // call.  If user is specified, then password must also be
  // specified, either in the data source object or provided on
  // the DataSource.getConnection() call.
  //
  // Each data source implementation subclass will maintain it's own <code>password</code> property.
  // This password property may or may not be declared transient, and therefore may be serialized
  // to a file in clear-text, care must taken by the user to prevent security breaches.
  protected String user = null;
  public final static String propertyKey_user = "user";
  public final static String propertyDefault_user = "APP";

  public static String getUser (java.util.Properties properties) { 
	String userString= properties.getProperty (propertyKey_user);
	return parseString(userString,propertyDefault_user); 
  }

  /**
   * To Derby, the default is HOLD_CURSORS_OVER_COMMIT
   */
  protected int resultSetHoldability = propertyNotSet_resultSetHoldability; // 0 means not set.
  public final static int HOLD_CURSORS_OVER_COMMIT = 1; // this matches jdbc 3 ResultSet.HOLD_CURSORS_OVER_COMMIT
  public final static int CLOSE_CURSORS_AT_COMMIT = 2;  // this matches jdbc 3 ResultSet.CLOSE_CURSORS_AT_COMMIT
  public final static int propertyNotSet_resultSetHoldability = 0;
  synchronized public void setResultSetHoldability (int resultSetHoldability) { this.resultSetHoldability = resultSetHoldability; }
  public int getResultSetHoldability () { return this.resultSetHoldability; }

   public final static int NOT_SET = 0; // 0 means not set.
   public final static int YES = 1; // ="yes" as property string
   public final static int NO = 2;  // ="no" as property string


  // ---------------------------- securityMechanism -----------------------------------
  //
  // The source security mechanism to use when connecting to this data source.
  // <p>
  // Security mechanism options are:
  // <ul>
  // <li> USER_ONLY_SECURITY
  // <li> CLEAR_TEXT_PASSWORD_SECURITY
  // <li> ENCRYPTED_PASSWORD_SECURITY
  // <li> ENCRYPTED_USER_AND_PASSWORD_SECURITY - both password and user are encrypted
  // </ul>
  // The default security mechanism is USER_ONLY_SECURITY.
  // <p>
  // If the application specifies a security
  // mechanism then it will be the only one attempted.
  // If the specified security mechanism is not supported by the conversation
  // then an exception will be thrown and there will be no additional retries.
  // <p>
  // This property is currently only available for the  DNC driver.
  // <p>
  // Both user and password need to be set for all security mechanism except USER_ONLY_SECURITY 
  // When using USER_ONLY_SECURITY, only the user property needs to be specified.
  //
  protected short securityMechanism = propertyDefault_securityMechanism;
  // TODO default  should be  USER_ONLY_SECURITY. Change when working on 
  // Network Server
  //  public final static short propertyDefault_securityMechanism = (short)
  //  org.apache.derby.client.net.NetConfiguration.SECMEC_USRIDONL;
  public final static short propertyDefault_securityMechanism = (short)
  org.apache.derby.client.net.NetConfiguration.SECMEC_USRIDONL;
  public final static String propertyKey_securityMechanism = "securityMechanism";


  // We use the NET layer constants to avoid a mapping for the NET driver.
  public static short getSecurityMechanism (java.util.Properties properties)
  {
    String securityMechanismString = properties.getProperty (propertyKey_securityMechanism);
    String passwordString = properties.getProperty (propertyKey_password);
    short setSecurityMechanism = parseShort (securityMechanismString, propertyDefault_securityMechanism);
	return getUpgradedSecurityMechanism(setSecurityMechanism,passwordString);
  }


  /**
   * Upgrade the security mechansim to USRIDPWD if it is set to USRIDONL but 
   * we have a password.
   */
  public static short getUpgradedSecurityMechanism(short securityMechanism, 
												   String password)
  {
	  // if securityMechanism is USER_ONLY (the default) we may need
	  // to change it to CLEAR_TEXT_PASSWORD in order to send the password.
	  if ((password != null)  && 		  
		  (securityMechanism == NetConfiguration.SECMEC_USRIDONL))
	  
		  return (short) NetConfiguration.SECMEC_USRIDPWD;
	  else
		  return securityMechanism;
  }
  // ---------------------------- getServerMessageTextOnGetMessage -----------------------------------
  //
  protected boolean retrieveMessageText = propertyDefault_retrieveMessageText;
  public final static boolean propertyDefault_retrieveMessageText = true;
  public final static String propertyKey_retrieveMessageText = "retrieveMessageText";
  

	public static boolean getRetrieveMessageText (java.util.Properties properties)
	{
		String retrieveMessageTextString = properties.getProperty (propertyKey_retrieveMessageText);
		return parseBoolean (retrieveMessageTextString, propertyDefault_retrieveMessageText);
	}

  // ---------------------------- traceLevel -----------------------------------
  //

	public final static int TRACE_NONE = 0x0;
	public final static int TRACE_ALL = 0xFFFFFFFF;
	public final static int propertyDefault_traceLevel = TRACE_ALL;
	public final static String propertyKey_traceLevel = "traceLevel";

	protected int traceLevel = propertyDefault_traceLevel;
	public static int getTraceLevel (java.util.Properties properties)
  {
    String traceLevelString = properties.getProperty (propertyKey_traceLevel);
    return parseInt (traceLevelString, propertyDefault_traceLevel);
  }

  // ---------------------------- traceFile -----------------------------------
  //
  protected String traceFile = null;
  public final static String propertyKey_traceFile = "traceFile";
  public static String getTraceFile (java.util.Properties properties)
  { return properties.getProperty (propertyKey_traceFile); }

  // ---------------------------- traceDirectory -----------------------------------
  // For the suffix of the trace file when traceDirectory is enabled.
  private transient int traceFileSuffixIndex_ = 0;
  //
  protected String traceDirectory = null;
  public final static String propertyKey_traceDirectory = "traceDirectory";
 
  public static String getTraceDirectory (java.util.Properties properties)
  { return properties.getProperty (propertyKey_traceDirectory); }

  // ---------------------------- traceFileAppend -----------------------------------
  //
  protected boolean traceFileAppend = propertyDefault_traceFileAppend;
  public final static boolean propertyDefault_traceFileAppend = false;
  public final static String propertyKey_traceFileAppend = "traceFileAppend";
  public static boolean getTraceFileAppend (java.util.Properties properties)
  {
    String traceFileAppendString = properties.getProperty (propertyKey_traceFileAppend);
    return parseBoolean (traceFileAppendString, propertyDefault_traceFileAppend);
  }

  // ---------------------------- password -----------------------------------
  //
  // The password property is defined in subclasses, but the method
  // getPassword (java.util.Properties properties) is in this class to eliminate
  // dependencies on j2ee for connections that go thru the driver manager.
  public final static String propertyKey_password = "password";

  public static String getPassword (java.util.Properties properties) { return properties.getProperty ("password"); }

  //------------------------ interface methods ---------------------------------

  public javax.naming.Reference getReference () throws javax.naming.NamingException
  {
    // This method creates a new Reference object to represent this data source.
    // The class name of the data source object is saved in the Reference,
    // so that an object factory will know that it should create an instance
    // of that class when a lookup operation is performed. The class
    // name of the object factory, org.apache.derby.client.ClientBaseDataSourceFactory,
    // is also stored in the reference.
    // This is not required by JNDI, but is recommend in practice.
    // JNDI will always use the object factory class specified in the reference when
    // reconstructing an object, if a class name has been specified.
    // See the JNDI SPI documentation
    // for further details on this topic, and for a complete description of the Reference
    // and StringRefAddr classes.
    //
    // This ClientBaseDataSource class provides several standard JDBC properties.
    // The names and values of the data source properties are also stored
    // in the reference using the StringRefAddr class.
    // This is all the information needed to reconstruct a ClientBaseDataSource object.

    javax.naming.Reference ref =
      new javax.naming.Reference (this.getClass().getName(),
                                  ClientDataSourceFactory.className__,
                                  null);

    Class clz = getClass();
    java.lang.reflect.Field[] fields = clz.getFields();
    for (int i=0; i<fields.length; i++) {
      String name = fields[i].getName();
      if (name.startsWith ("propertyKey_")) {
        if (java.lang.reflect.Modifier.isTransient (fields[i].getModifiers()))
          continue; // if it is transient, then skip this propertyKey.
        try {
          String propertyKey = fields[i].get (this).toString();
          // search for property field.
          java.lang.reflect.Field propertyField;
          clz = getClass(); // start from current class.
          while (true) {
            try {
              propertyField = clz.getDeclaredField (name.substring (12));
              break; // found the property field, so break the while loop.
            }
            catch (java.lang.NoSuchFieldException nsfe) {
              // property field is not found at current level of class, so continue to super class.
              clz = clz.getSuperclass();
              if (clz == Object.class)
                throw new javax.naming.NamingException ("bug check: corresponding property field does not exist");
              continue;
            }
          }

          if (!java.lang.reflect.Modifier.isTransient (propertyField.getModifiers())) {
            // if the property is not transient:
            // get the property.
            java.security.AccessController.doPrivileged (new org.apache.derby.client.am.SetAccessibleAction (
              propertyField, true));
            //propertyField.setAccessible (true);
            Object propertyObj = propertyField.get (this);
            String property = (propertyObj == null) ? null : String.valueOf (propertyObj);
            // add into reference.
            ref.add (new javax.naming.StringRefAddr (propertyKey, property));
          }
        }
        catch (java.lang.IllegalAccessException e) {
          throw new javax.naming.NamingException ("bug check: property cannot be accessed");
        }
        catch (java.security.PrivilegedActionException e) {
          throw new javax.naming.NamingException ("Privileged action exception occurred.");
        }
      }
    }
    return ref;
  }

  /**
   * Not an external.  Do not document in pubs.
   * Populates member data for this data source given a JNDI reference.
   */
  public void hydrateFromReference (javax.naming.Reference ref) throws java.sql.SQLException
  {
    javax.naming.RefAddr address;

    Class clz = getClass();
    java.lang.reflect.Field[] fields = clz.getFields();
    for (int i=0; i<fields.length; i++) {
      String name = fields[i].getName();
      if (name.startsWith ("propertyKey_")) {
        if (java.lang.reflect.Modifier.isTransient (fields[i].getModifiers()))
          continue; // if it is transient, then skip this propertyKey.
        try {
          String propertyKey = fields[i].get (this).toString();
          // search for property field.
          java.lang.reflect.Field propertyField;
          clz = getClass(); // start from current class.
          while (true) {
            try {
              propertyField = clz.getDeclaredField (name.substring (12));
              break; // found the property field, so break the while loop.
            }
            catch (java.lang.NoSuchFieldException nsfe) {
              // property field is not found at current level of class, so continue to super class.
              clz = clz.getSuperclass();
              if (clz == Object.class)
                throw new org.apache.derby.client.am.SqlException (
                  new org.apache.derby.client.am.LogWriter (this.logWriter, this.traceLevel),
                  "bug check: corresponding property field does not exist"
                );
              continue;
            }
          }

          if (!java.lang.reflect.Modifier.isTransient (propertyField.getModifiers())) {
            // if the property is not transient:
            // set the property.
            address = ref.get (propertyKey);
            if (address != null) {
              propertyField.setAccessible (true);
              String type = propertyField.getType().toString();
              if (type.equals ("boolean")) {
                boolean value = ((String) address.getContent()).equalsIgnoreCase("true");
                propertyField.setBoolean (this, value);
              }
              else if (type.equals ("byte")) {
                byte value = Byte.parseByte ((String)address.getContent());
                propertyField.setByte (this, value);
              }
              else if (type.equals ("short")) {
                short value = Short.parseShort ((String)address.getContent());
                propertyField.setShort (this, value);
              }
              else if (type.equals ("int")) {
                int value = Integer.parseInt ((String)address.getContent());
                propertyField.setInt (this, value);
              }
              else if (type.equals ("long")) {
                long value = Long.parseLong ((String)address.getContent());
                propertyField.setLong (this, value);
              }
              else if (type.equals ("float")) {
                float value = Float.parseFloat ((String)address.getContent());
                propertyField.setFloat (this, value);
              }
              else if (type.equals ("double")) {
                double value = Double.parseDouble ((String)address.getContent());
                propertyField.setDouble (this, value);
              }
              else if (type.equals ("char")) {
                char value = ((String)address.getContent()).charAt(0);
                propertyField.setChar (this, value);
              }
              else {
                propertyField.set (this, address.getContent());
              }
            }
          }
        }
        catch (java.lang.IllegalAccessException e) {
          throw new org.apache.derby.client.am.SqlException (
            new org.apache.derby.client.am.LogWriter (this.logWriter, this.traceLevel),
            "bug check: property cannot be accessed"
          );
        }
      }
    }
  }

  // ----------------------supplemental methods---------------------------------
  /**
   * Not an external.  Do not document in pubs.
   * Returns all non-transient properties of a ClientBaseDataSource.
   */
  public java.util.Properties getProperties () throws java.sql.SQLException
  {
    java.util.Properties properties = new java.util.Properties();

    Class clz = getClass();
    java.lang.reflect.Field[] fields = clz.getFields();
    for (int i=0; i<fields.length; i++) {
      String name = fields[i].getName();
      if (name.startsWith ("propertyKey_")) {
        if (java.lang.reflect.Modifier.isTransient (fields[i].getModifiers()))
          continue; // if it is transient, then skip this propertyKey.
        try {
          String propertyKey = fields[i].get (this).toString();
          // search for property field.
          java.lang.reflect.Field propertyField;
          clz = getClass(); // start from current class.
          while (true) {
            try {
              propertyField = clz.getDeclaredField (name.substring (12));
              break; // found the property field, so break the while loop.
            }
            catch (java.lang.NoSuchFieldException nsfe) {
              // property field is not found at current level of class, so continue to super class.
              clz = clz.getSuperclass();
              if (clz == Object.class)
                throw new org.apache.derby.client.am.SqlException (
                  new org.apache.derby.client.am.LogWriter (this.logWriter, this.traceLevel),
                  "bug check: corresponding property field does not exist"
                );
              continue;
            }
          }

          if (!java.lang.reflect.Modifier.isTransient (propertyField.getModifiers())) {
            // if the property is not transient:
            // get the property.
            propertyField.setAccessible (true);
            Object propertyObj = propertyField.get (this);
            String property = String.valueOf (propertyObj); // don't use toString becuase it may be null.
            if ("password".equals(propertyKey)) {
              StringBuffer sb = new StringBuffer (property);
              for (int j = 0; j< property.length(); j++) {
                sb.setCharAt(j,'*');
              }
              property = sb.toString();
            }
            // add into prperties.
            properties.setProperty (propertyKey, property);
          }
        }
        catch (java.lang.IllegalAccessException e) {
          throw new org.apache.derby.client.am.SqlException (
            new org.apache.derby.client.am.LogWriter (this.logWriter, this.traceLevel),
            "bug check: property cannot be accessed"
          );
        }
      }
    }

    return properties;
  }

  //---------------------- helper methods --------------------------------------

  // The java.io.PrintWriter overrides the traceFile setting.
  // If neither traceFile nor jdbc logWriter are set, then null is returned.
  public org.apache.derby.client.am.LogWriter computeDncLogWriterForNewConnection (
    String logWriterInUseSuffix) // used only for trace directories to indicate whether
                                 // log writer is use is from xads, cpds, sds, ds, driver, config, reset.
    throws org.apache.derby.client.am.SqlException
  {
    return computeDncLogWriterForNewConnection (
                                this.logWriter,
                                this.traceDirectory,
                                this.traceFile,
                                this.traceFileAppend,
                                this.traceLevel,
                                logWriterInUseSuffix,
                                this.traceFileSuffixIndex_++);
  }

  // Called on for connection requests.
  // The java.io.PrintWriter overrides the traceFile setting.
  // If neither traceFile, nor logWriter, nor traceDirectory are set, then null is returned.
  static public org.apache.derby.client.am.LogWriter computeDncLogWriterForNewConnection (
    java.io.PrintWriter logWriter,
    String traceDirectory,
    String traceFile,
    boolean traceFileAppend,
    int traceLevel,
    String logWriterInUseSuffix, // used only for trace directories to indicate whether
                                 // log writer is use is from xads, cpds, sds, ds, driver, config.
    int traceFileSuffixIndex) throws org.apache.derby.client.am.SqlException
  {
    int globaltraceFileSuffixIndex = Configuration.traceFileSuffixIndex__++;

    org.apache.derby.client.am.LogWriter dncLogWriter;
    // compute regular dnc log writer if there is any
    dncLogWriter =
      computeDncLogWriter (
        logWriter,
                                        traceDirectory,
                                        traceFile,
                                        traceFileAppend,
                                        logWriterInUseSuffix,
        traceFileSuffixIndex,
        traceLevel);
    if (dncLogWriter != null) return dncLogWriter;
    // compute global default dnc log writer if there is any
    dncLogWriter =
      computeDncLogWriter (
        null,
        Configuration.traceDirectory__,
        Configuration.traceFile__,
        Configuration.traceFileAppend__,
        "_global",
        globaltraceFileSuffixIndex,
        Configuration.traceLevel__);
    return dncLogWriter;
    }

  // Compute a DNC log writer before a connection is created.
  static org.apache.derby.client.am.LogWriter computeDncLogWriter (
    java.io.PrintWriter logWriter,
    String traceDirectory,
    String traceFile,
    boolean traceFileAppend,
    String logWriterInUseSuffix,
    int traceFileSuffixIndex,
    int traceLevel) throws org.apache.derby.client.am.SqlException
  {
    // Otherwise, the trace file will still be created even TRACE_NONE.
    if (traceLevel == TRACE_NONE) return null;

    java.io.PrintWriter printWriter =
      computePrintWriter (
        logWriter,
        traceDirectory,
        traceFile,
        traceFileAppend,
        logWriterInUseSuffix,
        traceFileSuffixIndex);
    if (printWriter == null) return null;

    org.apache.derby.client.am.LogWriter dncLogWriter;
    dncLogWriter = new org.apache.derby.client.net.NetLogWriter (printWriter, traceLevel);
    if (printWriter != logWriter && traceDirectory != null)
      // When printWriter is an internal trace file and
      // traceDirectory is not null, each connection has
      // its own trace file and the trace file is not cached,
      // so we can close it when DNC log writer is closed.
      dncLogWriter.printWriterNeedsToBeClosed_ = true;
    return dncLogWriter;
  }

  // Compute a DNC log writer after a connection is created.
  // Declared public for use by am.Connection.  Not a public external.
  public static org.apache.derby.client.am.LogWriter computeDncLogWriter (
    org.apache.derby.client.am.Connection connection,
    java.io.PrintWriter logWriter,
    String traceDirectory,
    String traceFile,
    boolean traceFileAppend,
    String logWriterInUseSuffix,
    int traceFileSuffixIndex,
    int traceLevel) throws org.apache.derby.client.am.SqlException
  {
    // Otherwise, the trace file will still be created even TRACE_NONE.
    if (traceLevel == TRACE_NONE) return null;

    java.io.PrintWriter printWriter =
      computePrintWriter (
        logWriter,
        traceDirectory,
        traceFile,
        traceFileAppend,
        logWriterInUseSuffix,
        traceFileSuffixIndex);
    if (printWriter == null) return null;

    org.apache.derby.client.am.LogWriter dncLogWriter =
      connection.agent_.newLogWriter_ (printWriter, traceLevel);
    if (printWriter != logWriter && traceDirectory != null)
      // When printWriter is an internal trace file and
      // traceDirectory is not null, each connection has
      // its own trace file and the trace file is not cached,
      // so we can close it when DNC log writer is closed.
      dncLogWriter.printWriterNeedsToBeClosed_ = true;
    return dncLogWriter;
  }

  // This method handles all the override semantics.
  // The logWriter overrides the traceFile, and traceDirectory settings.
  // If neither traceFile, nor logWriter, nor traceDirectory are set, then null is returned.
  static java.io.PrintWriter computePrintWriter (
    java.io.PrintWriter logWriter,
    String traceDirectory,
    String traceFile,
    boolean traceFileAppend,
    String logWriterInUseSuffix, // used only for trace directories to indicate whether
                                 // log writer is use is from xads, cpds, sds, ds, driver, config.
    int traceFileSuffixIndex) throws org.apache.derby.client.am.SqlException
  {
    if (logWriter != null)  // java.io.PrintWriter is specified
      return logWriter;
    else { // check trace file setting.
      if (traceDirectory != null) {
        String fileName;
        if (traceFile == null) fileName = traceDirectory + "/" + logWriterInUseSuffix + "_" + traceFileSuffixIndex;
        else fileName = traceDirectory + "/" + traceFile + logWriterInUseSuffix + "_" + traceFileSuffixIndex;
        return LogWriter.getPrintWriter (fileName, true); // no file append and not enable caching.
      } else if (traceFile != null) {
        return LogWriter.getPrintWriter (traceFile, traceFileAppend);
      }
    }
    return null;
  }

  private static boolean parseBoolean (String boolString, boolean defaultBool)
  {
    if (boolString != null) return (boolString.equalsIgnoreCase ("true") || boolString.equalsIgnoreCase ("yes"));
    return defaultBool;
  }

  private static String parseString (String string, String defaultString)
  {
    if (string != null) return string;
    return defaultString;
  }

  private static short parseShort (String shortString, short defaultShort)
  {
    if (shortString != null)  return Short.parseShort (shortString);
    return defaultShort;
  }

  private static int parseInt (String intString, int defaultInt)
  {
    if (intString != null) return Integer.parseInt (intString);
    return defaultInt;
  }

  private static long parseLong (String longString, long defaultLong)
  {
    if (longString != null) return Long.parseLong (longString);
    return defaultLong;
  }

  private static int parseTernaryValue (String valueString, int defaultValue)
  {
    if ("true".equalsIgnoreCase (valueString) || "yes".equalsIgnoreCase (valueString)) return YES;
    if ("false".equalsIgnoreCase (valueString) || "no".equalsIgnoreCase (valueString)) return NO;
    if (valueString != null) {
      int value = Integer.parseInt (valueString);
      if (value < 0 || value > 2) throw new java.lang.NumberFormatException (valueString);
      return value;
    }
    return defaultValue;
  }

 // tokenize "property=value;property=value..." and returns new properties object
  //This method is used both by ClientDriver to parse the url and 
  // ClientDataSource.setConnectionAttributes
	public static java.util.Properties tokenizeAttributes (String attributeString,
                                               java.util.Properties properties) throws SqlException
{
	java.util.Properties augmentedProperties;

	if (attributeString == null)
		return properties;

	if (properties != null)
		augmentedProperties = (java.util.Properties) properties.clone();
	else
		augmentedProperties = new Properties();
    try {
		java.util.StringTokenizer attrTokenizer = 
			new java.util.StringTokenizer(attributeString,";");
      while (attrTokenizer.hasMoreTokens()) {
		  String v = attrTokenizer.nextToken();

			int eqPos = v.indexOf('=');
			if (eqPos == -1)
				throw new SqlException (null, "Invalid attribute syntax: " + attributeString);

			augmentedProperties.setProperty((v.substring(0, eqPos)).trim(),
									(v.substring(eqPos + 1)).trim()
									);
		}
    }
    catch (java.util.NoSuchElementException e) {
      // A null log writer is passed, because jdbc 1 sqlexceptions are automatically traced
      throw new SqlException (null, e, "Invalid attribute syntax: " + attributeString);
    }
	checkBoolean(augmentedProperties,propertyKey_retrieveMessageText);
    return augmentedProperties;
	
  }

	private static void checkBoolean(Properties set, String attribute) throws SqlException
    {
        final String[] booleanChoices = {"true", "false"};
        checkEnumeration( set, attribute, booleanChoices);
	}


	private static void checkEnumeration(Properties set, String attribute, String[] choices) throws SqlException
    {
		String value = set.getProperty(attribute);
		if (value == null)
			return;

        for( int i = 0; i < choices.length; i++)
        {
            if( value.toUpperCase(java.util.Locale.ENGLISH).equals( choices[i].toUpperCase(java.util.Locale.ENGLISH)))
                return;
        }

        // The attribute value is invalid. Construct a string giving the choices for
        // display in the error message.
        String choicesStr = "{";
        for( int i = 0; i < choices.length; i++)
        {
            if( i > 0)
                choicesStr += "|";
            choicesStr += choices[i];
        }
        
		throw new SqlException (null, "JDBC attribute " + attribute +
								"has an invalid value " + value + 
								" Valid values are " + choicesStr);
	}



}


