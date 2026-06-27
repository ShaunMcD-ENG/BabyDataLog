package com.babydatalog.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babydatalog.app.ui.components.AddEditBabyDialog
import com.babydatalog.app.ui.screens.feeding.FeedingFormScreen
import com.babydatalog.app.ui.screens.feeding.FeedingListScreen
import com.babydatalog.app.ui.screens.growth.GrowthFormScreen
import com.babydatalog.app.ui.screens.growth.GrowthListScreen
import com.babydatalog.app.ui.screens.home.HomeScreen
import com.babydatalog.app.ui.screens.milestone.MilestoneFormScreen
import com.babydatalog.app.ui.screens.milestone.MilestoneListScreen
import com.babydatalog.app.ui.screens.nappy.NappyFormScreen
import com.babydatalog.app.ui.screens.nappy.NappyListScreen
import com.babydatalog.app.ui.screens.settings.SettingsScreen
import com.babydatalog.app.ui.screens.summary.SummaryScreen
import com.babydatalog.app.ui.screens.sync.SyncScreen
import com.babydatalog.app.viewmodel.BabyViewModel
import com.babydatalog.app.viewmodel.FeedingViewModel
import com.babydatalog.app.viewmodel.GrowthViewModel
import com.babydatalog.app.viewmodel.MilestoneViewModel
import com.babydatalog.app.viewmodel.NappyViewModel
import com.babydatalog.app.viewmodel.SummaryViewModel

object Routes {
    const val HOME = "home"
    const val FEEDING_LIST = "feeding_list"
    const val FEEDING_ADD = "feeding_add"
    const val FEEDING_EDIT = "feeding_edit/{feedingId}"
    const val NAPPY_LIST = "nappy_list"
    const val NAPPY_ADD = "nappy_add"
    const val NAPPY_EDIT = "nappy_edit/{nappyId}"
    const val MILESTONE_LIST = "milestone_list"
    const val MILESTONE_ADD = "milestone_add"
    const val MILESTONE_EDIT = "milestone_edit/{milestoneId}"
    const val GROWTH_LIST = "growth_list"
    const val GROWTH_ADD = "growth_add"
    const val GROWTH_EDIT = "growth_edit/{measurementId}"
    const val SUMMARY = "summary"
    const val SETTINGS = "settings"
    const val SYNC = "sync"

    fun feedingEdit(feedingId: Long) = "feeding_edit/$feedingId"
    fun nappyEdit(nappyId: Long) = "nappy_edit/$nappyId"
    fun milestoneEdit(milestoneId: Long) = "milestone_edit/$milestoneId"
    fun growthEdit(id: Long) = "growth_edit/$id"
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val babyUiState by babyViewModel.uiState.collectAsStateWithLifecycle()

    val bottomNavItems = listOf(
        BottomNavItem(
            route = Routes.HOME,
            label = "Home",
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") }
        ),
        BottomNavItem(
            route = Routes.FEEDING_LIST,
            label = "Feedings",
            icon = { Icon(Icons.Filled.ChildCare, contentDescription = "Feedings") }
        ),
        BottomNavItem(
            route = Routes.NAPPY_LIST,
            label = "Nappies",
            icon = { Icon(Icons.Filled.BabyChangingStation, contentDescription = "Nappies") }
        ),
        BottomNavItem(
            route = Routes.MILESTONE_LIST,
            label = "Milestones",
            icon = { Icon(Icons.Filled.Star, contentDescription = "Milestones") }
        ),
        BottomNavItem(
            route = Routes.GROWTH_LIST,
            label = "Growth",
            icon = { Icon(Icons.Filled.MonitorWeight, contentDescription = "Growth") }
        ),
        BottomNavItem(
            route = Routes.SUMMARY,
            label = "Summary",
            icon = { Icon(Icons.Filled.BarChart, contentDescription = "Summary") }
        ),
        BottomNavItem(
            route = Routes.SYNC,
            label = "Sync",
            icon = { Icon(Icons.Filled.Sync, contentDescription = "Sync") }
        )
    )

    // Routes where the bottom bar should be visible
    val bottomBarRoutes = setOf(
        Routes.HOME,
        Routes.FEEDING_LIST,
        Routes.NAPPY_LIST,
        Routes.MILESTONE_LIST,
        Routes.GROWTH_LIST,
        Routes.SUMMARY,
        Routes.SETTINGS,
        Routes.SYNC
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = item.icon,
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToAddFeeding = { navController.navigate(Routes.FEEDING_ADD) },
                    onNavigateToAddNappy = { navController.navigate(Routes.NAPPY_ADD) },
                    onNavigateToAddMilestone = { navController.navigate(Routes.MILESTONE_ADD) },
                    onNavigateToAddGrowth = { navController.navigate(Routes.GROWTH_ADD) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    babies = babyUiState.babies,
                    selectedBaby = babyUiState.selectedBaby,
                    onSelectBaby = babyViewModel::selectBaby,
                    onAddBaby = babyViewModel::startAddBaby,
                    onEditBaby = babyViewModel::startEditBaby
                )
            }
            composable(Routes.FEEDING_LIST) {
                val vm: FeedingViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                FeedingListScreen(
                    onNavigateToAdd = { navController.navigate(Routes.FEEDING_ADD) },
                    onNavigateToEdit = { id -> navController.navigate(Routes.feedingEdit(id)) }
                )
            }
            composable(Routes.FEEDING_ADD) {
                val vm: FeedingViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                FeedingFormScreen(
                    feedingId = 0L,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.FEEDING_EDIT,
                arguments = listOf(navArgument("feedingId") { type = NavType.LongType })
            ) { backStackEntry ->
                val feedingId = backStackEntry.arguments?.getLong("feedingId") ?: 0L
                FeedingFormScreen(
                    feedingId = feedingId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.NAPPY_LIST) {
                val vm: NappyViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                NappyListScreen(
                    onNavigateToAdd = { navController.navigate(Routes.NAPPY_ADD) },
                    onNavigateToEdit = { id -> navController.navigate(Routes.nappyEdit(id)) }
                )
            }
            composable(Routes.NAPPY_ADD) {
                val vm: NappyViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                NappyFormScreen(
                    nappyId = 0L,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.NAPPY_EDIT,
                arguments = listOf(navArgument("nappyId") { type = NavType.LongType })
            ) { backStackEntry ->
                val nappyId = backStackEntry.arguments?.getLong("nappyId") ?: 0L
                NappyFormScreen(
                    nappyId = nappyId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.MILESTONE_LIST) {
                val vm: MilestoneViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                MilestoneListScreen(
                    onNavigateToAdd = { navController.navigate(Routes.MILESTONE_ADD) },
                    onNavigateToEdit = { id -> navController.navigate(Routes.milestoneEdit(id)) }
                )
            }
            composable(Routes.MILESTONE_ADD) {
                val vm: MilestoneViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                MilestoneFormScreen(
                    milestoneId = 0L,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.MILESTONE_EDIT,
                arguments = listOf(navArgument("milestoneId") { type = NavType.LongType })
            ) { backStackEntry ->
                val milestoneId = backStackEntry.arguments?.getLong("milestoneId") ?: 0L
                MilestoneFormScreen(
                    milestoneId = milestoneId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.GROWTH_LIST) {
                val vm: GrowthViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                GrowthListScreen(
                    onNavigateToAdd = { navController.navigate(Routes.GROWTH_ADD) },
                    onNavigateToEdit = { id -> navController.navigate(Routes.growthEdit(id)) }
                )
            }
            composable(Routes.GROWTH_ADD) {
                val vm: GrowthViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                GrowthFormScreen(
                    measurementId = 0L,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.GROWTH_EDIT,
                arguments = listOf(navArgument("measurementId") { type = NavType.LongType })
            ) { backStackEntry ->
                val measurementId = backStackEntry.arguments?.getLong("measurementId") ?: 0L
                GrowthFormScreen(
                    measurementId = measurementId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SUMMARY) {
                val vm: SummaryViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                SummaryScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.SYNC) {
                SyncScreen()
            }
        }

        // Baby add/edit dialog — rendered outside NavHost so it overlays any screen
        if (babyUiState.isAddingBaby || babyUiState.isEditingBaby) {
            AddEditBabyDialog(
                isEditing = babyUiState.isEditingBaby,
                name = babyUiState.newBabyName,
                onNameChange = babyViewModel::updateNewBabyName,
                birthDateMs = babyUiState.newBabyBirthDateMs,
                onBirthDateChange = babyViewModel::updateNewBabyBirthDate,
                birthWeightGrams = babyUiState.newBabyBirthWeightGrams,
                onBirthWeightChange = babyViewModel::updateNewBabyBirthWeight,
                onSave = babyViewModel::saveBaby,
                onDismiss = babyViewModel::cancelDialog,
                onDelete = if (babyUiState.isEditingBaby) {
                    { babyUiState.editBaby?.let { babyViewModel.deleteBaby(it) } }
                } else null
            )
        }
    }
}
