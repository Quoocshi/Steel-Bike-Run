package com.example.steelbikerunmobile.presentation.screen.customer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steelbikerunmobile.data.repository.DemoMapData
import com.example.steelbikerunmobile.domain.model.BookingDraft
import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.PriceEstimate
import com.example.steelbikerunmobile.domain.model.SurgeZone
import com.example.steelbikerunmobile.domain.model.VehicleInfo
import com.example.steelbikerunmobile.domain.usecase.driver.GetNearbyDriversUseCase
import com.example.steelbikerunmobile.domain.usecase.driver.SwitchToDriverUseCase
import retrofit2.HttpException
import com.example.steelbikerunmobile.data.location.LocationStreamProvider
import com.example.steelbikerunmobile.domain.usecase.trip.CreateTripUseCase
import com.example.steelbikerunmobile.domain.usecase.trip.GetPriceEstimateUseCase
import com.example.steelbikerunmobile.domain.usecase.trip.ObserveTripUpdatesUseCase
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

/** State of the role-switch (CUSTOMER → DRIVER) flow. */
enum class RoleSwitchPhase {
    IDLE,                 // not switching
    SWITCHING,            // hitting /driver/switch with no body
    AWAITING_VEHICLE_INFO,// backend told us we need vehicle info → show form
    SUBMITTING_VEHICLE,   // hitting /driver/switch with vehicle body
    DONE,                 // success — let UI react (Home will route to DriverHomeScreen)
}

data class VehicleInfoForm(
    val vehiclePlate: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val licenseNumber: String = "",
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
    // Role-switch (Customer → Driver)
    val roleSwitchPhase: RoleSwitchPhase = RoleSwitchPhase.IDLE,
    val vehicleForm: VehicleInfoForm = VehicleInfoForm(),
    val roleSwitchError: String? = null,
)

@HiltViewModel
class CustomerHomeViewModel @Inject constructor(
    private val getNearbyDriversUseCase: GetNearbyDriversUseCase,
    private val getPriceEstimateUseCase: GetPriceEstimateUseCase,
    private val createTripUseCase: CreateTripUseCase,
    private val switchToDriverUseCase: SwitchToDriverUseCase,
    private val observeTripUpdatesUseCase: ObserveTripUpdatesUseCase,
    private val locationStreamProvider: LocationStreamProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerHomeUiState())
    val uiState: StateFlow<CustomerHomeUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null
    private var tripProgressJob: Job? = null
    private var wsListenerJob: Job? = null
    private var locationJob: Job? = null
    private var currentTripId: String? = null

    init { 
        startLocationTracking()
        refreshNearbyDrivers() 
    }

    fun startLocationTracking() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            if (locationStreamProvider.hasLocationPermission()) {
                locationStreamProvider.observeLocation().collect { heartbeat ->
                    _uiState.update { it.copy(pickup = heartbeat.location) }
                }
            }
        }
    }

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
                .fold(
                    onSuccess = { tripId ->
                        currentTripId = tripId
                        _uiState.update { it.copy(isLoading = false) }
                        startWebSocketMatching()
                    },
                    onFailure = {
                        _uiState.update { it.copy(isLoading = false, flowStep = CustomerFlowStep.TRIP_PREVIEW) }
                    }
                )
        }
    }

    fun onCancelBooking() = resetToHome()

    fun onCancelFinding() {
        trackingJob?.cancel()
        wsListenerJob?.cancel()
        observeTripUpdatesUseCase.unsubscribe()
        currentTripId = null
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
        // Listen for trip completion via WebSocket status messages
        tripProgressJob?.cancel()
        tripProgressJob = viewModelScope.launch {
            observeTripUpdatesUseCase.tripStatusMessages().collect { statusData ->
                when (statusData.status) {
                    "COMPLETED" -> {
                        onTripCompleted()
                        return@collect
                    }
                    "CANCELLED" -> {
                        resetToHome()
                        return@collect
                    }
                }
            }
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

    /**
     * Subscribe vào WebSocket và lắng nghe DriverFound message từ Matching Engine.
     * Khi backend tìm thấy tài xế và tài xế accept, server gửi DriverFoundMessage
     * đến /topic/trip/{customerId}.
     */
    private fun startWebSocketMatching() {
        wsListenerJob?.cancel()
        wsListenerJob = viewModelScope.launch {
            // Subscribe vào kênh trip updates (dùng tripId làm customer channel)
            val tripId = currentTripId ?: return@launch
            observeTripUpdatesUseCase.subscribe(tripId)

            // Lắng nghe DriverFound message
            observeTripUpdatesUseCase.driverFoundMessages().collect { driver ->
                _uiState.update {
                    it.copy(
                        flowStep = CustomerFlowStep.TRACKING,
                        tripStatus = TripStatus.ACCEPTED,
                        trackedDriver = TrackedDriverInfo(
                            name = driver.driverName,
                            plate = driver.vehiclePlate.ifBlank { "--" },
                            rating = driver.driverRating,
                            vehicleModel = driver.vehicleModel.ifBlank { "Xe máy" },
                            vehicleColor = driver.vehicleColor.ifBlank { "--" },
                        ),
                        tripStatusMessage = "Tài xế đang đến điểm đón (${driver.etaMinutes} phút)",
                    )
                }
                // Start listening for status changes
                startTripStatusListener()
                return@collect  // Chỉ cần nhận 1 lần
            }
        }
    }

    /**
     * Lắng nghe trip status changes sau khi driver đã accept.
     * IN_PROGRESS -> driver đã đón khách, bắt đầu chuyến đi
     * COMPLETED -> chuyến đi hoàn thành
     */
    private fun startTripStatusListener() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            observeTripUpdatesUseCase.tripStatusMessages().collect { statusData ->
                when (statusData.status) {
                    "IN_PROGRESS" -> onTripStarted()
                    "COMPLETED" -> onTripCompleted()
                    "CANCELLED" -> {
                        wsListenerJob?.cancel()
                        observeTripUpdatesUseCase.unsubscribe()
                        resetToHome()
                    }
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

    // ── Role switch: CUSTOMER → DRIVER ─────────────────────────────────────────

    /**
     * Called by the screen in LaunchedEffect(Unit) to handle the case where this ViewModel
     * instance is reused after a DRIVER → CUSTOMER switch (Hilt scopes the VM to the
     * NavBackStackEntry, so it survives the Home-screen role transition).
     * Resets any stale in-flight phase back to IDLE so the button becomes usable again.
     */
    fun onScreenResumed() {
        startLocationTracking()
        val phase = _uiState.value.roleSwitchPhase
        if (phase != RoleSwitchPhase.IDLE && phase != RoleSwitchPhase.AWAITING_VEHICLE_INFO) {
            _uiState.update { it.copy(roleSwitchPhase = RoleSwitchPhase.IDLE, roleSwitchError = null) }
        }
    }

    /**
     * Entry point for the "Trở thành tài xế" button.
     *
     * Business flow:
     *  - Case 1 (Lần đầu — chưa có driver profile): POST /driver/switch with null body
     *    → backend returns 4xx → show vehicle-info form.
     *  - Case 2 (Các lần sau — đã có profile): POST /driver/switch with null body
     *    → backend returns new JWT with DRIVER role → success, navigation happens automatically
     *    via DataStore → HomeScreen recomposes to DriverHomeScreen.
     *
     * We do NOT call GET /driver/profile first because that endpoint requires DRIVER role;
     * calling it as CUSTOMER always returns 403, making it impossible to distinguish
     * "no profile yet" from "profile exists".
     */
    fun onSwitchToDriverClicked() {
        if (_uiState.value.roleSwitchPhase != RoleSwitchPhase.IDLE) return
        viewModelScope.launch {
            _uiState.update { it.copy(roleSwitchPhase = RoleSwitchPhase.SWITCHING, roleSwitchError = null) }
            switchToDriverUseCase(vehicleInfo = null).fold(
                onSuccess = {
                    // JWT updated in DataStore → HomeScreen will recompose to DriverHomeScreen
                    // automatically. Set IDLE (not DONE) so the same VM instance works
                    // correctly if the user switches back to CUSTOMER and tries again.
                    _uiState.update { it.copy(roleSwitchPhase = RoleSwitchPhase.IDLE) }
                },
                onFailure = { t ->
                    val httpCode = (t.cause as? HttpException)?.code() ?: -1
                    when {
                        t.cause is java.io.IOException ->
                            // Network error — show message, don't show form
                            _uiState.update {
                                it.copy(
                                    roleSwitchPhase = RoleSwitchPhase.IDLE,
                                    roleSwitchError = t.message
                                        ?: "Không có kết nối mạng. Vui lòng thử lại.",
                                )
                            }
                        httpCode == 401 || httpCode == 403 ->
                            // Auth error — ask user to re-login
                            _uiState.update {
                                it.copy(
                                    roleSwitchPhase = RoleSwitchPhase.IDLE,
                                    roleSwitchError = "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.",
                                )
                            }
                        httpCode >= 500 ->
                            // Server error
                            _uiState.update {
                                it.copy(
                                    roleSwitchPhase = RoleSwitchPhase.IDLE,
                                    roleSwitchError = t.message?.ifBlank { null }
                                        ?: "Lỗi máy chủ. Vui lòng thử lại sau.",
                                )
                            }
                        else ->
                            // 400/404/422 or unknown → backend signalling "no vehicle info yet"
                            _uiState.update {
                                it.copy(roleSwitchPhase = RoleSwitchPhase.AWAITING_VEHICLE_INFO)
                            }
                    }
                },
            )
        }
    }

    fun onVehiclePlateChange(value: String) =
        _uiState.update { it.copy(vehicleForm = it.vehicleForm.copy(vehiclePlate = value), roleSwitchError = null) }

    fun onVehicleModelChange(value: String) =
        _uiState.update { it.copy(vehicleForm = it.vehicleForm.copy(vehicleModel = value), roleSwitchError = null) }

    fun onVehicleColorChange(value: String) =
        _uiState.update { it.copy(vehicleForm = it.vehicleForm.copy(vehicleColor = value), roleSwitchError = null) }

    fun onLicenseNumberChange(value: String) =
        _uiState.update { it.copy(vehicleForm = it.vehicleForm.copy(licenseNumber = value), roleSwitchError = null) }

    fun onSubmitVehicleInfo() {
        val form = _uiState.value.vehicleForm
        if (form.vehiclePlate.isBlank() ||
            form.vehicleModel.isBlank() ||
            form.vehicleColor.isBlank() ||
            form.licenseNumber.length != 12
        ) {
            _uiState.update {
                it.copy(roleSwitchError = "Nhập đủ thông tin xe và bằng lái 12 chữ số")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(roleSwitchPhase = RoleSwitchPhase.SUBMITTING_VEHICLE, roleSwitchError = null) }
            switchToDriverUseCase(
                VehicleInfo(
                    vehiclePlate = form.vehiclePlate.trim(),
                    vehicleModel = form.vehicleModel.trim(),
                    vehicleColor = form.vehicleColor.trim(),
                    licenseNumber = form.licenseNumber.trim()
                )
            ).fold(
                onSuccess = {
                    // Same as above: navigation is driven by DataStore, reset to IDLE.
                    _uiState.update { it.copy(roleSwitchPhase = RoleSwitchPhase.IDLE) }
                },
                onFailure = { t ->
                    _uiState.update {
                        it.copy(
                            roleSwitchPhase = RoleSwitchPhase.AWAITING_VEHICLE_INFO,
                            roleSwitchError = t.message ?: "Không thể đăng ký tài xế",
                        )
                    }
                }
            )
        }
    }

    fun onDismissRoleSwitch() {
        _uiState.update {
            it.copy(roleSwitchPhase = RoleSwitchPhase.IDLE, roleSwitchError = null)
        }
    }

    override fun onCleared() {
        trackingJob?.cancel()
        tripProgressJob?.cancel()
        wsListenerJob?.cancel()
        locationJob?.cancel()
        observeTripUpdatesUseCase.unsubscribe()
        super.onCleared()
    }
}
