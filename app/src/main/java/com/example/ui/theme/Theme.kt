package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = InkNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5F0),
    onPrimaryContainer = InkNavyDark,
    secondary = BurnishedGold,
    onSecondary = Color.White,
    secondaryContainer = BurnishedGoldContainer,
    onSecondaryContainer = OnBurnishedGoldContainer,
    tertiary = EditorialSage,
    onTertiary = Color.White,
    tertiaryContainer = EditorialSageContainer,
    onTertiaryContainer = OnEditorialSageContainer,
    background = ParchmentBg,
    onBackground = SlateInk,
    surface = ParchmentSurface,
    onSurface = SlateInk,
    surfaceVariant = ParchmentVariant,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLow = Color(0xFFFDFBF7),
    surfaceContainer = ParchmentVariant,
    surfaceContainerHigh = ParchmentContainerHigh,
    outline = HairlineRule,
    outlineVariant = Color(0xFFE7E1D6),
    error = TerracottaError,
    onError = Color.White,
    errorContainer = TerracottaContainer,
    onErrorContainer = OnTerracottaContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = GildedBrass,
    onPrimary = Color(0xFF1F1602),
    primaryContainer = GildedBrassContainer,
    onPrimaryContainer = OnGildedBrassContainer,
    secondary = DarkSage,
    onSecondary = Color(0xFF0A2117),
    secondaryContainer = DarkSageContainer,
    onSecondaryContainer = OnDarkSageContainer,
    tertiary = Color(0xFF9CC0E8),
    onTertiary = Color(0xFF0A2440),
    background = DarkStudyBg,
    onBackground = IvoryInk,
    surface = DarkStudySurface,
    onSurface = IvoryInk,
    surfaceVariant = DarkStudyVariant,
    onSurfaceVariant = WarmAsh,
    surfaceContainerLow = Color(0xFF131D2B),
    surfaceContainer = DarkStudyVariant,
    surfaceContainerHigh = DarkStudyContainerHigh,
    outline = DarkHairlineRule,
    outlineVariant = Color(0xFF233245),
    error = Color(0xFFEF8B82),
    onError = Color(0xFF4F130D),
    errorContainer = Color(0xFF6B211A),
    onErrorContainer = Color(0xFFFCE4E1)
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun MeaningMattersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
