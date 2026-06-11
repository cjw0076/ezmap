package com.example.ez_capstone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.server.models.PlaceItem
import com.example.ez_capstone.ui.theme.AccentEnd
import com.example.ez_capstone.ui.theme.CardBackground
import com.example.ez_capstone.ui.theme.Secondary
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary

@Composable
fun PlacesOverlay(
    places: List<PlaceItem>,
    onPlaceSelected: (PlaceItem) -> Unit,
    onNavigateToPlace: (PlaceItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(places) { place ->
            PlaceCard(
                place = place,
                onClick = { onPlaceSelected(place) },
                onNavigate = { onNavigateToPlace(place) }
            )
        }
    }
}

@Composable
private fun PlaceCard(
    place: PlaceItem,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground.copy(alpha = 0.9f))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Text(
            text = place.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (place.category.isNotBlank()) {
            Text(
                text = place.category,
                fontSize = 12.sp,
                color = Secondary
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Text(
            text = place.address,
            fontSize = 12.sp,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (place.distance_m > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            val distanceText = if (place.distance_m >= 1000) {
                "%.1fkm".format(place.distance_m / 1000.0)
            } else {
                "${place.distance_m}m"
            }
            Text(
                text = distanceText,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onNavigate,
            colors = ButtonDefaults.buttonColors(containerColor = AccentEnd),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            Text("여기로 안내", color = TextPrimary, fontSize = 13.sp)
        }
    }
}
