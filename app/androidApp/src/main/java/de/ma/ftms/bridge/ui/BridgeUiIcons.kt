package de.ma.ftms.bridge.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few symbols the screens use, drawn here so the app needs no icon library. Each is a single
 * path on a 24 by 24 grid and takes its colour from where it is shown.
 */
internal object BridgeUiIcons {
    val ArrowBack: ImageVector = icon("BridgeArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")

    val MoreVertical: ImageVector = icon(
        "BridgeMoreVertical",
        "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z" +
            "M12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z" +
            "M12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
    )

    val Close: ImageVector = icon(
        "BridgeClose",
        "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
    )

    val Add: ImageVector = icon("BridgeAdd", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")

    val Remove: ImageVector = icon("BridgeRemove", "M19,13H5v-2h14v2z")

    val ExpandMore: ImageVector = icon("BridgeExpandMore", "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z")

    val ExpandLess: ImageVector = icon("BridgeExpandLess", "M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z")

    val ChevronRight: ImageVector = icon("BridgeChevronRight", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")

    val ArrowUp: ImageVector = icon("BridgeArrowUp", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z")

    val ArrowDown: ImageVector = icon("BridgeArrowDown", "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z")

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
