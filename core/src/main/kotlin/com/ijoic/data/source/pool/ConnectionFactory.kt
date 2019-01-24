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

import com.ijoic.data.source.Connection
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.impl.DefaultPooledObject

/**
 * Connection factory
 *
 * @author verstsiu created at 2019-01-24 16:49
 */
internal class ConnectionFactory(
  private val createConnection: () -> Connection): BasePooledObjectFactory<Connection>() {

  override fun wrap(obj: Connection?) = DefaultPooledObject(obj)

  override fun create() = createConnection()
}