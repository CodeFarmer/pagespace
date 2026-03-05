package io.gluth.pagespace.android

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gluth.pagespace.backend.WikipediaContentBackend
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import io.gluth.pagespace.presenter.NavigationPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageSpaceViewModel : ViewModel() {

    companion object {
        private const val TAG = "PageSpace"
    }

    private val backend = WikipediaContentBackend(androidHttpGet())
    val graph = PageGraph()
    val layout = ForceDirectedLayout(graph, 760.0, 660.0)
    val presenter = NavigationPresenter(graph, layout, backend)

    var currentPage by mutableStateOf<Page?>(null)
        private set
    var htmlBody by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var linkDensity by mutableIntStateOf(presenter.linkDensity)
        private set

    // Search state
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<Page>>(emptyList())
        private set
    var showSearchResults by mutableStateOf(false)
        private set

    // Navigation version counter — triggers recomposition of spatial canvas
    var positionsVersion by mutableIntStateOf(0)
        private set

    private var navJob: Job? = null
    private var searchJob: Job? = null

    init {
        Log.i(TAG, "ViewModel init, navigating to default page")
        navigateTo(backend.defaultPage())
    }

    fun navigateTo(page: Page) {
        navJob?.cancel()
        navJob = viewModelScope.launch {
            try {
                isLoading = true
                errorMessage = null
                val myGen = presenter.newNavGeneration()

                Log.i(TAG, "Fetching nav data for: ${page.id}")
                val result = withContext(Dispatchers.IO) {
                    presenter.fetchNavData(page)
                }
                Log.i(TAG, "Fetch complete for: ${page.id}, error=${result.error}, links=${result.linkedPages.size}")

                if (result.error != null) {
                    if (presenter.currentNavGeneration() == myGen) {
                        errorMessage = result.error
                        isLoading = false
                    }
                    return@launch
                }

                val applied = presenter.applyNavigation(result, myGen)
                if (applied == null) {
                    Log.w(TAG, "Navigation stale for: ${page.id}")
                    isLoading = false
                    return@launch
                }

                currentPage = applied.page
                htmlBody = applied.bodyWithSeeAlso
                positionsVersion++
                isLoading = false
                Log.i(TAG, "Navigation applied for: ${page.id}")

                // Second-order enrichment
                val neighbors = applied.truncatedLinks
                val density = presenter.linkDensity
                launch {
                    try {
                        val soResult = withContext(Dispatchers.IO) {
                            presenter.fetchSecondOrderData(neighbors, density, myGen)
                        }
                        presenter.applySecondOrder(soResult, applied.page, myGen)
                        positionsVersion++
                        Log.i(TAG, "Second-order enrichment complete")
                    } catch (e: Exception) {
                        Log.e(TAG, "Second-order enrichment failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Navigation failed for: ${page.id}", e)
                errorMessage = "Error: ${e.message}"
                isLoading = false
            }
        }
    }

    fun navigateBack() {
        val page = presenter.navigateBack() ?: return
        navigateTo(page)
    }

    fun adjustDensity(delta: Int) {
        val newDensity = (presenter.linkDensity + delta).coerceIn(5, 50)
        if (newDensity == presenter.linkDensity) return
        val result = presenter.applyDensity(newDensity) ?: return
        linkDensity = newDensity
        currentPage = result.page
        htmlBody = result.bodyWithSeeAlso
        positionsVersion++
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (query.isEmpty()) {
            searchResults = emptyList()
            showSearchResults = false
            return
        }

        // Instant local matches
        val local = presenter.localSearch(query)
        searchResults = local
        showSearchResults = local.isNotEmpty()

        // Debounced backend search
        searchJob?.cancel()
        val gen = presenter.newSearchGeneration()
        searchJob = viewModelScope.launch {
            delay(300)
            val backendResults = withContext(Dispatchers.IO) {
                presenter.backendSearch(query)
            }
            if (!presenter.applyBackendSearchResults(backendResults, gen)) return@launch

            val currentLocal = presenter.localSearch(searchQuery)
            val localIds = currentLocal.map { it.id }.toSet()
            val remoteOnly = backendResults.filter { it.id !in localIds }
            searchResults = currentLocal + remoteOnly.take(10 - currentLocal.size)
            showSearchResults = searchResults.isNotEmpty()
        }
    }

    fun selectSearchResult(page: Page) {
        searchQuery = ""
        searchResults = emptyList()
        showSearchResults = false
        navigateTo(page)
    }

    fun dismissSearch() {
        searchQuery = ""
        searchResults = emptyList()
        showSearchResults = false
    }

    fun dismissError() {
        errorMessage = null
    }
}
