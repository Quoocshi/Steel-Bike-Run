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
import com.example.steelbikerunmobile.domain.usecase.trip.ReverseGeocodeUseCase
import com.example.steelbikerunmobile.domain.usecase.trip.SearchDestinationUseCase
import com.example.steelbikerunmobile.data.local.datastore.AuthPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ΓöÇΓöÇ Shared trip status (mirrors backend enum) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
enum class TripStatus { REQUESTED, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED }

// ΓöÇΓöÇ Customer flow step state machine ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
enum class CustomerFlowStep {
    HOME,           // idle ΓÇô map + floating search bar
    SEARCHING,      // destination selection sheet open
    TRIP_PREVIEW,   // bottom sheet: price + payment + confirm
    FINDING_DRIVER, // radar animation ΓÇô waiting for match
    TRACKING,       // driver found ΓÇô approaching customer
    IN_PROGRESS,    // customer in vehicle, trip underway
    RECEIPT,        // trip completed ΓÇô showing receipt
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
    val pickupAddress: String = "Vß╗ï tr├¡ hiß╗çn tß║íi",
    val destinationAddress: String = "",
    val distanceKm: Double = 0.0,
    val durationMinutes: Int = 0,
    val baseFare: Long = 0L,
    val surgeMultiplier: Double = 1.0,
    val totalFare: Long = 0L,
    val rating: Int = 0,
    val comment: String = "",
)

/** State of the role-switch (CUSTOMER ΓåÆ DRIVER) flow. */
enum class RoleSwitchPhase {
    IDLE,                 // not switching
    SWITCHING,            // hitting /driver/switch with no body
    AWAITING_VEHICLE_INFO,// backend told us we need vehicle info ΓåÆ show form
    SUBMITTING_VEHICLE,   // hitting /driver/switch with vehicle body
    DONE,                 // success ΓÇö let UI react (Home will route to DriverHomeScreen)
}

data class VehicleInfoForm(
    val vehiclePlate: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val licenseNumber: String = "",
)

data class CustomerHomeUiState(
    val pickup: LatLng = DemoMapData.defaultPickup,
    val pickupAddress: String = "Vß╗ï tr├¡ hiß╗çn tß║íi",
    val flowStep: CustomerFlowStep = CustomerFlowStep.HOME,
    // Destination
    val destinationAddress: String = "",
    val destination: LatLng? = null,
    val searchResults: List<Pair<String, LatLng>> = emptyList(),
    // Map data
    val nearbyDrivers: List<NearbyDriver> = DemoMapData.drivers,
    val surgeZones: List<SurgeZone> = DemoMapData.surgeZones,
    // Booking
    val estimate: PriceEstimate? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    // Tracking (TRACKING step)
    val trackedDriver: TrackedDriverInfo? = null,
    val trackedDriverLocation: LatLng? = null,
    val tripStatusMessage: String = "T├ái xß║┐ ─æang ─æß║┐n ─æiß╗âm ─æ├│n",
    // In-progress (IN_PROGRESS step)
    val tripStatus: TripStatus = TripStatus.REQUESTED,
    val tripStartTimeMs: Long = 0L,
    // Receipt (RECEIPT step)
    val receipt: TripReceipt? = null,
    // UI
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Role-switch (Customer ΓåÆ Driver)
    val roleSwitchPhase: RoleSwitchPhase = RoleSwitchPhase.IDLE,
    val vehicleForm: VehicleInfoForm = VehicleInfoForm(),
    val roleSwitchError: String? = null,
    // Triggers
    val recenterTrigger: Long = 0L,
)

@HiltViewModel
class CustomerHomeViewModel @Inject constructor(
    private val getNearbyDriversUseCase: GetNearbyDriversUseCase,
    private val getPriceEstimateUseCase: GetPriceEstimateUseCase,
    private val createTripUseCase: CreateTripUseCase,
    private val switchToDriverUseCase: SwitchToDriverUseCase,
    private val observeTripUpdatesUseCase: ObserveTripUpdatesUseCase,
    private val searchDestinationUseCase: SearchDestinationUseCase,
    private val reverseGeocodeUseCase: ReverseGeocodeUseCase,
    private val locationStreamProvider: LocationStreamProvider,
    private val authDataStore: AuthPreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerHomeUiState())
    val uiState: StateFlow<CustomerHomeUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null
    private var tripProgressJob: Job? = null
    private var wsListenerJob: Job? = null
    private var locationJob: Job? = null
    private var searchJob: Job? = null
    private var driverLocationJob: Job? = null
    private var nearbyDriverPollingJob: Job? = null
    private var currentTripId: String? = null
    /** Customer ID lưu từ DataStore — dùng để subscribe đúng kênh /topic/trip/{customerId}. */
    private var currentCustomerId: String? = null

    init {
        startLocationTracking()
        refreshNearbyDrivers()
        startNearbyDriverPolling()
        // Load customer ID từ DataStore ngay khi khởi tạo
        viewModelScope.launch {
            authDataStore.authSessionFlow.collect { session ->
                if (session != null) currentCustomerId = session.userId
            }
        }
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

    // ΓöÇΓöÇ Navigation triggers ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    fun onSearchBarClicked() =
        _uiState.update { it.copy(flowStep = CustomerFlowStep.SEARCHING, searchResults = emptyList()) }

    fun onDismissSearch() =
        _uiState.update { it.copy(flowStep = CustomerFlowStep.HOME, searchResults = emptyList()) }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(500) // Debounce
            searchDestinationUseCase(query).onSuccess { results ->
                _uiState.update { it.copy(searchResults = results) }
            }
        }
    }

    fun onRecenterClicked() {
        _uiState.update { it.copy(recenterTrigger = System.currentTimeMillis()) }
    }

    fun onDestinationSelected(address: String, destination: LatLng) {
        _uiState.update {
            it.copy(
                destinationAddress = address,
                destination = destination,
                flowStep = CustomerFlowStep.TRIP_PREVIEW,
                estimate = null,
                isLoading = true,
                pickupAddress = "Đang lấy địa chỉ...",
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
        driverLocationJob?.cancel()
        wsListenerJob?.cancel()
        observeTripUpdatesUseCase.unsubscribe()
        currentTripId = null
        resetToHome()
    }

    // ΓöÇΓöÇ Trip status transitions ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    /** Driver has arrived at pickup ΓåÆ transition to IN_PROGRESS. */
    fun onTripStarted() {
        trackingJob?.cancel()
        _uiState.update {
            it.copy(
                flowStep = CustomerFlowStep.IN_PROGRESS,
                tripStatus = TripStatus.IN_PROGRESS,
                tripStartTimeMs = System.currentTimeMillis(),
                tripStatusMessage = "Chuyß║┐n ─æi ─æang diß╗àn ra...",
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

    /** Driver has marked the trip complete ΓåÆ show receipt. */
    fun onTripCompleted() {
        tripProgressJob?.cancel()
        val state = _uiState.value
        val est = state.estimate
        val receipt = TripReceipt(
            pickupAddress = state.pickupAddress,
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
        driverLocationJob?.cancel()
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

    // ── Private logic ─────────────────────────────────────────────────────────────

    private fun fetchEstimate(destination: LatLng) {
        val current = _uiState.value
        viewModelScope.launch {
            val addressResult = reverseGeocodeUseCase(current.pickup)
            val finalPickupAddress = addressResult.getOrNull() ?: "Vị trí hiện tại"
            _uiState.update { it.copy(pickupAddress = finalPickupAddress) }

            getPriceEstimateUseCase(
                BookingDraft(current.pickup, destination, current.destinationAddress)
            ).fold(
                onSuccess = { estimate ->
                    _uiState.update { it.copy(estimate = estimate, isLoading = false) }
                },
                onFailure = { t ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = t.message ?: "Không thể kết nối server để tính giá. Vui lòng thử lại."
                        )
                    }
                }
            )
        }
    }

    /**
     * Subscribe vào WebSocket và lắng nghe DriverFound message từ Matching Engine.
     * Backend broadcast đến /topic/trip/{customerId} khi driver accept cuốc.
     */
    private fun startWebSocketMatching() {
        wsListenerJob?.cancel()
        wsListenerJob = viewModelScope.launch {
            // ✔ Dùng customerId (đúng) thay vì tripId (đã sai trước)
            val customerId = currentCustomerId
            if (customerId.isNullOrBlank()) {
                _uiState.update { it.copy(errorMessage = "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.") }
                return@launch
            }
            observeTripUpdatesUseCase.subscribe(customerId)

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
                
                // Start tracking driver's location
                startDriverLocationTracking(driver.driverId)
                
                // Start listening for status changes
                startTripStatusListener()
                return@collect  // Chß╗ë cß║ºn nhß║¡n 1 lß║ºn
            }
        }
    }

    private fun startDriverLocationTracking(driverId: String) {
        driverLocationJob?.cancel()
        driverLocationJob = viewModelScope.launch {
            observeTripUpdatesUseCase.driverLocationMessages(driverId).collect { loc ->
                _uiState.update { it.copy(trackedDriverLocation = loc) }
            }
        }
    }

    /**
     * Lß║»ng nghe trip status changes sau khi driver ─æ├ú accept.
     * IN_PROGRESS -> driver ─æ├ú ─æ├│n kh├ích, bß║»t ─æß║ºu chuyß║┐n ─æi
     * COMPLETED -> chuyß║┐n ─æi ho├án th├ánh
     */
    private fun startTripStatusListener() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            observeTripUpdatesUseCase.tripStatusMessages().collect { statusData ->
                when (statusData.status) {
                    "ARRIVED" -> onDriverArrived()
                    "IN_PROGRESS" -> onTripStarted()
                    "COMPLETED" -> onTripCompleted()
                    "CANCELLED" -> {
                        wsListenerJob?.cancel()
                        driverLocationJob?.cancel()
                        observeTripUpdatesUseCase.unsubscribe()
                        resetToHome()
                    }
                }
            }
        }
    }

    private fun onDriverArrived() {
        _uiState.update { 
            it.copy(
                tripStatus = TripStatus.ARRIVED,
                tripStatusMessage = "T├ái xß║┐ ─æ├ú ─æß║┐n ─æiß╗âm ─æ├│n"
            )
        }
    }

    private fun refreshNearbyDrivers() {
        viewModelScope.launch {
            getNearbyDriversUseCase(_uiState.value.pickup).onSuccess { drivers ->
                _uiState.update { it.copy(nearbyDrivers = drivers) }
            }
        }
    }

    /**
     * Poll danh sách driver gần đó mỗi 30 giây.
     * Chỉ chạy khi customer đang ở HOME hoặc TRIP_PREVIEW (không spam khi đang trong chuyến).
     */
    private fun startNearbyDriverPolling() {
        nearbyDriverPollingJob?.cancel()
        nearbyDriverPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                val step = _uiState.value.flowStep
                if (step == CustomerFlowStep.HOME || step == CustomerFlowStep.TRIP_PREVIEW) {
                    refreshNearbyDrivers()
                }
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

    // ΓöÇΓöÇ Role switch: CUSTOMER ΓåÆ DRIVER ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    /**
     * Called by the screen in LaunchedEffect(Unit) to handle the case where this ViewModel
     * instance is reused after a DRIVER ΓåÆ CUSTOMER switch (Hilt scopes the VM to the
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
     * Entry point for the "Trß╗ƒ th├ánh t├ái xß║┐" button.
     *
     * Business flow:
     *  - Case 1 (Lß║ºn ─æß║ºu ΓÇö ch╞░a c├│ driver profile): POST /driver/switch with null body
     *    ΓåÆ backend returns 4xx ΓåÆ show vehicle-info form.
     *  - Case 2 (C├íc lß║ºn sau ΓÇö ─æ├ú c├│ profile): POST /driver/switch with null body
     *    ΓåÆ backend returns new JWT with DRIVER role ΓåÆ success, navigation happens automatically
     *    via DataStore ΓåÆ HomeScreen recomposes to DriverHomeScreen.
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
                    // JWT updated in DataStore ΓåÆ HomeScreen will recompose to DriverHomeScreen
                    // automatically. Set IDLE (not DONE) so the same VM instance works
                    // correctly if the user switches back to CUSTOMER and tries again.
                    _uiState.update { it.copy(roleSwitchPhase = RoleSwitchPhase.IDLE) }
                },
                onFailure = { t ->
                    val httpCode = (t.cause as? HttpException)?.code() ?: -1
                    when {
                        t.cause is java.io.IOException ->
                            // Network error ΓÇö show message, don't show form
                            _uiState.update {
                                it.copy(
                                    roleSwitchPhase = RoleSwitchPhase.IDLE,
                                    roleSwitchError = t.message
                                        ?: "Kh├┤ng c├│ kß║┐t nß╗æi mß║íng. Vui l├▓ng thß╗¡ lß║íi.",
                                )
                            }
                        httpCode == 401 || httpCode == 403 ->
                            // Auth error ΓÇö ask user to re-login
                            _uiState.update {
                                it.copy(
                                    roleSwitchPhase = RoleSwitchPhase.IDLE,
                                    roleSwitchError = "Phi├¬n ─æ─âng nhß║¡p hß║┐t hß║ín. Vui l├▓ng ─æ─âng nhß║¡p lß║íi.",
                                )
                            }
                        httpCode >= 500 ->
                            // Server error
                            _uiState.update {
                                it.copy(
                                    roleSwitchPhase = RoleSwitchPhase.IDLE,
                                    roleSwitchError = t.message?.ifBlank { null }
                                        ?: "Lß╗ùi m├íy chß╗º. Vui l├▓ng thß╗¡ lß║íi sau.",
                                )
                            }
                        else ->
                            // 400/404/422 or unknown ΓåÆ backend signalling "no vehicle info yet"
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
                it.copy(roleSwitchError = "Nhß║¡p ─æß╗º th├┤ng tin xe v├á bß║▒ng l├íi 12 chß╗» sß╗æ")
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
                            roleSwitchError = t.message ?: "Kh├┤ng thß╗â ─æ─âng k├╜ t├ái xß║┐",
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
