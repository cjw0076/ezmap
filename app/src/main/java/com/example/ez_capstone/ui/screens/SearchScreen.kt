package com.example.ez_capstone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.ez_capstone.agent.models.PlaceResult
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.Surface
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.viewmodel.SearchViewModel
import com.google.gson.Gson

@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var query by remember { mutableStateOf("") }
    val gson = remember { Gson() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TextPrimary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onQueryChange(it)
                },
                placeholder = { Text("장소, 주소 검색", color = TextDim) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = TextDim.copy(0.3f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.size(24.dp))
            }
        }

        LazyColumn {
            items(results, key = { "${it.lat}_${it.lng}_${it.name}" }) { place ->
                PlaceSearchItem(place) {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("searchDestination", gson.toJson(place))
                    navController.popBackStack()
                }
                HorizontalDivider(color = TextDim.copy(0.1f))
            }

            if (results.isEmpty() && query.length >= 2 && !isLoading) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("검색 결과가 없습니다", color = TextDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceSearchItem(place: PlaceResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Surface, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.LocationOn, null, tint = Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(place.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (!place.address.isNullOrBlank()) {
                Text(place.address, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            if (!place.category.isNullOrBlank()) {
                Text(place.category, color = TextDim, fontSize = 11.sp)
            }
        }
        if (place.distanceM != null) {
            Text(
                formatDistance(place.distanceM),
                color = Accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) "${"%.1f".format(meters / 1000.0)}km" else "${meters}m"
}
