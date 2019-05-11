package net.spirangle.mapgen.util;

import net.spirangle.mapgen.MainWindow;

/**
 * Contains all default settings for a new map generation.
 */
public class Constants {

	public static final String VERSION                   = "1.0.0";
	public static final String WINDOW_TITLE              = "Map Generator for Wurm Unlimited";

	public static final int GRID_SIZE                    = 8;

	public static final String MAP_NAME                  = "";
	public static final int MAP_SIZE                     = 2048;
	public static final int MAP_HEIGHT                   = 4096;
	public static final double MAP_RESOLUTION            = (double)MAP_SIZE / 8.0;
	public static final double LANDSCAPE_RESOLUTION      = MAP_RESOLUTION;

	public static final int HEIGHTMAP_ITERATIONS         = 10;
	public static final int HEIGHTMAP_MIN_EDGE           = 64;
	public static final int HEIGHTMAP_BORDER_WEIGHT      = 6;
	public static final int HEIGHTMAP_COMPRESSION        = 25;
	public static final int HEIGHTMAP_PEAKS              = 20;

	public static final int EROSION_ITERATIONS           = 30;
	public static final int EROSION_MIN_SLOPE            = 30;
	public static final int EROSION_MAX_SLOPE            = 300;
	public static final int EROSION_MAX_SEDIMENT         = 100;
	public static final int EROSION_FRACTURE             = 20;

	public static final int DIRT_DROP_COUNT              = 60;
	public static final int MAX_DIRT_SLOPE               = 50;
	public static final int MAX_DIRT_DIAG_SLOPE          = 70;
	public static final int WATER_HEIGHT                 = 500;
	public static final double CLIFF_RATIO               = 2.5;
	public static final int DIRT_HEIGHT                  = 3300;
	public static final int SNOW_HEIGHT                  = 3900;
	public static final int TUNDRA_HEIGHT                = 3000;
	public static final boolean MORE_LAND                = true;
	public static final boolean LAND_SLIDE               = false;

	public static final long BIOME_SEED                  = 0;
	public static final double BIOME_RESOLUTION          = MAP_RESOLUTION;
	public static final float BIOME_DESERT_TEMP          = 19.0f;
	public static final float BIOME_STEPPE_TEMP          = 17.0f;
	public static final float BIOME_TUNDRA_TEMP          = 1.8f;
	public static final float BIOME_GLACIER_TEMP         = -1.0f;
	public static final float BIOME_SUBTROPICAL_TEMP     = 12.0f;
	public static final float BIOME_MEDITERRAN_TEMP      = 8.0f;
	public static final float BIOME_TEMPERATE_TEMP       = 5.0f;
	public static final int BIOME_PLANT_DENSITY          = 40;
	public static final int BIOME_FLOWER_DENSITY         = 30;
	public static final int BIOME_BEACHES                = 50;
	public static final int BIOME_CLAY_PITS              = 50;

	public static final double ORE_IRON                  = 1.0;
	public static final double ORE_GOLD                  = 0.05;
	public static final double ORE_SILVER                = 0.1;
	public static final double ORE_ZINC                  = 0.15;
	public static final double ORE_COPPER                = 0.2;
	public static final double ORE_LEAD                  = 0.2;
	public static final double ORE_TIN                   = 0.2;
	public static final double ORE_ADDY                  = 0.0;
	public static final double ORE_GLIMMER               = 0.0;
	public static final double ORE_MARBLE                = 0.2;
	public static final double ORE_SLATE                 = 0.2;
	public static final double ORE_SANDSTONE             = 0.2;
	public static final double ORE_ROCKSALT              = 0.2;

	public static final int BIOME_SEED_LIMIT_MULTIPLIER  = 10;

	public static enum VIEW_TYPE {
		ISO,TOPO,
		CAVE,HEIGHT,
		BIOMES
	};
}
