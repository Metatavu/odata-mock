package fi.metatavu.odata.mock.processor

import fi.metatavu.odata.mock.data.DataProvider
import fi.metatavu.odata.mock.data.DataProviderException
import fi.metatavu.odata.mock.data.FilterExpressionVisitor
import org.apache.olingo.commons.api.data.ContextURL
import org.apache.olingo.commons.api.data.ContextURL.Suffix
import org.apache.olingo.commons.api.data.Entity
import org.apache.olingo.commons.api.data.EntityCollection
import org.apache.olingo.commons.api.edm.EdmComplexType
import org.apache.olingo.commons.api.edm.EdmEntitySet
import org.apache.olingo.commons.api.edm.EdmPrimitiveType
import org.apache.olingo.commons.api.format.ContentType
import org.apache.olingo.commons.api.http.HttpHeader
import org.apache.olingo.commons.api.http.HttpStatusCode
import org.apache.olingo.server.api.*
import org.apache.olingo.server.api.processor.*
import org.apache.olingo.server.api.serializer.*
import org.apache.olingo.server.api.uri.*
import org.apache.olingo.server.api.uri.queryoption.ExpandOption
import org.apache.olingo.server.api.uri.queryoption.SelectOption
import org.apache.olingo.server.api.uri.queryoption.expression.Expression
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.*

/**
 * OData processor for mock data
 */
class ODataProcessor(private val dataProvider: DataProvider) : EntityCollectionProcessor, EntityProcessor,
    PrimitiveProcessor, PrimitiveValueProcessor, ComplexProcessor {

    private var odata: OData? = null
    private var edm: ServiceMetadata? = null

    override fun init(odata: OData, edm: ServiceMetadata) {
        this.odata = odata
        this.edm = edm
    }

    override fun readEntityCollection(
        request: ODataRequest,
        response: ODataResponse,
        uriInfo: UriInfo,
        requestedContentType: ContentType
    ) {
        val edmEntitySet = getEdmEntitySet(uriInfo.asUriInfoResource())
        val entityCollection: EntityCollection = dataProvider.readAll(edmEntitySet)
        val serializer = odata!!.createSerializer(requestedContentType)
        val expand = uriInfo.expandOption
        val select = uriInfo.selectOption
        val id = request.rawBaseUri + "/" + edmEntitySet.name
        val filterOption = uriInfo.filterOption

        if (filterOption != null) {
            try {
                val entityList: MutableList<Entity> = entityCollection.entities
                val entityIterator = entityList.iterator()
                while (entityIterator.hasNext()) {
                    val currentEntity = entityIterator.next()
                    val filterExpression: Expression = filterOption.expression
                    val expressionVisitor = FilterExpressionVisitor(currentEntity)
                    val visitorResult: Any = filterExpression.accept(expressionVisitor)

                    if (visitorResult is Boolean) {
                        if (java.lang.Boolean.TRUE != visitorResult) {
                            entityIterator.remove()
                        }
                    } else {
                        throw ODataApplicationException(
                            "A filter expression must evaluate to type Edm.Boolean",
                            HttpStatusCode.BAD_REQUEST.statusCode,
                            Locale.ENGLISH
                        )
                    }
                }
            } catch (e: ExpressionVisitException) {
                throw ODataApplicationException(
                    "Exception in filter evaluation",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.statusCode, Locale.ENGLISH
                )
            }
        }

        val skipOption = uriInfo.skipOption
        if (skipOption != null) {
            val skipNumber = skipOption.value
            if (skipNumber >= 0) {
                if (skipNumber <= entityCollection.entities.size) {
                    var i = skipNumber
                    while (i > 0) {
                        entityCollection.entities.removeAt(0)
                        i--
                    }
                } else {
                    entityCollection.entities.clear()
                }
            } else {
                throw ODataApplicationException(
                    "Invalid value for \$skip",
                    HttpStatusCode.BAD_REQUEST.statusCode,
                    Locale.ROOT
                )
            }
        }

        val serializedContent = serializer.entityCollection(
            edm, edmEntitySet.entityType, entityCollection,
            EntityCollectionSerializerOptions.with()
                .id(id)
                .contextURL(
                    if (isODataMetadataNone(requestedContentType)) null else getContextUrl(
                        edmEntitySet,
                        false,
                        expand,
                        select,
                        null
                    )
                )
                .count(uriInfo.countOption)
                .expand(expand).select(select)
                .build()
        ).content

        response.content = serializedContent
        response.statusCode = HttpStatusCode.OK.statusCode
        response.setHeader(HttpHeader.CONTENT_TYPE, requestedContentType.toContentTypeString())
    }

    override fun readEntity(
        request: ODataRequest,
        response: ODataResponse,
        uriInfo: UriInfo,
        requestedContentType: ContentType
    ) {
        val edmEntitySet = getEdmEntitySet(uriInfo.asUriInfoResource())
        val entity: Entity? = try {
            readEntityInternal(uriInfo.asUriInfoResource(), edmEntitySet)
        } catch (e: DataProviderException) {
            throw ODataApplicationException(e.message, 500, Locale.ENGLISH)
        }

        if (entity == null) {
            throw ODataApplicationException(
                "No entity found for this key", HttpStatusCode.NOT_FOUND
                    .statusCode, Locale.ENGLISH
            )
        } else {
            val serializer = odata!!.createSerializer(requestedContentType)
            val expand = uriInfo.expandOption
            val select = uriInfo.selectOption
            val serializedContent = serializer.entity(
                edm, edmEntitySet.entityType, entity,
                EntitySerializerOptions.with()
                    .contextURL(
                        if (isODataMetadataNone(requestedContentType)) null else getContextUrl(
                            edmEntitySet,
                            true,
                            expand,
                            select,
                            null
                        )
                    )
                    .expand(expand).select(select)
                    .build()
            ).content
            response.content = serializedContent
            response.statusCode = HttpStatusCode.OK.statusCode
            response.setHeader(HttpHeader.CONTENT_TYPE, requestedContentType.toContentTypeString())
        }
    }

    override fun createEntity(
        request: ODataRequest, response: ODataResponse, uriInfo: UriInfo,
        requestFormat: ContentType, responseFormat: ContentType
    ) {
        throw ODataApplicationException(
            "Entity create is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun deleteEntity(request: ODataRequest, response: ODataResponse, uriInfo: UriInfo) {
        throw ODataApplicationException(
            "Entity delete is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun readPrimitive(request: ODataRequest, response: ODataResponse, uriInfo: UriInfo, format: ContentType) {
        readProperty(response, uriInfo, format, false)
    }

    override fun readComplex(request: ODataRequest, response: ODataResponse, uriInfo: UriInfo, format: ContentType) {
        readProperty(response, uriInfo, format, true)
    }

    override fun readPrimitiveValue(
        request: ODataRequest,
        response: ODataResponse,
        uriInfo: UriInfo,
        format: ContentType
    ) {
        val edmEntitySet = getEdmEntitySet(uriInfo.asUriInfoResource())

        val entity: Entity? = try {
            readEntityInternal(uriInfo.asUriInfoResource(), edmEntitySet)
        } catch (e: DataProviderException) {
            throw ODataApplicationException(e.message, 500, Locale.ENGLISH)
        }

        if (entity == null) {
            throw ODataApplicationException(
                "No entity found for this key", HttpStatusCode.NOT_FOUND
                    .statusCode, Locale.ENGLISH
            )
        } else {
            val uriProperty = uriInfo.uriResourceParts[uriInfo.uriResourceParts.size - 1] as UriResourceProperty
            val edmProperty = uriProperty.property
            val property = entity.getProperty(edmProperty.name)
            if (property == null) {
                throw ODataApplicationException(
                    "No property found", HttpStatusCode.NOT_FOUND
                        .statusCode, Locale.ENGLISH
                )
            } else {
                if (property.value == null) {
                    response.statusCode = HttpStatusCode.NO_CONTENT.statusCode
                } else {
                    val value = property.value.toString()
                    val serializerContent = ByteArrayInputStream(
                        value.toByteArray(Charset.forName("UTF-8"))
                    )
                    response.content = serializerContent
                    response.statusCode = HttpStatusCode.OK.statusCode
                    response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.TEXT_PLAIN.toContentTypeString())
                }
            }
        }
    }

    /**
     * Reads a property
     *
     * @param response OData response
     * @param uriInfo URI info
     * @param contentType content type
     * @param complex whether property is a complex type
     */
    private fun readProperty(
        response: ODataResponse,
        uriInfo: UriInfo,
        contentType: ContentType,
        complex: Boolean
    ) {
        val edmEntitySet = getEdmEntitySet(uriInfo.asUriInfoResource())
        val entity: Entity? = try {
            readEntityInternal(uriInfo.asUriInfoResource(), edmEntitySet)
        } catch (e: DataProviderException) {
            throw ODataApplicationException(
                e.message,
                HttpStatusCode.INTERNAL_SERVER_ERROR.statusCode, Locale.ENGLISH
            )
        }

        if (entity == null) {
            throw ODataApplicationException(
                "No entity found for this key",
                HttpStatusCode.NOT_FOUND.statusCode, Locale.ENGLISH
            )
        } else {
            val uriProperty = uriInfo.uriResourceParts[uriInfo.uriResourceParts.size - 1] as UriResourceProperty
            val edmProperty = uriProperty.property
            val property = entity.getProperty(edmProperty.name)
            if (property == null) {
                throw ODataApplicationException(
                    "No property found",
                    HttpStatusCode.NOT_FOUND.statusCode, Locale.ENGLISH
                )
            } else {
                if (property.value == null) {
                    response.statusCode = HttpStatusCode.NO_CONTENT.statusCode
                } else {
                    val serializer = odata!!.createSerializer(contentType)
                    val contextURL = if (isODataMetadataNone(contentType)) null else getContextUrl(
                        edmEntitySet,
                        true,
                        null,
                        null,
                        edmProperty.name
                    )
                    val serializerContent = if (complex) serializer.complex(
                        edm, edmProperty.type as EdmComplexType, property,
                        ComplexSerializerOptions.with().contextURL(contextURL).build()
                    ).content else serializer.primitive(
                        edm, edmProperty.type as EdmPrimitiveType, property,
                        PrimitiveSerializerOptions.with()
                            .contextURL(contextURL)
                            .scale(edmProperty.scale)
                            .nullable(edmProperty.isNullable)
                            .precision(edmProperty.precision)
                            .maxLength(edmProperty.maxLength)
                            .unicode(edmProperty.isUnicode).build()
                    ).content
                    response.content = serializerContent
                    response.statusCode = HttpStatusCode.OK.statusCode
                    response.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString())
                }
            }
        }
    }

    /**
     * Reads a entity
     *
     * @param uriInfo URI info
     * @param entitySet entity set
     * @return entity or null if could not be read
     */
    private fun readEntityInternal(
        uriInfo: UriInfoResource,
        entitySet: EdmEntitySet
    ): Entity? {
        val resourceEntitySet = uriInfo.uriResourceParts[0] as UriResourceEntitySet
        return dataProvider.read(entitySet, resourceEntitySet.keyPredicates)
    }

    /**
     * Returns EDM entity set from URI info
     *
     * @param uriInfo URI info
     * @return EDM entity set
     */
    private fun getEdmEntitySet(uriInfo: UriInfoResource): EdmEntitySet {
        val resourcePaths = uriInfo.uriResourceParts

        if (resourcePaths[0] !is UriResourceEntitySet) {
            throw ODataApplicationException(
                "Invalid resource type for first segment.",
                HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
            )
        }

        val uriResource = resourcePaths[0] as UriResourceEntitySet
        return uriResource.entitySet
    }

    /**
     * Returns context URL
     *
     * @param entitySet entity set
     * @param isSingleEntity whether is a single entity or not
     * @param expand expand options
     * @param select select options
     * @param navOrPropertyPath navigation property path
     * @return context URL
     */
    private fun getContextUrl(
        entitySet: EdmEntitySet,
        isSingleEntity: Boolean,
        expand: ExpandOption?,
        select: SelectOption?,
        navOrPropertyPath: String?
    ): ContextURL {
        return ContextURL.with().entitySet(entitySet)
            .selectList(odata!!.createUriHelper().buildContextURLSelectList(entitySet.entityType, expand, select))
            .suffix(if (isSingleEntity) Suffix.ENTITY else null)
            .navOrPropertyPath(navOrPropertyPath)
            .build()
    }

    override fun updatePrimitive(
        request: ODataRequest, response: ODataResponse,
        uriInfo: UriInfo, requestFormat: ContentType,
        responseFormat: ContentType
    ) {
        throw ODataApplicationException(
            "Primitive property update is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun updatePrimitiveValue(
        request: ODataRequest, response: ODataResponse,
        uriInfo: UriInfo, requestFormat: ContentType, responseFormat: ContentType
    ) {
        throw ODataApplicationException(
            "Primitive property update is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun deletePrimitive(request: ODataRequest, response: ODataResponse, uriInfo: UriInfo) {
        throw ODataApplicationException(
            "Primitive property delete is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun deletePrimitiveValue(request: ODataRequest, response: ODataResponse, uriInfo: UriInfo) {
        throw ODataApplicationException(
            "Primitive property update is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun updateComplex(
        request: ODataRequest, response: ODataResponse,
        uriInfo: UriInfo, requestFormat: ContentType,
        responseFormat: ContentType
    ) {
        throw ODataApplicationException(
            "Complex property update is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun deleteComplex(request: ODataRequest, response: ODataResponse, uriInfo: UriInfo) {
        throw ODataApplicationException(
            "Complex property delete is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun updateEntity(
        request: ODataRequest, response: ODataResponse,
        uriInfo: UriInfo, requestFormat: ContentType,
        responseFormat: ContentType
    ) {
        throw ODataApplicationException(
            "Entity update is not supported yet.",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    companion object {
        fun isODataMetadataNone(contentType: ContentType): Boolean {
            return (contentType.isCompatible(ContentType.APPLICATION_JSON)
                    && ContentType.VALUE_ODATA_METADATA_NONE.equals(
                contentType.getParameter(ContentType.PARAMETER_ODATA_METADATA), ignoreCase = true
            ))
        }
    }
}