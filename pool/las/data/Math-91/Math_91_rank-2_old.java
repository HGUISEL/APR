/*
* Copyright (C) 2005-2007 Jeremy Haile, Les Hazlewood
*
* This library is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as published
* by the Free Software Foundation; either version 2.1 of the License, or
* (at your option) any later version.
*
* This library is distributed in the hope that it will be useful, but
* WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
* or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
* Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this library; if not, write to the
*
* Free Software Foundation, Inc.
* 59 Temple Place, Suite 330
* Boston, MA 02111-1307
* USA
*
* Or, you may view it online at
* http://www.opensource.org/licenses/lgpl-license.php
*/
package org.jsecurity.cache;

/**
 * A CacheManager provides and maintains the lifecycles of {@link Cache Cache} instances.
 *
 * <p>It is really a wrapper API around the underlying cache subsystem's central cache manager component.
 * (e.g. JCache, Ehcache, JCS, OSCache, JBossCache, TerraCotta, Coherence, GigaSpaces, etc, etc), allowing a
 * JSecurity user to configure any cache framework they choose.
 *
 * @author Jeremy Haile
 * @author Les Hazlewood
 * @since 0.2
 */
public interface CacheManager {

    /**
     * Acquires the cache with name <code>cacheName</code>.  If a cache does not yet exist with that name, a new one
     * will be created with that name and returned.
     *
     * @param cacheName the name of the cache to acquire.
     * @return the Cache with the given name
     * @throws CacheException if there is an error acquiring the Cache instance.
     */
    public Cache getCache( String cacheName ) throws CacheException;
}