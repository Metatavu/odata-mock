package fi.metatavu.odata.mock.data

import org.apache.olingo.commons.api.data.*
import org.apache.olingo.commons.api.edm.*
import org.apache.olingo.commons.api.ex.ODataException
import org.apache.olingo.commons.api.format.ContentType
import org.apache.olingo.server.api.uri.UriParameter
import org.apache.olingo.server.core.deserializer.json.ODataJsonDeserializer
import java.util.*

/**
 * Exception used for data provider errors
 *
 * @param message message
 * @param reason original exception
 */
class DataProviderException(message: String, reason: Throwable) : ODataException(message, reason) {
}

/**
 * Data provider
 */
class DataProvider {

    /**
     * Reads entities for entity set
     *
     * @param edmEntitySet Entity set
     * @return entity collection
     */
    fun readAll(edmEntitySet: EdmEntitySet): EntityCollection {
        val result = EntityCollection()
        val deserializer = ODataJsonDeserializer(ContentType.APPLICATION_JSON)
        val entityType: EdmEntityType = edmEntitySet.entityType
        val mockedEntries = DataContainer.getEntries(entityType.name)

        mockedEntries.forEach { mockedEntry ->
            mockedEntry.data.byteInputStream().use {
                val deserialized = deserializer.entity(it, entityType)
                result.entities.add(deserialized.entity)
            }
        }

        return result
    }

    /**
     * Reads an entity. Using entry id
     *
     * @param edmEntitySet entity set
     * @param entryId entry id
     * @return entity
     */
    fun readByEntryId(edmEntitySet: EdmEntitySet, entryId: UUID): Entity? {
        val mockedEntry = DataContainer.getEntry(entryId) ?: return null
        val deserializer = ODataJsonDeserializer(ContentType.APPLICATION_JSON)
        val entityType: EdmEntityType = edmEntitySet.entityType
        return deserializer.entity(mockedEntry.data.byteInputStream(), entityType).entity
    }

    /**
     * Finds entity by keys
     *
     * @param edmEntitySet entity set
     * @param keys keys
     * @return found entity or null
     */
    fun read(edmEntitySet: EdmEntitySet, keys: List<UriParameter>): Entity? {
        val entityType = edmEntitySet.entityType
        val entitySet = readAll(edmEntitySet)

        try {
            for (entity in entitySet.entities) {
                for (key in keys) {
                    val property = entityType.getProperty(key.name) as EdmProperty
                    val type = property.type as EdmPrimitiveType
                    val value = type.valueToString(
                        entity.getProperty(key.name).value,
                        property.isNullable,
                        property.maxLength,
                        property.precision,
                        property.scale,
                        property.isUnicode
                    )

                    if (value == key.text || "'$value'" == key.text) {
                        return entity
                    }
                }
            }
        } catch (e: EdmPrimitiveTypeException) {
            throw DataProviderException("Wrong key!", e)
        }

        return null
    }

}
