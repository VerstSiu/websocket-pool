/*
 *
 *  Copyright(c) 2019 VerstSiu
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.ijoic.data.source.pool

/**
 * Connection pool factory
 *
 * @author verstsiu created at 2019-01-26 15:39
 */
object ConnectionPoolFactory {

  private val creatorMap = mutableMapOf<String, () -> ConnectionPool>()
  private val poolMap = mutableMapOf<String, ConnectionPool>()

  private val editLock = Object()

  /**
   * Register connection pool creator with [key] and [onCreate]
   */
  @JvmStatic
  fun register(key: String, onCreate: () -> ConnectionPool) {
    synchronized(editLock) {
      creatorMap[key] = onCreate
    }
  }

  /**
   * Returns connection pool instance or null
   */
  @JvmStatic
  fun getConnectionPool(key: String): ConnectionPool? {
    synchronized(editLock) {
      val oldPool = poolMap[key]

      if (oldPool != null) {
        return oldPool
      }
      val creator = creatorMap[key] ?: return null
      val pool = creator.invoke()

      poolMap[key] = pool
      return pool
    }
  }
}