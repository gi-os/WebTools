package com.gios.webtools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** LightOS renders greyscale on a matte panel, so the palette is luminance only. */
private val MonoDark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color(0xFFBBBBBB),
)

/** Second voice: what a row says about itself. */
val Dim = Color(0xFF9A9A9A)

/** Third voice: a label nobody reads twice, and a row the thumb has not reached. */
val Faint = Color(0xFF5E5E5E)

/** Every rule in the app. One hairline, one grey. */
val RuleGrey = Color(0xFF262626)

/** A block standing in for something: the pull-down's handle, a page's shape while it loads. */
val Ghost = Color(0xFF3A3A3A)

/** The largest such block, where a picture will be. */
val Slab = Color(0xFF1A1A1A)

/**
 * Two typefaces, which is the one rule this app breaks.
 *
 * The content voice is the phone's own (Akkurat, through [akkuratFamilyOrDefault]) — names,
 * sentences, anything a person wrote. Everything *about* the content is monospaced and tracked
 * wide: labels, counts, states, the words on the bar, the numerals down the left of a list. The
 * split is what lets a row carry three facts without three sizes of the same face, and it is why
 * a numeral beside a name reads as an index rather than part of the name.
 */
object Mono {
    private val fam = FontFamily.Monospace

    /** A label: ADDRESS, SAVED, DOWNLOADS. Wide tracking, always upper case at the call site. */
    val label = TextStyle(fontFamily = fam, fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 1.5.sp)

    /** What a row is: an address, a size, a date. Narrower tracking, mixed case. */
    val detail = TextStyle(fontFamily = fam, fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp)

    /** The words on the bottom bar. The widest tracking in the app. */
    val bar = TextStyle(fontFamily = fam, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.9.sp)

    /** An index down the left of a list, or a row's number in the pull-down. */
    val numeral = TextStyle(fontFamily = fam, fontSize = 11.sp, fontWeight = FontWeight.Normal)
}

/** The measurements every screen shares, so a row is the same height wherever it is drawn. */
object Metrics {
    /** Left and right margin, everywhere. */
    val pad = 20.dp

    /** A shelf row: a name over its address, with an index beside it. */
    val row = 58.dp

    /** A row that is one fact and its state: Info, Settings. */
    val fact = 46.dp

    /** The bottom bar. */
    val bar = 56.dp

    /** The index column down the left of the shelf. */
    val index = 18.dp

    /** One row of the pull-down, and the band before the first row is live. */
    val pulleyRow = 44.dp
    val pulleyDead = 40.dp
}

@Composable
fun WebToolsTheme(content: @Composable () -> Unit) {
    val fam = remember { akkuratFamilyOrDefault() }
    val type = Typography(
        displaySmall = TextStyle(fontFamily = fam, fontSize = 32.sp, fontWeight = FontWeight.Light),
        // A screen's own name, once, at the top: Tickets, Settings, Downloads.
        titleLarge = TextStyle(fontFamily = fam, fontSize = 26.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.26).sp),
        // The field at the top of the shelf, and its placeholder.
        titleMedium = TextStyle(fontFamily = fam, fontSize = 20.sp, fontWeight = FontWeight.Normal),
        // A shelf row's name.
        bodyLarge = TextStyle(fontFamily = fam, fontSize = 19.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
        // A fact row's label, and a pull-down row.
        bodyMedium = TextStyle(fontFamily = fam, fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
        // The value beside a label in a grid, and running prose.
        bodySmall = TextStyle(fontFamily = fam, fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
        labelLarge = Mono.bar,
        labelSmall = Mono.label,
    )
    MaterialTheme(colorScheme = MonoDark, typography = type, content = content)
}
