package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.models.AppInfo
import com.example.models.ClonedApp
import com.example.models.SpoofProfile
import com.example.ui.ClonerViewModel
import com.example.ui.CloningState
import com.example.ui.SandboxRunningState
import com.example.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: ClonerViewModel by viewModels {
        ClonerViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = DarkCyberBg
                ) { innerPadding ->
                    DashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: ClonerViewModel,
    modifier: Modifier = Modifier
) {
    val installedApps by viewModel.filteredApps.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val clonedApps by viewModel.clonedApps.collectAsState()
    val cloningState by viewModel.cloningState.collectAsState()
    val cloningLogs by viewModel.cloningLogs.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val runningSandboxApp by viewModel.runningSandboxApp.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var activeTab by remember { mutableStateOf(0) }
    var isProfileCreatorOpen by remember { mutableStateOf(false) }
    var isTelemetryOpen by remember { mutableStateOf(false) }

    var newProfileName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCyberBg)
    ) {
        // Upper telemetry indicator section
        HeaderSection(
            activeSandboxCount = clonedApps.size,
            onTelemetryClick = { isTelemetryOpen = true }
        )

        // Statistics grid mapping
        StatsSummaryRow(
            totalAppsCount = installedApps.size,
            clonedCount = clonedApps.size,
            profilesCount = profiles.size
        )

        // Custom M3 cyber-themed tabs
        TabSelector(
            selectedTab = activeTab,
            onTabSelected = { activeTab = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (activeTab) {
                0 -> AppWorkspaceContent(
                    installedApps = installedApps,
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.updateSearchQuery(it) },
                    onAppSelect = { viewModel.selectAppForClone(it) },
                    clonedApps = clonedApps,
                    onLaunchVirtual = { viewModel.launchVirtualApp(it) }
                )
                1 -> ProfilesWorkspaceContent(
                    profiles = profiles,
                    onDeleteProfile = { viewModel.deleteProfile(it) },
                    onAddNewClick = { isProfileCreatorOpen = true }
                )
                2 -> VirtualSandboxLogContent(
                    clonedApps = clonedApps,
                    profiles = profiles,
                    onLaunchVirtual = { viewModel.launchVirtualApp(it) },
                    onDeleteClone = { viewModel.deleteClonedApp(it) }
                )
            }
        }
    }

    // Modal dialog - Profile Binding configuration Setup
    selectedApp?.let { app ->
        AppCloneSetupDialog(
            app = app,
            profiles = profiles,
            onDismiss = { viewModel.selectAppForClone(null) },
            onInitiateClone = { profileId ->
                viewModel.runCloneSimulation(app, profileId)
                viewModel.selectAppForClone(null)
            }
        )
    }

    // Modal dialog - Cloning terminal compiler logs console
    if (cloningState !is CloningState.Idle) {
        CloningProgressDialog(
            cloningState = cloningState,
            logs = cloningLogs,
            onClose = { viewModel.clearCloningProgress() }
        )
    }

    // Modal dialog - Profile Creator
    if (isProfileCreatorOpen) {
        ProfileCreatorDialog(
            newProfileName = newProfileName,
            onProfileNameChange = { newProfileName = it },
            onDismiss = {
                isProfileCreatorOpen = false
                newProfileName = ""
            },
            onSave = {
                if (newProfileName.isNotBlank()) {
                    viewModel.createProfile(newProfileName)
                }
                isProfileCreatorOpen = false
                newProfileName = ""
            }
        )
    }

    // Modal dialog - Runtime Sandbox simulated telemetry
    runningSandboxApp?.let { activeApp ->
        SandboxTelemetryViewerDialog(
            activeApp = activeApp,
            onClose = { viewModel.stopVirtualApp() }
        )
    }

    // Modal dialog - System diagnostics report
    if (isTelemetryOpen) {
        SystemTelemetryViewerDialog(
            clonedApps = clonedApps,
            profiles = profiles,
            onClose = { isTelemetryOpen = false }
        )
    }
}

@Composable
fun HeaderSection(
    activeSandboxCount: Int,
    onTelemetryClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(EmeraldGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VIRTUAL OVERRIDE ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Device Cloner",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            IconButton(
                onClick = onTelemetryClick,
                modifier = Modifier
                    .background(Color(0xFF1E2433), RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
                    .testTag("telemetry_sys_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "System Telemetry Viewer",
                    tint = CyanAccent
                )
            }
        }
    }
}

@Composable
fun StatsSummaryRow(
    totalAppsCount: Int,
    clonedCount: Int,
    profilesCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            title = "HOST APPS",
            value = totalAppsCount.toString(),
            icon = Icons.Default.PlayArrow,
            color = Color(0xFF607D8B),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "SANDBOXES",
            value = clonedCount.toString(),
            icon = Icons.Default.CheckCircle,
            color = EmeraldGreen,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "PROFILES",
            value = profilesCount.toString(),
            icon = Icons.Default.Settings,
            color = CyanAccent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    title,
                    fontSize = 11.sp,
                    color = SlateTextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                fontSize = 22.sp,
                color = color,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun TabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 12.dp, 16.dp, 0.dp)
            .background(CosmicCardBg, RoundedCornerShape(12.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val tabNames = listOf("App Store", "Profiles", "Virtual Sandbox")
        tabNames.forEachIndexed { index, name ->
            val active = selectedTab == index
            val tabBg = if (active) EmeraldGreen else Color.Transparent
            val textColor = if (active) DarkCyberBg else SlateTextSecondary

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tabBg)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_button_$index"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun AppWorkspaceContent(
    installedApps: List<AppInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAppSelect: (AppInfo) -> Unit,
    clonedApps: List<ClonedApp>,
    onLaunchVirtual: (ClonedApp) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field"),
            placeholder = { Text("Search installed packages...", color = SlateTextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateTextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CosmicCardBg,
                unfocusedContainerColor = CosmicCardBg,
                focusedBorderColor = EmeraldGreen,
                unfocusedBorderColor = BorderSlate,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (installedApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No user applications found in local system storage.",
                        color = SlateTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(installedApps) { app ->
                    val isClonedAlready = clonedApps.find { it.originalPackageName == app.packageName }
                    
                    AppItemRow(
                        app = app,
                        isClonedAlready = isClonedAlready,
                        onConfigClick = { onAppSelect(app) },
                        onLaunchClick = { isClonedAlready?.let { onLaunchVirtual(it) } }
                    )
                }
            }
        }
    }
}

@Composable
fun AppItemRow(
    app: AppInfo,
    isClonedAlready: ClonedApp?,
    onConfigClick: () -> Unit,
    onLaunchClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_row_${app.packageName}"),
        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2E3748), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.appName.take(1).uppercase(),
                    fontSize = 18.sp,
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = app.packageName,
                    fontSize = 12.sp,
                    color = SlateTextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E2433), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "v${app.versionName}",
                            fontSize = 10.sp,
                            color = CyanAccent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (app.isSystemApp) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2D1E2A), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SYSTEM",
                                fontSize = 10.sp,
                                color = HotPink,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isClonedAlready != null) {
                Button(
                    onClick = onLaunchClick,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("launch_cloned_${app.packageName}")
                ) {
                    Text(
                        text = "LAUNCH",
                        color = DarkCyberBg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                Button(
                    onClick = onConfigClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, EmeraldGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("clone_conf_${app.packageName}")
                ) {
                    Text(
                        text = "VIRTUALIZE",
                        color = EmeraldGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun ProfilesWorkspaceContent(
    profiles: List<SpoofProfile>,
    onDeleteProfile: (Long) -> Unit,
    onAddNewClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dynamic Profiles",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Button(
                onClick = onAddNewClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_custom_profile")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = DarkCyberBg, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("NEW PROFILE", color = DarkCyberBg, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(profiles) { profile ->
                ProfileRowCard(
                    profile = profile,
                    onDelete = { onDeleteProfile(profile.id) }
                )
            }
        }
    }
}

@Composable
fun ProfileRowCard(
    profile: SpoofProfile,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}"),
        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = profile.profileName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Build OS: Android ${profile.androidVersion} (API 34)",
                        fontSize = 12.sp,
                        color = SlateTextSecondary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .background(Color(0xFF2A1E24), CircleShape)
                        .size(32.dp)
                        .testTag("delete_profile_${profile.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Profile", tint = HotPink, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = BorderSlate)
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    PropertyBullet(label = "BRAND", value = profile.brand)
                    PropertyBullet(label = "MODEL", value = profile.model)
                    PropertyBullet(label = "CARRIER", value = profile.simOperator)
                    PropertyBullet(label = "IMEI", value = profile.imei)
                }
                Column(modifier = Modifier.weight(1f)) {
                    PropertyBullet(label = "DEVICE ID", value = profile.device)
                    PropertyBullet(label = "ANDROID ID", value = profile.androidId)
                    PropertyBullet(label = "MAC ADDR", value = profile.wifiMac)
                    PropertyBullet(label = "SSID", value = profile.ssid)
                }
            }
        }
    }
}

@Composable
fun PropertyBullet(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = SlateTextSecondary,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            fontSize = 10.sp,
            color = Color.White,
            maxLines = 1,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun VirtualSandboxLogContent(
    clonedApps: List<ClonedApp>,
    profiles: List<SpoofProfile>,
    onLaunchVirtual: (ClonedApp) -> Unit,
    onDeleteClone: (ClonedApp) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Sandbox Isolations Vault",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Active sandboxed packages and telemetry hooks map status.",
            fontSize = 12.sp,
            color = SlateTextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (clonedApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No clones compiled. Run 'Virtualize' under App Store.",
                        color = SlateTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(clonedApps) { clone ->
                    val profile = profiles.find { it.id == clone.spoofProfileId }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clone_card_${clone.clonedPackageName}"),
                        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = clone.originalAppName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = clone.clonedPackageName,
                                        fontSize = 12.sp,
                                        color = SlateTextSecondary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onLaunchVirtual(clone) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("launch_run_${clone.clonedPackageName}")
                                    ) {
                                        Text("LAUNCH", color = DarkCyberBg, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    }

                                    IconButton(
                                        onClick = { onDeleteClone(clone) },
                                        modifier = Modifier
                                            .background(Color(0xFF2A1E24), CircleShape)
                                            .size(32.dp)
                                            .testTag("delete_clone_${clone.clonedPackageName}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Purge Sandbox", tint = HotPink, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = BorderSlate)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "BOUND PROFILE IDENTITY:",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateTextSecondary,
                                        letterSpacing = 1.0.sp
                                    )
                                    Text(
                                        text = profile?.profileName ?: "Security Profile Override",
                                        fontSize = 13.sp,
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(EmeraldGreen.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .border(1.dp, EmeraldGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = clone.status,
                                        fontSize = 9.sp,
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppCloneSetupDialog(
    app: AppInfo,
    profiles: List<SpoofProfile>,
    onDismiss: () -> Unit,
    onInitiateClone: (Long) -> Unit
) {
    var selectedProfileId by remember { mutableStateOf(profiles.firstOrNull()?.id ?: 0L) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
            border = BorderStroke(1.dp, BorderSlate),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("clone_setup_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Bound Sandbox Hardware Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Bind custom environment properties for virtualization:",
                    fontSize = 12.sp,
                    color = SlateTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2433), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF2E3748), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(app.appName.take(1).uppercase(), color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(app.appName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(app.packageName, fontSize = 11.sp, color = SlateTextSecondary, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Spoofed Profile Identity:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (profiles.isEmpty()) {
                    Text("No profiles configured yet. Create one in Profiles Tab.", color = HotPink, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(profiles) { profile ->
                            val active = selectedProfileId == profile.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) EmeraldGreen.copy(alpha = 0.15f) else Color(0xFF131722))
                                    .border(1.dp, if (active) EmeraldGreen else BorderSlate, RoundedCornerShape(8.dp))
                                    .clickable { selectedProfileId = profile.id }
                                    .padding(10.dp)
                                    .testTag("select_profile_${profile.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(profile.profileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("${profile.brand} • ${profile.model}", fontSize = 11.sp, color = SlateTextSecondary)
                                }
                                RadioButton(
                                    selected = active,
                                    onClick = { selectedProfileId = profile.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen, unselectedColor = SlateTextSecondary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, BorderSlate),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL", color = SlateTextPrimary, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onInitiateClone(selectedProfileId) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_clone_button"),
                        enabled = profiles.isNotEmpty()
                    ) {
                        Text("COMPILE CLONE", color = DarkCyberBg, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun CloningProgressDialog(
    cloningState: CloningState,
    logs: List<String>,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = { }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCyberBg),
            border = BorderStroke(1.dp, if (cloningState is CloningState.Success) EmeraldGreen else BorderSlate),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("recompiler_progress_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MANIFEST / DEX LINK COMPILER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = EmeraldGreen,
                        letterSpacing = 1.0.sp
                    )

                    if (cloningState is CloningState.Running) {
                        CircularProgressIndicator(
                            color = EmeraldGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF07090D), RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                log,
                                color = EmeraldGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (cloningState) {
                    is CloningState.Running -> {
                        Text(
                            "Please hold. Packaging sandboxed virtual workspace configuration parameters...",
                            color = SlateTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is CloningState.Success -> {
                        Column {
                            Text(
                                "COMPILATION SUCCESSFUL",
                                color = EmeraldGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Virtual clone compiled. Target signatures verified.",
                                color = SlateTextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dismiss_success_recompile"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("COMPLETED", color = DarkCyberBg, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    is CloningState.Error -> {
                        Column {
                            Text(
                                "INTEGRATION FAILURE",
                                color = HotPink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                cloningState.message,
                                color = SlateTextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(containerColor = HotPink),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("CLOSE", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun ProfileCreatorDialog(
    newProfileName: String,
    onProfileNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
            border = BorderStroke(1.dp, BorderSlate),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("profile_creator_diag")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Dynamic Identity Generator",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Assign a label to generate completely randomized hardware/serial specifications securely.",
                    fontSize = 11.sp,
                    color = SlateTextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = onProfileNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                    placeholder = { Text("Profile Name (e.g. Nexus Override)", color = SlateTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF131722),
                        unfocusedContainerColor = Color(0xFF131722),
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderSlate,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL", color = SlateTextPrimary, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_profile_button"),
                        enabled = newProfileName.isNotBlank()
                    ) {
                        Text("COMPILE", color = DarkCyberBg, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun SandboxTelemetryViewerDialog(
    activeApp: SandboxRunningState,
    onClose: () -> Unit
) {
    var simulatorLogs by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(key1 = activeApp.clonedApp.clonedPackageName) {
        val simulatedQueries = listOf(
            "[SECURITY] Bypassing target SafetyNet compliance request",
            "[QUERY] Diverted wifiMac query to virtual value: ${activeApp.deviceProfile.wifiMac}",
            "[QUERY] Intercepted android_id fetch -> Returned: ${activeApp.deviceProfile.androidId}",
            "[GEO] Mock Location Isolation bound: Connected simulation.",
            "[INFO] Telephony operator identity spoof: ${activeApp.deviceProfile.simOperator}",
            "[HOOK] Redirected property ro.product.model request -> Returned: ${activeApp.deviceProfile.model}",
            "[HOOK] Diverted build.id request -> spoofed: ${activeApp.deviceProfile.buildId}",
            "[SECURITY] API query bypass validated successfully."
        )

        simulatorLogs = listOf("⚡ [SANDBOX INTERFACE CONFIG] Real-time sandbox hooks bound.")
        for (query in simulatedQueries) {
            delay(1500)
            simulatorLogs = simulatorLogs + query
        }
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCyberBg),
            border = BorderStroke(1.dp, EmeraldGreen),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("sandbox_telemetry_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE ISOLATION MONITOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen,
                            letterSpacing = 1.0.sp
                        )
                        Text(
                            text = activeApp.clonedApp.originalAppName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(EmeraldGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(1.dp, EmeraldGreen, RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    ) {
                        Text("ACTIVE RUNNING", fontSize = 9.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "Virtualizer Parameters Map:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF131722), RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    PropertyBullet(label = "SPOOF MODEL", value = activeApp.deviceProfile.model)
                    PropertyBullet(label = "SPOOF BRAND", value = activeApp.deviceProfile.brand)
                    PropertyBullet(label = "IMEI SERIAL", value = activeApp.deviceProfile.imei)
                    PropertyBullet(label = "SPOOF OPERATOR", value = activeApp.deviceProfile.simOperator)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "Real-Time Telemetry Log Traces:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color(0xFF07090D), RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(simulatorLogs) { item ->
                            Text(
                                text = item,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (item.startsWith("[SECURITY]")) AmberWarning else if (item.startsWith("[QUERY]")) CyanAccent else EmeraldGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = HotPink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("terminate_sandbox_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("TERMINATE OVERRIDE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SystemTelemetryViewerDialog(
    clonedApps: List<ClonedApp>,
    profiles: List<SpoofProfile>,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
            border = BorderStroke(1.dp, BorderSlate),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("telemetry_diag")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sandbox Diagnostics Telemetry",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Current operational status check.",
                    fontSize = 11.sp,
                    color = SlateTextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                DiagnosticMetricRow(label = "Isolation Sandbox Module", status = "Operational", isNormal = true)
                DiagnosticMetricRow(label = "Manifest / AXML Compiler", status = "Ready", isNormal = true)
                DiagnosticMetricRow(label = "Integrity Bypass Verification", status = "Passed (MEETS_STRONG)", isNormal = true)
                DiagnosticMetricRow(label = "SafetyNet Attestation Check", status = "Passed (MEETS_BASIC)", isNormal = true)

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("DISMISS REPORT", color = DarkCyberBg, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun DiagnosticMetricRow(
    label: String,
    status: String,
    isNormal: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = SlateTextPrimary, fontSize = 12.sp)
        Box(
            modifier = Modifier
                .background(
                    if (isNormal) EmeraldGreen.copy(alpha = 0.15f) else HotPink.copy(alpha = 0.15f),
                    RoundedCornerShape(6.dp)
                )
                .border(
                    1.dp,
                    if (isNormal) EmeraldGreen.copy(alpha = 0.4f) else HotPink.copy(alpha = 0.4f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = status,
                color = if (isNormal) EmeraldGreen else HotPink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
