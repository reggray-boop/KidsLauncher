package com.kidslauncher.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/** A ready-made gradient background. */
data class WallpaperPreset(val key: String, val name: String, val colors: List<String>) {
    fun drawable(cornerRadiusPx: Float = 0f): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors.map(Color::parseColor).toIntArray()).apply {
            cornerRadius = cornerRadiusPx
        }
}

object Wallpapers {

    val presets = listOf(
        WallpaperPreset("meadow", "Meadow", listOf("#4FC3F7", "#81C784", "#FFD54F")),
        WallpaperPreset("sky", "Sky Blue", listOf("#81D4FA", "#4FC3F7", "#1E88E5")),
        WallpaperPreset("ocean", "Ocean", listOf("#00E5FF", "#0091EA", "#1A237E")),
        WallpaperPreset("bubblegum", "Bubblegum", listOf("#FFC1E3", "#FF8AC4", "#F06292")),
        WallpaperPreset("princess", "Princess", listOf("#FBC2EB", "#E1A6F5", "#A18CD1")),
        WallpaperPreset("unicorn", "Unicorn", listOf("#FFB3D9", "#C9A7FF", "#9ED8FF")),
        WallpaperPreset("lavender", "Lavender", listOf("#E0C3FC", "#B39DDB", "#7E57C2")),
        WallpaperPreset("jungle", "Jungle", listOf("#C5E1A5", "#66BB6A", "#1B5E20")),
        WallpaperPreset("dino", "Dino", listOf("#D4FC79", "#96E6A1", "#26A69A")),
        WallpaperPreset("sunshine", "Sunshine", listOf("#FFF59D", "#FFCA28", "#FF9800")),
        WallpaperPreset("firetruck", "Fire Truck", listOf("#FFAB40", "#FF5252", "#C62828")),
        WallpaperPreset("peach", "Candy", listOf("#FFDEE9", "#FFC3A0", "#B5FFFC")),
        WallpaperPreset("rainbow", "Rainbow", listOf("#FF8A80", "#FFD180", "#FFFF8D", "#B9F6CA", "#80D8FF", "#EA80FC")),
        WallpaperPreset("space", "Space", listOf("#0F2027", "#203A43", "#3949AB")),
    )

    fun preset(key: String?): WallpaperPreset = presets.firstOrNull { it.key == key } ?: presets.first()

    /** The parent's own photo if there is one, otherwise the chosen gradient. */
    fun current(ctx: Context): Drawable {
        val bmp = WallpaperStore.load(ctx)
        return if (bmp != null) BitmapDrawable(ctx.resources, bmp) else preset(Prefs(ctx).wallpaperPreset).drawable()
    }
}
