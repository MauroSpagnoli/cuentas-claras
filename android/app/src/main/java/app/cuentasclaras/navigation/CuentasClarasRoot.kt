package app.cuentasclaras.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuentasclaras.R
import app.cuentasclaras.feature.auth.AuthNavHost
import app.cuentasclaras.home.HomeScreen
import app.cuentasclaras.session.SessionState
import app.cuentasclaras.session.SessionViewModel

/**
 * Navegación raíz: conmuta entre el área autenticada y el flujo de auth según
 * la sesión persistida. Al cambiar la sesión se descarta el grafo anterior, de
 * modo que no queda back-stack obsoleto.
 */
@Composable
fun CuentasClarasRoot(
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Loading -> SplashScreen()
        SessionState.LoggedOut -> AuthNavHost()
        is SessionState.LoggedIn -> HomeScreen(
            user = state.session.user,
            onSignOut = sessionViewModel::signOut,
        )
    }
}

@Composable
private fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            CircularProgressIndicator()
        }
    }
}
