package com.example.steelbikerunmobile.presentation.screen.driver.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steelbikerunmobile.data.repository.DemoMapData
import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.distanceTo
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.VehicleInfo
import com.example.steelbikerunmobile.domain.repository.TripRepository
import com.example.steelbikerunmobile.domain.usecase.driver.GetDriverProfileUseCase
import com.example.steelbikerunmobile.domain.usecase.driver.GetNearbyDriversUseCase
import com.example.steelbikerunmobile.domain.usecase.driver.ObserveCurrentH3IndexUseCase
import com.example.steelbikerunmobile.domain.usecase.driver.SetDriverOnlineStatusUseCase
import com.example.steelbikerunmobile.domain.usecase.driver.StreamLocationUseCase
import com.example.steelbikerunmobile.domain.usecase.driver.SwitchToCustomerUseCase
import com.example.steelbikerunmobile.domain.usecase.driver.SwitchToDriverUseCase
import com.example.steelbikerunmobile.domain.usecase.trip.ObserveDriverTripRequestsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Navigation step ────────────────────────────────────────────────────────────
enum class DriverHomeStep {
    HOME,
    FACE_SCAN,
    INCOMING_TRIP,
    TRIP_IN_PROGRESS,   // Driver has accepted and is executing the trip
    TRIP_SUMMARY,       // Trip completed – show earnings summary
}

/**
 * Sub-phase within TRIP_IN_PROGRESS, mirrors the business flow:
 * GOING_TO_PICKUP -> ARRIVED_AT_PICKUP -> IN_PROGRESS
 */
enum class TripExecutionPhase {
    GOING_TO_PICKUP,      // Phase 1: Driver đang đến điểm đón (ACCEPTED)
    ARRIVED_AT_PICKUP,    // Phase 2: Driver đã đến, chờ khách lên xe (ARRIVED)
    IN_PROGRESS,          // Phase 3: Đang chạy đến điểm đến (IN_PROGRESS)
}

// ── Incoming trip payload (from WebSocket / mock) ─────────────────────────────
data class IncomingTripData(
    val tripId: String = "TRIP-HCM-001",
    val customerName: String = "",
    val customerPhone: String = "",
    val pickupAddress: String = "Bến Thành Market, Q.1",
    val destinationAddress: String = "Sân bay Tân Sơn Nhất, Q.Tân Bình",
    val pickupLat: Double = 10.7727,
    val pickupLng: Double = 106.6980,
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val distanceToPickupKm: Double = 0.8,
    val totalDistanceKm: Double = 9.5,
    val estimatedEarnings: Long = 85_000,
    val surgeMultiplier: Double = 1.5,
    val durationMinutes: Int = 28,
    val countdownSeconds: Int = 15,
)

/** Active trip data retained while the trip is IN_PROGRESS. */
data class ActiveTripData(
    val tripId: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val estimatedEarnings: Long,
    val surgeMultiplier: Double,
    val durationMinutes: Int,
    val totalDistanceKm: Double,
    val pickupLat: Double,
    val pickupLng: Double,
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val executionPhase: TripExecutionPhase = TripExecutionPhase.GOING_TO_PICKUP,
    val tripStartTimeMs: Long = System.currentTimeMillis(),
)

/** Summary shown after trip completes. */
data class TripSummary(
    val earnings: Long,
    val distanceKm: Double,
    val durationMinutes: Int,
    val surgeMultiplier: Double,
)

data class DriverHomeUiState(
    val profile: DriverProfile? = null,
    val vehiclePlate: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val licenseNumber: String = "",
    val currentLocation: LatLng? = null,
    val nearbyDrivers: List<NearbyDriver> = emptyList(),
    val isLoading: Boolean = false,
    val isStreamingLocation: Boolean = false,
    // H3 cell index hiện tại, tính bởi server sau mỗi heartbeat thành công.
    // Dùng để hiển thị ô H3 tài xế đang đứng và xác nhận kết nối backend đang hoạt động.
    val currentH3Index: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    // Step machine
    val step: DriverHomeStep = DriverHomeStep.HOME,
    // Trip payloads
    val incomingTrip: IncomingTripData? = null,
    val activeTrip: ActiveTripData? = null,
    val tripSummary: TripSummary? = null,
    // Session stats
    val todayTrips: Int = 3,
    val todayEarnings: Long = 185_000L,
    // Khoảng cách còn lại đến điểm đón (null = chưa có vị trí hoặc không trong trip)
    val distanceToPickupMeters: Double? = null,
)

/** Khoảng cách tối đa (mét) để bấm "Đã đến điểm đón". */
const val ARRIVED_THRESHOLD_METERS = 150.0

@HiltViewModel
class DriverHomeViewModel @Inject constructor(
    private val getDriverProfileUseCase: GetDriverProfileUseCase,
    private val switchToDriverUseCase: SwitchToDriverUseCase,
    private val switchToCustomerUseCase: SwitchToCustomerUseCase,
    private val setDriverOnlineStatusUseCase: SetDriverOnlineStatusUseCase,
    private val streamLocationUseCase: StreamLocationUseCase,
    private val getNearbyDriversUseCase: GetNearbyDriversUseCase,
    private val observeCurrentH3IndexUseCase: ObserveCurrentH3IndexUseCase,
    private val observeDriverTripRequestsUseCase: ObserveDriverTripRequestsUseCase,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverHomeUiState())
    val uiState: StateFlow<DriverHomeUiState> = _uiState.asStateFlow()
    private var locationJob: Job? = null
    private var h3IndexJob: Job? = null
    private var tripListenerJob: Job? = null

    init {
        loadProfile()
        refreshNearbyDrivers(DemoMapData.defaultPickup)
        observeH3Index()
    }

    private fun observeH3Index() {
        h3IndexJob = viewModelScope.launch {
            observeCurrentH3IndexUseCase().collect { h3Index ->
                _uiState.update { it.copy(currentH3Index = h3Index) }
            }
        }
    }

    // ── Form fields ───────────────────────────────────────────────────────────
    fun onVehiclePlateChange(v: String) = _uiState.update { it.copy(vehiclePlate = v, errorMessage = null) }
    fun onVehicleModelChange(v: String) = _uiState.update { it.copy(vehicleModel = v, errorMessage = null) }
    fun onVehicleColorChange(v: String) = _uiState.update { it.copy(vehicleColor = v, errorMessage = null) }
    fun onLicenseNumberChange(v: String) = _uiState.update { it.copy(licenseNumber = v, errorMessage = null) }

    // ── Online toggle flow ────────────────────────────────────────────────────

    fun onToggleOnlineClicked(hasLocationPermission: Boolean) {
        val current = _uiState.value
        if (current.profile?.isOnline == true) {
            executeToggleOnline(hasLocationPermission)
            return
        }
        if (current.profile == null) {
            current.toVehicleInfoOrNull() ?: run {
                _uiState.update { it.copy(errorMessage = "Nhập đủ thông tin xe và bằng lái 12 chữ số") }
                return
            }
        }
        _uiState.update { it.copy(step = DriverHomeStep.FACE_SCAN, errorMessage = null) }
    }

    fun onFaceScanPassed(hasLocationPermission: Boolean) {
        _uiState.update { it.copy(step = DriverHomeStep.HOME) }
        executeToggleOnline(hasLocationPermission)
        // Subscribe WebSocket để lắng nghe cuốc xe mới khi online
        startListeningForTrips()
    }

    fun onFaceScanFailed() {
        _uiState.update { it.copy(step = DriverHomeStep.HOME, errorMessage = "Kiểm tra khuôn mặt thất bại. Hãy thử lại.") }
    }

    // ── Incoming trip ─────────────────────────────────────────────────────────

    /** Driver accepts the incoming trip → call backend API + move to TRIP_IN_PROGRESS. */
    fun onTripAccepted() {
        val incoming = _uiState.value.incomingTrip ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            tripRepository.acceptTrip(incoming.tripId).fold(
                onSuccess = {
                    val active = ActiveTripData(
                        tripId = incoming.tripId,
                        pickupAddress = incoming.pickupAddress,
                        destinationAddress = incoming.destinationAddress,
                        estimatedEarnings = incoming.estimatedEarnings,
                        surgeMultiplier = incoming.surgeMultiplier,
                        durationMinutes = incoming.durationMinutes,
                        totalDistanceKm = incoming.totalDistanceKm,
                        pickupLat = incoming.pickupLat,
                        pickupLng = incoming.pickupLng,
                        destLat = incoming.destLat,
                        destLng = incoming.destLng,
                        executionPhase = TripExecutionPhase.GOING_TO_PICKUP,
                    )
                    _uiState.update {
                        it.copy(
                            step = DriverHomeStep.TRIP_IN_PROGRESS,
                            incomingTrip = null,
                            activeTrip = active,
                            todayTrips = it.todayTrips + 1,
                            isLoading = false,
                            infoMessage = null,
                        )
                    }
                },
                onFailure = { t ->
                    _uiState.update {
                        it.copy(
                            step = DriverHomeStep.HOME,
                            incomingTrip = null,
                            isLoading = false,
                            errorMessage = t.message ?: "Cuốc xe đã được nhận bởi tài xế khác",
                        )
                    }
                    // Continue listening for next trip
                    startListeningForTrips()
                }
            )
        }
    }

    fun onTripDeclined() {
        _uiState.update { it.copy(step = DriverHomeStep.HOME, incomingTrip = null) }
        // Continue listening for next trip
        startListeningForTrips()
    }

    // ── Trip in progress ──────────────────────────────────────────────────────

    /** Phase 1 → 2: Driver xác nhận đã đến điểm đón → gọi API /arrive.
     *  Guard: driver phải cách điểm đón ≤ ARRIVED_THRESHOLD_METERS (150m). */
    fun onArrivedAtPickup() {
        val active = _uiState.value.activeTrip ?: return
        val currentLoc = _uiState.value.currentLocation

        // Kiểm tra khoảng cách nếu có vị trí GPS
        if (currentLoc != null) {
            val pickupLoc = LatLng(active.pickupLat, active.pickupLng)
            val distanceM = currentLoc.distanceTo(pickupLoc)
            if (distanceM > ARRIVED_THRESHOLD_METERS) {
                val remaining = distanceM.toInt()
                _uiState.update {
                    it.copy(errorMessage = "Bạn cách điểm đón ${remaining}m. Hãy đến trong vòng ${ARRIVED_THRESHOLD_METERS.toInt()}m để xác nhận.")
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            tripRepository.arriveAtPickup(active.tripId).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            activeTrip = state.activeTrip?.copy(
                                executionPhase = TripExecutionPhase.ARRIVED_AT_PICKUP
                            )
                        )
                    }
                },
                onFailure = { t ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = t.message ?: "Không thể cập nhật trạng thái") }
                }
            )
        }
    }

    /** Phase 2 → 3: Driver xác nhận khách lên xe, bắt đầu chạy → gọi API /start. */
    fun onStartTrip() {
        val active = _uiState.value.activeTrip ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            tripRepository.startTrip(active.tripId).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            activeTrip = state.activeTrip?.copy(
                                executionPhase = TripExecutionPhase.IN_PROGRESS
                            )
                        )
                    }
                },
                onFailure = { t ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = t.message ?: "Không thể bắt đầu chuyến") }
                }
            )
        }
    }

    /** Phase 3: Driver swipes to complete the active trip → call backend API. */
    fun onSwipeToComplete() {
        val active = _uiState.value.activeTrip ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            tripRepository.completeTrip(active.tripId).fold(
                onSuccess = {
                    val summary = TripSummary(
                        earnings = active.estimatedEarnings,
                        distanceKm = active.totalDistanceKm,
                        durationMinutes = active.durationMinutes,
                        surgeMultiplier = active.surgeMultiplier,
                    )
                    _uiState.update {
                        it.copy(
                            step = DriverHomeStep.TRIP_SUMMARY,
                            activeTrip = null,
                            tripSummary = summary,
                            todayEarnings = it.todayEarnings + active.estimatedEarnings,
                            isLoading = false,
                        )
                    }
                },
                onFailure = { t ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = t.message ?: "Không thể hoàn thành cuốc xe") }
                }
            )
        }
    }

    /** Dismiss summary → return to HOME (still online), resume listening. */
    fun onSummaryDismissed() {
        _uiState.update {
            it.copy(step = DriverHomeStep.HOME, tripSummary = null, infoMessage = "Sẵn sàng nhận cuốc tiếp theo!")
        }
        startListeningForTrips()
    }

    // ── Location streaming ────────────────────────────────────────────────────

    fun startLocationStream() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            _uiState.update { it.copy(isStreamingLocation = true, errorMessage = null) }
            try {
                streamLocationUseCase()
                    .collect { heartbeat ->
                        val loc = heartbeat.location
                        // Tính khoảng cách đến điểm đón nếu đang ở phase GOING_TO_PICKUP
                        val dist = _uiState.value.activeTrip
                            ?.takeIf { it.executionPhase == TripExecutionPhase.GOING_TO_PICKUP }
                            ?.let { trip -> loc.distanceTo(LatLng(trip.pickupLat, trip.pickupLng)) }
                        _uiState.update {
                            it.copy(
                                currentLocation = loc,
                                distanceToPickupMeters = dist,
                            )
                        }
                        refreshNearbyDrivers(loc)
                    }
            } catch (e: SecurityException) {
                // Permission bị thu hồi giữa chừng
                _uiState.update {
                    it.copy(
                        isStreamingLocation = false,
                        errorMessage = "Cần cấp quyền vị trí để gửi GPS"
                    )
                }
            } finally {
                // Flow kết thúc vì bất kỳ lý do gì → reset trạng thái
                _uiState.update { it.copy(isStreamingLocation = false) }
            }
        }
    }

    fun stopLocationStream() {
        locationJob?.cancel()
        locationJob = null
        streamLocationUseCase.stop()
        _uiState.update { it.copy(isStreamingLocation = false) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun executeToggleOnline(hasLocationPermission: Boolean) {
        val current = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            // No profile yet → first-time driver setup. The /driver/switch endpoint creates the
            // profile, sets isOnline=true, and rotates the JWT to role=DRIVER (saved internally).
            // After that, subsequent online/offline toggles just hit /driver/status.
            val result = if (current.profile == null) {
                val vehicleInfo = current.toVehicleInfoOrNull() ?: run {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Nhập đủ thông tin xe và bằng lái 12 chữ số",
                        )
                    }
                    return@launch
                }
                switchToDriverUseCase(vehicleInfo)
            } else {
                val desired = !current.profile.isOnline
                setDriverOnlineStatusUseCase(desired)
            }

            result.fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            profile = profile,
                            isLoading = false,
                            infoMessage = if (profile.isOnline) "Bạn đang online – GPS heartbeat mỗi 3 giây" else "Bạn đã offline",
                        )
                    }
                    if (profile.isOnline && hasLocationPermission) startLocationStream()
                    if (!profile.isOnline) stopLocationStream()
                },
                onFailure = { t ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = t.message ?: "Không thể đổi trạng thái") }
                }
            )
        }
    }

    // ── Role switch: DRIVER → CUSTOMER ────────────────────────────────────────

    /**
     * Hit /driver/switch-back so the backend issues a new JWT with role=CUSTOMER.
     * The repository persists the new token internally; the auth session flow then emits a
     * CUSTOMER session and the host HomeScreen automatically renders CustomerHomeScreen.
     */
    fun switchBackToCustomer() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            switchToCustomerUseCase().fold(
                onSuccess = {
                    // Stop streaming GPS — backend has set isOnline=false anyway. The
                    // session role flips to CUSTOMER as soon as the DataStore write
                    // propagates, which un-mounts this ViewModel.
                    stopLocationStream()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            infoMessage = "Đã chuyển về chế độ Khách hàng",
                        )
                    }
                },
                onFailure = { t ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = t.message ?: "Không thể chuyển về chế độ Khách hàng",
                        )
                    }
                }
            )
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            getDriverProfileUseCase().onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        profile = profile,
                        vehiclePlate = profile.vehiclePlate.orEmpty(),
                        vehicleModel = profile.vehicleModel.orEmpty(),
                        vehicleColor = profile.vehicleColor.orEmpty(),
                        licenseNumber = profile.licenseNumber.orEmpty(),
                    )
                }
                // Driver đã online từ phiên trước (backend ghi isOnline=true).
                // Cần: (1) re-sync lên backend để đảm bảo server biết driver này available,
                //       (2) bật GPS stream, (3) subscribe WebSocket nhận cuốc.
                if (profile.isOnline) {
                    viewModelScope.launch {
                        // Re-confirm online status với backend (tránh trường hợp server đã reset)
                        setDriverOnlineStatusUseCase(true)
                    }
                    startLocationStream()
                    startListeningForTrips()
                }

            }
        }
    }

    private fun refreshNearbyDrivers(location: LatLng) {
        viewModelScope.launch {
            getNearbyDriversUseCase(location).onSuccess { drivers ->
                _uiState.update { it.copy(nearbyDrivers = drivers) }
            }
        }
    }

    // -- WebSocket trip listener --

    /**
     * Subscribe vào WebSocket để lắng nghe cuốc xe mới từ Matching Engine.
     * Gọi khi driver bật Online hoặc sau khi hoàn thành/từ chối cuốc trước đó.
     */
    private fun startListeningForTrips() {
        tripListenerJob?.cancel()
        val driverId = _uiState.value.profile?.driverId ?: return
        tripListenerJob = viewModelScope.launch {
            observeDriverTripRequestsUseCase.subscribe(driverId)
            observeDriverTripRequestsUseCase.tripRequests().collect { request ->
                _uiState.update {
                    it.copy(
                        incomingTrip = IncomingTripData(
                            tripId = request.tripId,
                            customerName = request.customerName,
                            customerPhone = request.customerPhone,
                            pickupAddress = "${String.format("%.4f", request.pickupLat)}, ${String.format("%.4f", request.pickupLng)}",
                            destinationAddress = request.destAddress.ifBlank { "\u0110i\u1ec3m \u0111\u1ebfn" },
                            pickupLat = request.pickupLat,
                            pickupLng = request.pickupLng,
                            distanceToPickupKm = request.distanceToPickupKm,
                            totalDistanceKm = request.distanceToPickupKm * 3,
                            estimatedEarnings = request.finalPrice,
                            surgeMultiplier = request.surgeMultiplier,
                            durationMinutes = ((request.distanceToPickupKm * 3) / 25.0 * 60).toInt().coerceAtLeast(5),
                            countdownSeconds = request.timeoutSeconds,
                        ),
                        step = DriverHomeStep.INCOMING_TRIP,
                    )
                }
                return@collect  // Nhận 1 cuốc, chờ driver xử lý
            }
        }
    }

    private fun stopListeningForTrips() {
        tripListenerJob?.cancel()
        tripListenerJob = null
        observeDriverTripRequestsUseCase.unsubscribe()
    }

    private fun DriverHomeUiState.toVehicleInfoOrNull(): VehicleInfo? {
        if (vehiclePlate.isBlank() || vehicleModel.isBlank() || vehicleColor.isBlank() || licenseNumber.length != 12) return null
        return VehicleInfo(vehiclePlate.trim(), vehicleModel.trim(), vehicleColor.trim(), licenseNumber.trim())
    }

    override fun onCleared() {
        stopLocationStream()
        stopListeningForTrips()
        h3IndexJob?.cancel()
        super.onCleared()
    }
}
