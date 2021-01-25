/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
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
package org.apache.openmeetings.db.dao.basic;

import static org.apache.openmeetings.util.OpenmeetingsVariables.APPLICATION_NAME;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_APPLICATION_BASE_URL;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_APPLICATION_NAME;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_CRYPT_KEY;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_EXT_PROCESS_TTL;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_HEADER_CSP;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_HEADER_XFRAME;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_MAX_UPLOAD_SIZE_KEY;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_SIP_ENABLED;
import static org.apache.openmeetings.util.OpenmeetingsVariables.DEFAULT_APP_NAME;
import static org.apache.openmeetings.util.OpenmeetingsVariables.DEFAULT_BASE_URL;
import static org.apache.openmeetings.util.OpenmeetingsVariables.DEFAULT_MAX_UPLOAD_SIZE;
import static org.apache.openmeetings.util.OpenmeetingsVariables.EXT_PROCESS_TTL;
import static org.apache.openmeetings.util.OpenmeetingsVariables.configKeyCryptClassName;
import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;
import static org.apache.openmeetings.util.OpenmeetingsVariables.whiteboardDrawStatus;
import static org.apache.openmeetings.util.OpenmeetingsVariables.wicketApplicationName;

import java.lang.reflect.Constructor;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.apache.openjpa.conf.OpenJPAConfiguration;
import org.apache.openjpa.event.RemoteCommitProvider;
import org.apache.openjpa.event.TCPRemoteCommitProvider;
import org.apache.openjpa.persistence.OpenJPAEntityManagerSPI;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.apache.openmeetings.IApplication;
import org.apache.openmeetings.db.dao.IDataProviderDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.basic.Configuration;
import org.apache.openmeetings.util.DaoHelper;
import org.apache.openmeetings.util.crypt.CryptProvider;
import org.apache.wicket.Application;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insert/update/Delete on {@link Configuration}<br/>
 * <br/>
 * It provides basic mechanism to get a Conf Key:<br/>
 * {@link #getConfValue(String, Class, String)} <br/>
 * <br/>
 * <b> {@link #get(String)} is deprecated!</b>
 *
 * @author swagner
 *
 */
@Transactional
public class ConfigurationDao implements IDataProviderDao<Configuration> {
	private static final Logger log = Red5LoggerFactory.getLogger(ConfigurationDao.class, webAppRootKey);
	public final static String[] searchFields = {"key", "value"};

	@PersistenceContext
	private EntityManager em;

	@Autowired
	private UserDao userDao;

	public void updateClusterAddresses(String addresses) throws UnknownHostException {
		OpenJPAConfiguration cfg = ((OpenJPAEntityManagerSPI)OpenJPAPersistence.cast(em)).getConfiguration();
		RemoteCommitProvider prov = cfg.getRemoteCommitEventManager().getRemoteCommitProvider();
		if (prov instanceof TCPRemoteCommitProvider) {
			((TCPRemoteCommitProvider)prov).setAddresses(addresses);
		}
	}

	/**
	 * Retrieves Configuration regardless of its deleted status
	 *
	 * @param confKey
	 * @return
	 */
	public Configuration forceGet(String confKey) {
		try {
			List<Configuration> list = em.createNamedQuery("forceGetConfigurationByKey", Configuration.class)
					.setParameter("key", confKey).getResultList();
			return list.isEmpty() ? null : list.get(0);
		} catch (Exception e) {
			log.error("[forceGet]: ", e);
		}
		return null;
	}

	public List<Configuration> get(String... keys) {
		List<Configuration> result = new ArrayList<>();
		for (String key : keys) { //iteration is necessary to fill list with all values
			List<Configuration> r = em.createNamedQuery("getConfigurationsByKeys", Configuration.class)
					.setParameter("keys", Arrays.asList(key))
					.getResultList();
			result.add(r.isEmpty() ? null : r.get(0));
		}
		return result;
	}

	/**
	 * Return a object using a custom type and a default value if the key is not
	 * present, or value is not set
	 *
	 * Example: Integer my_key = getConfValue("my_key", Integer.class, "15");
	 *
	 * @param key
	 * @param type
	 * @param defaultValue
	 * @return
	 */
	public <T> T getConfValue(String key, Class<T> type, String defaultValue) {
		try {
			List<Configuration> list = get(key);

			if (list == null || list.isEmpty() || list.get(0) == null) {
				log.warn("Could not find key in configurations: " + key);
			} else {
				String val = list.get(0).getValue();
				// Use the custom value as default value
				if (val != null) {
					defaultValue = val;
				}
			}

			if (defaultValue == null) {
				return null;
			}
			// Either this can be directly assigned or try to find a constructor
			// that handles it
			if (type.isAssignableFrom(defaultValue.getClass())) {
				return type.cast(defaultValue);
			}
			Constructor<T> c = type.getConstructor(defaultValue.getClass());
			return c.newInstance(defaultValue);

		} catch (Exception err) {
			log.error("cannot be cast to return type, you have misconfigured your configurations: " + key, err);
			return null;
		}
	}

	/**
	 */
	public Configuration add(String key, String value, Long userId, String comment) {
		Configuration c = new Configuration();
		c.setKey(key);
		c.setValue(value);
		c.setComment(comment);
		return update(c, userId);
	}

	public String getAppName() {
		if (APPLICATION_NAME == null) {
			APPLICATION_NAME = getConfValue(CONFIG_APPLICATION_NAME, String.class, DEFAULT_APP_NAME);
		}
		return APPLICATION_NAME;
	}

	public String getBaseUrl() {
		String val = getConfValue(CONFIG_APPLICATION_BASE_URL, String.class, DEFAULT_BASE_URL);
		if (val != null && !val.endsWith("/")) {
			val += "/";
		}
		return val;
	}

	public boolean isSipEnabled() {
		return "yes".equals(getConfValue(CONFIG_SIP_ENABLED, String.class, "no"));
	}

	@Override
	public Configuration get(long id) {
		return get(Long.valueOf(id));
	}

	@Override
	public Configuration get(Long id) {
		if (id == null) {
			return null;
		}
		return em.createNamedQuery("getConfigurationById", Configuration.class)
				.setParameter("id", id).getSingleResult();
	}

	@Override
	public List<Configuration> get(int start, int count) {
		return em.createNamedQuery("getNondeletedConfiguration", Configuration.class)
				.setFirstResult(start).setMaxResults(count).getResultList();
	}

	@Override
	public List<Configuration> get(String search, int start, int count, String sort) {
		TypedQuery<Configuration> q = em.createQuery(DaoHelper.getSearchQuery("Configuration", "c", search, true, false, sort, searchFields), Configuration.class);
		q.setFirstResult(start);
		q.setMaxResults(count);
		return q.getResultList();
	}

	@Override
	public long count() {
		return em.createNamedQuery("countConfigurations", Long.class).getSingleResult();
	}

	@Override
	public long count(String search) {
		TypedQuery<Long> q = em.createQuery(DaoHelper.getSearchQuery("Configuration", "c", search, true, true, null, searchFields), Long.class);
		return q.getSingleResult();
	}

	@Override
	public Configuration update(Configuration entity, Long userId) {
		return update(entity, userId, false);
	}

	public Configuration update(Configuration entity, Long userId, boolean deleted) {
		String key = entity.getKey();
		String value = entity.getValue();
		if (entity.getId() == null || entity.getId().longValue() <= 0) {
			entity.setInserted(new Date());
			entity.setDeleted(deleted);
			em.persist(entity);
		} else {
			if (userId != null) {
				entity.setUser(userDao.get(userId));
			}
			entity.setDeleted(deleted);
			entity.setUpdated(new Date());
			entity = em.merge(entity);
		}
		switch (key) {
			case CONFIG_CRYPT_KEY:
				configKeyCryptClassName = value;
				CryptProvider.reset();
				break;
			case "show.whiteboard.draw.status":
				whiteboardDrawStatus = Boolean.valueOf("1".equals(value));
				break;
			case CONFIG_APPLICATION_NAME:
				APPLICATION_NAME = value;
				break;
			case CONFIG_HEADER_XFRAME:
			{
				IApplication iapp = (IApplication)Application.get(wicketApplicationName);
				if (iapp != null) {
					iapp.setXFrameOptions(value);
				}
			}
				break;
			case CONFIG_HEADER_CSP:
			{
				IApplication iapp = (IApplication)Application.get(wicketApplicationName);
				if (iapp != null) {
					iapp.setContentSecurityPolicy(value);
				}
			}
				break;
			case CONFIG_EXT_PROCESS_TTL:
				EXT_PROCESS_TTL = Integer.parseInt(value);
				break;
		}
		return entity;
	}

	@Override
	public void delete(Configuration entity, Long userId) {
		entity.setUpdated(new Date());
		this.update(entity, userId, true);
	}

	/**
	 * returns the max upload size configured by max_upload_size config key
	 *
	 * @param configurationDao
	 * @return
	 */
	public long getMaxUploadSize() {
		try {
			return getConfValue(CONFIG_MAX_UPLOAD_SIZE_KEY, Long.class, "" + DEFAULT_MAX_UPLOAD_SIZE);
		} catch (Exception e) {
			log.error("Invalid value saved for max_upload_size conf key: ", e);
		}
		return DEFAULT_MAX_UPLOAD_SIZE;
	}

	public String getCryptKey() {
		if (configKeyCryptClassName == null) {
			String cryptClass = getConfValue(CONFIG_CRYPT_KEY, String.class, null);
			if (cryptClass != null) {
				configKeyCryptClassName = cryptClass;
			}
		}
		return configKeyCryptClassName;
	}

	public boolean getWhiteboardDrawStatus() {
		if (whiteboardDrawStatus == null) {
			String drawStatus = getConfValue("show.whiteboard.draw.status", String.class, "0");
			whiteboardDrawStatus = Boolean.valueOf("1".equals(drawStatus));
		}
		return whiteboardDrawStatus.booleanValue();
	}
}
