package com.MBEv2.core;

import com.MBEv2.core.entity.Player;
import org.joml.Vector3f;

import static com.MBEv2.core.utils.Constants.*;

public class Block {

    private static final int[] NON_STANDARD_BLOCK_TYPE = new int[STANDARD_BLOCKS_THRESHOLD];
    private static final int[][] NON_STANDARD_BLOCK_TEXTURE_INDICES = new int[STANDARD_BLOCKS_THRESHOLD][1];
    private static final int[][] STANDARD_BLOCK_TEXTURE_INDICES = new int[AMOUNT_OF_STANDARD_BLOCKS][1];
    private static final int[] NON_STANDARD_BLOCK_PROPERTIES = new int[STANDARD_BLOCKS_THRESHOLD];
    private static final int[] STANDARD_BLOCK_PROPERTIES = new int[AMOUNT_OF_STANDARD_BLOCKS];

    private static final byte[] BLOCK_TYPE_OCCLUSION_DATA = new byte[TOTAL_AMOUNT_OF_BLOCK_TYPES];
    private static final byte[] BLOCK_TYPE_DATA = new byte[TOTAL_AMOUNT_OF_BLOCK_TYPES];
    private static final byte[][] BLOCK_TYPE_XYZ_SUB_DATA = new byte[TOTAL_AMOUNT_OF_BLOCK_TYPES][0];
    private static final byte[][] BLOCK_TYPE_UV_SUB_DATA = new byte[TOTAL_AMOUNT_OF_BLOCK_TYPES][0];
    public static final int[][] NORMALS = {
            {0, 0, 1},
            {0, 1, 0},
            {1, 0, 0},
            {0, 0, -1},
            {0, -1, 0},
            {-1, 0, 0}};

    public static final int[][] CORNERS_OF_SIDE = {{1, 0, 5, 4}, {2, 0, 3, 1}, {3, 1, 7, 5}, {2, 3, 6, 7}, {6, 4, 7, 5}, {2, 0, 6, 4}};

    public static boolean occludes(short toTestBlock, short occludingBlock, int side, int x, int y, int z) {
        if (isLeaveType(occludingBlock)) return false;
        if (isGlassType(occludingBlock)) return isGlassType(toTestBlock);
        byte occlusionData = BLOCK_TYPE_OCCLUSION_DATA[getBlockType(occludingBlock)];
        byte blockData = BLOCK_TYPE_DATA[getBlockType(toTestBlock)];
        int occludingSide = (side + 3) % 6;
        int occlusionType = (occlusionData >> 6) & 3;
        int blockSideType = blockData & 1 << side;
        int occludingSideType = occlusionData & 1 << occludingSide;

        if ((occlusionType == OCCLUDES_ALL || occlusionType == OCCLUDES_DYNAMIC_ALL) && blockSideType != 0 && occludingSideType != 0)
            return true;

        if (occlusionType == OCCLUDES_DYNAMIC_SELF || occlusionType == OCCLUDES_DYNAMIC_ALL)
            return dynamicOcclusion(toTestBlock, occludingBlock, side, x, y, z, blockSideType, occludingSideType);

        return occlusionType == OCCLUDES_SELF && toTestBlock == occludingBlock;
    }

    public static boolean dynamicOcclusion(short toTestBlock, short occludingBlock, int side, int x, int y, int z, int blockSideType, int occludingSideType) {
        if (getBlockType(occludingBlock) == WATER_TYPE) {
            if (toTestBlock != occludingBlock) return false;
            if (side == TOP || side == BOTTOM) return true;

            int[] normal = NORMALS[side];
            short blockAboveToTestBlock = Chunk.getBlockInWorld(x, y + 1, z);
            short blockAboveOccludingBlock = Chunk.getBlockInWorld(x + normal[0], y + 1, z + normal[2]);

            int blockAboveToTestBlockType = getBlockType(blockAboveToTestBlock);
            int blockAboveOccludingBlockType = getBlockType(blockAboveOccludingBlock);

            if (getBlockTypeOcclusionData(blockAboveToTestBlock, BOTTOM) != 0 && getBlockTypeOcclusionData(blockAboveOccludingBlock, BOTTOM) == 0)
                return false;
            if ((blockAboveOccludingBlockType == WATER_TYPE) == (blockAboveToTestBlockType == WATER_TYPE)) return true;
            if (getBlockTypeOcclusionData(blockAboveOccludingBlock, BOTTOM) != 0 && blockAboveToTestBlockType == WATER_TYPE)
                return true;
            return blockAboveToTestBlockType != WATER_TYPE;
        }

        if (blockSideType != 0 && occludingSideType != 0) return true;

        int occludingSide = (side + 3) % 6;
        int occludingBlockData = BLOCK_TYPE_DATA[getBlockType(occludingBlock)];
        int occludingBlockSideType = occludingBlockData & 1 << occludingSide;

        return getBlockType(toTestBlock) == getBlockType(occludingBlock) && blockSideType != 0 && occludingBlockSideType != 0;
    }

    public static int getTextureIndex(short block, int side) {
        int[] blockTextureIndices;
        if (block < STANDARD_BLOCKS_THRESHOLD)
            blockTextureIndices = NON_STANDARD_BLOCK_TEXTURE_INDICES[block];
        else
            blockTextureIndices = STANDARD_BLOCK_TEXTURE_INDICES[block >> BLOCK_TYPE_BITS];
        return blockTextureIndices[side >= blockTextureIndices.length ? 0 : side];
    }

    public static boolean playerIntersectsBlock(float minX, float maxX, float minY, float maxY, float minZ, float maxZ, int blockX, int blockY, int blockZ, short block, Player player) {
        if (player.isNoClip()) return false;

        int blockType = getBlockType(block);
        byte[] blockXYZSubData = BLOCK_TYPE_XYZ_SUB_DATA[blockType];
        if (blockXYZSubData.length == 0 || blockType == WATER_TYPE) return false;

        float minBlockX = blockX + blockXYZSubData[MIN_X] * 0.0625f;
        float maxBlockX = 1 + blockX + blockXYZSubData[MAX_X] * 0.0625f;
        float minBlockY = blockY + blockXYZSubData[MIN_Y] * 0.0625f;
        float maxBlockY = 1 + blockY + blockXYZSubData[MAX_Y] * 0.0625f;
        float minBlockZ = blockZ + blockXYZSubData[MIN_Z] * 0.0625f;
        float maxBlockZ = 1 + blockZ + blockXYZSubData[MAX_Z] * 0.0625f;

        return minX < maxBlockX && maxX > minBlockX && minY < maxBlockY && maxY > minBlockY && minZ < maxBlockZ && maxZ > minBlockZ;
    }

    public static boolean intersectsBlock(double x, double y, double z, short block) {
        int blockType = getBlockType(block);
        byte[] XYZSubData = BLOCK_TYPE_XYZ_SUB_DATA[blockType];
        if (blockType == WATER_TYPE || blockType == AIR_TYPE || XYZSubData.length == 0) return false;
        x = fraction(x) * 16.0;
        y = fraction(y) * 16.0;
        z = fraction(z) * 16.0;

        return x > XYZSubData[MIN_X] && x < XYZSubData[MAX_X] + 16 && y > XYZSubData[MIN_Y] && y < XYZSubData[MAX_Y] + 16 && z > XYZSubData[MIN_Z] && z < XYZSubData[MAX_Z] + 16;
    }

    public static int getToPlaceBlockAddend(int primaryCameraDirection, Vector3f target) {
        if (primaryCameraDirection == FRONT) return fraction(target.z) > 0.5f ? 0 : 3;
        if (primaryCameraDirection == TOP) return fraction(target.y) > 0.5f ? 0 : 3;
//        if (primaryCameraDirection == RIGHT)
        return fraction(target.x) > 0.5f ? 0 : 3;
    }

    public static double fraction(double number) {
        int addend = number < 0 ? 1 : 0;
        return (number - (int) number) + addend;
    }

    public static byte getBlockTypeData(short block) {
        return BLOCK_TYPE_DATA[getBlockType(block)];
    }

    public static int getBlockProperties(short block) {
        if (block < STANDARD_BLOCKS_THRESHOLD)
            return NON_STANDARD_BLOCK_PROPERTIES[block];
        return STANDARD_BLOCK_PROPERTIES[block >> BLOCK_TYPE_BITS];
    }

    public static byte getSubX(short block, int side, int corner) {
        int blockType = getBlockType(block);
        if (BLOCK_TYPE_XYZ_SUB_DATA[blockType].length == 0) return 0;
        return BLOCK_TYPE_XYZ_SUB_DATA[blockType][CORNERS_OF_SIDE[side][corner] & 1];
    }

    public static byte getSubY(short block, int side, int corner) {
        int blockType = getBlockType(block);
        if (BLOCK_TYPE_XYZ_SUB_DATA[blockType].length == 0) return 0;
        return BLOCK_TYPE_XYZ_SUB_DATA[blockType][CORNERS_OF_SIDE[side][corner] < 4 ? MAX_Y : MIN_Y];
    }

    public static byte getSubZ(short block, int side, int corner) {
        int blockType = getBlockType(block);
        if (BLOCK_TYPE_XYZ_SUB_DATA[blockType].length == 0) return 0;
        return BLOCK_TYPE_XYZ_SUB_DATA[blockType][(CORNERS_OF_SIDE[side][corner] & 3) < 2 ? MAX_Z : MIN_Z];
    }

    public static byte getSubU(short block, int side, int corner) {
        int blockType = getBlockType(block);
        if (BLOCK_TYPE_UV_SUB_DATA[blockType].length == 0) return 0;
        return BLOCK_TYPE_UV_SUB_DATA[blockType][(side << 3) + (corner << 1)];
    }

    public static byte getSubV(short block, int side, int corner) {
        int blockType = getBlockType(block);
        if (BLOCK_TYPE_UV_SUB_DATA[blockType].length == 0) return 0;
        return BLOCK_TYPE_UV_SUB_DATA[blockType][(side << 3) + (corner << 1) + 1];
    }

    public static int getBlockTypeOcclusionData(short block, int side) {
        if (block < STANDARD_BLOCKS_THRESHOLD)
            return BLOCK_TYPE_OCCLUSION_DATA[NON_STANDARD_BLOCK_TYPE[block]] & 1 << side;
        return BLOCK_TYPE_OCCLUSION_DATA[block & BLOCK_TYPE_MASK] & 1 << side;
    }

    public static byte[] getXYZSubData(short block) {
        return BLOCK_TYPE_XYZ_SUB_DATA[getBlockType(block)];
    }

    public static int getBlockType(short block) {
        if (block < STANDARD_BLOCKS_THRESHOLD)
            return NON_STANDARD_BLOCK_TYPE[block];
        return block & BLOCK_TYPE_MASK;
    }

    public static boolean isLeaveType(short block) {
        int baseBlock = (block >> BLOCK_TYPE_BITS) << BLOCK_TYPE_BITS;
        return baseBlock == OAK_LEAVES || baseBlock == SPRUCE_LEAVES || baseBlock == DARK_OAK_LEAVES;
    }

    public static boolean isGlassType(short block) {
        int baseBlock = (block >> BLOCK_TYPE_BITS) << BLOCK_TYPE_BITS;
        return baseBlock == GLASS;
    }

    public static short getToPlaceBlock(short toPlaceBlock, int primaryCameraDirection, int primaryXZDirection, Vector3f target) {
        primaryCameraDirection %= 3;
        int addend = getToPlaceBlockAddend(primaryCameraDirection, target);

        if (toPlaceBlock < STANDARD_BLOCKS_THRESHOLD) {
            if (toPlaceBlock == FRONT_CREATOR_HEAD) {
                if (primaryXZDirection == BACK) return FRONT_CREATOR_HEAD;
                if (primaryXZDirection == FRONT) return BACK_CREATOR_HEAD;
                if (primaryXZDirection == RIGHT) return LEFT_CREATOR_HEAD;
                if (primaryXZDirection == LEFT) return RIGHT_CREATOR_HEAD;
            }
            return toPlaceBlock;
        }
        int blockType = toPlaceBlock & BLOCK_TYPE_MASK;
        int baseBlock = (toPlaceBlock >> BLOCK_TYPE_BITS) << BLOCK_TYPE_BITS;

        int toPlaceBlockType = getToPlaceBlockType(blockType, primaryCameraDirection, addend);

        if (baseBlock == UP_DOWN_OAK_LOG) {
            if (primaryCameraDirection == FRONT) return (short) (FRONT_BACK_OAK_LOG | toPlaceBlockType);
            if (primaryCameraDirection == TOP) return (short) (UP_DOWN_OAK_LOG | toPlaceBlockType);
            return (short) (LEFT_RIGHT_OAK_LOG | toPlaceBlockType);
        }
        if (baseBlock == UP_DOWN_STRIPPED_OAK_LOG) {
            if (primaryCameraDirection == FRONT) return (short) (FRONT_BACK_STRIPPED_OAK_LOG | toPlaceBlockType);
            if (primaryCameraDirection == TOP) return (short) (UP_DOWN_STRIPPED_OAK_LOG | toPlaceBlockType);
            return (short) (LEFT_RIGHT_STRIPPED_OAK_LOG | toPlaceBlockType);
        }

        if (baseBlock == UP_DOWN_SPRUCE_LOG) {
            if (primaryCameraDirection == FRONT) return (short) (FRONT_BACK_SPRUCE_LOG | toPlaceBlockType);
            if (primaryCameraDirection == TOP) return (short) (UP_DOWN_SPRUCE_LOG | toPlaceBlockType);
            return (short) (LEFT_RIGHT_SPRUCE_LOG | toPlaceBlockType);
        }
        if (baseBlock == UP_DOWN_STRIPPED_SPRUCE_LOG) {
            if (primaryCameraDirection == FRONT) return (short) (FRONT_BACK_STRIPPED_SPRUCE_LOG | toPlaceBlockType);
            if (primaryCameraDirection == TOP) return (short) (UP_DOWN_STRIPPED_SPRUCE_LOG | toPlaceBlockType);
            return (short) (LEFT_RIGHT_STRIPPED_SPRUCE_LOG | toPlaceBlockType);
        }

        if (baseBlock == UP_DOWN_DARK_OAK_LOG) {
            if (primaryCameraDirection == FRONT) return (short) (FRONT_BACK_DARK_OAK_LOG | toPlaceBlockType);
            if (primaryCameraDirection == TOP) return (short) (UP_DOWN_DARK_OAK_LOG | toPlaceBlockType);
            return (short) (LEFT_RIGHT_DARK_OAK_LOG | toPlaceBlockType);
        }
        if (baseBlock == UP_DOWN_STRIPPED_DARK_OAK_LOG) {
            if (primaryCameraDirection == FRONT) return (short) (FRONT_BACK_STRIPPED_DARK_OAK_LOG | toPlaceBlockType);
            if (primaryCameraDirection == TOP) return (short) (UP_DOWN_STRIPPED_DARK_OAK_LOG | toPlaceBlockType);
            return (short) (LEFT_RIGHT_STRIPPED_DARK_OAK_LOG | toPlaceBlockType);
        }

        return (short) (baseBlock | toPlaceBlockType);
    }

    private static int getToPlaceBlockType(int blockType, int primaryCameraDirection, int addend) {
        if (blockType == BOTTOM_SLAB)
            return SLABS[primaryCameraDirection + addend];
        if (blockType == BOTTOM_PLATE)
            return PLATES[primaryCameraDirection + addend];
        if (blockType == FRONT_BACK_WALL) return WALLS[primaryCameraDirection];
        if (blockType == UP_DOWN_POST) return POSTS[primaryCameraDirection];

        return blockType;
    }

    //I don't know how to use JSON-Files, so just ignore it
    public static void init() {

        NON_STANDARD_BLOCK_TYPE[AIR] = AIR_TYPE;
        NON_STANDARD_BLOCK_TYPE[OUT_OF_WORLD] = FULL_BLOCK;

        NON_STANDARD_BLOCK_TEXTURE_INDICES[WATER] = new int[]{WATER_TEXTURE};
        NON_STANDARD_BLOCK_TYPE[WATER] = WATER_TYPE;
        NON_STANDARD_BLOCK_TEXTURE_INDICES[LAVA] = new int[]{LAVA_TEXTURE};
        NON_STANDARD_BLOCK_TYPE[LAVA] = WATER_TYPE;
        NON_STANDARD_BLOCK_PROPERTIES[LAVA] = LIGHT_EMITTING_MASK;
        NON_STANDARD_BLOCK_TEXTURE_INDICES[CACTUS] = new int[]{CACTUS_SIDE_TEXTURE, CACTUS_TOP_TEXTURE, CACTUS_SIDE_TEXTURE, CACTUS_SIDE_TEXTURE, CACTUS_TOP_TEXTURE, CACTUS_SIDE_TEXTURE};
        NON_STANDARD_BLOCK_TYPE[CACTUS] = CACTUS_TYPE;
        NON_STANDARD_BLOCK_TEXTURE_INDICES[FRONT_CREATOR_HEAD] = new int[]{CREATOR_HEAD_FRONT_TEXTURE, CREATOR_HEAD_TOP_TEXTURE, CREATOR_HEAD_RIGHT_TEXTURE, CREATOR_HEAD_BACK_TEXTURE, CREATOR_HEAD_BOTTOM_TEXTURE, CREATOR_HEAD_LEFT_TEXTURE};
        NON_STANDARD_BLOCK_TEXTURE_INDICES[RIGHT_CREATOR_HEAD] = new int[]{CREATOR_HEAD_LEFT_TEXTURE, CREATOR_HEAD_TOP_TEXTURE, CREATOR_HEAD_FRONT_TEXTURE, CREATOR_HEAD_RIGHT_TEXTURE, ROTATED_CREATOR_HEAD_BOTTOM_TEXTURE, CREATOR_HEAD_BACK_TEXTURE};
        NON_STANDARD_BLOCK_TEXTURE_INDICES[BACK_CREATOR_HEAD] = new int[]{CREATOR_HEAD_BACK_TEXTURE, CREATOR_HEAD_TOP_TEXTURE, CREATOR_HEAD_LEFT_TEXTURE, CREATOR_HEAD_FRONT_TEXTURE, CREATOR_HEAD_BOTTOM_TEXTURE, CREATOR_HEAD_RIGHT_TEXTURE};
        NON_STANDARD_BLOCK_TEXTURE_INDICES[LEFT_CREATOR_HEAD] = new int[]{CREATOR_HEAD_RIGHT_TEXTURE, CREATOR_HEAD_TOP_TEXTURE, CREATOR_HEAD_BACK_TEXTURE, CREATOR_HEAD_LEFT_TEXTURE, ROTATED_CREATOR_HEAD_BOTTOM_TEXTURE, CREATOR_HEAD_FRONT_TEXTURE};
        NON_STANDARD_BLOCK_TYPE[FRONT_CREATOR_HEAD] = PLAYER_HEAD;
        NON_STANDARD_BLOCK_TYPE[RIGHT_CREATOR_HEAD] = PLAYER_HEAD;
        NON_STANDARD_BLOCK_TYPE[BACK_CREATOR_HEAD] = PLAYER_HEAD;
        NON_STANDARD_BLOCK_TYPE[LEFT_CREATOR_HEAD] = PLAYER_HEAD;

        STANDARD_BLOCK_TEXTURE_INDICES[GRASS >> BLOCK_TYPE_BITS] = new int[]{GRASS_SIDE_TEXTURE, GRASS_TOP_TEXTURE, GRASS_SIDE_TEXTURE, GRASS_SIDE_TEXTURE, DIRT_TEXTURE, GRASS_SIDE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[DIRT >> BLOCK_TYPE_BITS] = new int[]{DIRT_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[STONE >> BLOCK_TYPE_BITS] = new int[]{STONE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[MUD >> BLOCK_TYPE_BITS] = new int[]{MUD_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[ANDESITE >> BLOCK_TYPE_BITS] = new int[]{ANDESITE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[SNOW >> BLOCK_TYPE_BITS] = new int[]{SNOW_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[SAND >> BLOCK_TYPE_BITS] = new int[]{SAND_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[STONE_BRICK >> BLOCK_TYPE_BITS] = new int[]{STONE_BRICK_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[SLATE >> BLOCK_TYPE_BITS] = new int[]{SLATE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[GLASS >> BLOCK_TYPE_BITS] = new int[]{GLASS_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[COBBLESTONE >> BLOCK_TYPE_BITS] = new int[]{COBBLESTONE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[GRAVEL >> BLOCK_TYPE_BITS] = new int[]{GRAVEL_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[POLISHED_STONE >> BLOCK_TYPE_BITS] = new int[]{POLISHED_STONE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[COURSE_DIRT >> BLOCK_TYPE_BITS] = new int[]{COURSE_DIRT_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[CHISELED_STONE >> BLOCK_TYPE_BITS] = new int[]{CHISELED_STONE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[CHISELED_POLISHED_STONE >> BLOCK_TYPE_BITS] = new int[]{CHISELED_POLISHED_STONE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[ICE >> BLOCK_TYPE_BITS] = new int[]{ICE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[CLAY >> BLOCK_TYPE_BITS] = new int[]{CLAY_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[MOSS >> BLOCK_TYPE_BITS] = new int[]{MOSS_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[HEAVY_ICE >> BLOCK_TYPE_BITS] = new int[]{HEAVY_ICE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[CHISELED_SLATE >> BLOCK_TYPE_BITS] = new int[]{CHISELED_SLATE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[COAL_ORE >> BLOCK_TYPE_BITS] = new int[]{COAL_ORE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[IRON_ORE >> BLOCK_TYPE_BITS] = new int[]{IRON_ORE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[DIAMOND_ORE >> BLOCK_TYPE_BITS] = new int[]{DIAMOND_ORE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[OAK_PLANKS >> BLOCK_TYPE_BITS] = new int[]{OAK_PLANKS_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[SPRUCE_PLANKS >> BLOCK_TYPE_BITS] = new int[]{SPRUCE_PLANKS_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[DARK_OAK_PLANKS >> BLOCK_TYPE_BITS] = new int[]{DARK_OAK_PLANKS_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[OAK_LEAVES >> BLOCK_TYPE_BITS] = new int[]{OAK_LEAVES_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[SPRUCE_LEAVES >> BLOCK_TYPE_BITS] = new int[]{SPRUCE_LEAVES_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[DARK_OAK_LEAVES >> BLOCK_TYPE_BITS] = new int[]{DARK_OAK_LEAVES_TEXTURE};

        STANDARD_BLOCK_TEXTURE_INDICES[UP_DOWN_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{OAK_LOG_TEXTURE, OAK_LOG_TOP_TEXTURE, OAK_LOG_TEXTURE, OAK_LOG_TEXTURE, OAK_LOG_TOP_TEXTURE, OAK_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[FRONT_BACK_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{OAK_LOG_TOP_TEXTURE, ROTATED_OAK_LOG_TEXTURE, ROTATED_OAK_LOG_TEXTURE, OAK_LOG_TOP_TEXTURE, OAK_LOG_TEXTURE, ROTATED_OAK_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[LEFT_RIGHT_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{ROTATED_OAK_LOG_TEXTURE, OAK_LOG_TEXTURE, OAK_LOG_TOP_TEXTURE, ROTATED_OAK_LOG_TEXTURE, ROTATED_OAK_LOG_TEXTURE, OAK_LOG_TOP_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[UP_DOWN_STRIPPED_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{STRIPPED_OAK_LOG_TEXTURE, STRIPPED_OAK_LOG_TOP_TEXTURE, STRIPPED_OAK_LOG_TEXTURE, STRIPPED_OAK_LOG_TEXTURE, STRIPPED_OAK_LOG_TOP_TEXTURE, STRIPPED_OAK_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[FRONT_BACK_STRIPPED_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{STRIPPED_OAK_LOG_TOP_TEXTURE, ROTATED_STRIPPED_OAK_LOG_TEXTURE, ROTATED_STRIPPED_OAK_LOG_TEXTURE, STRIPPED_OAK_LOG_TOP_TEXTURE, STRIPPED_OAK_LOG_TEXTURE, ROTATED_STRIPPED_OAK_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[LEFT_RIGHT_STRIPPED_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{ROTATED_STRIPPED_OAK_LOG_TEXTURE, STRIPPED_OAK_LOG_TEXTURE, STRIPPED_OAK_LOG_TOP_TEXTURE, ROTATED_STRIPPED_OAK_LOG_TEXTURE, ROTATED_STRIPPED_OAK_LOG_TEXTURE, STRIPPED_OAK_LOG_TOP_TEXTURE};

        STANDARD_BLOCK_TEXTURE_INDICES[UP_DOWN_SPRUCE_LOG >> BLOCK_TYPE_BITS] = new int[]{SPRUCE_LOG_TEXTURE, SPRUCE_LOG_TOP_TEXTURE, SPRUCE_LOG_TEXTURE, SPRUCE_LOG_TEXTURE, SPRUCE_LOG_TOP_TEXTURE, SPRUCE_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[FRONT_BACK_SPRUCE_LOG >> BLOCK_TYPE_BITS] = new int[]{SPRUCE_LOG_TOP_TEXTURE, ROTATED_SPRUCE_LOG_TEXTURE, ROTATED_SPRUCE_LOG_TEXTURE, SPRUCE_LOG_TOP_TEXTURE, SPRUCE_LOG_TEXTURE, ROTATED_SPRUCE_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[LEFT_RIGHT_SPRUCE_LOG >> BLOCK_TYPE_BITS] = new int[]{ROTATED_SPRUCE_LOG_TEXTURE, SPRUCE_LOG_TEXTURE, SPRUCE_LOG_TOP_TEXTURE, ROTATED_SPRUCE_LOG_TEXTURE, ROTATED_SPRUCE_LOG_TEXTURE, SPRUCE_LOG_TOP_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[UP_DOWN_STRIPPED_SPRUCE_LOG >> BLOCK_TYPE_BITS] = new int[]{STRIPPED_SPRUCE_LOG_TEXTURE, STRIPPED_SPRUCE_LOG_TOP_TEXTURE, STRIPPED_SPRUCE_LOG_TEXTURE, STRIPPED_SPRUCE_LOG_TEXTURE, STRIPPED_SPRUCE_LOG_TOP_TEXTURE, STRIPPED_SPRUCE_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[FRONT_BACK_STRIPPED_SPRUCE_LOG >> BLOCK_TYPE_BITS] = new int[]{STRIPPED_SPRUCE_LOG_TOP_TEXTURE, ROTATED_STRIPPED_SPRUCE_LOG_TEXTURE, ROTATED_STRIPPED_SPRUCE_LOG_TEXTURE, STRIPPED_SPRUCE_LOG_TOP_TEXTURE, STRIPPED_SPRUCE_LOG_TEXTURE, ROTATED_STRIPPED_SPRUCE_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[LEFT_RIGHT_STRIPPED_SPRUCE_LOG >> BLOCK_TYPE_BITS] = new int[]{ROTATED_STRIPPED_SPRUCE_LOG_TEXTURE, STRIPPED_SPRUCE_LOG_TEXTURE, STRIPPED_SPRUCE_LOG_TOP_TEXTURE, ROTATED_STRIPPED_SPRUCE_LOG_TEXTURE, ROTATED_STRIPPED_SPRUCE_LOG_TEXTURE, STRIPPED_SPRUCE_LOG_TOP_TEXTURE};

        STANDARD_BLOCK_TEXTURE_INDICES[UP_DOWN_DARK_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{DARK_OAK_LOG_TEXTURE, DARK_OAK_LOG_TOP_TEXTURE, DARK_OAK_LOG_TEXTURE, DARK_OAK_LOG_TEXTURE, DARK_OAK_LOG_TOP_TEXTURE, DARK_OAK_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[FRONT_BACK_DARK_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{DARK_OAK_LOG_TOP_TEXTURE, ROTATED_DARK_OAK_LOG_TEXTURE, ROTATED_DARK_OAK_LOG_TEXTURE, DARK_OAK_LOG_TOP_TEXTURE, DARK_OAK_LOG_TEXTURE, ROTATED_DARK_OAK_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[LEFT_RIGHT_DARK_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{ROTATED_DARK_OAK_LOG_TEXTURE, DARK_OAK_LOG_TEXTURE, DARK_OAK_LOG_TOP_TEXTURE, ROTATED_DARK_OAK_LOG_TEXTURE, ROTATED_DARK_OAK_LOG_TEXTURE, DARK_OAK_LOG_TOP_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[UP_DOWN_STRIPPED_DARK_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{STRIPPED_DARK_OAK_LOG_TEXTURE, STRIPPED_DARK_OAK_LOG_TOP_TEXTURE, STRIPPED_DARK_OAK_LOG_TEXTURE, STRIPPED_DARK_OAK_LOG_TEXTURE, STRIPPED_DARK_OAK_LOG_TOP_TEXTURE, STRIPPED_DARK_OAK_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[FRONT_BACK_STRIPPED_DARK_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{STRIPPED_DARK_OAK_LOG_TOP_TEXTURE, ROTATED_STRIPPED_DARK_OAK_LOG_TEXTURE, ROTATED_STRIPPED_DARK_OAK_LOG_TEXTURE, STRIPPED_DARK_OAK_LOG_TOP_TEXTURE, STRIPPED_DARK_OAK_LOG_TEXTURE, ROTATED_STRIPPED_DARK_OAK_LOG_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[LEFT_RIGHT_STRIPPED_DARK_OAK_LOG >> BLOCK_TYPE_BITS] = new int[]{ROTATED_STRIPPED_DARK_OAK_LOG_TEXTURE, STRIPPED_DARK_OAK_LOG_TEXTURE, STRIPPED_DARK_OAK_LOG_TOP_TEXTURE, ROTATED_STRIPPED_DARK_OAK_LOG_TEXTURE, ROTATED_STRIPPED_DARK_OAK_LOG_TEXTURE, STRIPPED_DARK_OAK_LOG_TOP_TEXTURE};

        STANDARD_BLOCK_TEXTURE_INDICES[BLACK >> BLOCK_TYPE_BITS] = new int[]{BLACK_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[WHITE >> BLOCK_TYPE_BITS] = new int[]{WHITE_TEXTURE};
        STANDARD_BLOCK_PROPERTIES[WHITE >> BLOCK_TYPE_BITS] = LIGHT_EMITTING_MASK;
        STANDARD_BLOCK_TEXTURE_INDICES[CYAN >> BLOCK_TYPE_BITS] = new int[]{CYAN_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[MAGENTA >> BLOCK_TYPE_BITS] = new int[]{MAGENTA_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[YELLOW >> BLOCK_TYPE_BITS] = new int[]{YELLOW_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[BLUE >> BLOCK_TYPE_BITS] = new int[]{BLUE_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[GREEN >> BLOCK_TYPE_BITS] = new int[]{GREEN_TEXTURE};
        STANDARD_BLOCK_TEXTURE_INDICES[RED >> BLOCK_TYPE_BITS] = new int[]{RED_TEXTURE};


        BLOCK_TYPE_OCCLUSION_DATA[AIR_TYPE] = 0b00000000;
        BLOCK_TYPE_DATA[AIR_TYPE] = 0b00000000;

        BLOCK_TYPE_OCCLUSION_DATA[FULL_BLOCK] = 0b00111111;
        BLOCK_TYPE_DATA[FULL_BLOCK] = 0b01111111;

        BLOCK_TYPE_OCCLUSION_DATA[LEAVE_TYPE] = 0b00000000;
        BLOCK_TYPE_DATA[LEAVE_TYPE] = 0b00111111;

        BLOCK_TYPE_OCCLUSION_DATA[GLASS_TYPE] = 0b01111111;
        BLOCK_TYPE_DATA[GLASS_TYPE] = 0b00111111;

        BLOCK_TYPE_OCCLUSION_DATA[WATER_TYPE] = (byte) 0b11111111;
        BLOCK_TYPE_DATA[WATER_TYPE] = (byte) 0b10111111;

        BLOCK_TYPE_OCCLUSION_DATA[BOTTOM_SLAB] = (byte) 0b10010000;
        BLOCK_TYPE_DATA[BOTTOM_SLAB] = 0b00111101;

        BLOCK_TYPE_OCCLUSION_DATA[TOP_SLAB] = (byte) 0b10000010;
        BLOCK_TYPE_DATA[TOP_SLAB] = 0b00101111;

        BLOCK_TYPE_OCCLUSION_DATA[FRONT_SLAB] = (byte) 0b10000001;
        BLOCK_TYPE_DATA[FRONT_SLAB] = 0b00110111;

        BLOCK_TYPE_OCCLUSION_DATA[BACK_SLAB] = (byte) 0b10001000;
        BLOCK_TYPE_DATA[BACK_SLAB] = 0b00111110;

        BLOCK_TYPE_OCCLUSION_DATA[LEFT_SLAB] = (byte) 0b10100000;
        BLOCK_TYPE_DATA[LEFT_SLAB] = 0b00111011;

        BLOCK_TYPE_OCCLUSION_DATA[RIGHT_SLAB] = (byte) 0b10000100;
        BLOCK_TYPE_DATA[RIGHT_SLAB] = 0b00011111;

        BLOCK_TYPE_OCCLUSION_DATA[UP_DOWN_POST] = (byte) 0b10000000;
        BLOCK_TYPE_DATA[UP_DOWN_POST] = 0b00010010;

        BLOCK_TYPE_OCCLUSION_DATA[FRONT_BACK_POST] = (byte) 0b10000000;
        BLOCK_TYPE_DATA[FRONT_BACK_POST] = 0b00001001;

        BLOCK_TYPE_OCCLUSION_DATA[LEFT_RIGHT_POST] = (byte) 0b10000000;
        BLOCK_TYPE_DATA[LEFT_RIGHT_POST] = 0b00100100;

        BLOCK_TYPE_OCCLUSION_DATA[UP_DOWN_WALL] = (byte) 0b10000000;
        BLOCK_TYPE_DATA[UP_DOWN_WALL] = 0b00101101;

        BLOCK_TYPE_OCCLUSION_DATA[FRONT_BACK_WALL] = (byte) 0b10000000;
        BLOCK_TYPE_DATA[FRONT_BACK_WALL] = 0b00110110;

        BLOCK_TYPE_OCCLUSION_DATA[LEFT_RIGHT_WALL] = (byte) 0b10000000;
        BLOCK_TYPE_DATA[LEFT_RIGHT_WALL] = 0b00011011;

        BLOCK_TYPE_OCCLUSION_DATA[BOTTOM_PLATE] = (byte) 0b10010000;
        BLOCK_TYPE_DATA[BOTTOM_PLATE] = 0b00111101;

        BLOCK_TYPE_OCCLUSION_DATA[TOP_PLATE] = (byte) 0b10000010;
        BLOCK_TYPE_DATA[TOP_PLATE] = 0b00101111;

        BLOCK_TYPE_OCCLUSION_DATA[FRONT_PLATE] = (byte) 0b10000001;
        BLOCK_TYPE_DATA[FRONT_PLATE] = 0b00110111;

        BLOCK_TYPE_OCCLUSION_DATA[BACK_PLATE] = (byte) 0b10001000;
        BLOCK_TYPE_DATA[BACK_PLATE] = 0b00111110;

        BLOCK_TYPE_OCCLUSION_DATA[LEFT_PLATE] = (byte) 0b10100000;
        BLOCK_TYPE_DATA[LEFT_PLATE] = 0b00111011;

        BLOCK_TYPE_OCCLUSION_DATA[RIGHT_PLATE] = (byte) 0b10000100;
        BLOCK_TYPE_DATA[RIGHT_PLATE] = 0b00011111;

        BLOCK_TYPE_OCCLUSION_DATA[PLAYER_HEAD] = (byte) 0b00000000;
        BLOCK_TYPE_DATA[PLAYER_HEAD] = 0b00010000;

        BLOCK_TYPE_OCCLUSION_DATA[CACTUS_TYPE] = (byte) 0b10000000;
        BLOCK_TYPE_DATA[CACTUS_TYPE] = (byte) 0b10010010;


        BLOCK_TYPE_XYZ_SUB_DATA[CACTUS_TYPE] = new byte[]{1, -1, 0, 0, 1, -1};

        BLOCK_TYPE_UV_SUB_DATA[CACTUS_TYPE] = new byte[]{1, 0, -1, 0, 1, 0, -1, 0, 1, 1, -1, 1, 1, -1, -1, -1, 1, 0, -1, 0, 1, 0, -1, 0, 1, 0, -1, 0, 1, 0, -1, 0, -1, -1, -1, 1, 1, -1, 1, 1, -1, 0, 1, 0, -1, 0, 1, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[PLAYER_HEAD] = new byte[]{4, -4, 0, -8, 4, -4};

        BLOCK_TYPE_UV_SUB_DATA[PLAYER_HEAD] = new byte[]{4, 4, -4, 4, 4, -4, -4, -4, 4, 4, -4, 4, 4, -4, -4, -4, 4, 4, -4, 4, 4, -4, -4, -4, 4, 4, -4, 4, 4, -4, -4, -4, -4, -4, -4, 4, 4, -4, 4, 4, -4, 4, 4, 4, -4, -4, 4, -4};

        BLOCK_TYPE_XYZ_SUB_DATA[UP_DOWN_WALL] = new byte[]{0, 0, 4, -4, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[UP_DOWN_WALL] = new byte[]{0, 4, 0, 4, 0, -4, 0, -4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 4, 0, -4, 0, -4, 0, 4, 0, 4, 0, -4, 0, -4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 4, 0, -4, 0, -4};

        BLOCK_TYPE_XYZ_SUB_DATA[FRONT_BACK_WALL] = new byte[]{0, 0, 0, 0, 4, -4};

        BLOCK_TYPE_UV_SUB_DATA[FRONT_BACK_WALL] = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 4, 0, -4, 0, 4, 0, -4, 0, 4, 0, -4, 0, 4, 0, -4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -4, 0, 4, 0, -4, 0, 4, -4, 0, 4, 0, -4, 0, 4, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[LEFT_RIGHT_WALL] = new byte[]{4, -4, 0, 0, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[LEFT_RIGHT_WALL] = new byte[]{4, 0, -4, 0, 4, 0, -4, 0, 0, 4, 0, 4, 0, -4, 0, -4, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, -4, 0, 4, 0, -4, 0, -4, 0, -4, 0, 4, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[LEFT_RIGHT_POST] = new byte[]{0, 0, 4, -4, 4, -4};

        BLOCK_TYPE_UV_SUB_DATA[LEFT_RIGHT_POST] = new byte[]{0, 0, 0, 0, 0, -8, 0, -8, 4, 0, -4, 0, 4, 0, -4, 0, 4, 4, -4, 4, 4, -4, -4, -4, 0, 8, 0, 8, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 8, -4, 4, 4, 4, -4, -4, 4, -4};

        BLOCK_TYPE_XYZ_SUB_DATA[FRONT_BACK_POST] = new byte[]{4, -4, 4, -4, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[FRONT_BACK_POST] = new byte[]{4, 4, -4, 4, 4, -4, -4, -4, 0, 8, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 0, -8, 4, 4, -4, 4, 4, -4, -4, -4, -8, 0, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 0, -8};

        BLOCK_TYPE_XYZ_SUB_DATA[UP_DOWN_POST] = new byte[]{4, -4, 0, 0, 4, -4};

        BLOCK_TYPE_UV_SUB_DATA[UP_DOWN_POST] = new byte[]{0, 0, -8, 0, 0, 0, -8, 0, 4, 4, -4, 4, 4, -4, -4, -4, 8, 0, 0, 0, 8, 0, 0, 0, 0, 0, -8, 0, 0, 0, -8, 0, -4, -4, -4, 4, 4, -4, 4, 4, 0, 0, 8, 0, 0, 0, 8, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[BOTTOM_SLAB] = new byte[]{0, 0, 0, -8, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[BOTTOM_SLAB] = new byte[]{0, 8, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 8, 0, 0, 0, 0, 0, 8, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 8, 0, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[TOP_SLAB] = new byte[]{0, 0, 8, 0, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[TOP_SLAB] = new byte[]{0, 0, 0, 0, 0, -8, 0, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 0, -8, 0, 0, 0, 0, 0, -8, 0, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 0, -8};

        BLOCK_TYPE_XYZ_SUB_DATA[FRONT_SLAB] = new byte[]{0, 0, 0, 0, 8, 0};

        BLOCK_TYPE_UV_SUB_DATA[FRONT_SLAB] = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 0, 0, 0, -8, 0, 0, -8, 0, 0, 0, -8, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[BACK_SLAB] = new byte[]{0, 0, 0, 0, 0, -8};

        BLOCK_TYPE_UV_SUB_DATA[BACK_SLAB] = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 0, 0, 0, -8, 0, 0, 0, -8, 0, 0, 0, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 8, 0, 0, 0, 8, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[RIGHT_SLAB] = new byte[]{8, 0, 0, 0, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[RIGHT_SLAB] = new byte[]{0, 0, -8, 0, 0, 0, -8, 0, 0, 8, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, -8, 0, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[LEFT_SLAB] = new byte[]{0, -8, 0, 0, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[LEFT_SLAB] = new byte[]{8, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, -8, 0, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 0, 0, 0, -8, 0, 0, 0, 0, 0, 8, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[BOTTOM_PLATE] = new byte[]{0, 0, 0, -12, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[BOTTOM_PLATE] = new byte[]{0, 12, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 12, 0, 12, 0, 0, 0, 0, 0, 12, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 12, 0, 12, 0, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[TOP_PLATE] = new byte[]{0, 0, 12, 0, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[TOP_PLATE] = new byte[]{0, 0, 0, 0, 0, -12, 0, -12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -12, 0, -12, 0, 0, 0, 0, 0, -12, 0, -12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -12, 0, -12};

        BLOCK_TYPE_XYZ_SUB_DATA[FRONT_PLATE] = new byte[]{0, 0, 0, 0, 12, 0};

        BLOCK_TYPE_UV_SUB_DATA[FRONT_PLATE] = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 12, 0, 0, 0, 12, 0, 0, 0, 12, 0, 0, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -12, 0, 0, 0, -12, 0, 0, -12, 0, 0, 0, -12, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[BACK_PLATE] = new byte[]{0, 0, 0, 0, 0, -12};

        BLOCK_TYPE_UV_SUB_DATA[BACK_PLATE] = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -12, 0, 0, 0, -12, 0, 0, 0, -12, 0, 0, 0, -12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 12, 0, 0, 0, 12, 0, 0, 12, 0, 0, 0, 12, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[RIGHT_PLATE] = new byte[]{12, 0, 0, 0, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[RIGHT_PLATE] = new byte[]{0, 0, -12, 0, 0, 0, -12, 0, 0, 12, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, -12, 0, -12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[LEFT_PLATE] = new byte[]{0, -12, 0, 0, 0, 0};

        BLOCK_TYPE_UV_SUB_DATA[LEFT_PLATE] = new byte[]{12, 0, 0, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, -12, 0, -12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -12, 0, 0, 0, -12, 0, 0, 0, 0, 0, 12, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        BLOCK_TYPE_XYZ_SUB_DATA[FULL_BLOCK] = new byte[]{0, 0, 0, 0, 0, 0};
        BLOCK_TYPE_XYZ_SUB_DATA[LEAVE_TYPE] = new byte[]{0, 0, 0, 0, 0, 0};
        BLOCK_TYPE_XYZ_SUB_DATA[GLASS_TYPE] = new byte[]{0, 0, 0, 0, 0, 0};
        BLOCK_TYPE_XYZ_SUB_DATA[WATER_TYPE] = new byte[]{0, 0, 0, -2, 0, 0};
    }
}
