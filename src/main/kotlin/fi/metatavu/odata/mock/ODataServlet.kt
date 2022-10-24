package fi.metatavu.odata.mock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fi.metatavu.odata.mock.data.DataProvider
import fi.metatavu.odata.mock.processor.ODataProcessor
import fi.metatavu.odata.mock.sessions.SessionContainer
import org.apache.olingo.commons.api.edmx.EdmxReference
import org.apache.olingo.server.api.OData
import org.apache.olingo.server.api.ODataHttpHandler
import org.apache.olingo.server.api.ServiceMetadata
import org.apache.olingo.server.core.MetadataParser
import org.apache.olingo.server.core.SchemaBasedEdmProvider
import org.eclipse.microprofile.config.ConfigProvider
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import javax.servlet.ServletException
import javax.servlet.annotation.WebServlet
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

/**
 * Login payload JSON
 */
private data class LoginPayload (
    val CompanyDB: String?,
    val UserName: String?,
    val Password: String?
)

/**
 * OData servlet
 */
@WebServlet(urlPatterns = ["/odata/*"], loadOnStartup = 1)
class ODataServlet : AbstractServlet() {

    private val edmProvider: SchemaBasedEdmProvider = loadEdmProvider()

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

    @Throws(ServletException::class, IOException::class)
    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
        if ("/Login" == req.pathInfo) {
            handleLogin(req, resp)
        } else if ("/Logout" == req.pathInfo) {
            handleLogout(req, resp)
        } else {
            handleODataRequest(req, resp)
        }
    }

    /**
     * Handles logout request
     *
     * @param req request
     * @param resp response
     */
    private fun handleLogout(req: HttpServletRequest, resp: HttpServletResponse) {
        val session = getSession(req)
        if (session == null || !isValidSession(req)) {
            resp.status = 401
            return
        }

        SessionContainer.removeSession(session)
        resp.addCookie(Cookie(cookieName, null))
        resp.status = 204
    }

    /**
     * Handles login request
     *
     * @param req request
     * @param resp response
     */
    private fun handleLogin(req: HttpServletRequest, resp: HttpServletResponse) {
        val objectMapper = jacksonObjectMapper()
        val payload: LoginPayload = objectMapper.readValue(req.inputStream)

        if (payload.CompanyDB != companydb) {
            LOG.warn("CompanyDB ${payload.CompanyDB} does not match expected $companydb")
            resp.status = 401
            return
        }

        if (payload.Password != password) {
            LOG.warn("Password ${payload.Password} does not match expected $password")
            resp.status = 401
            return
        }

        if (payload.UserName != username) {
            LOG.warn("UserName ${payload.UserName} does not match expected $username")
            resp.status = 401
            return
        }

        val session = SessionContainer.addSession()
        val cookie = Cookie(cookieName, session)
        cookie.path = "DEF"
        resp.addCookie(cookie)
        resp.status = 200
    }

    /**
     * Handles OData request
     *
     * @param req request
     * @param resp response
     */
    private fun handleODataRequest(
        req: HttpServletRequest,
        resp: HttpServletResponse
    ) {
        try {
            if (!isValidSession(req)) {
                resp.status = 401
                return
            }

            val session: HttpSession = req.getSession(true)
            var dataProvider: DataProvider? = session.getAttribute(DataProvider::class.java.name) as DataProvider?
            if (dataProvider == null) {
                dataProvider = DataProvider()
                session.setAttribute(DataProvider::class.java.name, dataProvider)
                LOG.info("Created new data provider.")
            }

            val odata: OData = OData.newInstance()
            val edm: ServiceMetadata = odata.createServiceMetadata(edmProvider, ArrayList<EdmxReference>())
            val handler: ODataHttpHandler = odata.createHandler(edm)
            handler.register(ODataProcessor(dataProvider))
            handler.process(req, resp)
        } catch (e: RuntimeException) {
            LOG.error("Server Error", e)
            throw ServletException(e)
        }
    }

    /**
     * Loads EDM provider
     *
     * @return loaded EDM provider
     */
    private fun loadEdmProvider(): SchemaBasedEdmProvider {
        getEdmFile().use { edmStream ->
            InputStreamReader(edmStream).use { edmReader ->
                return MetadataParser().buildEdmProvider(edmReader)
            }
        }
    }

    /**
     * Returns file input stream for edm file
     *
     * @return file input stream for edm file
     */
    private fun getEdmFile(): FileInputStream {
        val config = ConfigProvider.getConfig()
        val edmFile = config.getValue("odata.mock.edm.file", String::class.java)
        return FileInputStream(edmFile)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ODataServlet::class.java)
    }

}