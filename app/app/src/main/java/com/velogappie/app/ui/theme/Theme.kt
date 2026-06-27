package com.velogappie.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velogappie.app.R

enum class AppTheme { LIGHT, DARK }
enum class AppAccent { CORAL, SAGE, RUBY, DUNE }

val HankenGrotesk = FontFamily(
    Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
    Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(R.font.hanken_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.hanken_grotesk_bold, FontWeight.Bold),
    Font(R.font.hanken_grotesk_extrabold, FontWeight.ExtraBold),
)

@Immutable
data class VeloColors(
    val surface2: Color,
    val navBg: Color,
    val textDim: Color,
    val textDim2: Color,
    val textFaint: Color,
    val iconInactive: Color,
    val hairline: Color,
    val connectedDot: Color,
    val connectedLabel: Color,
    val connectedBg: Color,
    val warning: Color,
    val destructiveText: Color,
    val destructiveBg: Color,
    val destructiveBorder: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentWeak: Color,
    val textOnAccent: Color,
)

val LocalVeloColors = staticCompositionLocalOf {
    VeloColors(
        surface2 = Color.Unspecified,
        navBg = Color.Unspecified,
        textDim = Color.Unspecified,
        textDim2 = Color.Unspecified,
        textFaint = Color.Unspecified,
        iconInactive = Color.Unspecified,
        hairline = Color.Unspecified,
        connectedDot = Color.Unspecified,
        connectedLabel = Color.Unspecified,
        connectedBg = Color.Unspecified,
        warning = Color.Unspecified,
        destructiveText = Color.Unspecified,
        destructiveBg = Color.Unspecified,
        destructiveBorder = Color.Unspecified,
        accent = Color.Unspecified,
        accentPressed = Color.Unspecified,
        accentWeak = Color.Unspecified,
        textOnAccent = Color.Unspecified,
    )
}

private data class Tokens(
    val bg: Color, val surface: Color, val surface2: Color,
    val text: Color, val textDim: Color, val textDim2: Color, val textFaint: Color,
    val iconInactive: Color, val navBg: Color,
    val hairline: Color, val hairlineStrong: Color,
    val connectedDot: Color, val connectedLabel: Color, val connectedBg: Color,
    val destructiveText: Color, val destructiveBg: Color, val destructiveBorder: Color,
    val warning: Color,
)

private val darkTokens = Tokens(
    bg = Color(0xFF151717),
    surface = Color(0xFF1F2120),
    surface2 = Color(0xFF232524),
    text = Color(0xFFE6E7E4),
    textDim = Color(0xFFA6A8A4),
    textDim2 = Color(0xFF878A86),
    textFaint = Color(0xFF6F726E),
    iconInactive = Color(0xFF7D807C),
    navBg = Color(0xFF111313),
    hairline = Color(0x0DE2E4E0),
    hairlineStrong = Color(0x1FE2E4E0),
    connectedDot = Color(0xFF7BAE76),
    connectedLabel = Color(0xFF9FC79A),
    connectedBg = Color(0x217BAE76),
    destructiveText = Color(0xFFD26456),
    destructiveBg = Color(0x1FC13E2E),
    destructiveBorder = Color(0x40C13E2E),
    warning = Color(0xFFD29A56),
)

private val lightTokens = Tokens(
    bg = Color(0xFFE6E7E4),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFFFFFFF),
    text = Color(0xFF202221),
    textDim = Color(0xFF6F726E),
    textDim2 = Color(0xFF888B87),
    textFaint = Color(0xFF888B87),
    iconInactive = Color(0xFF989B96),
    navBg = Color(0xFFF1F0EC),
    hairline = Color(0x0D1F211F),
    hairlineStrong = Color(0x1A1F211F),
    connectedDot = Color(0xFF5E7E5C),
    connectedLabel = Color(0xFF4D6A4B),
    connectedBg = Color(0x215E7E5C),
    destructiveText = Color(0xFFD26456),
    destructiveBg = Color(0x1FC13E2E),
    destructiveBorder = Color(0x40C13E2E),
    warning = Color(0xFFD29A56),
)

private data class AccentTokens(
    val accent: Color, val pressed: Color, val weak: Color, val on: Color,
)

private val accentTable = mapOf(
    AppAccent.CORAL to mapOf(
        AppTheme.DARK to AccentTokens(Color(0xFFF0795A), Color(0xFFD9633F), Color(0x29F0795A), Color(0xFF141513)),
        AppTheme.LIGHT to AccentTokens(Color(0xFFE0613F), Color(0xFFC74E2D), Color(0x29E0613F), Color(0xFFFFFFFF)),
    ),
    AppAccent.SAGE to mapOf(
        AppTheme.DARK to AccentTokens(Color(0xFF8FAE8C), Color(0xFF6F8E6B), Color(0x298FAE8C), Color(0xFF141513)),
        AppTheme.LIGHT to AccentTokens(Color(0xFF5E7E5C), Color(0xFF4B6B49), Color(0x295E7E5C), Color(0xFFFFFFFF)),
    ),
    AppAccent.RUBY to mapOf(
        AppTheme.DARK to AccentTokens(Color(0xFFC24A54), Color(0xFFA23C46), Color(0x29C24A54), Color(0xFFFFFFFF)),
        AppTheme.LIGHT to AccentTokens(Color(0xFFA23C46), Color(0xFF8C3039), Color(0x29A23C46), Color(0xFFFFFFFF)),
    ),
    AppAccent.DUNE to mapOf(
        AppTheme.DARK to AccentTokens(Color(0xFFC7BDAD), Color(0xFFAEA493), Color(0x29C7BDAD), Color(0xFF141513)),
        AppTheme.LIGHT to AccentTokens(Color(0xFFAEA493), Color(0xFF968C7B), Color(0x29AEA493), Color(0xFF141513)),
    ),
)

@Composable
fun VeloGappieTheme(theme: AppTheme, accent: AppAccent, content: @Composable () -> Unit) {
    val t = if (theme == AppTheme.DARK) darkTokens else lightTokens
    val a = accentTable.getValue(accent).getValue(theme)

    val colorScheme = (if (theme == AppTheme.DARK) darkColorScheme() else lightColorScheme()).copy(
        background = t.bg,
        onBackground = t.text,
        surface = t.surface,
        onSurface = t.text,
        surfaceVariant = t.surface2,
        onSurfaceVariant = t.textDim,
        surfaceContainer = t.navBg,
        outline = t.textDim2,
        outlineVariant = t.hairline,
        error = t.destructiveText,
        onError = Color.White,
        primary = a.accent,
        onPrimary = a.on,
        primaryContainer = a.weak,
        onPrimaryContainer = a.accent,
        secondary = a.accent,
        onSecondary = a.on,
        secondaryContainer = a.weak,
        onSecondaryContainer = a.accent,
    )

    val veloColors = VeloColors(
        surface2 = t.surface2,
        navBg = t.navBg,
        textDim = t.textDim,
        textDim2 = t.textDim2,
        textFaint = t.textFaint,
        iconInactive = t.iconInactive,
        hairline = t.hairline,
        connectedDot = t.connectedDot,
        connectedLabel = t.connectedLabel,
        connectedBg = t.connectedBg,
        warning = t.warning,
        destructiveText = t.destructiveText,
        destructiveBg = t.destructiveBg,
        destructiveBorder = t.destructiveBorder,
        accent = a.accent,
        accentPressed = a.pressed,
        accentWeak = a.weak,
        textOnAccent = a.on,
    )

    val typography = Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.withFont(FontWeight.ExtraBold),
            displayMedium = base.displayMedium.withFont(FontWeight.ExtraBold),
            displaySmall = base.displaySmall.withFont(FontWeight.ExtraBold),
            headlineLarge = base.headlineLarge.withFont(FontWeight.ExtraBold),
            headlineMedium = base.headlineMedium.withFont(FontWeight.ExtraBold),
            headlineSmall = base.headlineSmall.withFont(FontWeight.ExtraBold),
            titleLarge = base.titleLarge.withFont(FontWeight.Bold),
            titleMedium = base.titleMedium.withFont(FontWeight.Bold),
            titleSmall = base.titleSmall.withFont(FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.withFont(FontWeight.Medium),
            bodyMedium = base.bodyMedium.withFont(FontWeight.Medium),
            bodySmall = base.bodySmall.withFont(FontWeight.Medium),
            labelLarge = base.labelLarge.withFont(FontWeight.SemiBold),
            labelMedium = base.labelMedium.withFont(FontWeight.SemiBold),
            labelSmall = base.labelSmall.withFont(FontWeight.SemiBold),
        )
    }

    CompositionLocalProvider(LocalVeloColors provides veloColors) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

private fun TextStyle.withFont(weight: FontWeight): TextStyle =
    copy(fontFamily = HankenGrotesk, fontWeight = weight, letterSpacing = (-0.2).sp)

object AppShapes {
    val Card = RoundedCornerShape(22.dp)
    val HeroCard = RoundedCornerShape(26.dp)
    val Tile = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(18.dp)
    val NavPill = RoundedCornerShape(13.dp)
}
