package com.orientesanasrekinatajs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Map as MapIcon
import androidx.compose.material.icons.outlined.Map as OutlinedMapIcon
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Spacer
import com.orientesanasrekinatajs.ui.components.RouteEditorPanelState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled
import com.orientesanasrekinatajs.ui.theme.LocalLongPressFeedback
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.ui.components.routeColor
import com.orientesanasrekinatajs.ui.components.MoveOrderArrows
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction

@Composable
internal fun ManageRoutesDialog(
    primaryRoute: OptimizedRoute?,
    alternativeRoutes: List<OptimizedRoute>,
    primaryRouteIndex: Int,
    routeMetadata: Map<String, RouteMetadata>,
    onAction: (RouteManagementAction) -> Unit,
    onDismiss: () -> Unit,
    activeRouteId: String? = primaryRoute?.id,
    panelState: RouteEditorPanelState? = null,
    onPanelStateChange: (RouteEditorPanelState) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var closing by remember { mutableStateOf(false) }
    var localPanelState by rememberSaveable { mutableStateOf(RouteEditorPanelState.HALF) }
    var pendingActiveRouteId by remember { mutableStateOf<String?>(null) }
    var dialogFocusManager by remember { mutableStateOf<FocusManager?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val routes = primaryRoute?.let { primary ->
        val safePrimaryIndex = primaryRouteIndex.coerceIn(0, alternativeRoutes.size)
        alternativeRoutes.take(safePrimaryIndex) + primary + alternativeRoutes.drop(safePrimaryIndex)
    } ?: alternativeRoutes
    val initialRouteKeys = routes.map(OptimizedRoute::id)
    val primaryKey = primaryRoute?.id
    val defaultNames = routes.map { route ->
        route.id to managedRouteDefaultName(
            route = route,
            primaryRoute = primaryRoute,
            isPrimary = route.id == primaryKey,
        )
    }.toMap()
    val initialMetadata = initialRouteKeys.associateWith { key ->
        val metadata = routeMetadata[key] ?: RouteMetadata()
        metadata.copy(name = metadata.name.ifBlank { defaultNames.getValue(key) })
    }
    var managedRoutes by remember(initialRouteKeys) { mutableStateOf(routes) }
    var managedMetadata by remember(initialRouteKeys, routeMetadata) { mutableStateOf(initialMetadata) }
    var showDeleteUnstarredConfirmation by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var focusedRouteKey by remember { mutableStateOf<String?>(null) }
    var openMenuRouteKey by remember { mutableStateOf<String?>(null) }
    var pendingDeleteRouteKey by remember { mutableStateOf<String?>(null) }
    val routeScrollState = rememberLazyListState()
    val animationsEnabled = LocalAnimationsEnabled.current
    val latestAnimationsEnabled by rememberUpdatedState(animationsEnabled)
    val longPressFeedback by rememberUpdatedState(LocalLongPressFeedback.current)
    val handles = remember { mutableMapOf<String, LayoutCoordinates>() }
    val nameFields = remember { mutableMapOf<String, LayoutCoordinates>() }
    var backgroundCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var draggedRouteId by remember { mutableStateOf<String?>(null) }
    var draggedCenterY by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    var releasedRouteId by remember { mutableStateOf<String?>(null) }
    var releasedTop by remember { mutableFloatStateOf(0f) }
    val releaseProgress = remember(releasedRouteId) { Animatable(0f) }
    var movedRouteId by remember { mutableStateOf<String?>(null) }
    var moveRequest by remember { mutableIntStateOf(0) }
    var followOffset by remember { mutableStateOf<Int?>(null) }
    val moveHighlight = remember { Animatable(0f) }
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val maxSpeed = with(density) { 600.dp.toPx() }
    fun destination(): Int? = routeScrollState.layoutInfo.visibleItemsInfo
        .filter { item -> managedRoutes.any { it.id == item.key } }
        .minByOrNull { abs(it.offset + it.size / 2f - draggedCenterY) }
        ?.let { item -> managedRoutes.indexOfFirst { it.id == item.key } }
    fun moveRoute(from: Int, to: Int, follow: Boolean = false) {
        if (from !in managedRoutes.indices || to !in managedRoutes.indices || from == to) return
        val id = managedRoutes[from].id
        followOffset = if (follow) routeScrollState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == id }?.offset else null
        movedRouteId = id
        managedRoutes = managedRoutes.toMutableList().apply { add(to, removeAt(from)) }
        moveRequest++
    }
    LaunchedEffect(moveRequest) {
        if (moveRequest == 0) return@LaunchedEffect
        moveHighlight.snapTo(0.5f)
        withFrameNanos { }
        val item = routeScrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == movedRouteId }
        val offset = followOffset
        if (offset != null && item != null) {
            // Keep arrow controls at the same height for repeated taps.
            val scroll = item.offset - offset
            if (animationsEnabled) routeScrollState.animateScrollBy(scroll.toFloat(), tween(220))
            else routeScrollState.scrollBy(scroll.toFloat())
        } else if (offset != null) {
            val index = managedRoutes.indexOfFirst { it.id == movedRouteId }
            if (index >= 0) {
                if (animationsEnabled) routeScrollState.animateScrollToItem(index, -offset)
                else routeScrollState.scrollToItem(index, -offset)
            }
        }
        delay(300)
        if (animationsEnabled) moveHighlight.animateTo(0f, tween(700)) else moveHighlight.snapTo(0f)
        movedRouteId = null
    }
    LaunchedEffect(releasedRouteId) {
        if (releasedRouteId == null) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        releaseProgress.animateTo(1f, tween(220))
        releasedRouteId = null
    }
    LaunchedEffect(draggedRouteId) {
        if (draggedRouteId == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = frame
            val layout = routeScrollState.layoutInfo
            val viewportBottom = (listCoordinates?.size?.height ?: 0).toFloat()
            val edgeSize = minOf(edge, (viewportBottom - layout.viewportStartOffset) / 3f).coerceAtLeast(1f)
            val direction = when {
                pointerY < layout.viewportStartOffset + edgeSize ->
                    -((layout.viewportStartOffset + edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
                pointerY > viewportBottom - edgeSize ->
                    ((pointerY - viewportBottom + edgeSize) / edgeSize).coerceIn(0f, 1f)
                else -> 0f
            }
            if (direction != 0f) routeScrollState.scrollBy(direction * maxSpeed * seconds)
        }
    }
    val hasUnsavedChanges = managedRoutes.map(OptimizedRoute::id) != initialRouteKeys ||
        managedMetadata != initialMetadata
    fun clearInputFocus() {
        dialogFocusManager?.clearFocus(force = true)
        keyboardController?.hide()
        focusedRouteKey = null
    }
    fun requestCancel() {
        clearInputFocus()
        if (hasUnsavedChanges) showDiscardConfirmation = true else closing = true
    }
    var headerHeight by remember { mutableIntStateOf(0) }
    var actionHeight by remember { mutableIntStateOf(0) }
    var footerHeight by remember { mutableIntStateOf(0) }
    BackHandler { if (focusedRouteKey != null) clearInputFocus() else requestCancel() }
    ManageRoutesPanel(
        closing = closing,
        onClosed = onDismiss,
        collapsedHeightPx = headerHeight + actionHeight + footerHeight + with(density) { 33.dp.toPx() },
        panelState = panelState ?: localPanelState,
        onPanelStateChange = { localPanelState = it; onPanelStateChange(it) },
        modifier = modifier,
        gesturesEnabled = draggedRouteId == null && releasedRouteId == null,
    ) { headerDragModifier, showList ->
        val focusManager = LocalFocusManager.current
        DisposableEffect(focusManager) {
            dialogFocusManager = focusManager
            onDispose { dialogFocusManager = null }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .testTag("manage_routes_dialog_background")
                .onGloballyPositioned { backgroundCoordinates = it }
                .pointerInput(focusManager) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val root = backgroundCoordinates?.localToRoot(down.position)
                        if (root != null && nameFields.values.none {
                            it.isAttached && it.boundsInRoot().contains(root)
                        }) clearInputFocus()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(headerDragModifier.fillMaxWidth().wrapContentHeight(unbounded = true).onSizeChanged { headerHeight = it.height }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 2.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                Text(
                    stringResource(R.string.manage_routes),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("manage_routes_title"),
                )
            }
                OutlinedButton(
                    onClick = { clearInputFocus(); showDeleteUnstarredConfirmation = true },
                    enabled = managedRoutes.any { managedMetadata[it.id]?.isStarred != true },
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).onSizeChanged { actionHeight = it.height }.testTag("managed_routes_delete_unstarred"),
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Text(stringResource(R.string.delete_all_unstarred_routes),
                        modifier = Modifier.padding(start = 6.dp), textAlign = TextAlign.Center)
                }
                HorizontalDivider(Modifier.padding(top = 4.dp))
                val routeRow: @Composable (Int, OptimizedRoute, Modifier, Boolean) -> Unit = { index, managedRoute, rowModifier, preview ->
                    val key = managedRoute.id
                    DisposableEffect(key, preview) {
                        onDispose {
                            if (!preview) {
                                handles.remove(key)
                                nameFields.remove(key)
                            }
                        }
                    }
                    val metadata = managedMetadata[key] ?: initialMetadata[key] ?: RouteMetadata()
                    val duplicateName = stringResource(R.string.route_copy_name, metadata.name)
                    Row(
                        modifier = rowModifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .testTag("managed_route_$key")
                            .graphicsLayer {
                                alpha = if (preview) 1f else when (key) {
                                    releasedRouteId -> 0f
                                    draggedRouteId -> 0.2f
                                    else -> 1f
                                }
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(routeColor(metadata.colorIndex).copy(alpha = if (movedRouteId == key && !preview) 0.14f + moveHighlight.value else 0.14f))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .width(7.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(routeColor(metadata.colorIndex)),
                        )
                        OutlinedTextField(
                            value = metadata.name,
                            onValueChange = { name ->
                                managedMetadata = managedMetadata +
                                    (key to metadata.copy(name = name.take(60)))
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.managed_route_name))
                                    if (metadata.isStarred) {
                                        Icon(Icons.Default.Star, stringResource(R.string.starred_route),
                                            Modifier.size(12.dp).testTag("managed_route_star_$key"))
                                    }
                                }
                            },
                            singleLine = true,
                            readOnly = preview,
                            modifier = Modifier
                                .weight(1f)
                                .offset(y = (-4).dp)
                                .testTag("managed_route_name_$index")
                                .onGloballyPositioned { if (!preview) nameFields[key] = it }
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        focusedRouteKey = key
                                    } else if (focusedRouteKey == key) {
                                        focusedRouteKey = null
                                    }
                                },
                        )
                        Box {
                            IconButton(modifier = Modifier.size(40.dp), enabled = draggedRouteId == null && releasedRouteId == null, onClick = { clearInputFocus(); openMenuRouteKey = key }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.route_actions))
                            }
                            DropdownMenu(
                                expanded = openMenuRouteKey == key,
                                onDismissRequest = { openMenuRouteKey = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.set_active_route)) },
                                    leadingIcon = { Icon(Icons.Default.MapIcon, null) },
                                    enabled = key != (pendingActiveRouteId ?: activeRouteId),
                                    onClick = {
                                        pendingActiveRouteId = key
                                        managedMetadata = managedMetadata + (key to metadata.copy(isHidden = false))
                                        if (key in initialRouteKeys && routeMetadata[key]?.isHidden != true) {
                                            onAction(RouteManagementAction.Select(key))
                                        }
                                        openMenuRouteKey = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (metadata.isStarred) R.string.unstar_route else R.string.star_route)) },
                                    leadingIcon = { Icon(if (metadata.isStarred) Icons.Default.Star else Icons.Default.StarBorder, null) },
                                    onClick = {
                                        managedMetadata = managedMetadata + (key to metadata.copy(isStarred = !metadata.isStarred))
                                        openMenuRouteKey = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate_route)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                    onClick = {
                                        val duplicate = managedRoute.copy(id = java.util.UUID.randomUUID().toString())
                                        managedRoutes = managedRoutes.toMutableList().also { it.add(index + 1, duplicate) }
                                        val nextColor = com.orientesanasrekinatajs.domain.model.nextRouteColorIndex(managedMetadata.values)
                                        managedMetadata = managedMetadata + (duplicate.id to metadata.copy(
                                            name = duplicateName.take(60), isStarred = false,
                                            colorIndex = nextColor, isDisplayed = false,
                                        ))
                                        openMenuRouteKey = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (metadata.isHidden) R.string.unhide_route else R.string.hide_route)) },
                                    leadingIcon = { Icon(if (metadata.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) },
                                    onClick = {
                                        managedMetadata = managedMetadata + (key to metadata.copy(isHidden = !metadata.isHidden))
                                        openMenuRouteKey = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (metadata.isDisplayed) R.string.remove_route_from_map else R.string.display_route_on_map)) },
                                    leadingIcon = {
                                        Icon(
                                            if (metadata.isDisplayed) Icons.Default.MapIcon
                                            else Icons.Outlined.OutlinedMapIcon,
                                            null,
                                        )
                                    },
                                    onClick = {
                                        managedMetadata = managedMetadata + (key to metadata.copy(isDisplayed = !metadata.isDisplayed))
                                        openMenuRouteKey = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_route)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    enabled = !metadata.isStarred,
                                    onClick = { pendingDeleteRouteKey = key; openMenuRouteKey = null },
                                )
                            }
                        }
                        MoveOrderArrows(
                            onMoveUp = {
                                clearInputFocus()
                                moveRoute(index, index - 1, follow = true)
                            },
                            onMoveDown = {
                                clearInputFocus()
                                moveRoute(index, index + 1, follow = true)
                            },
                            canMoveUp = index > 0 && draggedRouteId == null && releasedRouteId == null,
                            canMoveDown = index < managedRoutes.lastIndex && draggedRouteId == null && releasedRouteId == null,
                            upDescription = stringResource(R.string.move_route_up),
                            downDescription = stringResource(R.string.move_route_down),
                        )
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = stringResource(R.string.drag_route_to_reorder),
                            modifier = Modifier
                                .testTag("managed_route_drag_$key")
                                .size(40.dp)
                                .onGloballyPositioned { if (!preview) handles[key] = it }
                                .background(
                                    if (preview) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent,
                                    RoundedCornerShape(20.dp),
                                )
                                .padding(8.dp),
                        )
                    }
                }
                if (showList) {
                Box(
                    Modifier.weight(1f).clipToBounds()
                        .testTag("managed_routes_list")
                        .onGloballyPositioned { listCoordinates = it }
                        .pointerInput(routeScrollState) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (releasedRouteId != null) return@awaitEachGesture
                                val root = listCoordinates?.localToRoot(down.position) ?: return@awaitEachGesture
                                val id = handles.entries.firstOrNull { (_, coordinates) ->
                                    coordinates.isAttached && coordinates.boundsInRoot().contains(root)
                                }?.key ?: return@awaitEachGesture
                                val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                val item = routeScrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                                    ?: return@awaitEachGesture
                                clearInputFocus()
                                openMenuRouteKey = null
                                draggedRouteId = id
                                draggedCenterY = item.offset + item.size / 2f
                                pointerY = press.position.y
                                rowHeight = item.size.toFloat()
                                longPressFeedback()
                                try {
                                    val released = drag(press.id) { change ->
                                        draggedCenterY += change.positionChange().y
                                        pointerY = change.position.y
                                        change.consume()
                                    }
                                    if (released) {
                                        val from = managedRoutes.indexOfFirst { it.id == id }
                                        val to = destination()
                                        if (from >= 0 && to != null) {
                                            if (latestAnimationsEnabled) {
                                                releasedTop = (draggedCenterY - rowHeight / 2f).coerceIn(
                                                    0f, ((listCoordinates?.size?.height ?: 0) - rowHeight).coerceAtLeast(0f),
                                                )
                                                releasedRouteId = id
                                            }
                                            moveRoute(from, to)
                                        }
                                    }
                                } finally {
                                    draggedRouteId = null
                                }
                            }
                        },
                ) {
                    LazyColumn(
                        state = routeScrollState,
                        userScrollEnabled = draggedRouteId == null && releasedRouteId == null,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(managedRoutes, key = { _, route -> route.id }) { index, route ->
                            routeRow(index, route, Modifier.animateItem(
                                fadeInSpec = if (animationsEnabled && releasedRouteId != route.id) tween(220) else null,
                                fadeOutSpec = if (animationsEnabled) tween(180) else null,
                                placementSpec = if (animationsEnabled && releasedRouteId != route.id) tween(280) else null,
                            ), false)
                        }
                    }
                    val targetIndex = if (draggedRouteId != null) destination() else null
                    val sourceIndex = managedRoutes.indexOfFirst { it.id == draggedRouteId }
                    val target = targetIndex?.let { index -> routeScrollState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == managedRoutes[index].id } }
                    if (target != null && targetIndex != sourceIndex) {
                        val y = target.offset + if (requireNotNull(targetIndex) > sourceIndex) target.size else 0
                        HorizontalDivider(
                            Modifier.fillMaxWidth().zIndex(2f).offset { IntOffset(0, y.coerceIn(0, ((listCoordinates?.size?.height ?: 0) - with(density) { 3.dp.roundToPx() }).coerceAtLeast(0))) }.testTag("managed_route_insertion"),
                            thickness = 3.dp, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val previewId = draggedRouteId ?: releasedRouteId
                    managedRoutes.firstOrNull { it.id == previewId }?.let { route ->
                        Surface(
                            Modifier.fillMaxWidth().offset {
                                val top = if (releasedRouteId != null) {
                                    val targetTop = routeScrollState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == route.id }?.offset?.toFloat() ?: releasedTop
                                    releasedTop + (targetTop - releasedTop) * releaseProgress.value
                                } else (draggedCenterY - rowHeight / 2f).coerceIn(
                                    0f, ((listCoordinates?.size?.height ?: 0) - rowHeight).coerceAtLeast(0f),
                                )
                                IntOffset(0, top.roundToInt())
                            }.testTag(if (releasedRouteId != null) "managed_route_drop_preview" else "managed_route_drag_preview")
                                .clearAndSetSemantics { },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shadowElevation = 8.dp,
                        ) {
                            routeRow(managedRoutes.indexOf(route), route, Modifier, true)
                        }
                    }
                }
                }
                if (showList && managedRoutes.isEmpty()) {
                    Text(stringResource(R.string.no_routes_to_manage), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                if (showList) HorizontalDivider()
                // Keep the footer at the bottom even after the list leaves composition.
                Box(
                    modifier = if (showList) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).onSizeChanged { footerHeight = it.height },
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = ::requestCancel) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = {
                            clearInputFocus()
                            onAction(
                                RouteManagementAction.Apply(
                                    orderedRoutes = managedRoutes,
                                    metadata = managedMetadata.mapValues { (key, metadata) ->
                                        metadata.copy(order = managedRoutes.indexOfFirst { it.id == key })
                                    },
                                ),
                            )
                            pendingActiveRouteId?.takeIf { id -> managedRoutes.any { it.id == id } }
                                ?.let { onAction(RouteManagementAction.Select(it)) }
                            closing = true
                        },
                    ) { Text(stringResource(R.string.save)) }
                }
                }
        }
    }
    if (showDeleteUnstarredConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_unstarred_routes_title),
            message = stringResource(R.string.delete_unstarred_routes_confirmation),
            onConfirm = {
                showDeleteUnstarredConfirmation = false
                val starredKeys = managedRoutes.map(OptimizedRoute::id)
                    .filter { key -> managedMetadata[key]?.isStarred == true }
                managedRoutes = managedRoutes.filter { it.id in starredKeys }
                managedMetadata = managedMetadata.filterKeys(starredKeys.toSet()::contains)
            },
            onDismiss = { showDeleteUnstarredConfirmation = false },
        )
    }
    pendingDeleteRouteKey?.let { routeKey ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_route_title),
            message = stringResource(
                R.string.delete_route_confirmation,
                managedMetadata[routeKey]?.name.orEmpty(),
            ),
            onConfirm = {
                pendingDeleteRouteKey = null
                managedRoutes = managedRoutes.filterNot { it.id == routeKey }
                managedMetadata = managedMetadata - routeKey
            },
            onDismiss = { pendingDeleteRouteKey = null },
        )
    }
    if (showDiscardConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.discard_route_management_changes_title),
            message = stringResource(R.string.discard_route_management_changes_confirmation),
            onConfirm = {
                showDiscardConfirmation = false
                closing = true
            },
            onDismiss = { showDiscardConfirmation = false },
        )
    }
}

@Composable
internal fun managedRouteDefaultName(
    route: OptimizedRoute,
    primaryRoute: OptimizedRoute?,
    isPrimary: Boolean,
): String {
    if (isPrimary || primaryRoute == null) return stringResource(R.string.primary_route_tab)
    val difference = route.totalScore - primaryRoute.totalScore
    return when {
        difference > 0 -> androidx.compose.ui.res.pluralStringResource(
            R.plurals.more_score_points_route_tab,
            difference,
            difference,
        )
        difference < 0 -> androidx.compose.ui.res.pluralStringResource(
            R.plurals.fewer_score_points_route_tab,
            -difference,
            -difference,
        )
        else -> stringResource(R.string.zero_score_points_route_tab)
    }
}
