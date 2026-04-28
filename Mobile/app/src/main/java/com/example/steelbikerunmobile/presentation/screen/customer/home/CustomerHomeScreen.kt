package com.example.steelbikerunmobile.presentation.screen.customer.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.steelbikerunmobile.presentation.screen.customer.component.CustomerMapView
import com.example.steelbikerunmobile.presentation.screen.customer.component.DestinationSearchSheet
import com.example.steelbikerunmobile.presentation.screen.customer.component.DriverTrackingCard
import com.example.steelbikerunmobile.presentation.screen.customer.component.FindingDriverOverlay
import com.example.steelbikerunmobile.presentation.screen.customer.component.TripInProgressCard
import com.example.steelbikerunmobile.presentation.screen.customer.component.TripPreviewSheet
import com.example.steelbikerunmobile.presentation.screen.customer.component.TripReceiptOverlay
import com.example.steelbikerunmobile.presentation.theme.SteelBikeTheme

@Composable
fun CustomerHomeScreen(
    onLogout: () -> Unit,
    viewModel: CustomerHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // System back-button behaviour per step
    BackHandler(enabled = uiState.flowStep != CustomerFlowStep.HOME) {
        when (uiState.flowStep) {
            CustomerFlowStep.SEARCHING      -> viewModel.onDismissSearch()
            CustomerFlowStep.TRIP_PREVIEW   -> viewModel.onCancelBooking()
            CustomerFlowStep.FINDING_DRIVER -> viewModel.onCancelFinding()
            CustomerFlowStep.TRACKING       -> { /* can't go back while driver is approaching */ }
            CustomerFlowStep.IN_PROGRESS    -> { /* can't cancel in-progress ride */ }
            CustomerFlowStep.RECEIPT        -> viewModel.onReceiptDismissed()
            CustomerFlowStep.HOME           -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Layer 1: Full-screen Google Map ────────────────────────────────────
        CustomerMapView(
            pickup                = uiState.pickup,
            destination           = uiState.destination,
            nearbyDrivers         = uiState.nearbyDrivers,
            surgeZones            = uiState.surgeZones,
            trackedDriverLocation = uiState.trackedDriverLocation,
            flowStep              = uiState.flowStep,
            modifier              = Modifier.fillMaxSize(),
        )

        // ── Layer 2: Top bar (HOME only) ───────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.flowStep == CustomerFlowStep.HOME,
            enter   = fadeIn() + slideInVertically { -it },
            exit    = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        ) {
            TopMapBar(
                onSearchClicked = viewModel::onSearchBarClicked,
                onLogout = onLogout,
            )
        }

        // ── Layer 3: Recenter FAB (HOME + TRACKING + IN_PROGRESS) ─────────────
        val showFab = uiState.flowStep == CustomerFlowStep.HOME ||
            uiState.flowStep == CustomerFlowStep.TRACKING ||
            uiState.flowStep == CustomerFlowStep.IN_PROGRESS

        if (showFab) {
            FloatingActionButton(
                onClick = { },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 220.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor   = MaterialTheme.colorScheme.primary,
                shape          = CircleShape,
            ) {
                Icon(Icons.Outlined.GpsFixed, contentDescription = "Định vị lại")
            }
        }

        // ── Layer 4: Flow-step overlays ────────────────────────────────────────

        // SEARCHING
        if (uiState.flowStep == CustomerFlowStep.SEARCHING) {
            DestinationSearchSheet(
                onSelect  = viewModel::onDestinationSelected,
                onDismiss = viewModel::onDismissSearch,
            )
        }

        // TRIP_PREVIEW
        if (uiState.flowStep == CustomerFlowStep.TRIP_PREVIEW) {
            TripPreviewSheet(
                pickupLatLng          = uiState.pickup,
                destinationAddress    = uiState.destinationAddress,
                estimate              = uiState.estimate,
                paymentMethod         = uiState.paymentMethod,
                onPaymentMethodChange = viewModel::onPaymentMethodChange,
                onConfirm             = viewModel::onConfirmBooking,
                onDismiss             = viewModel::onCancelBooking,
                isLoading             = uiState.isLoading,
            )
        }

        // FINDING_DRIVER — radar animation
        AnimatedVisibility(
            visible  = uiState.flowStep == CustomerFlowStep.FINDING_DRIVER,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            FindingDriverOverlay(onCancel = viewModel::onCancelFinding)
        }

        // TRACKING — driver approaching card at bottom
        AnimatedVisibility(
            visible  = uiState.flowStep == CustomerFlowStep.TRACKING,
            enter    = fadeIn() + slideInVertically { it },
            exit     = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            uiState.trackedDriver?.let { driver ->
                DriverTrackingCard(
                    driver         = driver,
                    statusMessage  = uiState.tripStatusMessage,
                    onCall         = { },
                    onMessage      = { },
                    onTripComplete = viewModel::onTripStarted,   // manual override
                )
            }
        }

        // IN_PROGRESS — customer in vehicle, show live ride card
        AnimatedVisibility(
            visible  = uiState.flowStep == CustomerFlowStep.IN_PROGRESS,
            enter    = fadeIn() + slideInVertically { it },
            exit     = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            uiState.trackedDriver?.let { driver ->
                TripInProgressCard(
                    driver    = driver,
                    onMessage = { },
                )
            }
        }

        // RECEIPT — full-screen receipt overlay
        AnimatedVisibility(
            visible  = uiState.flowStep == CustomerFlowStep.RECEIPT && uiState.receipt != null,
            enter    = fadeIn() + slideInVertically { it / 2 },
            exit     = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            uiState.receipt?.let { receipt ->
                TripReceiptOverlay(
                    receipt          = receipt,
                    onRatingChanged  = viewModel::onRatingChanged,
                    onCommentChanged = viewModel::onCommentChanged,
                    onDismiss        = viewModel::onReceiptDismissed,
                )
            }
        }
    }
}

// ── Floating top bar ──────────────────────────────────────────────────────────
@Composable
private fun TopMapBar(
    onSearchClicked: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSearchClicked),
            shape = RoundedCornerShape(50.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Bạn muốn đi đâu?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
            IconButton(onClick = onLogout) {
                Icon(
                    Icons.Outlined.Logout,
                    contentDescription = "Đăng xuất",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun CustomerHomePreview() {
    SteelBikeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Google Maps (requires API key)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TopMapBar(onSearchClicked = {}, onLogout = {})
        }
    }
}
