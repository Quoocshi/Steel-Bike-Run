package com.example.steelbikerunmobile.presentation.screen.customer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.steelbikerunmobile.presentation.screen.customer.home.VehicleInfoForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchToDriverSheet(
    form: VehicleInfoForm,
    isSubmitting: Boolean,
    errorMessage: String?,
    onVehiclePlateChange: (String) -> Unit,
    onVehicleModelChange: (String) -> Unit,
    onVehicleColorChange: (String) -> Unit,
    onLicenseNumberChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Trở thành tài xế",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Đây là lần đầu bạn chuyển sang chế độ Tài xế. " +
                    "Vui lòng cung cấp thông tin xe và bằng lái để hoàn tất hồ sơ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = form.vehiclePlate,
                onValueChange = onVehiclePlateChange,
                label = { Text("Biển số xe (VD: 51G-123.45)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
            )
            OutlinedTextField(
                value = form.vehicleModel,
                onValueChange = onVehicleModelChange,
                label = { Text("Hãng & dòng xe (VD: Honda Air Blade)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
            )
            OutlinedTextField(
                value = form.vehicleColor,
                onValueChange = onVehicleColorChange,
                label = { Text("Màu xe") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
            )
            OutlinedTextField(
                value = form.licenseNumber,
                onValueChange = onLicenseNumberChange,
                label = { Text("Số bằng lái (12 chữ số)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
            )

            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp),
                    )
                } else {
                    Text(
                        "Xác nhận & Chuyển sang Tài xế",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
