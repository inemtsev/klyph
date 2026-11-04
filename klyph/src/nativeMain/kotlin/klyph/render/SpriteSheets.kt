package klyph.render

/**
 * Creates a sprite sheet that cuts [texture] into [columns] by [rows] cells of [cellWidth] by
 * [cellHeight] texels, indexed row by row from the top-left cell: index `row * columns + column`.
 *
 * The first cell starts [margin] texels from the texture's left and top edges, and neighboring
 * cells are [spacing] texels apart. The cell size defaults to the largest that fits with the same
 * [margin] on all four edges. A grid with explicit sizes only needs to fit from the left and top
 * margin to the right and bottom edges. Pass [margin] and [spacing] by name.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 *
 * @throws IllegalArgumentException if [columns], [rows], [cellWidth] or [cellHeight] is not
 *   positive, if [margin] or [spacing] is negative, or if the grid does not fit in [texture].
 */
public fun SpriteSheet.Companion.fromGrid(
    texture: Texture,
    columns: Int,
    rows: Int,
    margin: Int = 0,
    spacing: Int = 0,
    cellWidth: Int = largestCell(texture.width, columns, margin, spacing),
    cellHeight: Int = largestCell(texture.height, rows, margin, spacing),
): SpriteSheet {
    require(columns > 0) { "Grid column count $columns is not positive." }
    require(rows > 0) { "Grid row count $rows is not positive." }
    require(margin >= 0) { "Grid margin $margin is less than zero." }
    require(spacing >= 0) { "Grid spacing $spacing is less than zero." }
    require(cellWidth > 0) { "Grid cell width $cellWidth is not positive." }
    require(cellHeight > 0) { "Grid cell height $cellHeight is not positive." }
    val gridWidth = margin.toLong() + columns.toLong() * cellWidth + (columns - 1).toLong() * spacing
    val gridHeight = margin.toLong() + rows.toLong() * cellHeight + (rows - 1).toLong() * spacing
    require(gridWidth <= texture.width && gridHeight <= texture.height) {
        "Grid of $columns x $rows cells of ${cellWidth}x$cellHeight needs ${gridWidth}x$gridHeight texels but $texture is smaller."
    }
    val regions = ArrayList<TextureRegion>(columns * rows)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            regions.add(
                TextureRegion(
                    texture,
                    x = margin + column * (cellWidth + spacing),
                    y = margin + row * (cellHeight + spacing),
                    width = cellWidth,
                    height = cellHeight,
                ),
            )
        }
    }
    return SpriteSheet(texture, regions)
}

/**
 * Returns the region at [index], or `null` if [index] is outside `0 until size`.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 */
public fun SpriteSheet.getOrNull(index: Int): TextureRegion? = regions.getOrNull(index)

/**
 * Returns the largest cell size for [cells] cells across [extent] texels with [margin] on both
 * sides and [spacing] between cells, or 0 when [cells] is not positive so the caller's checks
 * report the bad count instead of a division by zero.
 */
private fun largestCell(extent: Int, cells: Int, margin: Int, spacing: Int): Int =
    if (cells <= 0) 0 else (extent - 2 * margin - (cells - 1) * spacing) / cells
