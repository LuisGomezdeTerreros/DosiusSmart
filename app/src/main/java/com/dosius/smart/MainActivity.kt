package com.dosius.smart

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dosius.smart.data.alarm.GlucoseAlarmChecker
import com.dosius.smart.data.preferences.AppPreferences
import com.dosius.smart.data.service.GlucoseRefreshService
import com.dosius.smart.presentation.entry.QuickEntrySheet
import com.dosius.smart.presentation.navigation.AppNavHost
import com.dosius.smart.presentation.navigation.Screen
import com.dosius.smart.presentation.onboarding.OnboardingScreen
import com.dosius.smart.presentation.theme.DosiusPurple
import com.dosius.smart.presentation.theme.DosiusSmartTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var alarmChecker: GlucoseAlarmChecker
    @javax.inject.Inject lateinit var appPreferences: AppPreferences

    private data class BottomNavItem(
        val screen: Screen,
        val label: String,
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    0
                )
            }
        }

        startForegroundService(Intent(this, GlucoseRefreshService::class.java))

        val leftNavItems = listOf(
            BottomNavItem(Screen.Logs,       "Logs",  Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List),
            BottomNavItem(Screen.Dashboard,  "Trend", Icons.AutoMirrored.Filled.ShowChart, Icons.AutoMirrored.Outlined.ShowChart),
        )
        val rightNavItems = listOf(
            BottomNavItem(Screen.Parameters, "Params", Icons.Filled.Tune,    Icons.Outlined.Tune),
            BottomNavItem(Screen.Data,       "Data",   Icons.Filled.Storage, Icons.Outlined.Storage),
        )

        setContent {
            DosiusSmartTheme {
                val onboardingComplete by appPreferences.onboardingCompleteOrNull
                    .collectAsStateWithLifecycle(initialValue = null)

                if (onboardingComplete != true) {
                    OnboardingScreen()
                    return@DosiusSmartTheme
                }

                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isOnSubScreen = currentRoute == Screen.Settings.route
                        || currentRoute == Screen.Alarms.route
                val showBottomBar = !isOnSubScreen
                var showQuickEntry by remember { mutableStateOf(false) }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_dosius),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Dosius Smart",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            HorizontalDivider()
                            NavigationDrawerItem(
                                icon = {
                                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                                },
                                label = { Text("Alarms") },
                                selected = currentRoute == Screen.Alarms.route,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(Screen.Alarms.route) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            NavigationDrawerItem(
                                icon = {
                                    Icon(Icons.Outlined.Settings, contentDescription = null)
                                },
                                label = { Text("Settings") },
                                selected = currentRoute == Screen.Settings.route,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(Screen.Settings.route) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {
                    if (showQuickEntry) {
                        QuickEntrySheet(
                            onEntryTypeSelected = { showQuickEntry = false },
                            onDismiss = { showQuickEntry = false }
                        )
                    }
                    val notchRadius = 52.dp
                    val barHeight = 76.dp
                    val notchDip = 40.dp
                    Scaffold(
                        floatingActionButton = {
                            if (showBottomBar) {
                                FloatingActionButton(
                                    onClick = { showQuickEntry = true },
                                    modifier = Modifier.size(60.dp).offset(y = 90.dp),
                                    containerColor = DosiusPurple,
                                    contentColor = Color.White,
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Entry",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        },
                        floatingActionButtonPosition = FabPosition.Center,
                        topBar = {
                            val isDataScreen = currentRoute == Screen.Data.route
                            Surface(
                                shadowElevation = if (isDataScreen) 0.dp else 4.dp,
                                color = Color.White
                            ) {
                                TopAppBar(
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.White,
                                        scrolledContainerColor = Color.White,
                                    ),
                                    navigationIcon = {
                                        if (isOnSubScreen) {
                                            IconButton(onClick = { navController.popBackStack() }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Back"
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { scope.launch { drawerState.open() } }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Menu,
                                                    contentDescription = "Open menu"
                                                )
                                            }
                                        }
                                    },
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Image(
                                                painter = painterResource(id = R.drawable.logo_dosius),
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Dosius Smart",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 1.sp
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        },
                        bottomBar = {
                            if (showBottomBar) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawBehind {
                                            drawNotchedNavBar(
                                                notchRadiusDp = notchRadius,
                                                notchDipDp = notchDip,
                                                color = Color.White,
                                            )
                                        }
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .height(barHeight + notchDip)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(barHeight)
                                            .align(Alignment.BottomCenter),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        (leftNavItems + listOf(null) + rightNavItems).forEach { item ->
                                            if (item == null) {
                                                Spacer(modifier = Modifier.width(72.dp))
                                            } else {
                                                val selected = currentRoute == item.screen.route
                                                Column(
                                                    modifier = Modifier
                                                        .clickable {
                                                            navController.navigate(item.screen.navRoute) {
                                                                popUpTo(Screen.Dashboard.route) { saveState = true }
                                                                launchSingleTop = true
                                                                restoreState = true
                                                            }
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .then(
                                                                if (selected) Modifier.background(
                                                                    DosiusPurple.copy(alpha = 0.12f),
                                                                    RoundedCornerShape(50)
                                                                ) else Modifier
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                            contentDescription = item.label,
                                                            tint = if (selected) DosiusPurple else Color.Gray,
                                                            modifier = Modifier.size(26.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = item.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (selected) DosiusPurple else Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .padding(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = if (showBottomBar)
                                        (innerPadding.calculateBottomPadding() - notchDip).coerceAtLeast(0.dp)
                                    else
                                        innerPadding.calculateBottomPadding()
                                )
                                .consumeWindowInsets(innerPadding)
                                .fillMaxSize()
                        ) {
                            AppNavHost(navController = navController)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        alarmChecker.cancel()
    }
}

private fun DrawScope.drawNotchedNavBar(
    notchRadiusDp: Dp,
    notchDipDp: Dp,
    color: Color,
) {
    val r = notchRadiusDp.toPx()
    val dipY = notchDipDp.toPx()
    val totalH = size.height
    val cx = size.width / 2f
    // bar top edge is at dipY; notch dips up from there
    val barTop = dipY
    val c = r * 0.6f  // bezier control point offset

    val path = Path().apply {
        moveTo(0f, barTop)
        lineTo(cx - r * 1.4f, barTop)
        cubicTo(
            cx - r * 1.4f + c, barTop,
            cx - r * 0.5f, barTop + dipY,
            cx, barTop + dipY
        )
        cubicTo(
            cx + r * 0.5f, barTop + dipY,
            cx + r * 1.4f - c, barTop,
            cx + r * 1.4f, barTop
        )
        lineTo(size.width, barTop)
        lineTo(size.width, totalH)
        lineTo(0f, totalH)
        close()
    }
    val shadowPath = Path().apply {
        moveTo(0f, barTop - 1f)
        lineTo(cx - r * 1.4f, barTop - 1f)
        cubicTo(
            cx - r * 1.4f + c, barTop - 1f,
            cx - r * 0.5f, barTop + dipY - 1f,
            cx, barTop + dipY - 1f
        )
        cubicTo(
            cx + r * 0.5f, barTop + dipY - 1f,
            cx + r * 1.4f - c, barTop - 1f,
            cx + r * 1.4f, barTop - 1f
        )
        lineTo(size.width, barTop - 1f)
        lineTo(size.width, totalH)
        lineTo(0f, totalH)
        close()
    }
    drawPath(shadowPath, color = Color.Black.copy(alpha = 0.08f))
    drawPath(path, color)
}
