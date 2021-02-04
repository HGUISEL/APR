/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.atlas.repository.audit;

import org.apache.atlas.AtlasException;
import org.apache.atlas.EntityAuditEvent;
import org.apache.atlas.EntityAuditEvent.EntityAuditAction;
import org.apache.atlas.RequestContextV1;
import org.apache.atlas.listener.EntityChangeListener;
import org.apache.atlas.v1.model.instance.Referenceable;
import org.apache.atlas.v1.model.instance.Struct;
import org.apache.atlas.type.AtlasEntityType;
import org.apache.atlas.type.AtlasStructType;
import org.apache.atlas.type.AtlasType;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener on entity create/update/delete, tag add/delete. Adds the corresponding audit event to the audit repository.
 */
@Component
public class EntityAuditListener implements EntityChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(EntityAuditListener.class);

    private final EntityAuditRepository auditRepository;
    private final AtlasTypeRegistry     typeRegistry;

    @Inject
    public EntityAuditListener(EntityAuditRepository auditRepository, AtlasTypeRegistry typeRegistry) {
        this.auditRepository = auditRepository;
        this.typeRegistry    = typeRegistry;
    }

    @Override
    public void onEntitiesAdded(Collection<Referenceable> entities, boolean isImport) throws AtlasException {
        List<EntityAuditEvent> events = new ArrayList<>();
        for (Referenceable entity : entities) {
            EntityAuditEvent event = createEvent(entity, isImport ? EntityAuditAction.ENTITY_IMPORT_CREATE : EntityAuditAction.ENTITY_CREATE);
            events.add(event);
        }

        auditRepository.putEvents(events);
    }

    @Override
    public void onEntitiesUpdated(Collection<Referenceable> entities, boolean isImport) throws AtlasException {
        List<EntityAuditEvent> events = new ArrayList<>();
        for (Referenceable entity : entities) {
            EntityAuditEvent event = createEvent(entity, isImport ? EntityAuditAction.ENTITY_IMPORT_UPDATE : EntityAuditAction.ENTITY_UPDATE);
            events.add(event);
        }

        auditRepository.putEvents(events);
    }

    @Override
    public void onTraitsAdded(Referenceable entity, Collection<? extends Struct> traits) throws AtlasException {
        if (traits != null) {
            for (Struct trait : traits) {
                EntityAuditEvent event = createEvent(entity, EntityAuditAction.TAG_ADD,
                                                     "Added trait: " + AtlasType.toV1Json(trait));

                auditRepository.putEvents(event);
            }
        }
    }

    @Override
    public void onTraitsDeleted(Referenceable entity, Collection<String> traitNames) throws AtlasException {
        if (traitNames != null) {
            for (String traitName : traitNames) {
                EntityAuditEvent event = createEvent(entity, EntityAuditAction.TAG_DELETE, "Deleted trait: " + traitName);

                auditRepository.putEvents(event);
            }
        }
    }

    @Override
    public void onTraitsUpdated(Referenceable entity, Collection<? extends Struct> traits) throws AtlasException {
        if (traits != null) {
            for (Struct trait : traits) {
                EntityAuditEvent event = createEvent(entity, EntityAuditAction.TAG_UPDATE,
                                                     "Updated trait: " + AtlasType.toV1Json(trait));

                auditRepository.putEvents(event);
            }
        }
    }

    @Override
    public void onEntitiesDeleted(Collection<Referenceable> entities, boolean isImport) throws AtlasException {
        List<EntityAuditEvent> events = new ArrayList<>();
        for (Referenceable entity : entities) {
            EntityAuditEvent event = createEvent(entity, isImport ? EntityAuditAction.ENTITY_IMPORT_DELETE : EntityAuditAction.ENTITY_DELETE, "Deleted entity");
            events.add(event);
        }

        auditRepository.putEvents(events);
    }

    public List<EntityAuditEvent> getAuditEvents(String guid) throws AtlasException{
        return auditRepository.listEvents(guid, null, (short) 10);
    }

    private EntityAuditEvent createEvent(Referenceable entity, EntityAuditAction action)
            throws AtlasException {
        String detail = getAuditEventDetail(entity, action);

        return createEvent(entity, action, detail);
    }

    private EntityAuditEvent createEvent(Referenceable entity, EntityAuditAction action, String details)
            throws AtlasException {
        return new EntityAuditEvent(entity.getId()._getId(), RequestContextV1.get().getRequestTime(), RequestContextV1.get().getUser(), action, details, entity);
    }

    private String getAuditEventDetail(Referenceable entity, EntityAuditAction action) throws AtlasException {
        Map<String, Object> prunedAttributes = pruneEntityAttributesForAudit(entity);

        String auditPrefix  = getAuditPrefix(action);
        String auditString  = auditPrefix + AtlasType.toV1Json(entity);
        byte[] auditBytes   = auditString.getBytes(StandardCharsets.UTF_8);
        long   auditSize    = auditBytes != null ? auditBytes.length : 0;
        long   auditMaxSize = auditRepository.repositoryMaxSize();

        if (auditMaxSize >= 0 && auditSize > auditMaxSize) { // don't store attributes in audit
            LOG.warn("audit record too long: entityType={}, guid={}, size={}; maxSize={}. entity attribute values not stored in audit",
                    entity.getTypeName(), entity.getId()._getId(), auditSize, auditMaxSize);

            Map<String, Object> attrValues = entity.getValuesMap();

            clearAttributeValues(entity);

            auditString = auditPrefix + AtlasType.toV1Json(entity);

            addAttributeValues(entity, attrValues);
        }

        restoreEntityAttributes(entity, prunedAttributes);

        return auditString;
    }

    private void clearAttributeValues(Referenceable entity) throws AtlasException {
        Map<String, Object> attributesMap = entity.getValuesMap();

        if (MapUtils.isNotEmpty(attributesMap)) {
            for (String attribute : attributesMap.keySet()) {
                entity.setNull(attribute);
            }
        }
    }

    private void addAttributeValues(Referenceable entity, Map<String, Object> attributesMap) throws AtlasException {
        if (MapUtils.isNotEmpty(attributesMap)) {
            for (String attr : attributesMap.keySet()) {
                entity.set(attr, attributesMap.get(attr));
            }
        }
    }

    private Map<String, Object> pruneEntityAttributesForAudit(Referenceable entity) throws AtlasException {
        Map<String, Object> ret               = null;
        Map<String, Object> entityAttributes  = entity.getValuesMap();
        List<String>        excludeAttributes = auditRepository.getAuditExcludeAttributes(entity.getTypeName());
        AtlasEntityType     entityType        = typeRegistry.getEntityTypeByName(entity.getTypeName());

        if (CollectionUtils.isNotEmpty(excludeAttributes) && MapUtils.isNotEmpty(entityAttributes) && entityType != null) {
            for (AtlasStructType.AtlasAttribute attribute : entityType.getAllAttributes().values()) {
                String        attrName  = attribute.getName();
                Object        attrValue = entityAttributes.get(attrName);

                if (excludeAttributes.contains(attrName)) {
                    if (ret == null) {
                        ret = new HashMap<>();
                    }

                    ret.put(attrName, attrValue);
                    entity.setNull(attrName);
                } else if (attribute.isOwnedRef()) {
                    if (attrValue instanceof Collection) {
                        for (Object arrElem : (Collection) attrValue) {
                            if (arrElem instanceof Referenceable) {
                                ret = pruneAttributes(ret, (Referenceable) arrElem);
                            }
                        }
                    } else if (attrValue instanceof Referenceable) {
                        ret = pruneAttributes(ret, (Referenceable) attrValue);
                    }
                }
            }
        }

        return ret;
    }

    private Map<String, Object> pruneAttributes(Map<String, Object> ret, Referenceable attribute) throws AtlasException {
        Referenceable       attrInstance = attribute;
        Map<String, Object> prunedAttrs  = pruneEntityAttributesForAudit(attrInstance);

        if (MapUtils.isNotEmpty(prunedAttrs)) {
            if (ret == null) {
                ret = new HashMap<>();
            }

            ret.put(attrInstance.getId()._getId(), prunedAttrs);
        }

        return ret;
    }

    private void restoreEntityAttributes(Referenceable entity, Map<String, Object> prunedAttributes) throws AtlasException {
        if (MapUtils.isEmpty(prunedAttributes)) {
            return;
        }

        AtlasEntityType     entityType       = typeRegistry.getEntityTypeByName(entity.getTypeName());

        if (entityType != null && MapUtils.isNotEmpty(entityType.getAllAttributes())) {
            Map<String, Object> entityAttributes = entity.getValuesMap();

            for (AtlasStructType.AtlasAttribute attribute : entityType.getAllAttributes().values()) {
                String attrName  = attribute.getName();
                Object attrValue = entityAttributes.get(attrName);

                if (prunedAttributes.containsKey(attrName)) {
                    entity.set(attrName, prunedAttributes.get(attrName));
                } else if (attribute.isOwnedRef()) {
                    if (attrValue instanceof Collection) {
                        for (Object arrElem : (Collection) attrValue) {
                            if (arrElem instanceof Referenceable) {
                                restoreAttributes(prunedAttributes, (Referenceable) arrElem);
                            }
                        }
                    } else if (attrValue instanceof Referenceable) {
                        restoreAttributes(prunedAttributes, (Referenceable) attrValue);
                    }
                }
            }
        }
    }

    private void restoreAttributes(Map<String, Object> prunedAttributes, Referenceable attributeEntity) throws AtlasException {
        Object                      obj          = prunedAttributes.get(attributeEntity.getId()._getId());

        if (obj instanceof Map) {
            restoreEntityAttributes(attributeEntity, (Map) obj);
        }
    }

    private String getAuditPrefix(EntityAuditAction action) {
        final String ret;

        switch (action) {
            case ENTITY_CREATE:
                ret = "Created: ";
                break;
            case ENTITY_UPDATE:
                ret = "Updated: ";
                break;
            case ENTITY_DELETE:
                ret = "Deleted: ";
                break;
            case TAG_ADD:
                ret = "Added trait: ";
                break;
            case TAG_DELETE:
                ret = "Deleted trait: ";
                break;
            case TAG_UPDATE:
                ret = "Updated trait: ";
                break;
            case ENTITY_IMPORT_CREATE:
                ret = "Created by import: ";
                break;
            case ENTITY_IMPORT_UPDATE:
                ret = "Updated by import: ";
                break;
            case ENTITY_IMPORT_DELETE:
                ret = "Deleted by import: ";
                break;
            default:
                ret = "Unknown: ";
        }

        return ret;
    }
}
