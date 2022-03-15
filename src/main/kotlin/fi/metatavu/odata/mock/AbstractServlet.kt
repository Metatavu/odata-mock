package fi.metatavu.odata.mock

import fi.metatavu.odata.mock.sessions.SessionContainer
import org.eclipse.microprofile.config.ConfigProvider
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest

/**
 * Abstract base servlet for all servlets
 */
abstract class AbstractServlet: HttpServlet() {

    protected val cookieName: String
        get() {
            val config = ConfigProvider.getConfig()
            return config.getValue("odata.mock.session.cookie.name", String::class.java)
        }

    /**
     * Returns session from request
     *
     * @param req request
     * @return session or null if not found
     */
    protected fun getSession(req: HttpServletRequest): String? {
        return req.cookies.find { it.name == cookieName }?.value
    }

    /**
     * Checks if request has valid session
     *
     * @param req request
     * @return whether session is valid or not
     */
    protected fun isValidSession(req: HttpServletRequest): Boolean {
        val session = getSession(req) ?: return false
        return SessionContainer.isActive(session)
    }

}