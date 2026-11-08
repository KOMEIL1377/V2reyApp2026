package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BndTelegramLinks
import com.example.model.ProxyNode
import com.example.model.ProxyProtocol
import com.example.parser.ProxyConfigParser
import com.example.ui.components.BndTopHeaderBar
import com.example.ui.components.OpenVpnAuthDialog
import com.example.ui.theme.BndBgDark
import com.example.ui.theme.BndErrorRed
import com.example.ui.theme.BndNeonPurple
import com.example.ui.theme.BndOrchidTertiary
import com.example.ui.theme.BndSoftPurple
import com.example.ui.theme.BndSuccessGreen
import com.example.ui.theme.BndSurfaceCard
import com.example.ui.theme.BndSurfaceDark
import com.example.ui.theme.BndSurfaceVariantDark
import com.example.ui.theme.BndTextMuted
import com.example.ui.theme.BndTextPrimary
import com.example.ui.theme.BndTextSecondary
import com.example.ui.theme.BndVioletPrimary
import com.example.viewmodel.MainViewModel

@Composable
fun ServerListScreen(
    viewModel: MainViewModel,
    onServerSelected: (ProxyNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nodes by viewModel.nodes.collectAsState()
    val selectedNode by viewModel.selectedNode.collectAsState()
    val isTestingAll by viewModel.isTestingAllPings.collectAsState()
    val savedSubUrl by viewModel.subscriptionUrl.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocolFilter by remember { mutableStateOf<ProxyProtocol?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    // Dynamic OpenVPN authentication state: Pair(fileContent, profileName)
    var pendingOpenVpnAuth by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filteredNodes = remember(nodes, searchQuery, selectedProtocolFilter) {
        nodes.filter { node ->
            val matchesQuery = searchQuery.isBlank() ||
                node.name.contains(searchQuery, ignoreCase = true) ||
                node.server.contains(searchQuery, ignoreCase = true)
            val matchesProtocol = selectedProtocolFilter == null || node.protocol == selectedProtocolFilter
            matchesQuery && matchesProtocol
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BndBgDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with BND Logo
            BndTopHeaderBar(
                subtitle = "سرورها و کانفیگ‌ها (${nodes.size})",
                actions = {
                    if (nodes.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.sortNodesByPing() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BndSurfaceCard)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort by ping",
                                tint = BndSoftPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { viewModel.testAllPings() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BndSurfaceCard)
                                .size(36.dp)
                        ) {
                            if (isTestingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = BndOrchidTertiary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Test all",
                                    tint = BndOrchidTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BndVioletPrimary)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add node",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                if (nodes.isNotEmpty()) {
                    SearchInputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ProtocolFilterChips(
                        selected = selectedProtocolFilter,
                        onSelect = { selectedProtocolFilter = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (nodes.isEmpty()) {
                    CleanEmptyServerState(
                        onImportClick = { showImportDialog = true },
                        onBuyClick = { viewModel.openTelegramUrl(context, BndTelegramLinks.STORE_BOT) }
                    )
                } else if (filteredNodes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "سروری با این مشخصات یافت نشد",
                            color = BndTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredNodes, key = { it.id }) { node ->
                            ServerListItem(
                                node = node,
                                isSelected = selectedNode?.id == node.id,
                                onSelect = {
                                    viewModel.selectNode(node)
                                    onServerSelected(node)
                                },
                                onTestPing = { viewModel.testSinglePing(node) },
                                onDelete = { viewModel.deleteNode(node.id) },
                                onCopy = {
                                    copyToClipboard(context, node.rawUri.ifEmpty { node.server })
                                    Toast.makeText(context, "لینک کانفیگ کپی شد", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }

        // Floating Action Button to Import
        FloatingActionButton(
            onClick = { showImportDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_import_server"),
            containerColor = BndVioletPrimary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add node")
        }

        // Main Import Dialog (Text / OpenVPN File Picker / Subscription URL)
        if (showImportDialog) {
            ImportNodeDialog(
                initialSubUrl = savedSubUrl,
                onDismiss = { showImportDialog = false },
                onImportRaw = { text ->
                    if (ProxyConfigParser.isOpenVpnConfig(text)) {
                        if (ProxyConfigParser.checkOpenVpnRequiresAuth(text)) {
                            // Requires username & password
                            pendingOpenVpnAuth = Pair(text, "OpenVPN Profile")
                        } else {
                            // No auth directive / credentials embedded -> process immediately!
                            viewModel.importOpenVpnProfile(text, "OpenVPN Profile")
                            showImportDialog = false
                        }
                    } else {
                        viewModel.importConfigText(text)
                        showImportDialog = false
                    }
                },
                onOpenVpnAuthRequired = { content, filename ->
                    pendingOpenVpnAuth = Pair(content, filename)
                },
                onImportOpenVpnNoAuth = { content, filename ->
                    viewModel.importOpenVpnProfile(content, filename)
                    showImportDialog = false
                },
                onImportSubscription = { url ->
                    viewModel.importSubscriptionUrl(url)
                    showImportDialog = false
                }
            )
        }

        // Dynamic OpenVPN Credentials Dialog (Username & Password)
        pendingOpenVpnAuth?.let { (content, filename) ->
            OpenVpnAuthDialog(
                profileName = filename,
                onDismiss = { pendingOpenVpnAuth = null },
                onConfirm = { username, password ->
                    viewModel.importOpenVpnProfile(
                        configText = content,
                        profileName = filename,
                        username = username,
                        password = password
                    )
                    pendingOpenVpnAuth = null
                    showImportDialog = false
                }
            )
        }
    }
}

@Composable
private fun CleanEmptyServerState(
    onImportClick: () -> Unit,
    onBuyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(BndSurfaceCard)
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(BndVioletPrimary, BndOrchidTertiary)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = BndOrchidTertiary,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "هیچ سروری افزوده نشده است",
            color = BndTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No servers added. Import subscription or config",
            color = BndTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "برای شروع می‌توانید فایل کانفیگ .ovpn از حافظه انتخاب کرده، کانفیگ مستقیم اضافه کنید، یا لینک سابسکریپشن را وارد نمایید.",
            color = BndTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(26.dp))

        Button(
            onClick = onImportClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("empty_state_import_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("افزودن کانفیگ / فایل .ovpn یا سابسکریپشن", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onBuyClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("empty_state_buy_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BndSurfaceVariantDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = BndOrchidTertiary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("خرید اشتراک اختصاصی از ربات (${BndTelegramLinks.STORE_BOT_HANDLE})", color = BndOrchidTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_server_input"),
        placeholder = {
            Text("جستجوی نام یا آدرس سرور…", color = BndTextMuted, fontSize = 13.sp)
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = BndSoftPurple)
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BndSurfaceCard,
            unfocusedContainerColor = BndSurfaceCard,
            focusedBorderColor = BndVioletPrimary,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = BndTextPrimary,
            unfocusedTextColor = BndTextPrimary
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun ProtocolFilterChips(
    selected: ProxyProtocol?,
    onSelect: (ProxyProtocol?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChipItem(
                label = "همه پروتکل‌ها",
                isSelected = selected == null,
                onClick = { onSelect(null) }
            )
        }
        items(ProxyProtocol.values()) { proto ->
            FilterChipItem(
                label = proto.displayName,
                isSelected = selected == proto,
                onClick = { onSelect(proto) }
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BndVioletPrimary else BndSurfaceCard)
            .border(
                1.dp,
                if (isSelected) BndOrchidTertiary else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else BndTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ServerListItem(
    node: ProxyNode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTestPing: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("node_item_${node.id}")
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BndSurfaceVariantDark else BndSurfaceCard
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(BndVioletPrimary, BndOrchidTertiary)
                )
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.flag,
                    fontSize = 26.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        color = if (isSelected) Color.White else BndTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(node.protocol.badgeColorHex).copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = node.protocol.displayName,
                                color = Color(node.protocol.badgeColorHex),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (node.protocol == ProxyProtocol.OPENVPN && !node.openVpnUsername.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BndSuccessGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Auth: ${node.openVpnUsername}",
                                    color = BndSuccessGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "${node.server}:${node.port}",
                            color = BndTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PingBadge(
                    pingMs = node.pingMs,
                    isTesting = node.isTesting,
                    onClick = onTestPing
                )

                if (isSelected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(BndSuccessGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = BndTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(BndSurfaceDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("تست پینگ این سرور", color = BndTextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onTestPing()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = BndSoftPurple)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("کپی کانفیگ / مشخصات", color = BndTextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BndSoftPurple)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف سرور", color = BndErrorRed) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = BndErrorRed)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImportNodeDialog(
    initialSubUrl: String,
    onDismiss: () -> Unit,
    onImportRaw: (String) -> Unit,
    onOpenVpnAuthRequired: (content: String, filename: String) -> Unit,
    onImportOpenVpnNoAuth: (content: String, filename: String) -> Unit,
    onImportSubscription: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var rawInputText by remember { mutableStateOf("") }
    var subscriptionInputUrl by remember { mutableStateOf(initialSubUrl) }

    // System File Picker for OpenVPN (.ovpn and .conf files)
    val openVpnFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val filename = getFileName(context, uri) ?: "OpenVPN_Profile.ovpn"
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (content.isNotBlank()) {
                    if (ProxyConfigParser.isOpenVpnConfig(content)) {
                        // Dynamic authentication inspection
                        if (ProxyConfigParser.checkOpenVpnRequiresAuth(content)) {
                            // Requires credentials
                            onOpenVpnAuthRequired(content, filename)
                        } else {
                            // Credentials not required / already embedded -> import immediately!
                            onImportOpenVpnNoAuth(content, filename)
                        }
                    } else {
                        // General config file (e.g. list of URLs)
                        onImportRaw(content)
                    }
                } else {
                    Toast.makeText(context, "فایل انتخاب شده خالی است", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "خطا در خواندن فایل انتخابی", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BndSurfaceDark,
        title = {
            Text(
                text = "افزودن کانفیگ یا سابسکریپشن",
                color = BndTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = BndSurfaceDark,
                    contentColor = BndOrchidTertiary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = BndOrchidTertiary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("متن یا URL", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("فایل OpenVPN", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("سابسکریپشن", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (selectedTab) {
                    0 -> {
                        // Tab 0: Raw Text or URIs
                        Text(
                            text = "پشتیبانی از vless://, hy2://, tuic://, amnezia://, vmess://, trojan://, openvpn:// و متن .ovpn:",
                            color = BndTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = rawInputText,
                            onValueChange = { rawInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("import_raw_input"),
                            placeholder = {
                                Text("کانفیگ‌ها را اینجا پیست کنید…", color = BndTextMuted, fontSize = 12.sp)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BndSurfaceCard,
                                unfocusedContainerColor = BndSurfaceCard,
                                focusedBorderColor = BndVioletPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = BndTextPrimary,
                                unfocusedTextColor = BndTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    val clip = getFromClipboard(context)
                                    if (clip.isNotEmpty()) rawInputText = clip
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = BndNeonPurple)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("پیست از کلیپ‌بورد", color = BndNeonPurple, fontSize = 12.sp)
                            }

                            // Quick file picker shortcut
                            TextButton(
                                onClick = {
                                    openVpnFilePicker.launch(arrayOf("*/*", "application/x-openvpn-profile", "text/plain"))
                                }
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = BndOrchidTertiary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("انتخاب فایل", color = BndOrchidTertiary, fontSize = 12.sp)
                            }
                        }
                    }

                    1 -> {
                        // Tab 1: Dedicated OpenVPN File Picker
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    openVpnFilePicker.launch(arrayOf("*/*", "application/x-openvpn-profile", "text/plain"))
                                },
                            colors = CardDefaults.cardColors(containerColor = BndSurfaceCard),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(
                                    listOf(BndVioletPrimary, BndOrchidTertiary)
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(BndVioletPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = BndOrchidTertiary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "انتخاب فایل .ovpn یا .conf از حافظه",
                                    color = BndTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "پشتیبانی از ساختار استاندارد OpenVPN و احراز هویت خودکار",
                                    color = BndTextMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        openVpnFilePicker.launch(arrayOf("*/*", "application/x-openvpn-profile", "text/plain"))
                                    },
                                    modifier = Modifier.testTag("openvpn_choose_file_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مرور فایل‌ها (Browse)", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• در صورت وجود دستور auth-user-pass، فرم دریافت یوزرنیم و پسورد نمایش داده می‌شود.\n• در صورت عدم نیاز به احراز هویت، کانفیگ فوراً اضافه خواهد شد.",
                            color = BndTextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }

                    2 -> {
                        // Tab 2: Subscription URL
                        Text(
                            text = "لینک سابسکریپشن (Subscription URL) را وارد نمایید:",
                            color = BndTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = subscriptionInputUrl,
                            onValueChange = { subscriptionInputUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_sub_url_input"),
                            placeholder = {
                                Text("https://sub.bndteam.vip/api/v1/client/subscribe?token=...", color = BndTextMuted, fontSize = 12.sp)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BndSurfaceCard,
                                unfocusedContainerColor = BndSurfaceCard,
                                focusedBorderColor = BndVioletPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = BndTextPrimary,
                                unfocusedTextColor = BndTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                val clip = getFromClipboard(context)
                                if (clip.isNotEmpty()) subscriptionInputUrl = clip
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = BndNeonPurple)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پیست از کلیپ‌بورد", color = BndNeonPurple, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab != 1) {
                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            onImportRaw(rawInputText)
                        } else {
                            onImportSubscription(subscriptionInputUrl)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
                    modifier = Modifier.testTag("confirm_import_button")
                ) {
                    Text("بارگذاری سرورها")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن", color = BndTextSecondary)
            }
        }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("V2reyBND Config", text)
    clipboard.setPrimaryClip(clip)
}

private fun getFromClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (result == null) {
        val path = uri.path
        val cut = path?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = path.substring(cut + 1)
        }
    }
    return result
}
