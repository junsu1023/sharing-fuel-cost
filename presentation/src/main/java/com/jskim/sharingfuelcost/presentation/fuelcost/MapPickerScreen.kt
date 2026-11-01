package com.jskim.sharingfuelcost.presentation.fuelcost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.Place
import com.jskim.sharingfuelcost.presentation.R
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

private const val MAP_MOVE_MILLIS = 300

/** 지도 중심의 핀이 가리키는 위치를 탑승 위치로 고른다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapPickerScreen(
    initialPoint: GeoPoint,
    otherPickups: List<GeoPoint>,
    pick: MapPickState,
    cameraTarget: GeoPoint?,
    query: String,
    search: AddressSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectPlace: (Place) -> Unit,
    onCameraIdle: (GeoPoint) -> Unit,
    onConfirm: () -> Unit,
    onUseAddress: () -> Unit,
    onBack: () -> Unit,
) {
    var mapFailed by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back))
                    }
                },
            )
        },
        bottomBar = {
            Column {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    MapPickSummary(pick, mapFailed)
                }
                BottomActions(
                    tonalLabel = stringResource(R.string.action_enter_by_address),
                    onTonal = onUseAddress,
                    filledLabel = stringResource(R.string.action_use_this_location),
                    onFilled = onConfirm,
                    filledEnabled = !mapFailed && pick is MapPickState.Resolved,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (mapFailed) {
                Text(
                    text = stringResource(R.string.map_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                KakaoMapView(
                    initialPoint = initialPoint,
                    otherPickups = otherPickups,
                    cameraTarget = cameraTarget,
                    onMapClick = { focusManager.clearFocus() },
                    onCameraIdle = onCameraIdle,
                    onError = { mapFailed = true },
                )
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = stringResource(R.string.cd_map_center),
                    tint = MaterialTheme.colorScheme.primary,
                    // 핀 끝이 지도 중심을 가리키도록 위로 올린다.
                    modifier = Modifier.size(48.dp).offset(y = (-22).dp),
                )
                MapSearchPanel(
                    query = query,
                    search = search,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onSelect = {
                        focusManager.clearFocus()
                        onSelectPlace(it)
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun MapSearchPanel(
    query: String,
    search: AddressSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Column {
            FuelTextField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.label_map_search),
                onSearch = onSearch,
            )
            if (query.isNotBlank() && search != AddressSearchState.Idle) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    when (search) {
                        AddressSearchState.Idle -> Unit
                        AddressSearchState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        is AddressSearchState.Results -> {
                            if (search.places.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.map_search_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            search.places.forEach { place -> PlaceRow(place) { onSelect(place) } }
                        }
                        is AddressSearchState.Failed -> Text(
                            text = stringResource(search.reason.messageRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapPickSummary(pick: MapPickState, mapFailed: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            mapFailed -> Unit
            pick is MapPickState.Resolved -> {
                Text(pick.place.name, style = MaterialTheme.typography.titleMedium)
                if (pick.place.address != pick.place.name) {
                    Text(
                        text = pick.place.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            pick is MapPickState.Failed -> Text(
                text = stringResource(pick.reason.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            pick == MapPickState.NoAddress -> Text(
                text = stringResource(R.string.map_no_address),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun KakaoMapView(
    initialPoint: GeoPoint,
    otherPickups: List<GeoPoint>,
    cameraTarget: GeoPoint?,
    onMapClick: () -> Unit,
    onCameraIdle: (GeoPoint) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnMapClick by rememberUpdatedState(onMapClick)
    var readyMap by remember { mutableStateOf<KakaoMap?>(null) }

    val mapView = remember {
        MapView(context).apply {
            start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() = Unit

                    override fun onMapError(error: Exception) {
                        latestOnError()
                    }
                },
                object : KakaoMapReadyCallback() {
                    override fun getPosition(): LatLng =
                        LatLng.from(initialPoint.latitude, initialPoint.longitude)

                    override fun onMapReady(map: KakaoMap) {
                        addPickupLabels(map, otherPickups)
                        readyMap = map
                        // 누른 곳을 지도 중심으로 옮긴다. 이동이 끝나면 중심 좌표로 주소를 찾는다.
                        map.setOnMapClickListener { _, position, _, _ ->
                            latestOnMapClick()
                            map.moveToAnimated(position)
                        }
                        map.setOnCameraMoveEndListener { _, position, _ ->
                            latestOnCameraIdle(
                                GeoPoint(position.position.longitude, position.position.latitude),
                            )
                        }
                        latestOnCameraIdle(initialPoint)
                    }
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    LaunchedEffect(readyMap, cameraTarget) {
        val map = readyMap ?: return@LaunchedEffect
        val target = cameraTarget ?: return@LaunchedEffect
        map.moveToAnimated(LatLng.from(target.latitude, target.longitude))
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

private fun KakaoMap.moveToAnimated(position: LatLng) {
    moveCamera(CameraUpdateFactory.newCenterPosition(position), CameraAnimation.from(MAP_MOVE_MILLIS))
}

private fun addPickupLabels(map: KakaoMap, pickups: List<GeoPoint>) {
    val manager = map.labelManager ?: return
    val layer = manager.layer ?: return
    val styles = manager.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.map_marker))) ?: return
    pickups.forEach { point ->
        layer.addLabel(LabelOptions.from(LatLng.from(point.latitude, point.longitude)).setStyles(styles))
    }
}
