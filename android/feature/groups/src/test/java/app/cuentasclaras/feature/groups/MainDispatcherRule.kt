package app.cuentasclaras.feature.groups

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sustituye `Dispatchers.Main` por un dispatcher de test. Se usa con
 * `runTest(mainDispatcherRule.dispatcher)` para que las corrutinas lanzadas por
 * el ViewModel avancen de forma determinista.
 */
class MainDispatcherRule(
    val dispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
