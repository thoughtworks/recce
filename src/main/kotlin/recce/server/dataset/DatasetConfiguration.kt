package recce.server.dataset

import io.micronaut.context.BeanLocator
import recce.server.PostConstructable
import javax.validation.constraints.NotNull

class DatasetConfiguration(@NotNull val source: DataLoadDefinition, @NotNull val target: DataLoadDefinition) :
    PostConstructable {
    lateinit var name: String
    override fun populate(locator: BeanLocator) {
        source.populate(locator)
        target.populate(locator)
    }
}
