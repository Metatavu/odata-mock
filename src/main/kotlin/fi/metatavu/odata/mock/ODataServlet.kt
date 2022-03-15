package fi.metatavu.odata.mock

import fi.metatavu.odata.mock.data.DataProvider
import fi.metatavu.odata.mock.processor.ODataProcessor
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
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession


/**
 * OData servlet
 */
@WebServlet(urlPatterns = ["/odata/*"], loadOnStartup = 1)
class ODataServlet : HttpServlet() {

    private val edmProvider: SchemaBasedEdmProvider = loadEdmProvider()

    @Throws(ServletException::class, IOException::class)
    override fun service(req: HttpServletRequest, resp: HttpServletResponse?) {
        try {
            val session: HttpSession = req.getSession(true)
            var dataProvider: DataProvider? = session.getAttribute(DataProvider::class.java.getName()) as DataProvider?
            if (dataProvider == null) {
                dataProvider = DataProvider()
                session.setAttribute(DataProvider::class.java.getName(), dataProvider)
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