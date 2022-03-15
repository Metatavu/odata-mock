package fi.metatavu.odata.mock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fi.metatavu.odata.mock.sessions.SessionContainer
import org.eclipse.microprofile.config.ConfigProvider
import javax.servlet.annotation.WebServlet
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Session servlet
 */
@WebServlet(urlPatterns = ["/Login", "/Logout"], loadOnStartup = 1)
class SessionServlet : AbstractServlet() {

    private val companydb: String
        get() {
            return ConfigProvider.getConfig()
                .getValue("odata.mock.session.companydb", String::class.java)
        }

    private val username: String
        get() {
            return ConfigProvider.getConfig()
                .getValue("odata.mock.session.username", String::class.java)
        }

    private val password: String
        get() {
            return ConfigProvider.getConfig()
                .getValue("odata.mock.session.password", String::class.java)
        }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        if (req.servletPath.equals("/Login")) {
            val objectMapper = jacksonObjectMapper()
            val payload: LoginPayload = objectMapper.readValue(req.inputStream)

            if (payload.CompanyDB != companydb || payload.Password != password || payload.UserName != username) {
                resp.status = 401
                return
            }

            val session = SessionContainer.addSession()
            resp.addCookie(Cookie(cookieName, session))
            resp.addCookie(Cookie("ROUTEID", "DEF"))
            resp.status = 200
        } else if (req.servletPath.equals("/Logout")) {
            val session = getSession(req)
            if (session == null || !isValidSession(req)) {
                resp.status = 401
                return
            }

            SessionContainer.removeSession(session)
            resp.addCookie(Cookie(cookieName, null))
            resp.status = 200
        } else {
            resp.sendError(501)
        }
    }

    /**
     * Login payload JSON
     */
    private data class LoginPayload (
        val CompanyDB: String?,
        val UserName: String?,
        val Password: String?
    )

}