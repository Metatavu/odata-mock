package fi.metatavu.odata.mock.data

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.metatavu.odata.mock.api.model.Entry
import java.util.UUID

/**
 * Mock data container
 */
class DataContainer {

    companion object {

        private val entries = mutableListOf<Entry>()

        /**
         * Adds mock entry
         *
         * @param entry entry
         * @return created mock entry
         */
        fun addEntry(entry: Entry): Entry {
            val result = entry.copy(id = UUID.randomUUID())
            entries.add(result)
            return result
        }

        /**
         * Returns entry by id
         *
         * @param id id
         * @return entry or null if not found
         */
        fun getEntry(id: UUID): Entry? {
            return entries.find { it.id == id }
        }

        /**
         * Returns entries
         *
         * @param name filter by entry name
         * @return entries
         */
        fun getEntries(name: String?): List<Entry> {
            if (name.isNullOrEmpty()) {
                return entries.toList()
            }

            return entries.filter { it.name == name }
        }

        /**
         * Removes entry
         *
         * @param id id
         */
        fun removeEntry(id: UUID) {
            entries.removeIf { it.id.toString() == id.toString() }
        }

        /**
         * Removes entry
         *
         * @param entryName entry name
         * @param propertyName property name
         * @param propertyValue property value
         */
        fun removeEntryByKey(entryName: String, propertyName: String, propertyValue: Any) {
            val objectMapper = jacksonObjectMapper()

            entries.removeIf {
                if (it.name == entryName) {
                    val entry: ObjectNode = objectMapper.readTree(it.data) as ObjectNode
                    val entryId = "${entry.get(propertyName)}".trim('"')
                    val queryId = "$propertyValue".trim('"')
                    entryId == queryId
                } else {
                    false
                }
            }
        }

        /**
         * Removes entries
         *
         * @param name filter by entry name
         */
        fun removeEntries(name: String?) {
            if (name.isNullOrEmpty()) {
                entries.clear()
            } else {
                entries.removeIf { it.name == name }
            }
        }

    }

}