package com.example.steelbikerunmobile.presentation.screen.customer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steelbikerunmobile.data.repository.DemoMapData
import com.example.steelbikerunmobile.domain.model.BookingDraft
import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.PriceEstimate
import com.example.steelbikerunmobile.domain.model.SurgeZone
import com.example.steelbikerunmobile.domain.usecase.driver.GetNearbyDriversUseCase
import com.example.steelbikerunmobile.domain.usecase.trip.CreateTripUseCase
import com.example.steelbikerunmobile.domain.usecase.trip.GetPriceEstimateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ── Shared trip status (mirrors backend enum) ──────────────────────────────────
enum class TripStatus { REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED }

// ── Customer flow step state machine ──────────────────────────────────────────
enum class CustomerFlowStep {
    HOME,           // idle – map + floating search bar
    SEARCHING,      // destination selection sheet open
    TRIP_PREVIEW,   // bottom sheet: price + payment + confirm
    FINDING_DRIVER, // radar animation – waiting for match
    TRACKING,       // driver found – approaching customer
    IN_PROGRESS,    // customer in vehicle, trip underway
    RECEIPT,        // trip completed – showing receipt
}

enum class PaymentMethod { CASH, CARD }

data class TrackedDriverInfo(
    val name: String,
    val plate: String,
    val rating: Float,
    val vehicleModel: String,
    val vehicleColor: String,
    val phone: String = "+84 912 345 678",
)

/** Receipt data populated when the trip transitions to COMPLETED. */
data class TripReceipt(
    val pickupAddress: String = "Vị trí hiện tại",
    val destinationAddress: String = "",
    val distanceKm: Double = 0.0,
    val durationMinutes: Int = 0,
    val baseFare: Long = 0L,
    val surgeMultiplier: Double = 1.0,
    val totalFare: Long = 0L,
    val rating: Int = 0,
    val comment: String = "",
)

data class CustomerHomeUiState(
    val pickup: LatLng = DemoMapData.defaultPickup,
    val flowStep: CustomerFlowStep = CustomerFlowStep.HOME,
    // Destination
    val destinationAddress: String = "",
    val destination: LatLng? = null,
    // Map data
    val nearbyDrivers: List<NearbyDriver> = DemoMapData.drivers,
    val surgeZones: List<SurgeZone> = DemoMapData.surgeZones,
    // Booking
    val estimate: PriceEstimate? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    // Tracking (TRACKING step)
    val trackedDriver: TrackedDriverInfo? = null,
    val trackedDriverLocation: LatLng? = null,
    val tripStatusMessage: String = "Tài xế đang đến điểm đón",
    // In-progress (IN_PROGRESS step)
    val tripStatus: TripStatus = TripStatus.REQUESTED,
    val tripStartTimeMs: Long = 0L,
    // Receipt (RECEIPT step)
    val receipt: TripReceipt? = null,
    // UI
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CustomerHomeViewModel @Inject constructor(
    private val getNearbyDriversUseCase: GetNearbyDriversUseCase,
    private val getPriceEstimateUseCase: GetPriceEstimateUseCase,
    private val createTripUseCase: CreateTripUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerHomeUiState())
    val uiState: StateFlow<CustomerHomeUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null
    private var tripProgressJob: Job? = null

    init { refreshNearbyDrivers() }

    // ── Navigation triggers ────────────────────────────────────────────────────

    fun onSearchBarClicked() =
        _uiState.update { it.copy(flowStep = CustomerFlowStep.SEARCHING) }

    fun onDismissSearch() =
        _uiState.update { it.copy(flowStep = CustomerFlowStep.HOME) }

    fun onDestinationSelected(address: String, destination: LatLng) {
        _uiState.update {
            it.copy(
                destinationAddress = address,
                destination = destination,
                flowStep = CustomerFlowStep.TRIP_PREVIEW,
                estimate = null,
                isLoading = true,
            )
        }
        fetchEstimate(destination)
    }

    fun onPaymentMethodChange(method: PaymentMethod) =
        _uiState.update { it.copy(paymentMethod = method) }

    fun onConfirmBooking() {
        val current = _uiState.value
        val destination = current.destination ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, flowStep = CustomerFlowStep.FINDING_DRIVER, tripStatus = TripStatus.REQUESTED) }
            createTripUseCase(BookingDraft(current.pickup, destination, current.destinationAddress))
            _uiState.update { it.copy(isLoading = false) }
            startMatchingMock()
        }
    }

    fun onCancelBooking() = resetToHome()

    fun onCancelFinding() {
        trackingJob?.cancel()
        resetToHome()
    }

    // ── Trip status transitions ────────────────────────────────────────────────

    /** Driver has arrived at pickup → transition to IN_PROGRESS. */
    fun onTripStarted() {
        trackingJob?.cancel()
        _uiState.update {
            it.copy(
                flowStep = CustomerFlowStep.IN_PROGRESS,
                tripStatus = TripStatus.IN_PROGRESS,
                tripStartTimeMs = System.currentTimeMillis(),
                tripStatusMessage = "Chuyến đi đang diễn ra...",
            )
        }
        // Mock: trip ends after 10 s
        tripProgressJob?.cancel()
        tripProgressJob = viewModelScope.launch {
            delay(10_000L)
            onTripCompleted()
        }
    }

    /** Driver has marked the trip complete → show receipt. */
    fun onTripCompleted() {
        tripProgressJob?.cancel()
        val state = _uiState.value
        val est = state.estimate
        val receipt = TripReceipt(
            pickupAddress = "Điểm đón của bạn",
            destinationAddress = state.destinationAddress,
            distanceKm = est?.distanceKm ?: 3.5,
            durationMinutes = est?.durationMinutes ?: 18,
            baseFare = est?.basePrice?.toLong() ?: 42_000L,
            surgeMultiplier = est?.surgeMultiplier ?: 1.0,
            totalFare = est?.finalPrice?.toLong() ?: 42_000L,
        )
        _uiState.update {
            it.copy(
                flowStep = CustomerFlowStep.RECEIPT,
                tripStatus = TripStatus.COMPLETED,
                receipt = receipt,
            )
        }
    }

    fun onRatingChanged(rating: Int) =
        _uiState.update { it.copy(receipt = it.receipt?.copy(rating = rating)) }

    fun onCommentChanged(text: String) =
        _uiState.update { it.copy(receipt = it.receipt?.copy(comment = text)) }

    fun onReceiptDismissed() {
        tripProgressJob?.cancel()
        trackingJob?.cancel()
        _uiState.update {
            it.copy(
                flowStep = CustomerFlowStep.HOME,
                tripStatus = TripStatus.REQUESTED,
                destination = null,
                destinationAddress = "",
                estimate = null,
                trackedDriver = null,
                trackedDriverLocation = null,
                receipt = null,
                tripStartTimeMs = 0L,
            )
        }
        refreshNearbyDrivers()
    }

    // ── Private logic ──────────────────────────────────────────────────────────

    private fun fetchEstimate(destination: LatLng) {
        val current = _uiState.value
        viewModelScope.launch {
            getPriceEstimateUseCase(
                BookingDraft(current.pickup, destination, current.destinationAddress)
            ).fold(
                onSuccess = { estimate ->
                    _uiState.update { it.copy(estimate = estimate, isLoading = false) }
                },
                onFailure = { _uiState.update { it.copy(isLoading = false) } }
            )
        }
    }

    private fun startMatchingMock() {
        viewModelScope.launch {
            delay(4_000L)
            mockDriverFound()
        }
    }

    private fun mockDriverFound() {
        val pickup = _uiState.value.pickup
        val mockStart = LatLng(pickup.latitude + 0.0030, pickup.longitude + 0.0025)
        val mockDriver = DemoMapData.drivers.first()
        _uiState.update {
            it.copy(
                flowStep = CustomerFlowStep.TRACKING,
                tripStatus = TripStatus.ACCEPTED,
                trackedDriver = TrackedDriverInfo(
                    name = mockDriver.fullName,
                    plate = mockDriver.vehiclePlate ?: "51G-001.23",
                    rating = mockDriver.rating,
                    vehicleModel = "Honda Air Blade 150",
                    vehicleColor = "Đen",
                ),
                trackedDriverLocation = mockStart,
                tripStatusMessage = "Tài xế đang đến điểm đón",
            )
        }
        startDriverTracking(mockStart)
    }

    private fun startDriverTracking(startLocation: LatLng) {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            val pickup = _uiState.value.pickup
            var current = startLocation
            repeat(30) {
                delay(2_000L)
                current = LatLng(
                    latitude = current.latitude + (pickup.latitude - current.latitude) * 0.14,
                    longitude = current.longitude + (pickup.longitude - current.longitude) * 0.14,
                )
                val distanceM = haversineMeters(current, pickup)
                _uiState.update {
                    it.copy(
                        trackedDriverLocation = current,
                        tripStatusMessage = when {
                            distanceM < 25 -> "Tài xế đã đến nơi! 🎉"
                            distanceM < 120 -> "Tài xế sắp đến..."
                            else -> "Tài xế đang đến điểm đón"
                        },
                    )
                }
                if (distanceM < 25) {
                    delay(2_500L) // Show "arrived" for 2.5s then start trip
                    onTripStarted()
                    return@launch
                }
            }
        }
    }

    private fun refreshNearbyDrivers() {
        viewModelScope.launch {
            getNearbyDriversUseCase(_uiState.value.pickup).onSuccess { drivers ->
                _uiState.update { it.copy(nearbyDrivers = drivers) }
            }
        }
    }

    private fun resetToHome() {
        _uiState.update {
            it.copy(
                flowStep = CustomerFlowStep.HOME,
                destination = null,
                destinationAddress = "",
                estimate = null,
            )
        }
    }

    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
                sin(dLng / 2).pow(2)
        return 2 * R * asin(sqrt(h))
    }

    override fun onCleared() {
        trackingJob?.cancel()
        tripProgressJob?.cancel()
        super.onCleared()
    }
}
