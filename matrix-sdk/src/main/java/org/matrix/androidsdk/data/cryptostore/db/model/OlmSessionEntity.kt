/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.androidsdk.data.cryptostore.db.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.matrix.androidsdk.data.cryptostore.db.deserializeFromRealm
import org.matrix.androidsdk.data.cryptostore.db.serializeForRealm
import org.matrix.olm.OlmSession

fun OlmSessionEntity.Companion.createPrimaryKey(sessionId: String, deviceKey: String) = "$sessionId|$deviceKey"

// olmSessionData is a serialized OlmSession
open class OlmSessionEntity(@PrimaryKey var primaryKey: String = "",
                            var sessionId: String? = null,
                            var deviceKey: String? = null,
                            var olmSessionData: String? = null)
    : RealmObject() {

    fun getOlmSession(): OlmSession? {
        return deserializeFromRealm(olmSessionData)
    }

    fun putOlmSession(olmSession: OlmSession?) {
        olmSessionData = serializeForRealm(olmSession)
    }

    companion object
}