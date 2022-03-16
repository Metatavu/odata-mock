package fi.metatavu.odata.mock.sessions

import java.util.UUID

/**
 * Container for mocked sessions
 */
class SessionContainer {

    companion object {
        private val sessions: MutableList<String> = mutableListOf()

        /**
         * Adds new session
         *
         * @return session id
         */
        fun addSession(): String {
            val result = UUID.randomUUID().toString()
            sessions.add(result)
            return result
        }

        /**
         * Removes session
         *
         * @param session id
         */
        fun removeSession(session: String) {
            sessions.remove(session)
        }

        /**
         * Returns whether session is active or not
         *
         * @param session id
         * @return whether session is active or not
         */
        fun isActive(session: String): Boolean {
            return sessions.contains(session)
        }

    }

}