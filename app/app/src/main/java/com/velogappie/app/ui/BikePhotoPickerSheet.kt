package com.velogappie.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.velogappie.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikePhotoPickerSheet(
    onPickFromGallery: () -> Unit,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedModel by remember { mutableStateOf<BikeModel?>(null) }
    val context = LocalContext.current

    BackHandler {
        if (selectedModel != null) selectedModel = null else onDismiss()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (selectedModel == null) {
            Text(
                stringResource(R.string.photo_picker_choose_model),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(VELORETTI_MODELS) { model ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedModel = model
                            onModelSelected(model.name)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Text(
                            model.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedModel = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
                }
                Text(
                    selectedModel!!.name,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.photo_picker_choose_color),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(selectedModel!!.colors) { color ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val query = Uri.encode(color.searchQuery)
                            val url = "https://duckduckgo.com/?q=$query&iax=images&ia=images"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(color.name, style = MaterialTheme.typography.bodyLarge)
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.photo_picker_search),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onPickFromGallery,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.a11y_gallery))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.photo_picker_from_gallery))
        }
        Spacer(Modifier.height(8.dp))
    }
}
