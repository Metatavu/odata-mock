package fi.metatavu.odata.mock.rest

import fi.metatavu.odata.mock.api.model.Entry
import fi.metatavu.odata.mock.api.spec.EntriesApi
import fi.metatavu.odata.mock.data.DataContainer
import java.util.*
import javax.ws.rs.NotFoundException
import javax.ws.rs.Path

/**
 * Entries API implementation
 */
@Path("/")
class EntriesApiImpl: EntriesApi {

    override fun createEntry(entry: Entry): Entry {
        return DataContainer.addEntry(entry)
    }

    override fun deleteEntries(name: String?) {
        DataContainer.removeEntries(name = name)
    }

    override fun deleteEntry(entryId: UUID) {
        DataContainer.removeEntry(id = entryId)
    }

    override fun findEntry(entryId: UUID): Entry {
        return DataContainer.getEntry(id = entryId) ?: throw NotFoundException("Not found")
    }

}