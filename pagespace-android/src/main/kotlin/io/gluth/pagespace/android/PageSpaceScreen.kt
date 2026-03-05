package io.gluth.pagespace.android

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.gluth.pagespace.domain.Page

@Composable
fun PageSpaceScreen(viewModel: PageSpaceViewModel = viewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = viewModel.presenter.history.canGoBack()) {
        viewModel.navigateBack()
    }

    // Show error via snackbar
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = Color(0xFF121423)
    ) { padding ->
        if (isLandscape) {
            LandscapeLayout(viewModel, Modifier.padding(padding))
        } else {
            PortraitLayout(viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PortraitLayout(viewModel: PageSpaceViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // Spatial pane with search overlay (~45%)
        Box(modifier = Modifier.fillMaxWidth().weight(0.45f)) {
            SpatialCanvas(
                layout = viewModel.layout,
                currentPage = viewModel.currentPage,
                positionsVersion = viewModel.positionsVersion,
                onNavigate = { viewModel.navigateTo(it) },
                modifier = Modifier.fillMaxSize().background(Color(0xFF121423))
            )
            // Search bar at top
            SearchBar(
                query = viewModel.searchQuery,
                results = viewModel.searchResults,
                showResults = viewModel.showSearchResults,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSelect = { viewModel.selectSearchResult(it) },
                onSearch = {
                    val results = viewModel.searchResults
                    if (results.isNotEmpty()) {
                        viewModel.selectSearchResult(results.first())
                    }
                },
                onDismiss = { viewModel.dismissSearch() },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
            // Density controls at bottom-right
            DensityControls(
                density = viewModel.linkDensity,
                onAdjust = { viewModel.adjustDensity(it) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            )
            // Loading indicator
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Content pane (~55%)
        Box(modifier = Modifier.fillMaxWidth().weight(0.55f)) {
            ContentView(
                htmlBody = viewModel.htmlBody,
                onNavigate = { pageId ->
                    viewModel.navigateTo(Page(pageId, pageId))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LandscapeLayout(viewModel: PageSpaceViewModel, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxSize()) {
        // Content pane (~1/3)
        Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
            ContentView(
                htmlBody = viewModel.htmlBody,
                onNavigate = { pageId ->
                    viewModel.navigateTo(Page(pageId, pageId))
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Spatial pane (~2/3)
        Box(modifier = Modifier.fillMaxHeight().weight(2f)) {
            SpatialCanvas(
                layout = viewModel.layout,
                currentPage = viewModel.currentPage,
                positionsVersion = viewModel.positionsVersion,
                onNavigate = { viewModel.navigateTo(it) },
                modifier = Modifier.fillMaxSize().background(Color(0xFF121423))
            )
            SearchBar(
                query = viewModel.searchQuery,
                results = viewModel.searchResults,
                showResults = viewModel.showSearchResults,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSelect = { viewModel.selectSearchResult(it) },
                onSearch = {
                    val results = viewModel.searchResults
                    if (results.isNotEmpty()) {
                        viewModel.selectSearchResult(results.first())
                    }
                },
                onDismiss = { viewModel.dismissSearch() },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
            DensityControls(
                density = viewModel.linkDensity,
                onAdjust = { viewModel.adjustDensity(it) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            )
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun DensityControls(
    density: Int,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { onAdjust(-5) }, modifier = Modifier.size(36.dp)) {
            Text("-", fontSize = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = density.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = { onAdjust(5) }, modifier = Modifier.size(36.dp)) {
            Text("+", fontSize = 16.sp)
        }
    }
}
