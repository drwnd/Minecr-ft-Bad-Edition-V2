package terrascape.generation;

import terrascape.core.Block;
import terrascape.dataStorage.Chunk;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Random;

import static terrascape.utils.Constants.*;

public class MeshGenerator {

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
        worldCoordinate = chunk.getWorldCoordinate();
    }

    public void generateMesh() {
        chunk.setMeshed(true);

        chunk.generateSurroundingChunks();
        if (chunk.getLightLength() != 1) chunk.optimizeLightStorage();
        if (chunk.getBlockLength() != 1) chunk.optimizeBlockStorage();
        chunk.generateOcclusionCullingData();

        if (chunk.getBlockLength() == 1 && chunk.getSaveBlock(0) == AIR) return;

        @SuppressWarnings("unchecked")
        ArrayList<Integer>[] verticesLists = new ArrayList[6];
        ArrayList<Integer> foliageVerticesList = new ArrayList<>();
        ArrayList<Integer> waterVerticesList = new ArrayList<>();
        for (int side = 0; side < 6; side++) verticesLists[side] = new ArrayList<>();

        for (blockX = 0; blockX < CHUNK_SIZE; blockX++)
            for (blockZ = 0; blockZ < CHUNK_SIZE; blockZ++)
                for (blockY = 0; blockY < CHUNK_SIZE; blockY++) {

                    block = chunk.getSaveBlock(blockX, blockY, blockZ);

                    int blockType = Block.getBlockType(block);
                    if (blockType == AIR_TYPE) continue;

                    if (blockType == FLOWER_TYPE) {
                        list = foliageVerticesList;
                        addFlowerToList();
                        continue;
                    }

                    int faceCount = Block.getFaceCount(blockType);

                    if (block != WATER) addOpaqueBlock(faceCount, foliageVerticesList, verticesLists);
                    if (Block.isWaterLogged(block)) addWaterBlock(waterVerticesList);
                }

        for (int side = 0; side < 6; side++) {
            ArrayList<Integer> sideVertices = verticesLists[side];
            int[] vertices = new int[sideVertices.size()];
            for (int i = 0, size = sideVertices.size(); i < size; i++)
                vertices[i] = sideVertices.get(i);
            chunk.setVertices(vertices, side);
        }

        int[] waterVertices = new int[waterVerticesList.size()];
        for (int i = 0, size = waterVerticesList.size(); i < size; i++)
            waterVertices[i] = waterVerticesList.get(i);
        chunk.setWaterVertices(waterVertices);

        int[] foliageVertices = new int[foliageVerticesList.size()];
        for (int i = 0, size = foliageVerticesList.size(); i < size; i++)
            foliageVertices[i] = foliageVerticesList.get(i);
        chunk.setFoliageVertices(foliageVertices);
    }

    private void addOpaqueBlock(int faceCount, ArrayList<Integer> foliageVerticesList, ArrayList<Integer>[] verticesLists) {
        for (aabbIndex = 0; aabbIndex < faceCount; aabbIndex += 6)
            for (side = 0; side < 6; side++) {
                byte[] normal = Block.NORMALS[side];
                short occludingBlock = chunk.getBlock(blockX + normal[0], blockY + normal[1], blockZ + normal[2]);
                if (Block.occludesOpaque(block, occludingBlock, side, aabbIndex, worldCoordinate.x | blockX, worldCoordinate.y | blockY, worldCoordinate.z | blockZ))
                    continue;

                int texture = Block.getTextureIndex(block, side);

                int u = texture & 15;
                int v = texture >> 4 & 15;

                if (Block.isLeaveType(block)) addFoliageSideToList(foliageVerticesList, u, v);
                else addSideToList(u, v, verticesLists[side]);
            }
    }

    private void addWaterBlock(ArrayList<Integer> waterVerticesList) {
        for (side = 0; side < 6; side++) {
            if (block != WATER && Block.getBlockOcclusionData(block, side) == -1L) continue;

            byte[] normal = Block.NORMALS[side];
            short occludingBlock = chunk.getBlock(blockX + normal[0], blockY + normal[1], blockZ + normal[2]);

            if (Block.occludesWater(occludingBlock, side, worldCoordinate.x | blockX, worldCoordinate.y | blockY, worldCoordinate.z | blockZ))
                continue;

            addWaterSideToList(waterVerticesList);
        }
    }


    private void addSideToList(int u, int v, ArrayList<Integer> list) {
        this.list = list;

        switch (side) {
            case NORTH:
                addVertexToList(blockX + 1, blockY + 1, blockZ + 1, u + 1, v, 0);
                addVertexToList(blockX, blockY + 1, blockZ + 1, u, v, 1);
                addVertexToList(blockX + 1, blockY, blockZ + 1, u + 1, v + 1, 2);
                addVertexToList(blockX, blockY, blockZ + 1, u, v + 1, 3);
                break;
            case TOP:
                addVertexToList(blockX, blockY + 1, blockZ, u + 1, v, 0);
                addVertexToList(blockX, blockY + 1, blockZ + 1, u, v, 1);
                addVertexToList(blockX + 1, blockY + 1, blockZ, u + 1, v + 1, 2);
                addVertexToList(blockX + 1, blockY + 1, blockZ + 1, u, v + 1, 3);
                break;
            case WEST:
                addVertexToList(blockX + 1, blockY + 1, blockZ, u + 1, v, 0);
                addVertexToList(blockX + 1, blockY + 1, blockZ + 1, u, v, 1);
                addVertexToList(blockX + 1, blockY, blockZ, u + 1, v + 1, 2);
                addVertexToList(blockX + 1, blockY, blockZ + 1, u, v + 1, 3);
                break;
            case SOUTH:
                addVertexToList(blockX, blockY + 1, blockZ, u + 1, v, 0);
                addVertexToList(blockX + 1, blockY + 1, blockZ, u, v, 1);
                addVertexToList(blockX, blockY, blockZ, u + 1, v + 1, 2);
                addVertexToList(blockX + 1, blockY, blockZ, u, v + 1, 3);
                break;
            case BOTTOM:
                addVertexToList(blockX + 1, blockY, blockZ + 1, u + 1, v, 3);
                addVertexToList(blockX, blockY, blockZ + 1, u, v, 1);
                addVertexToList(blockX + 1, blockY, blockZ, u + 1, v + 1, 2);
                addVertexToList(blockX, blockY, blockZ, u, v + 1, 0);
                break;
            case EAST:
                addVertexToList(blockX, blockY + 1, blockZ + 1, u + 1, v, 1);
                addVertexToList(blockX, blockY + 1, blockZ, u, v, 0);
                addVertexToList(blockX, blockY, blockZ + 1, u + 1, v + 1, 3);
                addVertexToList(blockX, blockY, blockZ, u, v + 1, 2);
                break;
        }
    }

    private void addVertexToList(int inChunkX, int inChunkY, int inChunkZ, int u, int v, int corner) {
        int x = worldCoordinate.x + inChunkX;
        int y = worldCoordinate.y + inChunkY;
        int z = worldCoordinate.z + inChunkZ;

        int blockType = Block.getBlockType(block);
        int subX = Block.getSubX(blockType, side, corner, aabbIndex);
        int subY = Block.getSubY(blockType, side, corner, aabbIndex);
        int subZ = Block.getSubZ(blockType, side, corner, aabbIndex);

        int skyLight = getVertexSkyLightInWorld(x, y, z, subX, subY, subZ);
        int blockLight = getVertexBlockLightInWorld(x, y, z, subX, subY, subZ);

        if ((Block.getBlockTypeData(block) & DYNAMIC_SHAPE_MASK) != 0) {
            addVertexToListDynamic(inChunkX, inChunkY, inChunkZ, u, v, skyLight, blockLight, corner);
            return;
        }

        int subU = Block.getSubU(blockType, side, corner, aabbIndex);
        int subV = Block.getSubV(blockType, side, corner, aabbIndex);

        int ambientOcclusionLevel = getAmbientOcclusionLevel(inChunkX, inChunkY, inChunkZ, subX, subY, subZ);
        list.add(packData1(ambientOcclusionLevel, (inChunkX << 4) + subX + 15, (inChunkY << 4) + subY + 15, (inChunkZ << 4) + subZ + 15));
        list.add(packData2(skyLight, blockLight, (u << 4) + subU + 15, (v << 4) + subV + 15));
    }

    private void addVertexToListDynamic(int inChunkX, int inChunkY, int inChunkZ, int u, int v, int skyLight, int blockLight, int corner) {
        int subX = 0;
        int subY = 0;
        int subZ = 0;
        int subU = 0;
        int subV = 0;

        if (Block.getBlockType(block) == LIQUID_TYPE) {
            short blockAbove = chunk.getBlock(blockX, blockY + 1, blockZ);
            if (side == TOP) {
                if (Block.getBlockType(blockAbove) != LIQUID_TYPE && !Block.isWaterLogged(block) && Block.getBlockOcclusionData(blockAbove, BOTTOM) != -1L)
                    subY = -2;
            } else if (side != BOTTOM) {
                if ((corner == 0 || corner == 1) && blockAbove != block && Block.getBlockOcclusionData(blockAbove, BOTTOM) != -1L) {
                    subY = -2;
                    subV = 2;
                } else if (corner == 2 || corner == 3) {
                    byte[] normal = Block.NORMALS[side];
                    short adjacentBlock = chunk.getBlock(blockX + normal[0], blockY, blockZ + normal[2]);
                    if (adjacentBlock == block && (blockAbove == block || Block.getBlockOcclusionData(blockAbove, BOTTOM) != 0)) {
                        subY = 14;
                        subV = -14;
                    }
                }
            }
        } else if (Block.getBlockType(block) == CACTUS_TYPE) {
            int blockType = Block.getBlockType(block);
            switch (side) {
                case TOP, BOTTOM -> {
                    subX = Block.getSubX(blockType, side, corner, 0);
                    subZ = Block.getSubZ(blockType, side, corner, 0);
                    subU = Block.getSubU(blockType, side, corner, 0);
                    subV = Block.getSubV(blockType, side, corner, 0);
                }
                case NORTH, SOUTH -> subZ = Block.getSubZ(blockType, side, corner, 0);

                case WEST, EAST -> subX = Block.getSubX(blockType, side, corner, 0);
            }
        }
        int ambientOcclusionLevel = getAmbientOcclusionLevel(inChunkX, inChunkY, inChunkZ, subX, subY, subZ);
        list.add(packData1(ambientOcclusionLevel, (inChunkX << 4) + subX + 15, (inChunkY << 4) + subY + 15, (inChunkZ << 4) + subZ + 15));
        list.add(packData2(skyLight, blockLight, (u << 4) + subU + 15, (v << 4) + subV + 15));
    }


    private void addWaterSideToList(ArrayList<Integer> list) {
        this.list = list;
        int u = (byte) 64 & 15;
        int v = (byte) 64 >> 4 & 15;

        switch (side) {
            case NORTH:
                addWaterVertexToList(blockX + 1, blockY + 1, blockZ + 1, u + 1, v, 0, Block.SIDES_WITH_CORNER[1]);
                addWaterVertexToList(blockX, blockY + 1, blockZ + 1, u, v, 1, Block.SIDES_WITH_CORNER[0]);
                addWaterVertexToList(blockX + 1, blockY, blockZ + 1, u + 1, v + 1, 2, Block.SIDES_WITH_CORNER[5]);
                addWaterVertexToList(blockX, blockY, blockZ + 1, u, v + 1, 3, Block.SIDES_WITH_CORNER[4]);
                break;
            case TOP:
                addWaterVertexToList(blockX, blockY + 1, blockZ, u + 1, v, 0, Block.SIDES_WITH_CORNER[2]);
                addWaterVertexToList(blockX, blockY + 1, blockZ + 1, u, v, 1, Block.SIDES_WITH_CORNER[0]);
                addWaterVertexToList(blockX + 1, blockY + 1, blockZ, u + 1, v + 1, 2, Block.SIDES_WITH_CORNER[3]);
                addWaterVertexToList(blockX + 1, blockY + 1, blockZ + 1, u, v + 1, 3, Block.SIDES_WITH_CORNER[1]);
                break;
            case WEST:
                addWaterVertexToList(blockX + 1, blockY + 1, blockZ, u + 1, v, 0, Block.SIDES_WITH_CORNER[3]);
                addWaterVertexToList(blockX + 1, blockY + 1, blockZ + 1, u, v, 1, Block.SIDES_WITH_CORNER[1]);
                addWaterVertexToList(blockX + 1, blockY, blockZ, u + 1, v + 1, 2, Block.SIDES_WITH_CORNER[7]);
                addWaterVertexToList(blockX + 1, blockY, blockZ + 1, u, v + 1, 3, Block.SIDES_WITH_CORNER[5]);
                break;
            case SOUTH:
                addWaterVertexToList(blockX, blockY + 1, blockZ, u + 1, v, 0, Block.SIDES_WITH_CORNER[2]);
                addWaterVertexToList(blockX + 1, blockY + 1, blockZ, u, v, 1, Block.SIDES_WITH_CORNER[3]);
                addWaterVertexToList(blockX, blockY, blockZ, u + 1, v + 1, 2, Block.SIDES_WITH_CORNER[6]);
                addWaterVertexToList(blockX + 1, blockY, blockZ, u, v + 1, 3, Block.SIDES_WITH_CORNER[7]);
                break;
            case BOTTOM:
                addWaterVertexToList(blockX + 1, blockY, blockZ + 1, u + 1, v, 3, Block.SIDES_WITH_CORNER[5]);
                addWaterVertexToList(blockX, blockY, blockZ + 1, u, v, 1, Block.SIDES_WITH_CORNER[4]);
                addWaterVertexToList(blockX + 1, blockY, blockZ, u + 1, v + 1, 2, Block.SIDES_WITH_CORNER[7]);
                addWaterVertexToList(blockX, blockY, blockZ, u, v + 1, 0, Block.SIDES_WITH_CORNER[6]);
                break;
            case EAST:
                addWaterVertexToList(blockX, blockY + 1, blockZ + 1, u + 1, v, 1, Block.SIDES_WITH_CORNER[0]);
                addWaterVertexToList(blockX, blockY + 1, blockZ, u, v, 0, Block.SIDES_WITH_CORNER[2]);
                addWaterVertexToList(blockX, blockY, blockZ + 1, u + 1, v + 1, 3, Block.SIDES_WITH_CORNER[4]);
                addWaterVertexToList(blockX, blockY, blockZ, u, v + 1, 2, Block.SIDES_WITH_CORNER[6]);
                break;
        }
    }

    private void addWaterVertexToList(int inChunkX, int inChunkY, int inChunkZ, int u, int v, int corner, byte[] adjacentSides) {
        int x = worldCoordinate.x + inChunkX;
        int y = worldCoordinate.y + inChunkY;
        int z = worldCoordinate.z + inChunkZ;

        int subX = 0;
        int subY = 0;
        int subZ = 0;
        int subU = 0;
        int subV = 0;

        boolean shouldSimulateWaves = false;
        short blockAbove = chunk.getBlock(blockX, blockY + 1, blockZ);

        if (side == TOP) {
            if (Block.getBlockOcclusionData(blockAbove, BOTTOM) != -1L) {
                subY = -2;
                shouldSimulateWaves = true;
            }
        } else if (side != BOTTOM) {
            if ((corner == 0 || corner == 1)) { // Top corners
                if (Block.isWaterLogged(blockAbove)) shouldSimulateWaves = true;
                else if (Block.getBlockOcclusionData(blockAbove, BOTTOM) != -1L) {
                    shouldSimulateWaves = true;
                    subY = -2;
                    subV = 2;
                }
            } else { // Bottom corners
                byte[] normal = Block.NORMALS[side];
                short adjacentBlock = chunk.getBlock(blockX + normal[0], blockY, blockZ + normal[2]);
                short blockBelow = chunk.getBlock(blockX, blockY - 1, blockZ);

                if (Block.isWaterLogged(adjacentBlock) && (Block.isWaterLogged(blockAbove) || Block.getBlockOcclusionData(blockAbove, BOTTOM) == -1L)) {
                    subY = 14;
                    subV = -14;
                    shouldSimulateWaves = true;
                }
                if (Block.isWaterLogged(blockBelow) || Block.getBlockOcclusionData(blockBelow, TOP) != -1)
                    shouldSimulateWaves = true;
            }
        } else {
            short blockBelow = chunk.getBlock(blockX, blockY - 1, blockZ);
            shouldSimulateWaves = Block.getBlockOcclusionData(blockBelow, TOP) != -1L;
        }

        if (block != WATER && Block.isWaterLogged(block))
            for (int side : adjacentSides) {
                if (Block.getBlockOcclusionData(block, side) != -1L) continue;
                shouldSimulateWaves = false;
                break;
            }

        if (inChunkX == 0 || inChunkX == CHUNK_SIZE || inChunkZ == 0 || inChunkZ == CHUNK_SIZE)
            shouldSimulateWaves = false;

        int skyLight = getVertexSkyLightInWorld(x, y, z, subX, subY, subZ);
        int blockLight = getVertexBlockLightInWorld(x, y, z, subX, subY, subZ);

        int ambientOcclusionLevel = getAmbientOcclusionLevel(inChunkX, inChunkY, inChunkZ, subX, subY, subZ);
        list.add(packData1(ambientOcclusionLevel, (inChunkX << 4) + subX + 15, (inChunkY << 4) + subY + 15, (inChunkZ << 4) + subZ + 15));
        list.add(packWaterData(shouldSimulateWaves ? 1 : 0, skyLight, blockLight, (u << 4) + subU + 15, (v << 4) + subV + 15));
    }


    private void addFoliageSideToList(ArrayList<Integer> list, int u, int v) {
        this.list = list;

        switch (side) {
            case NORTH:
                addFoliageVertexToList(blockX + 1, blockY + 1, blockZ + 1, u + 1, v, 0, aabbIndex);
                addFoliageVertexToList(blockX, blockY + 1, blockZ + 1, u, v, 1, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY, blockZ + 1, u + 1, v + 1, 2, aabbIndex);
                addFoliageVertexToList(blockX, blockY, blockZ + 1, u, v + 1, 3, aabbIndex);
                break;
            case TOP:
                addFoliageVertexToList(blockX, blockY + 1, blockZ, u + 1, v, 0, aabbIndex);
                addFoliageVertexToList(blockX, blockY + 1, blockZ + 1, u, v, 1, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY + 1, blockZ, u + 1, v + 1, 2, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY + 1, blockZ + 1, u, v + 1, 3, aabbIndex);
                break;
            case WEST:
                addFoliageVertexToList(blockX + 1, blockY + 1, blockZ, u + 1, v, 0, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY + 1, blockZ + 1, u, v, 1, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY, blockZ, u + 1, v + 1, 2, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY, blockZ + 1, u, v + 1, 3, aabbIndex);
                break;
            case SOUTH:
                addFoliageVertexToList(blockX, blockY + 1, blockZ, u + 1, v, 0, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY + 1, blockZ, u, v, 1, aabbIndex);
                addFoliageVertexToList(blockX, blockY, blockZ, u + 1, v + 1, 2, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY, blockZ, u, v + 1, 3, aabbIndex);
                break;
            case BOTTOM:
                addFoliageVertexToList(blockX + 1, blockY, blockZ + 1, u + 1, v, 3, aabbIndex);
                addFoliageVertexToList(blockX, blockY, blockZ + 1, u, v, 1, aabbIndex);
                addFoliageVertexToList(blockX + 1, blockY, blockZ, u + 1, v + 1, 2, aabbIndex);
                addFoliageVertexToList(blockX, blockY, blockZ, u, v + 1, 0, aabbIndex);
                break;
            case EAST:
                addFoliageVertexToList(blockX, blockY + 1, blockZ + 1, u + 1, v, 1, aabbIndex);
                addFoliageVertexToList(blockX, blockY + 1, blockZ, u, v, 0, aabbIndex);
                addFoliageVertexToList(blockX, blockY, blockZ + 1, u + 1, v + 1, 3, aabbIndex);
                addFoliageVertexToList(blockX, blockY, blockZ, u, v + 1, 2, aabbIndex);
                break;
        }
    }

    private void addFoliageVertexToList(int inChunkX, int inChunkY, int inChunkZ, int u, int v, int corner, int subDataAddend) {
        int x = worldCoordinate.x + inChunkX;
        int y = worldCoordinate.y + inChunkY;
        int z = worldCoordinate.z + inChunkZ;

        int blockType = Block.getBlockType(block);
        int subX = Block.getSubX(blockType, side, corner, subDataAddend);
        int subY = Block.getSubY(blockType, side, corner, subDataAddend);
        int subZ = Block.getSubZ(blockType, side, corner, subDataAddend);

        int skyLight = getVertexSkyLightInWorld(x, y, z, subX, subY, subZ);
        int blockLight = getVertexBlockLightInWorld(x, y, z, subX, subY, subZ);

        if ((Block.getBlockTypeData(block) & DYNAMIC_SHAPE_MASK) != 0) {
            addVertexToListDynamic(inChunkX, inChunkY, inChunkZ, u, v, skyLight, blockLight, corner);
            return;
        }

        int subU = Block.getSubU(blockType, side, corner, subDataAddend);
        int subV = Block.getSubV(blockType, side, corner, subDataAddend);

        int ambientOcclusionLevel = getAmbientOcclusionLevel(inChunkX, inChunkY, inChunkZ, subX, subY, subZ);
        list.add(packData1(ambientOcclusionLevel, (inChunkX << 4) + subX + 15, (inChunkY << 4) + subY + 15, (inChunkZ << 4) + subZ + 15));
        list.add(packFoliageData(1, skyLight, blockLight, (u << 4) + subU + 15, (v << 4) + subV + 15));
    }

    private void addFlowerToList() {
        int texture = Block.getTextureIndex(block, side);

        int u = texture & 15;
        int v = texture >> 4 & 15;

        int x = worldCoordinate.x + blockX;
        int y = worldCoordinate.y + blockY;
        int z = worldCoordinate.z + blockZ;

        int skyLight = Chunk.getSkyLightInWorld(x, y, z);
        int blockLight = Chunk.getBlockLightInWorld(x, y, z);

        side = TOP;

        random.setSeed((long) (z * 5135.64843) << 24 | (long) (x * 18941.484138) << 5);
        int randomX = (int) (random.nextDouble() * 8 - 4);
        int randomZ = (int) (random.nextDouble() * 8 - 4);
        int bottomWindMultiplier = Block.getBlockOcclusionData(Chunk.getBlockInWorld(x, y - 1, z), TOP) == -1 ? 0 : 1;
        int topWindMultiplier = 1;

        list.add(packData1(0, (blockX << 4) + 3 + 15 + randomX, (blockY + 1 << 4) + 15, (blockZ << 4) + 3 + 15 + randomZ));
        list.add(packFoliageData(topWindMultiplier, skyLight, blockLight, (u << 4) + 15, (v << 4) + 15));
        list.add(packData1(0, (blockX << 4) + 3 + 15 + randomX, (blockY << 4) + 15, (blockZ << 4) + 3 + 15 + randomZ));
        list.add(packFoliageData(bottomWindMultiplier, skyLight, blockLight, (u << 4) + 15, (v + 1 << 4) + 15));
        list.add(packData1(0, (blockX << 4) + 13 + 15 + randomX, (blockY + 1 << 4) + 15, (blockZ << 4) + 13 + 15 + randomZ));
        list.add(packFoliageData(topWindMultiplier, skyLight, blockLight, (u + 1 << 4) + 15, (v << 4) + 15));
        list.add(packData1(0, (blockX << 4) + 13 + 15 + randomX, (blockY << 4) + 15, (blockZ << 4) + 13 + 15 + randomZ));
        list.add(packFoliageData(bottomWindMultiplier, skyLight, blockLight, (u + 1 << 4) + 15, (v + 1 << 4) + 15));

        list.add(packData1(0, (blockX << 4) + 3 + 15 + randomX, (blockY + 1 << 4) + 15, (blockZ << 4) + 13 + 15 + randomZ));
        list.add(packFoliageData(topWindMultiplier, skyLight, blockLight, (u << 4) + 15, (v << 4) + 15));
        list.add(packData1(0, (blockX << 4) + 3 + 15 + randomX, (blockY << 4) + 15, (blockZ << 4) + 13 + 15 + randomZ));
        list.add(packFoliageData(bottomWindMultiplier, skyLight, blockLight, (u << 4) + 15, (v + 1 << 4) + 15));
        list.add(packData1(0, (blockX << 4) + 13 + 15 + randomX, (blockY + 1 << 4) + 15, (blockZ << 4) + 3 + 15 + randomZ));
        list.add(packFoliageData(topWindMultiplier, skyLight, blockLight, (u + 1 << 4) + 15, (v << 4) + 15));
        list.add(packData1(0, (blockX << 4) + 13 + 15 + randomX, (blockY << 4) + 15, (blockZ << 4) + 3 + 15 + randomZ));
        list.add(packFoliageData(bottomWindMultiplier, skyLight, blockLight, (u + 1 << 4) + 15, (v + 1 << 4) + 15));
    }


    private int packData1(int ambientOcclusionLevel, int inChunkX, int inChunkY, int inChunkZ) {
        return ambientOcclusionLevel << 30 | inChunkX << 20 | inChunkY << 10 | inChunkZ;
    }

    private int packData2(int skyLight, int blockLight, int u, int v) {
        return side << 26 | skyLight << 22 | blockLight << 18 | u << 9 | v;
    }

    private int packWaterData(int waveMultiplier, int skyLight, int blockLight, int u, int v) {
        return waveMultiplier << 29 | side << 26 | skyLight << 22 | blockLight << 18 | u << 9 | v;
    }

    private int packFoliageData(int windMultiplier, int skyLight, int blockLight, int u, int v) {
        return windMultiplier << 29 | side << 26 | skyLight << 22 | blockLight << 18 | u << 9 | v;
    }

    private int getAmbientOcclusionLevel(int inChunkX, int inChunkY, int inChunkZ, int subX, int subY, int subZ) {
        int level = 0;
        int x = worldCoordinate.x + inChunkX;
        int y = worldCoordinate.y + inChunkY;
        int z = worldCoordinate.z + inChunkZ;
        int startX = 0, startY = 0, startZ = 0;

        switch (side) {
            case NORTH -> {
                startX = subX == 0 ? x - 1 : subX < 0 ? --x : x;
                startY = subY == 0 ? y - 1 : subY < 0 ? --y : y;
                startZ = subZ != 0 ? --z : z;
            }
            case TOP -> {
                startX = subX == 0 ? x - 1 : subX < 0 ? --x : x;
                startY = subY != 0 ? --y : y;
                startZ = subZ == 0 ? z - 1 : subZ < 0 ? --z : z;
            }
            case WEST -> {
                startX = subX != 0 ? --x : x;
                startY = subY == 0 ? y - 1 : subY < 0 ? --y : y;
                startZ = subZ == 0 ? z - 1 : subZ < 0 ? --z : z;
            }
            case SOUTH -> {
                startX = subX == 0 ? x - 1 : subX < 0 ? --x : x;
                startY = subY == 0 ? y - 1 : subY < 0 ? --y : y;
                startZ = subZ != 0 ? z : --z;
            }
            case BOTTOM -> {
                startX = subX == 0 ? x - 1 : subX < 0 ? --x : x;
                startY = subY != 0 ? y : --y;
                startZ = subZ == 0 ? z - 1 : subZ < 0 ? --z : z;
            }
            case EAST -> {
                startX = subX != 0 ? x : --x;
                startY = subY == 0 ? y - 1 : subY < 0 ? --y : y;
                startZ = subZ == 0 ? z - 1 : subZ < 0 ? --z : z;
            }
        }

        for (int totalX = startX; totalX <= x; totalX++)
            for (int totalZ = startZ; totalZ <= z; totalZ++)
                for (int totalY = startY; totalY <= y; totalY++) {
                    if (totalX == (worldCoordinate.x | blockX) && totalY == (worldCoordinate.y | blockY) && totalZ == (worldCoordinate.z | blockZ))
                        continue;
                    if (Block.hasAmbientOcclusion(Chunk.getBlockInWorld(totalX, totalY, totalZ), block)) level++;
                }

        return Math.min(3, level);
    }

    public int getVertexBlockLightInWorld(int x, int y, int z, int subX, int subY, int subZ) {
        byte max = 0;
        int startX, startY, startZ;

        switch (side) {
            case NORTH -> {
                startX = x - 1;
                startY = y - 1;
                startZ = subZ == 0 ? z : ++z;
            }
            case SOUTH -> {
                startX = x - 1;
                startY = y - 1;
                startZ = subZ == 0 ? --z : z;
            }
            case TOP -> {
                startX = x - 1;
                startZ = z - 1;
                startY = subY == 0 ? y : ++y;
            }
            case BOTTOM -> {
                startX = x - 1;
                startZ = z - 1;
                startY = subY == 0 ? --y : y;
            }
            case WEST -> {
                startY = y - 1;
                startZ = z - 1;
                startX = subX == 0 ? x : ++x;
            }
            default -> { // EAST
                startY = y - 1;
                startZ = z - 1;
                startX = subX == 0 ? --x : x;
            }
        }

        for (int lightX = startX; lightX <= x; lightX++)
            for (int lightY = startY; lightY <= y; lightY++)
                for (int lightZ = startZ; lightZ <= z; lightZ++) {
                    byte currentBlockLight = Chunk.getBlockLightInWorld(lightX, lightY, lightZ);
                    if (max < currentBlockLight) max = currentBlockLight;
                }
        return max;
    }

    public int getVertexSkyLightInWorld(int x, int y, int z, int subX, int subY, int subZ) {
        byte max = 0;
        int startX, startY, startZ;

        switch (side) {
            case NORTH -> {
                startX = x - 1;
                startY = y - 1;
                startZ = subZ == 0 ? z : ++z;
            }
            case SOUTH -> {
                startX = x - 1;
                startY = y - 1;
                startZ = subZ == 0 ? --z : z;
            }
            case TOP -> {
                startX = x - 1;
                startZ = z - 1;
                startY = subY == 0 ? y : ++y;
            }
            case BOTTOM -> {
                startX = x - 1;
                startZ = z - 1;
                startY = subY == 0 ? --y : y;
            }
            case WEST -> {
                startY = y - 1;
                startZ = z - 1;
                startX = subX == 0 ? x : ++x;
            }
            default -> { // EAST
                startY = y - 1;
                startZ = z - 1;
                startX = subX == 0 ? --x : x;
            }
        }

        for (int lightX = startX; lightX <= x; lightX++)
            for (int lightY = startY; lightY <= y; lightY++)
                for (int lightZ = startZ; lightZ <= z; lightZ++) {
                    byte currentBlockLight = Chunk.getSkyLightInWorld(lightX, lightY, lightZ);
                    if (max < currentBlockLight) max = currentBlockLight;
                }
        return max;
    }

    private Chunk chunk;
    private Vector3i worldCoordinate;

    private int blockX, blockY, blockZ;
    private int side, aabbIndex;
    private short block;
    private ArrayList<Integer> list;
    private final Random random = new Random();
}