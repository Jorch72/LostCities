package mcjty.lostcities.dimensions.world.lost;

import mcjty.lostcities.api.*;
import mcjty.lostcities.dimensions.world.ChunkHeightmap;
import mcjty.lostcities.dimensions.world.LostCitiesTerrainGenerator;
import mcjty.lostcities.dimensions.world.LostCityChunkGenerator;
import mcjty.lostcities.dimensions.world.lost.cityassets.*;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.varia.Counter;
import mcjty.lostcities.varia.QualityRandom;
import net.minecraft.block.Block;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class BuildingInfo implements ILostChunkInfo {
    public final int chunkX;
    public final int chunkZ;
    public final ChunkCoord coord;
    public final LostCityChunkGenerator provider;

    public final boolean isCity;
    public final boolean hasBuilding;
    public final int building2x2Section;    // -1 for not, 0 for top left, 1 for top right, 2 for bottom left, 3 for bottom right

    public final ILostCityMultiBuilding multiBuilding;
    public final ILostCityBuilding buildingType;
    public final BuildingPart fountainType;
    public final BuildingPart parkType;
    public final BuildingPart bridgeType;
    public final BuildingPart stairType;
    public final BuildingPart frontType;
    private final float stairPriority;      // A random number that indicates if this chunk should get a stair if there are competing stairs around it. The highest wins
    public final BuildingPart railDungeon;    // Dungeon next to rails. Will only generate if there are actually rails next to it
    public final StreetType streetType;
    private final int floors;
    public final int floorsBelowGround;
    public final BuildingPart[] floorTypes;
    public final BuildingPart[] floorTypes2;
    public final boolean[] connectionAtX;
    public final boolean[] connectionAtZ;
    public final boolean noLoot;
    public final float ruinHeight;      // The height (as a percentage between 0 and 1) at which we focus the ruin layer. Set to -1 if this building is not ruined

    public final int highwayXLevel;     // 0 or 1 if there is a highway at this chunk
    public final int highwayZLevel;     // 0 or 1 if there is a highway at this chunk
    public final int cityLevel;         // The first floor of buildings starts at groundLevel + cityLevel * 6

    public final boolean xBridge;       // A boolean indicating that this chunk is a candidate for holding a bridge (no guarantee)
    public final boolean zBridge;       // A boolean indicating that this chunk is a candidate for holding a bridge (no guarantee)

    public final boolean xRailCorridor; // A boolean indicating that this chunk is a candidate for holding a corridor (no guarantee)
    public final boolean zRailCorridor; // A boolean indicating that this chunk is a candidate for holding a corridor (no guarantee)

    public final Block doorBlock;

    // Transient info that is calculated on demand
    private BuildingInfo xmin = null;
    private BuildingInfo xmax = null;
    private BuildingInfo zmin = null;
    private BuildingInfo zmax = null;
    private DamageArea damageArea = null;
    private Palette palette = null;
    private CompiledPalette compiledPalette = null;
    private Boolean isOcean = null;

    private boolean xBridgeTypeCalculated = false;
    private boolean zBridgeTypeCalculated = false;
    private BuildingPart xBridgeType = null;
    private BuildingPart zBridgeType = null;

    private boolean stairsCalculated = false;
    private Direction stairDirection;
    private boolean actualStairsCalculated = false;
    private Direction actualStairDirection;

    // A list of todo's for mob spawners and other things
    private final List<Pair<BlockPos, ConditionTodo>> mobSpawnerTodo = new ArrayList<>();
    private final List<Pair<BlockPos, ConditionTodo>> chestTodo = new ArrayList<>();
    private final List<BlockPos> genericTodo = new ArrayList<>();
    private final List<Integer> torchTodo = new ArrayList<>();
    private final List<BlockPos> saplingTodo = new ArrayList<>();

    public static class ConditionTodo {
        private final String condition;
        private final String part;
        private final String building;

        public ConditionTodo(String condition, String part, BuildingInfo info) {
            this.part = part == null ? "<none>" : part;
            this.condition = condition;
            if (info.hasBuilding) {
                this.building = info.getBuildingType();
            } else {
                this.building = "<none>";
            }
        }

        public String getCondition() {
            return condition;
        }

        public String getPart() {
            return part;
        }

        public String getBuilding() {
            return building;
        }
    }

    // BuildingInfo cache
    private static Map<ChunkCoord, BuildingInfo> buildingInfoMap = new HashMap<>();
    private static Map<ChunkCoord, LostChunkCharacteristics> cityInfoMap = new HashMap<>();

    public void addSaplingTodo(BlockPos pos) {
        saplingTodo.add(pos);
    }

    public List<BlockPos> getSaplingTodo() {
        return saplingTodo;
    }

    public void clearSaplingTodo() {
        saplingTodo.clear();
    }

    public void addTorchTodo(int index) {
        torchTodo.add(index);
    }

    public List<Integer> getTorchTodo() {
        return torchTodo;
    }

    public void clearTorchTodo() {
        torchTodo.clear();
    }

    public void addGenericTodo(BlockPos pos) {
        genericTodo.add(pos);
    }

    public List<BlockPos> getGenericTodo() {
        return genericTodo;
    }

    public void clearGenericTodo() {
        genericTodo.clear();
    }

    public void addSpawnerTodo(BlockPos pos, ConditionTodo mobId) {
        mobSpawnerTodo.add(Pair.of(pos, mobId));
    }

    public void addChestTodo(BlockPos pos, @Nullable ConditionTodo lootTable) {
        chestTodo.add(Pair.of(pos, lootTable));
    }

    public List<Pair<BlockPos, ConditionTodo>> getMobSpawnerTodo() {
        return mobSpawnerTodo;
    }

    public List<Pair<BlockPos, ConditionTodo>> getChestTodo() {
        return chestTodo;
    }

    public void clearMobSpawnerTodo() {
        mobSpawnerTodo.clear();
    }

    public void clearChestTodo() {
        chestTodo.clear();
    }

    public CompiledPalette getCompiledPalette() {
        if (compiledPalette == null) {
            compiledPalette = new CompiledPalette(palette);
        }
        return compiledPalette;
    }

    public DamageArea getDamageArea() {
        if (damageArea == null) {
            damageArea = new DamageArea(chunkX, chunkZ, provider);
        }
        return damageArea;
    }

    /**
     * Based on which part of the chunk we have something for we find the correct
     * info object where we have to add the todo.
     */
    public BuildingInfo getTodoChunk(int x, int z) {
        if (x >= 8 && z >= 8) {
            return this;
        } else if (x < 8 && z >= 8) {
            return getXmin();
        } else if (x >= 8 && z < 8) {
            return getZmin();
        } else {
            return getXmin().getZmin();
        }
    }

    public Style getOutsideStyle() {
        return AssetRegistries.STYLES.get(provider.worldStyle.getOutsideStyle());
    }

    private void createPalette(Random rand) {
        Style style;
        if (!isCity) {
            style = getOutsideStyle();
        } else {
            String name = getCityStyle().getStyle();
            style = AssetRegistries.STYLES.get(name);
            if (style == null) {
                throw new RuntimeException("Cannot find style '" + name + "'!");
            }
        }
        palette = style.getRandomPalette(provider, rand);
    }

    // x between 0 and 15, z between 0 and 15
    public BuildingInfo getAdjacent(int x, int z) {
        if (x == 0) {
            return getXmin();
        } else if (x == 15) {
            return getXmax();
        } else if (z == 0) {
            return getZmin();
        } else if (z == 15) {
            return getZmax();
        } else {
            return null;
        }
    }

    public BuildingInfo getXmin() {
        if (xmin == null) {
            xmin = getBuildingInfo(chunkX - 1, chunkZ, provider);
        }
        return xmin;
    }

    public BuildingInfo getXmax() {
        if (xmax == null) {
            xmax = getBuildingInfo(chunkX + 1, chunkZ, provider);
        }
        return xmax;
    }

    public BuildingInfo getZmin() {
        if (zmin == null) {
            zmin = getBuildingInfo(chunkX, chunkZ - 1, provider);
        }
        return zmin;
    }

    public BuildingInfo getZmax() {
        if (zmax == null) {
            zmax = getBuildingInfo(chunkX, chunkZ + 1, provider);
        }
        return zmax;
    }

    public int getMaxHeight() {
        if (hasBuilding) {
            return getCityGroundLevel() + floors * 6;
        } else {
            int m = getMaxHighwayLevel();
            if (m >= 0) {
                return provider.profile.GROUNDLEVEL + m * 6;
            } else {
                return getCityGroundLevel();
            }
        }
    }

    public int getCityGroundLevel() {
        return provider.profile.GROUNDLEVEL + cityLevel * 6;
    }

    /**
     * Get the city ground level but lower the level outside cities
     */
    public int getCityGroundLevelOutsideLower() {
        if (isCity) {
            return provider.profile.GROUNDLEVEL + cityLevel * 6;
        } else {
            return provider.profile.GROUNDLEVEL + cityLevel * 6 -1;
        }
    }

    public boolean isValidFloor(int l) {
        return (l + floorsBelowGround) >= 0 && (l + floorsBelowGround) < floorTypes.length;
    }

    public BuildingPart getFloor(int l) {
        return floorTypes[l + floorsBelowGround];
    }

    public BuildingPart getFloorPart2(int l) {
        return floorTypes2[l + floorsBelowGround];
    }

    public ILostCityBuilding getBuilding() {
        return buildingType;
    }

    public CityStyle getCityStyle() {
        return (CityStyle) getChunkCharacteristics(chunkX, chunkZ, provider).cityStyle;
    }

    public static LostChunkCharacteristics getChunkCharacteristics(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        ChunkCoord key = new ChunkCoord(provider.dimensionId, chunkX, chunkZ);
        if (cityInfoMap.containsKey(key)) {
            return cityInfoMap.get(key);
        } else {
            LostChunkCharacteristics lostChunkCharacteristics = new LostChunkCharacteristics();

            float cityFactor = City.getCityFactor(chunkX, chunkZ, provider);
            lostChunkCharacteristics.isCity = cityFactor > provider.profile.CITY_THRESSHOLD;
            lostChunkCharacteristics.section = getMultiBuildingSection(chunkX, chunkZ, provider);
            if (lostChunkCharacteristics.section > 0) {
                lostChunkCharacteristics.cityLevel = getTopLeftCityInfo(lostChunkCharacteristics, chunkX, chunkZ, provider).cityLevel;
            } else {
                lostChunkCharacteristics.cityLevel = getCityLevel(chunkX, chunkZ, provider);
            }
            Random rand = getBuildingRandom(chunkX, chunkZ, provider.seed);
            lostChunkCharacteristics.couldHaveBuilding = lostChunkCharacteristics.isCity && checkBuildingPossibility(chunkX, chunkZ, provider, lostChunkCharacteristics.section, lostChunkCharacteristics.cityLevel, rand);

            ChunkCoord coord = new ChunkCoord(provider.dimensionId, chunkX, chunkZ);
            CityStyle cityStyle;
            // If this is a street we find other chunks connected to this and pick the cityStyle
            // that represents the majority. This is to prevent streets from switching style randomly if two
            // different styled cities mix
            if (lostChunkCharacteristics.isCity && !lostChunkCharacteristics.couldHaveBuilding) {
                Counter<String> counter = new Counter<>();
                for (int cx = -1 ; cx <= 1 ; cx++) {
                    for (int cz = -1 ; cz <= 1 ; cz++) {
                        cityStyle = City.getCityStyle(coord.getChunkX()+cx, coord.getChunkZ()+cz, provider);
                        counter.add(cityStyle.getName());
                        if (cx == 0 && cz == 0) {
                            counter.add(cityStyle.getName());   // Add this chunk again for a bias
                        }
                    }
                }
                cityStyle = AssetRegistries.CITYSTYLES.get(counter.getMostOccuring());
            } else {
                cityStyle = City.getCityStyle(chunkX, chunkZ, provider);
            }
            lostChunkCharacteristics.cityStyle = cityStyle;


            if (lostChunkCharacteristics.section >= 1) {
                LostChunkCharacteristics topleft = getTopLeftCityInfo(lostChunkCharacteristics, chunkX, chunkZ, provider);
                lostChunkCharacteristics.multiBuilding = topleft.multiBuilding;
                if (lostChunkCharacteristics.multiBuilding != null) {
                    switch (lostChunkCharacteristics.section) {
                        case 1:
                            lostChunkCharacteristics.buildingType = AssetRegistries.BUILDINGS.get(lostChunkCharacteristics.multiBuilding.getBuilding(1, 0));
                            break;
                        case 2:
                            lostChunkCharacteristics.buildingType = AssetRegistries.BUILDINGS.get(lostChunkCharacteristics.multiBuilding.getBuilding(0, 1));
                            break;
                        case 3:
                            lostChunkCharacteristics.buildingType = AssetRegistries.BUILDINGS.get(lostChunkCharacteristics.multiBuilding.getBuilding(1, 1));
                            break;
                        default:
                            throw new RuntimeException("What 2!");
                    }
                } else {
                    lostChunkCharacteristics.buildingType = topleft.buildingType;
                }
            } else {
                PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, provider);
                if (lostChunkCharacteristics.section == 0) {
                    String name = cityStyle.getRandomMultiBuilding(rand);
                    if (predefinedBuilding != null) {
                        name = predefinedBuilding.getBuilding();
                    }
                    lostChunkCharacteristics.multiBuilding = AssetRegistries.MULTI_BUILDINGS.get(name);
                    lostChunkCharacteristics.buildingType = AssetRegistries.BUILDINGS.get(lostChunkCharacteristics.multiBuilding.getBuilding(0, 0));
                } else {
                    lostChunkCharacteristics.multiBuilding = null;
                    String name = cityStyle.getRandomBuilding(rand);
                    if (predefinedBuilding != null) {
                        name = predefinedBuilding.getBuilding();
                    }
                    lostChunkCharacteristics.buildingType = AssetRegistries.BUILDINGS.get(name);
                }
            }

            LostCityEvent.CharacteristicsEvent event = new LostCityEvent.CharacteristicsEvent(provider.worldObj, provider,
                    chunkX, chunkZ, lostChunkCharacteristics);
            MinecraftForge.EVENT_BUS.post(event);

            cityInfoMap.put(key, lostChunkCharacteristics);
            return lostChunkCharacteristics;
        }
    }

    /**
     * Don't use the cache as we're busy building the cache.
     */
    public static boolean isCityRaw(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        float cityFactor = City.getCityFactor(chunkX, chunkZ, provider);
        return cityFactor > provider.profile.CITY_THRESSHOLD;
    }

    public static boolean isCity(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        return getChunkCharacteristics(chunkX, chunkZ, provider).isCity;
    }

    private static boolean checkBuildingPossibility(int chunkX, int chunkZ, LostCityChunkGenerator provider, int section, int cityLevel, Random rand) {
        boolean b;
        float bc = rand.nextFloat();

        PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, provider);
        if (predefinedBuilding != null) {
            return true;    // We don't need other tests
        }
        PredefinedCity.PredefinedStreet predefinedStreet = City.getPredefinedStreet(chunkX, chunkZ, provider);
        if (predefinedStreet != null) {
            return false;   // No building here
        }

        if (section >= 0) {
            // Part of multi-building. We have checked everything above
            b = true;
        } else if (bc >= provider.profile.BUILDING_CHANCE) {
            // Random says we should have no building here
            b = false;
        } else if (hasHighway(chunkX, chunkZ, provider)) {
            // We are above a highway. Check if we have room for a building
            int maxh = Math.max(Highway.getXHighwayLevel(chunkX, chunkZ, provider), Highway.getZHighwayLevel(chunkX, chunkZ, provider));
            b = cityLevel > maxh+1;       // Allow a building if it is higher then the maximum highway + one
            // Later we will take care to make sure we don't have too many cellars
            // Note that for easy of coding we still disallow multi-buildings above highways
        } else if (hasRailway(chunkX, chunkZ, provider)) {
            // We are above a railway. Check if we have room for a building
            Railway.RailChunkInfo info = Railway.getRailChunkType(chunkX, chunkZ, provider);
            if (info.getType() == RailChunkType.STATION_UNDERGROUND) {
                b = false;  // No building directly above the underground station
            } else {
                int maxh = info.getLevel();
                b = cityLevel > maxh + 1;       // Allow a building if it is higher then the maximum railway + one
                // Later we will take care to make sure we don't have too many cellars
                // Note that for easy of coding we still disallow multi-buildings above railways
            }
        } else {
            // General case
            b = true;
        }
        return b;
    }

    private static int getMultiBuildingSection(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        int section;
        if (isTopLeftOf2x2Building(chunkX, chunkZ, provider)) {
            section = 0;
        } else if (isTopLeftOf2x2Building(chunkX - 1, chunkZ, provider)) {
            section = 1;
        } else if (isTopLeftOf2x2Building(chunkX, chunkZ - 1, provider)) {
            section = 2;
        } else if (isTopLeftOf2x2Building(chunkX - 1, chunkZ - 1, provider)) {
            section = 3;
        } else {
            section = -1;
        }
        return section;
    }

    private BuildingInfo calculateTopLeft() {
        switch (building2x2Section) {
            case 0:
                return this;
            case 1:
                return getXmin();
            case 2:
                return getZmin();
            case 3:
                return getXmin().getZmin();
            default:
                throw new RuntimeException("What!");
        }
    }

    private static LostChunkCharacteristics getTopLeftCityInfo(LostChunkCharacteristics thisone, int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        switch (thisone.section) {
            case 0:
                return thisone;
            case 1:
                return getChunkCharacteristics(chunkX-1, chunkZ, provider);
            case 2:
                return getChunkCharacteristics(chunkX, chunkZ-1, provider);
            case 3:
                return getChunkCharacteristics(chunkX-1, chunkZ-1, provider);
            default:
                throw new RuntimeException("What!");
        }
    }

    private static boolean isCandidateForTopLeftOf2x2Building(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, provider);
        if (predefinedBuilding != null && predefinedBuilding.isMulti()) {
            return true;    // We don't need other tests. This is the top-left of a multibuilding
        }
        PredefinedCity.PredefinedStreet predefinedStreet = City.getPredefinedStreet(chunkX, chunkZ, provider);
        if (predefinedStreet != null) {
            return false;   // There is a street here so no building
        }
        if (isMultiBuildingCandidate(chunkX, chunkZ, provider)) {
            Random rand = getBuildingRandom(chunkX, chunkZ, provider.seed);
            return rand.nextFloat() < provider.profile.BUILDING2X2_CHANCE;
        } else {
            return false;
        }
    }

    private static boolean isMultiBuildingCandidate(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        return isCityRaw(chunkX, chunkZ, provider) && !hasHighway(chunkX, chunkZ, provider) && !hasRailway(chunkX, chunkZ, provider);
    }

    private static boolean hasHighway(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        return Highway.getXHighwayLevel(chunkX, chunkZ, provider) >= 0 || Highway.getZHighwayLevel(chunkX, chunkZ, provider) >= 0;
    }

    private static boolean hasRailway(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        return Railway.getRailChunkType(chunkX, chunkZ, provider).getType() != RailChunkType.NONE;
    }

    public Railway.RailChunkInfo getRailInfo() {
        return Railway.getRailChunkType(chunkX, chunkZ, provider);
    }

    // Return true if a highway at this level would be a tunnel
    public boolean isTunnel(int level) {
        if (isCity) {
            // We need a tunnel if the city goes above this level
            return cityLevel > level;
        }

        // Get the (possbily cached) heightmap for this chunk
        ChunkHeightmap heightmap = provider.getHeightmap(chunkX, chunkZ);
        // The height at which the highway would be + a thresshold of 3
        int highwayHeight = provider.profile.GROUNDLEVEL + level * 6 + 3;
        // If there are many places in the chunk above this height we will need a tunnel
        int cnt = 0;
        for (int x = 2 ; x < 16 ; x += 3) {
            for (int z = 2 ; z < 16 ; z += 3) {
                if (heightmap.getHeight(x, z) > highwayHeight) {
                    cnt++;
                }
            }
        }
        return cnt > 12;    // We make a tunnel if more then half of the chunk is above the highway
    }

    private static boolean isTopLeftOf2x2Building(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, provider);
        if (predefinedBuilding != null && predefinedBuilding.isMulti()) {
            // Regardless of other conditions, this is the top left of a multibuilding
            return true;
        }

        if (isCandidateForTopLeftOf2x2Building(chunkX, chunkZ, provider) &&
                !isCandidateForTopLeftOf2x2Building(chunkX - 1, chunkZ, provider) &&
                !isCandidateForTopLeftOf2x2Building(chunkX - 1, chunkZ - 1, provider) &&
                !isCandidateForTopLeftOf2x2Building(chunkX, chunkZ - 1, provider) &&

                !isCandidateForTopLeftOf2x2Building(chunkX + 1, chunkZ - 1, provider) &&
                !isCandidateForTopLeftOf2x2Building(chunkX + 1, chunkZ, provider) &&
                !isCandidateForTopLeftOf2x2Building(chunkX + 1, chunkZ + 1, provider) &&
                !isCandidateForTopLeftOf2x2Building(chunkX, chunkZ + 1, provider) &&
                !isCandidateForTopLeftOf2x2Building(chunkX - 1, chunkZ + 1, provider)
                ) {
            PredefinedCity.PredefinedStreet predefinedStreet = City.getPredefinedStreet(chunkX, chunkZ, provider);
            if (predefinedStreet != null) {
                return false;   // There is a street here so no building
            }
            return isMultiBuildingCandidate(chunkX + 1, chunkZ, provider) && isMultiBuildingCandidate(chunkX + 1, chunkZ + 1, provider) && isMultiBuildingCandidate(chunkX, chunkZ + 1, provider);
        } else {
            return false;
        }
    }

    public static void cleanCache() {
        buildingInfoMap.clear();
        cityInfoMap.clear();
    }

    public static BuildingInfo getBuildingInfo(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        ChunkCoord key = new ChunkCoord(provider.dimensionId, chunkX, chunkZ);
        if (buildingInfoMap.containsKey(key)) {
            return buildingInfoMap.get(key);
        }
        BuildingInfo info = new BuildingInfo(chunkX, chunkZ, provider);
        buildingInfoMap.put(key, info);
        return info;
    }

    private BuildingInfo(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        this.provider = provider;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.coord = new ChunkCoord(provider.dimensionId, chunkX, chunkZ);

        LostChunkCharacteristics characteristics = getChunkCharacteristics(chunkX, chunkZ, provider);

        isCity = characteristics.isCity;
        building2x2Section = characteristics.section;
        cityLevel = characteristics.cityLevel;
        buildingType = characteristics.buildingType;
        multiBuilding = characteristics.multiBuilding;

        Random rand = getBuildingRandom(chunkX, chunkZ, provider.seed);
        rand.nextFloat();       // Compatibility?

        boolean b = characteristics.couldHaveBuilding;
        if (b && building2x2Section < 0) {
            if (rand.nextFloat() < getChunkCharacteristics(chunkX - 1, chunkZ, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX + 1, chunkZ, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX, chunkZ - 1, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX, chunkZ + 1, provider).buildingType.getPrefersLonely()) {
                b = false;
            }
        }
        hasBuilding = b;

        // In a 2x2 building we copy all information from the top-left chunk
        if (building2x2Section >= 1) {
            BuildingInfo topleft = calculateTopLeft();
            highwayXLevel = topleft.highwayXLevel;
            highwayZLevel = topleft.highwayZLevel;
            streetType = topleft.streetType;
            fountainType = topleft.fountainType;
            parkType = topleft.parkType;
            floors = topleft.floors;
            floorsBelowGround = topleft.floorsBelowGround;
            doorBlock = topleft.doorBlock;
            bridgeType = topleft.bridgeType;
            stairType = topleft.stairType;
            stairPriority = topleft.stairPriority;
            palette = topleft.palette;
            compiledPalette = topleft.getCompiledPalette();
            noLoot = topleft.noLoot;
            ruinHeight = topleft.ruinHeight;
        } else {
            CityStyle cs = (CityStyle) characteristics.cityStyle;
            PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, provider);
            highwayXLevel = Highway.getXHighwayLevel(chunkX, chunkZ, provider);
            highwayZLevel = Highway.getZHighwayLevel(chunkX, chunkZ, provider);

            if (rand.nextDouble() < .2f) {
                streetType = StreetType.values()[rand.nextInt(StreetType.values().length)];
            } else {
                streetType = StreetType.NORMAL;
            }
            if (rand.nextFloat() < provider.profile.FOUNTAIN_CHANCE) {
                fountainType = AssetRegistries.PARTS.get(cs.getRandomFountain(rand));
            } else {
                fountainType = null;
            }
            parkType = AssetRegistries.PARTS.get(cs.getRandomPark(rand));
            float cityFactor = City.getCityFactor(chunkX, chunkZ, provider);

            int maxfloors = getMaxfloors(provider, cs);
            int f = provider.profile.BUILDING_MINFLOORS + rand.nextInt((int) (provider.profile.BUILDING_MINFLOORS_CHANCE + (cityFactor + .1f) * (provider.profile.BUILDING_MAXFLOORS_CHANCE - provider.profile.BUILDING_MINFLOORS_CHANCE)));
            f++;
            if (f > maxfloors) {
                f = maxfloors;
            }
            int minfloors = getMinfloors(provider, cs);
            if (f < minfloors) {
                f = minfloors;
            }
            floors = f;

            int maxcellars = getMaxcellars(provider, cs);
            int fb = provider.profile.BUILDING_MINCELLARS + ((maxcellars <= 0) ? 0 : rand.nextInt(maxcellars));
            if (getMaxHighwayLevel() >= 0) {
                // If we are above a highway we make sure we can't have too many cellars
                fb = Math.min(cityLevel-getMaxHighwayLevel()-1, fb);
                if (fb < 0) {
                    fb = 0;
                }
            }
            floorsBelowGround = fb;

            doorBlock = getRandomDoor(rand);
            bridgeType = AssetRegistries.PARTS.get(cs.getRandomBridge(rand));
            stairType = AssetRegistries.PARTS.get(cs.getRandomStair(rand));
            stairPriority = rand.nextFloat();
            createPalette(rand);
            float r = rand.nextFloat();
            noLoot = building2x2Section == -1 && r < provider.profile.BUILDING_WITHOUT_LOOT_CHANCE;
            r = rand.nextFloat();
            if (rand.nextFloat() < provider.profile.RUIN_CHANCE && (predefinedBuilding == null || !predefinedBuilding.isPreventRuins())) {
                ruinHeight = provider.profile.RUIN_MINLEVEL_PERCENT + (provider.profile.RUIN_MAXLEVEL_PERCENT - provider.profile.RUIN_MINLEVEL_PERCENT) * r;
            } else {
                ruinHeight = -1;
            }
        }

        floorTypes = new BuildingPart[floors + floorsBelowGround + 1];
        floorTypes2 = new BuildingPart[floors + floorsBelowGround + 1];

        connectionAtX = new boolean[floors + floorsBelowGround + 1];
        connectionAtZ = new boolean[floors + floorsBelowGround + 1];
        Building building = (Building) getBuilding();
        for (int i = 0; i <= floors + floorsBelowGround; i++) {
            ConditionContext conditionContext = new ConditionContext(cityLevel + i - floorsBelowGround, i - floorsBelowGround, floorsBelowGround, floors, "<none>", building.getName(),
                    chunkX, chunkZ);
            String randomPart = building.getRandomPart(rand, conditionContext);
            floorTypes[i] = AssetRegistries.PARTS.get(randomPart);
            randomPart = building.getRandomPart2(rand, conditionContext);
            floorTypes2[i] = AssetRegistries.PARTS.get(randomPart);
            connectionAtX[i] = isCity(chunkX - 1, chunkZ, provider) && (rand.nextFloat() < provider.profile.BUILDING_DOORWAYCHANCE);
            connectionAtZ[i] = isCity(chunkX, chunkZ - 1, provider) && (rand.nextFloat() < provider.profile.BUILDING_DOORWAYCHANCE);
        }

        if (hasBuilding && floorsBelowGround > 0) {
            xRailCorridor = false;
            zRailCorridor = false;
        } else {
            xRailCorridor = rand.nextFloat() < provider.profile.CORRIDOR_CHANCE;
            zRailCorridor = rand.nextFloat() < provider.profile.CORRIDOR_CHANCE;
        }

        if (isCity) {
            xBridge = false;
            zBridge = false;
        } else {
            xBridge = rand.nextFloat() < provider.profile.BRIDGE_CHANCE;
            zBridge = rand.nextFloat() < provider.profile.BRIDGE_CHANCE;
        }

        if (rand.nextFloat() < provider.profile.RAILWAY_DUNGEON_CHANCE) {
            if (!hasBuilding || (Railway.RAILWAY_LEVEL_OFFSET < (cityLevel - floorsBelowGround))) {
                railDungeon = AssetRegistries.PARTS.get(getCityStyle().getRandomRailDungeon(rand));
            } else {
                railDungeon = null;
            }
        } else {
            railDungeon = null;
        }

        if (rand.nextFloat() < provider.profile.BUILDING_FRONTCHANCE) {
            frontType = AssetRegistries.PARTS.get(getCityStyle().getRandomFront(rand));
        } else {
            frontType = null;
        }
    }

    private int getMaxcellars(LostCityChunkGenerator provider, CityStyle cs) {
        int maxcellars = provider.profile.BUILDING_MAXCELLARS + cityLevel;
        if (buildingType.getMaxCellars() != -1) {
            maxcellars = Math.min(maxcellars, buildingType.getMaxCellars());
        }
        if (buildingType.getMinCellars() != -1) {
            maxcellars = Math.max(maxcellars, buildingType.getMinCellars());
        }
        if (cs.getMaxCellarCount() != null) {
            maxcellars = Math.min(maxcellars, cs.getMaxCellarCount());
        }
        if (cs.getMinCellarCount() != null) {
            maxcellars = Math.max(maxcellars, cs.getMinCellarCount());
        }
        return maxcellars;
    }

    private int getMinfloors(LostCityChunkGenerator provider, CityStyle cs) {
        int minfloors = provider.profile.BUILDING_MINFLOORS + 1;    // +1 because this doesn't count the top
        if (buildingType.getMinFloors() != -1) {
            minfloors = Math.max(minfloors, buildingType.getMinFloors());
        }
        if (cs.getMinFloorCount() != null) {
            minfloors = Math.max(minfloors, cs.getMinFloorCount());
        }
        return minfloors;
    }

    private int getMaxfloors(LostCityChunkGenerator provider, CityStyle cs) {
        int maxfloors = provider.profile.BUILDING_MAXFLOORS;
        if (buildingType.getMaxFloors() != -1) {
            maxfloors = Math.min(maxfloors, buildingType.getMaxFloors());
        }
        if (cs.getMaxFloorCount() != null) {
            maxfloors = Math.min(maxfloors, cs.getMaxFloorCount());
        }
        return maxfloors;
    }

    public int getHighwayXLevel() {
        return Highway.getXHighwayLevel(chunkX, chunkZ, provider);
    }

    public int getHighwayZLevel() {
        return Highway.getZHighwayLevel(chunkX, chunkZ, provider);
    }

    /**
     * This function does not use the cache. So safe to use when the cache is building
     */
    public static int getCityLevel(int chunkX, int chunkZ, LostCityChunkGenerator provider) {
        if (provider.otherGenerator != null) {
            int height = provider.otherGenerator.getHeight(chunkX, chunkZ, 8, 8);
            return getLevelBasedOnHeight(height, provider);
        } else {
            // @todo: average out nearby biomes?
            Biome[] biomes = BiomeInfo.getBiomeInfo(provider, new ChunkCoord(provider.dimensionId, chunkX, chunkZ)).getBiomes();
            float h = 0.0f;
            for (Biome biome : biomes) {
                h += biome.getBaseHeight();
            }
            h /= biomes.length;

            // deep ocean = -1.8
            // ocean = -1
            // river = -0.5
            // swampland = -0.2
            // beach = 0
            // plains = .125
            // taiga = 0.2
            // ice mountains/desert hills = 0.45
            // hills edge/stone beach = 0.8
            // extreme hills = 1
            // savanna plateau = 1.5
            // mesa = 1.5
            int height = 0;

            if (h < 0.15f) {
                height = 70;
            } else if (h < 0.4f) {
                height = 79;
            } else if (h < 0.7f) {
                height = 88;
            } else if (h < 1.3) {
                height = 95;
            } else {
                height = 100;
            }
            return getLevelBasedOnHeight(height, provider);
        }
    }

    private static int getLevelBasedOnHeight(int height, LostCityChunkGenerator provider) {
        int cityLevel;
        if (height < provider.profile.CITY_LEVEL0_HEIGHT) {
            cityLevel = 0;
        } else if (height < provider.profile.CITY_LEVEL1_HEIGHT) {
            cityLevel = 1;
        } else if (height < provider.profile.CITY_LEVEL2_HEIGHT) {
            cityLevel = 2;
        } else if (height < provider.profile.CITY_LEVEL3_HEIGHT) {
            cityLevel = 3;
        } else {
            cityLevel = 4;
        }
        return cityLevel;
    }

    private Block getRandomDoor(Random rand) {
        Block doorBlock;
        switch (rand.nextInt(7)) {
            case 0:
                doorBlock = Blocks.BIRCH_DOOR;
                break;
            case 1:
                doorBlock = Blocks.ACACIA_DOOR;
                break;
            case 2:
                doorBlock = Blocks.DARK_OAK_DOOR;
                break;
            case 3:
                doorBlock = Blocks.SPRUCE_DOOR;
                break;
            case 4:
                doorBlock = Blocks.OAK_DOOR;
                break;
            case 5:
                doorBlock = Blocks.JUNGLE_DOOR;
                break;
            case 6:
                doorBlock = Blocks.IRON_DOOR;
                break;
            default:
                doorBlock = Blocks.OAK_DOOR;
        }
        return doorBlock;
    }

    public boolean isStreetSection() {
        return isCity && !hasBuilding;
    }

    public boolean isElevatedParkSection() {
        if (!isStreetSection()) {
            return false;
        }
        if (!getXmin().isStreetSection()) {
            return false;
        }
        if (!getXmax().isStreetSection()) {
            return false;
        }
        if (!getZmin().isStreetSection()) {
            return false;
        }
        if (!getZmax().isStreetSection()) {
            return false;
        }
        int cnt = 0;
        cnt += getXmin().getZmin().isStreetSection() ? 1 : 0;
        cnt += getXmin().getZmax().isStreetSection() ? 1 : 0;
        cnt += getXmax().getZmin().isStreetSection() ? 1 : 0;
        cnt += getXmax().getZmax().isStreetSection() ? 1 : 0;
        return cnt >= 3;
    }

    private Direction getStairDirection() {
        if (!stairsCalculated) {
            stairsCalculated = true;
            if (streetType != StreetType.PARK && !hasBuilding && isCity) {
                if (cityLevel == getXmin().cityLevel - 1 && !getXmin().hasBuilding && getXmin().isCity) {
                    stairDirection = Direction.XMIN;
                } else if (cityLevel == getXmax().cityLevel - 1 && !getXmax().hasBuilding && getXmax().isCity) {
                    stairDirection = Direction.XMAX;
                } else if (cityLevel == getZmin().cityLevel - 1 && !getZmin().hasBuilding && getZmin().isCity) {
                    stairDirection = Direction.ZMIN;
                } else if (cityLevel == getZmax().cityLevel - 1 && !getZmax().hasBuilding && getZmax().isCity) {
                    stairDirection = Direction.ZMAX;
                } else {
                    stairDirection = null;
                }
            } else {
                stairDirection = null;
            }
        }
        return stairDirection;
    }

    // This returns the actual stair direction. It keeps track if there are stair chunks around
    // it those have higher stair priority
    public Direction getActualStairDirection() {
        if (!actualStairsCalculated) {
            actualStairsCalculated = true;
            actualStairDirection = getStairDirection();
            if (actualStairDirection != null) {
                for (int cx = -1; cx <= 1; cx++) {
                    for (int cz = -1; cz <= 1; cz++) {
                        if (cx != 0 || cz != 0) {
                            BuildingInfo adjacent = getBuildingInfo(chunkX + cx, chunkZ + cz, provider);
                            if (adjacent.getStairDirection() != null && adjacent.stairPriority > stairPriority) {
                                actualStairDirection = null;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return actualStairDirection;
    }


    public BuildingPart hasBridge(LostCityChunkGenerator provider, Orientation orientation) {
        switch (orientation) {
            case X:
                return hasXBridge(provider);
            case Z:
                return hasZBridge(provider);
        }
        return null;
    }

    // To prevent adjacent bridges of the same direction we give the bridges at even chunk Z coordinates higher priority
    public BuildingPart hasXBridge(LostCityChunkGenerator provider) {
        if (xBridgeTypeCalculated) {
            return xBridgeType;
        }
        xBridgeTypeCalculated = true;
        xBridgeType = null;

        if (!xBridge) {
            return null;
        }
        if (!isSuitableForBridge(provider, this)) {
            return null;
        }
        if (chunkZ % 2 != 0 && (getZmin().hasXBridge(provider) != null || getZmax().hasXBridge(provider) != null)) {
            return null;
        }
        BuildingPart bt = bridgeType;
        BuildingInfo i = getXmin();
        while ((!i.isCity) && i.xBridge && isSuitableForBridge(provider, i)) {
            if (chunkZ % 2 != 0 && (i.getZmin().hasXBridge(provider) != null || i.getZmax().hasXBridge(provider) != null)) {
                return null;
            }
            bt = i.bridgeType;
            i = i.getXmin();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) {  // @todo support bridges at higher levels?
            return null;
        }

        BuildingInfo minimum = i;

        i = getXmax();
        while ((!i.isCity) && i.xBridge && isSuitableForBridge(provider, i)) {
            if (chunkZ % 2 != 0 && (i.getZmin().hasXBridge(provider) != null || i.getZmax().hasXBridge(provider) != null)) {
                return null;
            }
            i = i.getXmax();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) {
            return null;
        }
        xBridgeType = bt;
        // Here we can automatically mark the rest of the bridge as ok. Saves on calculation
        i = i.getXmin();
        while (i != minimum) {
            i.xBridgeType = bt;
            i.xBridgeTypeCalculated = true;
            i.zBridgeType = null;
            i.zBridgeTypeCalculated = true;
            i = i.getXmin();
        }

        return bt;
    }

    // To prevent adjacent bridges of the same direction we give the bridges at even chunk X coordinates higher priority
    public BuildingPart hasZBridge(LostCityChunkGenerator provider) {
        if (zBridgeTypeCalculated) {
            return zBridgeType;
        }
        zBridgeTypeCalculated = true;
        zBridgeType = null;

        if (!zBridge) {
            return null;
        }
        if (!isSuitableForBridge(provider, this)) {
            return null;
        }
        if (hasXBridge(provider) != null) {
            return null;
        }

        if (chunkX % 2 != 0 && (getXmin().hasZBridge(provider) != null || getXmax().hasZBridge(provider) != null)) {
            return null;
        }

        BuildingPart bt = bridgeType;
        BuildingInfo i = getZmin();
        while ((!i.isCity) && i.zBridge && isSuitableForBridge(provider, i)) {
            if (i.hasXBridge(provider) != null) {
                return null;
            }
            if (chunkX % 2 != 0 && (i.getXmin().hasZBridge(provider) != null || i.getXmax().hasZBridge(provider) != null)) {
                return null;
            }

            bt = i.bridgeType;
            i = i.getZmin();
        }

        BuildingInfo minimum = i;

        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) {
            return null;
        }
        i = getZmax();
        while ((!i.isCity) && i.zBridge && isSuitableForBridge(provider, i)) {
            if (i.hasXBridge(provider) != null) {
                return null;
            }
            if (chunkX % 2 != 0 && (i.getXmin().hasZBridge(provider) != null || i.getXmax().hasZBridge(provider) != null)) {
                return null;
            }
            i = i.getZmax();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) {
            return null;
        }
        zBridgeType = bt;
        // Here we can automatically mark the rest of the bridge as ok. Saves on calculation
        i = i.getZmin();
        while (i != minimum) {
            i.zBridgeType = bt;
            i.zBridgeTypeCalculated = true;
            i.xBridgeType = null;
            i.xBridgeTypeCalculated = true;
            i = i.getZmin();
        }

        return bt;
    }

    public boolean isOcean() {
        if (isOcean != null) {
            return isOcean;
        }
        Biome[] biomes = BiomeInfo.getBiomeInfo(provider, new ChunkCoord(provider.dimensionId, chunkX, chunkZ)).getBiomes();
        isOcean = isOcean(biomes);
        return isOcean;
    }

    private static boolean isOcean(Biome[] biomes) {
        int cnt = 0;
        for (Biome biome : biomes) {
            if (biome == Biomes.OCEAN || biome == Biomes.DEEP_OCEAN || biome == Biomes.FROZEN_OCEAN) {
                cnt++;
            }
        }
        return (cnt * 100 / biomes.length) > 50;
    }


    private boolean isSuitableForBridge(LostCityChunkGenerator provider, BuildingInfo i) {
        return i.cityLevel < cityLevel || LostCitiesTerrainGenerator.isWaterBiome(provider, i.coord);
    }


    public boolean hasXCorridor() {
        if (!xRailCorridor) {
            return false;
        }
        BuildingInfo i = getXmin();
        while (i.canRailGoThrough() && i.xRailCorridor) {
            i = i.getXmin();
        }
        if ((!i.hasBuilding) || i.floorsBelowGround == 0) {
            return false;
        }
        i = getXmax();
        while (i.canRailGoThrough() && i.xRailCorridor) {
            i = i.getXmax();
        }
        return !((!i.hasBuilding) || i.floorsBelowGround == 0);
    }

    public boolean hasZCorridor() {
        if (!zRailCorridor) {
            return false;
        }
        BuildingInfo i = getZmin();
        while (i.canRailGoThrough() && i.zRailCorridor) {
            i = i.getZmin();
        }
        if ((!i.hasBuilding) || i.floorsBelowGround == 0) {
            return false;
        }
        i = getZmax();
        while (i.canRailGoThrough() && i.zRailCorridor) {
            i = i.getZmax();
        }
        return !((!i.hasBuilding) || i.floorsBelowGround == 0);
    }

    // Return true if it is possible for a rail section to go through here
    public boolean canRailGoThrough() {
        if (!isCity) {
            // There is no city here so no passing possible
            return false;
        }
        if (!hasBuilding) {
            // There is no building here but we have a city so we can pass
            return true;
        }
        // Otherwise we can only pass if this building has no floors below ground
        return floorsBelowGround == 0;
    }

    // Return true if it is possible for a water corridor to go through here
    public boolean canWaterCorridorGoThrough() {
        if (!isCity) {
            // There is no city here so no passing possible
            return false;
        }
        if (!hasBuilding) {
            // There is no building here but we have a city so we can pass
            return true;
        }
        // Otherwise we can only pass if this building has at most one floor below ground
        return floorsBelowGround <= 1;
    }

    // Return true if the road from a neighbouring chunk can extend into this chunk
    public boolean doesRoadExtendTo() {
        boolean b = isCity && !hasBuilding;
        if (b) {
            return !isElevatedParkSection();
        }
        return false;
    }

    // Return true if there can be a road connection between the two given chunks
    public static boolean hasRoadConnection(BuildingInfo i1, BuildingInfo i2) {
        if (!i1.doesRoadExtendTo()) {
            return false;
        }
        if (!i2.doesRoadExtendTo()) {
            return false;
        }
        if (Math.abs(i1.cityLevel - i2.cityLevel) <= 0 /* @todo temporary, should be <= 1 */) {
            // We allow a road difference of 1 maximum
            return true;
        }
        return false;
    }

    public static Random getBuildingRandom(int chunkX, int chunkZ, long seed) {
        Random rand = new QualityRandom(seed + chunkZ * 341873128712L + chunkX * 132897987541L);
        rand.nextFloat();
        rand.nextFloat();
        return rand;
    }

    // Convert a local building level to a global one (where cityLevel == 0)
    public int localToGlobal(int l) {
        return l + cityLevel;
    }

    public int globalToLocal(int l) {
        return l - cityLevel;
    }

    public boolean hasConnectionAt(int level, Orientation orientation) {
        switch (orientation) {
            case X:
                return hasConnectionAtX(level);
            case Z:
                return hasConnectionAtZ(level);
        }
        throw new IllegalStateException("Cannot happen!");
    }

    // Call this from the street reference with the (potential building) as 'adj'
    // 'streetLevel' is the cityLevel at the position of the street
    public boolean hasFrontPartFrom(BuildingInfo adj) {
        BuildingInfo.StreetType st = streetType;
        boolean elevated = isElevatedParkSection();
        if (elevated) {
            st = BuildingInfo.StreetType.PARK;
        }

        if (adj.hasBuilding && adj.frontType != null && st == BuildingInfo.StreetType.NORMAL && cityLevel < adj.cityLevel + adj.getNumFloors()) {
            RailChunkType type = getRailInfo().getType();
            if (type == RailChunkType.STATION_UNDERGROUND) {
                return false;
            }
            if (type == RailChunkType.GOING_DOWN_ONE_FROM_SURFACE) {
                return false;
            }
            if (getMaxHighwayLevel() >= 0) {
                return false;
            }

            int local = adj.globalToLocal(cityLevel);
            if (adj.isValidFloor(local) && adj.getFloor(local).getMetaBoolean("dontconnect")) {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }


    // This checks if there can be a connection at minX
    public boolean hasConnectionAtX(int level) {
        if (!isCity) {
            return false;
        }
        if (building2x2Section == 1 || building2x2Section == 3) {
            return false;
        }
        if (level < 0 || level >= connectionAtX.length) {
            return false;
        }
        if (level < floorTypes.length && floorTypes[level].getMetaBoolean("dontconnect")) {
            return false;       // No connection supported
        }
        if (getXmin().hasFrontPartFrom(this)) {
            return true;
        }
        return connectionAtX[level];
    }

    // This checks if there can be a connection at minX
    public boolean hasConnectionAtXFromStreet(int level) {
        if (!isCity) {
            return false;
        }
        if (building2x2Section == 1 || building2x2Section == 3) {
            return false;
        }
        if (level < 0 || level >= connectionAtX.length) {
            return false;
        }
        if (hasFrontPartFrom(getXmin())) {
            return true;
        }
        return connectionAtX[level];
    }

    // This checks if there can be a connection at minZ
    public boolean hasConnectionAtZ(int level) {
        if (!isCity) {
            return false;
        }
        if (building2x2Section == 2 || building2x2Section == 3) {
            return false;
        }
        if (level < 0 || level >= connectionAtZ.length) {
            return false;
        }
        if (level < floorTypes.length && floorTypes[level].getMetaBoolean("dontconnect")) {
            return false;       // No connection supported
        }
        if (getZmin().hasFrontPartFrom(this)) {
            return true;
        }
        return connectionAtZ[level];
    }

    // This checks if there can be a connection at minZ
    public boolean hasConnectionAtZFromStreet(int level) {
        if (!isCity) {
            return false;
        }
        if (building2x2Section == 2 || building2x2Section == 3) {
            return false;
        }
        if (level < 0 || level >= connectionAtZ.length) {
            return false;
        }
        if (hasFrontPartFrom(getZmin())) {
            return true;
        }
        return connectionAtZ[level];
    }

    public enum StreetType {
        NORMAL,
        FULL,
        PARK
    }


    @Override
    public boolean isCity() {
        return this.isCity;
    }

    @Override
    public String getBuildingType() {
        return hasBuilding ? buildingType.getName() : null;
    }

    @Override
    public int getCityLevel() {
        return cityLevel;
    }

    @Override
    public int getNumFloors() {
        return floors;
    }

    @Override
    public int getNumCellars() {
        return floorsBelowGround;
    }

    @Override
    public float getDamage(int chunkY) {
        return getDamageArea().getDamage(chunkX * 16 + 8, chunkY * 16 + 8, chunkZ * 16 + 8);
    }

    @Override
    public Collection<ILostExplosion> getExplosions() {
        return getDamageArea().getExplosions().stream().map(explosion -> (ILostExplosion) explosion).collect(Collectors.toList());
    }

    @Override
    public int getMaxHighwayLevel() {
        return Math.max(getHighwayXLevel(), getHighwayZLevel());
    }

    @Nonnull
    @Override
    public RailChunkType getRailType() {
        return getRailInfo().getType();
    }

    @Override
    public int getRailLevel() {
        return getRailInfo().getLevel();
    }

    @Nullable
    @Override
    public ILostCityInfo getCityInfo() {
        if (City.isCityCenter(chunkX, chunkZ, provider)) {
            return new ILostCityInfo() {
                @Override
                public float getCityRadius() {
                    return City.getCityRadius(chunkX, chunkZ, provider);
                }

                @Override
                public String getCityStyle() {
                    return City.getCityStyleForCityCenter(chunkX, chunkZ, provider);
                }
            };
        } else {
            return null;
        }
    }

    @Override
    public int getRuinLevel() {
        if (!provider.profile.RUINS) {
            return -1;
        }
        if (ruinHeight < 0) {
            return -1;
        }
        return (int) (getCityGroundLevel() + 1 + (ruinHeight * getNumFloors() * 6.0f));
    }
}
