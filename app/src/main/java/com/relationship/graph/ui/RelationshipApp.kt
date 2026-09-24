package com.relationship.graph.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.relationship.graph.RelationshipApplication
import com.relationship.graph.ui.screens.BackupScreen
import com.relationship.graph.ui.screens.GraphScreen
import com.relationship.graph.ui.screens.KinshipQueryScreen
import com.relationship.graph.ui.screens.PeopleScreen
import com.relationship.graph.ui.screens.PersonDetailScreen
import com.relationship.graph.ui.screens.PersonEditorScreen
import com.relationship.graph.ui.screens.RelationEditorScreen
import com.relationship.graph.ui.screens.SettingsScreen
import com.relationship.graph.ui.security.LoadingScreen
import com.relationship.graph.ui.security.PinSetupScreen
import com.relationship.graph.ui.security.PinUnlockScreen

private object Routes {
    const val Graph = "graph"
    const val People = "people"
    const val Kinship = "kinship"
    const val Settings = "settings"
    const val Backup = "backup"
    const val PersonDetail = "person/{personId}"
    const val PersonEditor = "person-editor?personId={personId}"
    const val RelationEditor = "relation-editor?relationshipId={relationshipId}&personId={personId}"

    fun personDetail(personId: String) = "person/$personId"
    fun personEditor(personId: String? = null) =
        "person-editor?personId=${personId.orEmpty()}"

    fun relationEditor(relationshipId: String? = null, personId: String? = null) =
        "relation-editor?relationshipId=${relationshipId.orEmpty()}&personId=${personId.orEmpty()}"
}

@Composable
fun RelationshipApp(
    viewModel: RelationshipViewModel = viewModel(),
) {
    val app = LocalContext.current.applicationContext as RelationshipApplication
    val lockState by app.appLockController.state.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (lockState.phase) {
        com.relationship.graph.security.LockPhase.LOADING -> LoadingScreen()
        com.relationship.graph.security.LockPhase.SETUP -> PinSetupScreen(
            biometricAvailable = lockState.biometricAvailable,
            onSetup = app.appLockController::setupPin,
        )
        com.relationship.graph.security.LockPhase.LOCKED -> PinUnlockScreen(
            state = lockState,
            onUnlock = app.appLockController::unlock,
            onBiometricUnlock = app.appLockController::unlockWithBiometric,
            onClearError = app.appLockController::clearError,
        )
        com.relationship.graph.security.LockPhase.UNLOCKED -> MainNavigation(
            viewModel = viewModel,
            uiState = uiState,
            onLock = app.appLockController::lockNow,
        )
    }
}

@Composable
private fun MainNavigation(
    viewModel: RelationshipViewModel,
    uiState: AppUiState,
    onLock: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val topLevelRoutes = setOf(Routes.Graph, Routes.People, Routes.Kinship, Routes.Settings)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isLandscape && currentRoute in topLevelRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.Graph,
                        onClick = { navController.navigateTopLevel(Routes.Graph) },
                        icon = { Icon(Icons.Rounded.AccountTree, contentDescription = null) },
                        label = { Text("图谱") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.People,
                        onClick = { navController.navigateTopLevel(Routes.People) },
                        icon = { Icon(Icons.Rounded.People, contentDescription = null) },
                        label = { Text("人物") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.Kinship,
                        onClick = { navController.navigateTopLevel(Routes.Kinship) },
                        icon = { Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null) },
                        label = { Text("称谓") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.Settings,
                        onClick = { navController.navigateTopLevel(Routes.Settings) },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
        },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isLandscape && currentRoute in topLevelRoutes) {
                NavigationRail {
                    NavigationRailItem(
                        selected = currentRoute == Routes.Graph,
                        onClick = { navController.navigateTopLevel(Routes.Graph) },
                        icon = { Icon(Icons.Rounded.AccountTree, contentDescription = null) },
                        label = { Text("图谱") },
                    )
                    NavigationRailItem(
                        selected = currentRoute == Routes.People,
                        onClick = { navController.navigateTopLevel(Routes.People) },
                        icon = { Icon(Icons.Rounded.People, contentDescription = null) },
                        label = { Text("人物") },
                    )
                    NavigationRailItem(
                        selected = currentRoute == Routes.Kinship,
                        onClick = { navController.navigateTopLevel(Routes.Kinship) },
                        icon = { Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null) },
                        label = { Text("称谓") },
                    )
                    NavigationRailItem(
                        selected = currentRoute == Routes.Settings,
                        onClick = { navController.navigateTopLevel(Routes.Settings) },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
            NavHost(
                navController = navController,
                startDestination = Routes.Graph,
                modifier = Modifier.weight(1f),
            ) {
            composable(Routes.Graph) {
                GraphScreen(
                    state = uiState,
                    viewModel = viewModel,
                    onPersonClick = { navController.navigate(Routes.personDetail(it)) },
                    onAddPerson = { navController.navigate(Routes.personEditor()) },
                    onAddRelationship = { navController.navigate(Routes.relationEditor()) },
                    onEditRelationship = { relationshipId, _ ->
                        navController.navigate(Routes.relationEditor(relationshipId = relationshipId))
                    },
                )
            }
            composable(Routes.People) {
                PeopleScreen(
                    state = uiState,
                    viewModel = viewModel,
                    onPersonClick = { navController.navigate(Routes.personDetail(it)) },
                    onAddPerson = { navController.navigate(Routes.personEditor()) },
                )
            }
            composable(Routes.Kinship) {
                KinshipQueryScreen(state = uiState)
            }
            composable(
                route = Routes.PersonDetail,
                arguments = listOf(navArgument("personId") { type = NavType.StringType }),
            ) { entry ->
                val personId = entry.arguments?.getString("personId").orEmpty()
                PersonDetailScreen(
                    state = uiState,
                    personId = personId,
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(Routes.personEditor(personId)) },
                    onAddRelationship = {
                        navController.navigate(Routes.relationEditor(personId = personId))
                    },
                    onEditRelationship = { relationshipId ->
                        navController.navigate(Routes.relationEditor(relationshipId = relationshipId))
                    },
                    onConfirmInference = viewModel::confirmInference,
                    onDismissInference = viewModel::dismissInference,
                    onDeletePerson = viewModel::deletePerson,
                    onDeleteRelationship = viewModel::deleteRelationship,
                )
            }
            composable(
                route = Routes.PersonEditor,
                arguments = listOf(
                    navArgument("personId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val personId = entry.arguments?.getString("personId").orEmpty().ifBlank { null }
                PersonEditorScreen(
                    state = uiState,
                    personId = personId,
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                )
            }
            composable(
                route = Routes.RelationEditor,
                arguments = listOf(
                    navArgument("relationshipId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("personId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                RelationEditorScreen(
                    state = uiState,
                    relationshipId = entry.arguments?.getString("relationshipId").orEmpty().ifBlank { null },
                    initialPersonId = entry.arguments?.getString("personId").orEmpty().ifBlank { null },
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    state = uiState,
                    app = LocalContext.current.applicationContext as RelationshipApplication,
                    onOpenBackup = { navController.navigate(Routes.Backup) },
                    onSetMyPerson = viewModel::setMyPerson,
                    onLock = onLock,
                )
            }
            composable(Routes.Backup) {
                BackupScreen(
                    state = uiState,
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                )
            }
        }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
