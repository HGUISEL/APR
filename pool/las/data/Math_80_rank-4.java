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
package org.apache.openmeetings.web.app;

import static org.apache.openmeetings.core.util.WebSocketHelper.sendRoom;
import static org.apache.openmeetings.web.app.WebSession.getUserId;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.openmeetings.core.remote.KurentoHandler;
import org.apache.openmeetings.db.dao.log.ConferenceLogDao;
import org.apache.openmeetings.db.entity.basic.Client;
import org.apache.openmeetings.db.entity.log.ConferenceLog;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.manager.IClientManager;
import org.apache.openmeetings.db.util.ws.RoomMessage;
import org.apache.openmeetings.db.util.ws.TextRoomMessage;
import org.apache.wicket.util.collections.ConcurrentHashSet;
import org.apache.wicket.util.string.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.hazelcast.query.Predicates;

@Component
public class ClientManager implements IClientManager {
	private static final Logger log = LoggerFactory.getLogger(ClientManager.class);
	private static final String ROOMS_KEY = "ROOMS_KEY";
	private static final String ONLINE_USERS_KEY = "ONLINE_USERS_KEY";
	private static final String SERVERS_KEY = "SERVERS_KEY";
	private static final String INSTANT_TOKENS_KEY = "INSTANT_TOKENS_KEY";
	private static final String UID_BY_SID_KEY = "UID_BY_SID_KEY";
	private final Map<String, Client> onlineClients = new ConcurrentHashMap<>();
	private final Map<Long, Set<String>> onlineRooms = new ConcurrentHashMap<>();
	private final Map<String, ServerInfo> onlineServers = new ConcurrentHashMap<>();

	@Autowired
	private ConferenceLogDao confLogDao;
	@Autowired
	private Application app;
	@Autowired
	private KurentoHandler kHandler;

	private IMap<String, Client> map() {
		return app.hazelcast.getMap(ONLINE_USERS_KEY);
	}

	private Map<String, String> mapBySid() {
		return app.hazelcast.getMap(UID_BY_SID_KEY);
	}

	private IMap<Long, Set<String>> rooms() {
		return app.hazelcast.getMap(ROOMS_KEY);
	}

	private IMap<String, ServerInfo> servers() {
		return app.hazelcast.getMap(SERVERS_KEY);
	}

	private IMap<String, InstantToken> tokens() {
		return app.hazelcast.getMap(INSTANT_TOKENS_KEY);
	}

	@PostConstruct
	void init() {
		map().addEntryListener(new ClientListener(), true);
		rooms().addEntryListener(new RoomListener(), true);
		servers().addEntryListener(new EntryUpdatedListener<String, ServerInfo>() {

			@Override
			public void entryUpdated(EntryEvent<String, ServerInfo> event) {
				log.trace("ServerListener::Update");
				onlineServers.put(event.getKey(), event.getValue());
			}
		}, true);
	}

	public void add(Client c) {
		confLogDao.add(
				ConferenceLog.Type.CLIENT_CONNECT
				, c.getUserId(), "0", null
				, c.getRemoteAddress()
				, "");
		log.debug("Adding online client: {}, room: {}", c.getUid(), c.getRoom());
		c.setServerId(Application.get().getServerId());
		map().put(c.getUid(), c);
		onlineClients.put(c.getUid(), c);
		mapBySid().put(c.getSid(), c.getUid());
	}

	@Override
	public Client update(Client c) {
		map().put(c.getUid(), c);
		synchronized (onlineClients) {
			onlineClients.get(c.getUid()).merge(c);
		}
		return c;
	}

	@Override
	public Client get(String uid) {
		return uid == null ? null : onlineClients.get(uid);
	}

	@Override
	public Client getBySid(String sid) {
		if (sid == null) {
			return null;
		}
		String uid = mapBySid().get(sid);
		return uid == null ? null : get(uid);
	}

	@Override
	public String uidBySid(String sid) {
		if (sid == null) {
			return null;
		}
		return mapBySid().get(sid);
	}

	public void exitRoom(Client c) {
		Long roomId = c.getRoomId();
		removeFromRoom(c);
		if (roomId != null) {
			sendRoom(new TextRoomMessage(roomId, c, RoomMessage.Type.ROOM_EXIT, c.getUid()));
			confLogDao.add(
					ConferenceLog.Type.ROOM_LEAVE
					, c.getUserId(), "0", roomId
					, c.getRemoteAddress()
					, String.valueOf(roomId));
		}
	}

	@Override
	public void exit(Client c) {
		if (c != null) {
			confLogDao.add(
					ConferenceLog.Type.CLIENT_DISCONNECT
					, c.getUserId(), "0", null
					, c.getRemoteAddress()
					, "");
			exitRoom(c);
			kHandler.remove(c);
			log.debug("Removing online client: {}, roomId: {}", c.getUid(), c.getRoomId());
			map().remove(c.getUid());
			onlineClients.remove(c.getUid());
			mapBySid().remove(c.getSid());
		}
	}

	public void serverAdded(String serverId, String url) {
		ServerInfo si = new ServerInfo(url);
		servers().put(serverId, si);
		onlineServers.put(serverId, si);
	}

	public void serverRemoved(String serverId) {
		Map<String, Client> clients = map();
		for (Map.Entry<String, Client> e : clients.entrySet()) {
			if (serverId.equals(e.getValue().getServerId())) {
				exit(e.getValue());
			}
		}
		servers().remove(serverId);
		onlineServers.remove(serverId);
	}

	/**
	 * This method will return count of users in room _after_ adding
	 *
	 * @param c - client to be added to the room
	 * @return count of users in room _after_ adding
	 */
	public int addToRoom(Client c) {
		Room r = c.getRoom();
		Long roomId = r.getId();
		confLogDao.add(
				ConferenceLog.Type.ROOM_ENTER
				, c.getUserId(), "0", roomId
				, c.getRemoteAddress()
				, String.valueOf(roomId));
		log.debug("Adding online room client: {}, room: {}", c.getUid(), roomId);
		IMap<Long, Set<String>> rooms = rooms();
		rooms.lock(roomId);
		rooms.putIfAbsent(roomId, new ConcurrentHashSet<String>());
		Set<String> set = rooms.get(roomId);
		set.add(c.getUid());
		final int count = set.size();
		rooms.put(roomId, set);
		onlineRooms.put(roomId, set);
		rooms.unlock(roomId);
		String serverId = c.getServerId();
		addRoomToServer(serverId, r);
		update(c);
		return count;
	}

	private void addRoomToServer(String serverId, Room r) {
		if (!onlineServers.get(serverId).getRooms().contains(r.getId())) {
			IMap<String, ServerInfo> servers = servers();
			servers.lock(serverId);
			ServerInfo si = servers.get(serverId);
			si.add(r);
			servers.put(serverId, si);
			onlineServers.put(serverId, si);
			servers.unlock(serverId);
		}
	}

	public Client removeFromRoom(Client c) {
		Long roomId = c.getRoomId();
		log.debug("Removing online room client: {}, room: {}", c.getUid(), roomId);
		if (roomId != null) {
			IMap<Long, Set<String>> rooms = rooms();
			rooms.lock(roomId);
			Set<String> clients = rooms.get(roomId);
			if (clients != null) {
				clients.remove(c.getUid());
				rooms.put(roomId, clients);
				onlineRooms.put(roomId, clients);
			}
			rooms.unlock(roomId);
			if (clients == null || clients.isEmpty()) {
				String serverId = c.getServerId();
				IMap<String, ServerInfo> servers = servers();
				servers.lock(serverId);
				ServerInfo si = servers.get(serverId);
				si.remove(c.getRoom());
				servers.put(serverId, si);
				onlineServers.put(serverId, si);
				servers.unlock(serverId);
			}
			kHandler.leaveRoom(c);
			c.setRoom(null);
			c.clear();
			update(c);
		}
		return c;
	}

	public boolean isOnline(Long userId) {
		boolean isUserOnline = false;
		for (Map.Entry<String, Client> e : map().entrySet()) {
			if (e.getValue().getUserId().equals(userId)) {
				isUserOnline = true;
				break;
			}
		}
		return isUserOnline;
	}

	@Override
	public List<Client> list() {
		return new ArrayList<>(map().values());
	}

	@Override
	public Collection<Client> listByUser(Long userId) {
		return map().values(Predicates.equal("userId", userId));
	}

	@Override
	public List<Client> listByRoom(Long roomId) {
		return listByRoom(roomId, null);
	}

	public List<Client> listByRoom(Long roomId, Predicate<Client> filter) {
		List<Client> clients = new ArrayList<>();
		if (roomId != null) {
			Set<String> uids = onlineRooms.get(roomId);
			if (uids != null) {
				for (String uid : uids) {
					Client c = get(uid);
					if (c != null && (filter == null || filter.test(c))) {
						clients.add(c);
					}
				}
			}
		}
		return clients;
	}

	public Set<Long> listRoomIds(Long userId) {
		Set<Long> result = new HashSet<>();
		for (Entry<Long, Set<String>> me : onlineRooms.entrySet()) {
			for (String uid : me.getValue()) {
				Client c = get(uid);
				if (c != null && c.getUserId().equals(userId)) {
					result.add(me.getKey());
				}
			}
		}
		return result;
	}

	public boolean isInRoom(long roomId, long userId) {
		Set<String> clients = onlineRooms.get(roomId);
		if (clients != null) {
			for (String uid : clients) {
				Client c = get(uid);
				if (c != null && c.getUserId().equals(userId)) {
					return true;
				}
			}
		}
		return false;
	}

	private List<Client> getByKeys(Long userId, String sessionId) {
		return map().values().stream()
				.filter(c -> c.getUserId().equals(userId) && c.getSessionId().equals(sessionId))
				.collect(Collectors.toList());
	}

	public void invalidate(Long userId, String sessionId) {
		for (Client c : getByKeys(userId, sessionId)) {
			Map<String, String> invalid = Application.get().getInvalidSessions();
			invalid.putIfAbsent(sessionId, c.getUid());
			exit(c);
		}
	}

	private String getServerUrl(Map.Entry<String, ServerInfo> e, Room r) {
		final String curServerId = app.getServerId();
		String serverId = e.getKey();
		if (!curServerId.equals(serverId)) {
			addRoomToServer(serverId, r);
			String uuid = UUID.randomUUID().toString();
			tokens().put(uuid, new InstantToken(getUserId(), r.getId()));
			return e.getValue().getUrl() + "?token=" + uuid;
		}
		return null;
	}

	public String getServerUrl(Room r) {
		if (onlineServers.size() == 1) {
			return null;
		}
		Optional<Map.Entry<String, ServerInfo>> existing = onlineServers.entrySet().stream()
				.filter(e -> e.getValue().getRooms().contains(r.getId()))
				.findFirst();
		if (existing.isPresent()) {
			return getServerUrl(existing.get(), r);
		}
		Optional<Map.Entry<String, ServerInfo>> min = onlineServers.entrySet().stream()
				.min((e1, e2) -> e1.getValue().getCapacity() - e2.getValue().getCapacity());
		return getServerUrl(min.get(), r);
	}

	Optional<InstantToken> getToken(StringValue uuid) {
		return uuid.isEmpty() ? Optional.empty() : Optional.ofNullable(tokens().remove(uuid.toString()));
	}

	public class ClientListener implements
			EntryAddedListener<String, Client>
			, EntryUpdatedListener<String, Client>
			, EntryRemovedListener<String, Client>
	{
		@Override
		public void entryAdded(EntryEvent<String, Client> event) {
			final String uid = event.getKey();
			synchronized (onlineClients) {
				if (onlineClients.containsKey(uid)) {
					onlineClients.get(uid).merge(event.getValue());
				} else {
					onlineClients.put(uid, event.getValue());
				}
			}
		}

		@Override
		public void entryUpdated(EntryEvent<String, Client> event) {
			synchronized (onlineClients) {
				onlineClients.get(event.getKey()).merge(event.getValue());
			}
		}

		@Override
		public void entryRemoved(EntryEvent<String, Client> event) {
			log.trace("ClientListener::Remove");
			onlineClients.remove(event.getKey());
		}
	}

	public class RoomListener implements
			EntryAddedListener<Long, Set<String>>
			, EntryUpdatedListener<Long, Set<String>>
			, EntryRemovedListener<Long, Set<String>>
	{
		@Override
		public void entryAdded(EntryEvent<Long, Set<String>> event) {
			log.trace("RoomListener::Add");
			onlineRooms.put(event.getKey(), event.getValue());
		}

		@Override
		public void entryUpdated(EntryEvent<Long, Set<String>> event) {
			log.trace("RoomListener::Update");
			onlineRooms.put(event.getKey(), event.getValue());
		}

		@Override
		public void entryRemoved(EntryEvent<Long, Set<String>> event) {
			log.trace("RoomListener::Remove");
			onlineRooms.remove(event.getKey(), event.getValue());
		}
	}

	private static class ServerInfo implements Serializable {
		private static final long serialVersionUID = 1L;
		private int capacity = 0;
		private final String url;
		private final Set<Long> rooms = new HashSet<>();

		public ServerInfo(String url) {
			this.url = url;
		}

		public void add(Room r) {
			if (rooms.add(r.getId())) {
				capacity += r.getCapacity();
			}
		}

		public void remove(Room r) {
			if (rooms.remove(r.getId())) {
				capacity -= r.getCapacity();
			}
		}

		public String getUrl() {
			return url;
		}

		public int getCapacity() {
			return capacity;
		}

		public Set<Long> getRooms() {
			return rooms;
		}
	}

	public static class InstantToken implements Serializable {
		private static final long serialVersionUID = 1L;
		private final long userId;
		private final long roomId;
		private final long created;

		InstantToken(long userId, long roomId) {
			this.userId = userId;
			this.roomId = roomId;
			created = System.currentTimeMillis();
		}

		public long getUserId() {
			return userId;
		}

		public long getRoomId() {
			return roomId;
		}
	}
}
