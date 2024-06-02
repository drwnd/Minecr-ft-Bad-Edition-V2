package com.MBEv2.test;

import com.MBEv2.core.*;
import com.MBEv2.core.entity.*;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;

import java.util.LinkedList;
import java.util.NoSuchElementException;

import static com.MBEv2.core.utils.Constants.*;

public class GameLogic {

    private static Texture atlas;
    public static LinkedList<Chunk> toBufferChunks;
    public static LinkedList<Model> toUnloadModels;
    private static ChunkGenerator generator;

    private static Player player;


    public static void init() throws Exception {

        toBufferChunks = new LinkedList<>();
        toUnloadModels = new LinkedList<>();
        generator = new ChunkGenerator();

        atlas = new Texture(ObjectLoader.loadTexture("textures/atlas256.png"));

        for (int x = -RENDER_DISTANCE_XZ; x < RENDER_DISTANCE_XZ; x++) {
            for (int y = 0; y < RENDER_DISTANCE_Y * 2; y++) {
                for (int z = -RENDER_DISTANCE_XZ; z < RENDER_DISTANCE_XZ; z++) {
                    int index = GameLogic.getChunkIndex(x, y, z);
                    Chunk chunk = Chunk.getChunk(index);
                    chunk.generateMesh();
                    bufferChunkMesh(chunk);
                }
            }
        }

        player = new Player(atlas);
        player.init();

        player.getRenderer().init();
    }

    public static void loadUnloadChunks() {
        unloadChunks();
        generator.continueRunning();
    }

    public static void placeBlock(byte block, Vector3i position) {
        if (position == null)
            return;

        int chunkX = position.x >> 5;
        int chunkY = position.y >> 5;
        int chunkZ = position.z >> 5;

        int inChunkX = position.x & 31;
        int inChunkY = position.y & 31;
        int inChunkZ = position.z & 31;

        Chunk chunk = Chunk.getChunk(chunkX, chunkY, chunkZ);
        chunk.storeSave(inChunkX, inChunkY, inChunkZ, block);
        chunk.setModified();
        regenerateChunkMesh(chunk);

        if (inChunkX == 0) {
            Chunk neighbour = Chunk.getChunk(chunkX - 1, chunkY, chunkZ);
            if (neighbour != null)
                regenerateChunkMesh(neighbour);
        } else if (inChunkX == CHUNK_SIZE - 1) {
            Chunk neighbour = Chunk.getChunk(chunkX + 1, chunkY, chunkZ);
            if (neighbour != null)
                regenerateChunkMesh(neighbour);
        }

        if (inChunkY == 0) {
            Chunk neighbour = Chunk.getChunk(chunkX, chunkY - 1, chunkZ);
            if (neighbour != null)
                regenerateChunkMesh(neighbour);
        } else if (inChunkY == CHUNK_SIZE - 1) {
            Chunk neighbour = Chunk.getChunk(chunkX, chunkY + 1, chunkZ);
            if (neighbour != null)
                regenerateChunkMesh(neighbour);
        }

        if (inChunkZ == 0) {
            Chunk neighbour = Chunk.getChunk(chunkX, chunkY, chunkZ - 1);
            if (neighbour != null)
                regenerateChunkMesh(neighbour);
        } else if (inChunkZ == CHUNK_SIZE - 1) {
            Chunk neighbour = Chunk.getChunk(chunkX, chunkY, chunkZ + 1);
            if (neighbour != null)
                regenerateChunkMesh(neighbour);
        }
    }

    public static void regenerateChunkMesh(Chunk chunk) {
        chunk.generateMesh();

        deleteChunkMeshBuffers(chunk);
        bufferChunkMesh(chunk);

    }

    public static void bufferChunkMesh(Chunk chunk) {
        if (chunk.getVertices().length != 0) {
            Model model = ObjectLoader.loadModel(chunk.getVertices(), chunk.getWorldCoordinate());
            model.setTexture(atlas);
            chunk.setModel(model);
        } else
            chunk.setModel(null);

        if (chunk.getTransparentVertices().length != 0) {
            Model transparentModel = ObjectLoader.loadModel(chunk.getTransparentVertices(), chunk.getWorldCoordinate());
            transparentModel.setTexture(atlas);
            chunk.setTransparentModel(transparentModel);
        } else
            chunk.setTransparentModel(null);

        chunk.clearMesh();
    }

    public static void update() {
        for (int i = 0; i < MAX_CHUNKS_TO_BUFFER_PER_FRAME && !toBufferChunks.isEmpty(); i++) {
            Chunk chunk;
            try {
                chunk = toBufferChunks.removeFirst();
            } catch (NoSuchElementException e) {
                System.out.println("buffer chunks " + toBufferChunks.size());
                break;
            }
            deleteChunkMeshBuffers(chunk);
            bufferChunkMesh(chunk);
        }

        while (!toUnloadModels.isEmpty()) {
            Model model;
            try {
                model = toUnloadModels.removeFirst();
            } catch (NoSuchElementException e) {
                System.out.println("unload models " + toUnloadModels.size());
                break;
            }
            if (model != null)
                deleteModelBuffers(model);

        }

        player.update();
    }

    public static void unloadChunks() {
        Vector3f cameraPosition = player.getCamera().getPosition();

        int chunkX = (int) Math.floor(cameraPosition.x) >> 5;
        int chunkY = (int) Math.floor(cameraPosition.y) >> 5;
        int chunkZ = (int) Math.floor(cameraPosition.z) >> 5;

        for (Chunk chunk : Chunk.getWorld()) {
            if (chunk == null)
                continue;

            if (Math.abs(chunk.getX() - chunkX) <= RENDER_DISTANCE_XZ + 2 && Math.abs(chunk.getZ() - chunkZ) <= RENDER_DISTANCE_XZ + 2 && Math.abs(chunk.getY() - chunkY) <= RENDER_DISTANCE_Y + 2)
                continue;

            toBufferChunks.remove(chunk);
            deleteChunkMeshBuffers(chunk);
            Chunk.storeChunk(null, chunk.getIndex());
        }
    }

    public static void deleteChunkMeshBuffers(Chunk chunk) {
        if (chunk.getModel() != null) {
            ObjectLoader.removeVAO(chunk.getModel().getVao());
            ObjectLoader.removeVBO(chunk.getModel().getVbo());
        }
        if (chunk.getTransparentModel() != null) {
            ObjectLoader.removeVAO(chunk.getTransparentModel().getVao());
            ObjectLoader.removeVBO(chunk.getTransparentModel().getVbo());
        }
    }

    public static void deleteModelBuffers(Model model) {
        ObjectLoader.removeVAO(model.getVao());
        ObjectLoader.removeVBO(model.getVbo());
    }

    public static void input() {
        player.input();
    }

    public static float[] getCrossHairVertices() {
        WindowManager window = Launcher.getWindow();

        int width = window.getWidth();
        int height = window.getHeight();
        float size = 16;

        return new float[]{
                -size * GUI_SIZE / width, size * GUI_SIZE / height,
                -size * GUI_SIZE / width, -size * GUI_SIZE / height,
                size * GUI_SIZE / width, size * GUI_SIZE / height,

                -size * GUI_SIZE / width, -size * GUI_SIZE / height,
                size * GUI_SIZE / width, -size * GUI_SIZE / height,
                size * GUI_SIZE / width, size * GUI_SIZE / height
        };
    }

    public static float[] getHotBarVertices() {
        WindowManager window = Launcher.getWindow();

        int width = window.getWidth();
        int height = window.getHeight();
        float sizeX = 180;
        float sizeY = 40;

        return new float[]{

                -sizeX * GUI_SIZE / width, -0.5f,
                -sizeX * GUI_SIZE / width, sizeY * GUI_SIZE / height - 0.5f,
                sizeX * GUI_SIZE / width, -0.5f,

                -sizeX * GUI_SIZE / width, sizeY * GUI_SIZE / height - 0.5f,
                sizeX * GUI_SIZE / width, sizeY * GUI_SIZE / height - 0.5f,
                sizeX * GUI_SIZE / width, -0.5f
        };
    }

    public static float[] getHotBarElementVertices(int index) {
        WindowManager window = Launcher.getWindow();

        int width = window.getWidth();
        int height = window.getHeight();

        float sizeX = 180;
        float sizeY = 40;

        float lowerX = (-sizeX * GUI_SIZE + 4 + 40 * index) / width;
        float upperX = (-sizeX * GUI_SIZE + 36 + 40 * index) / width;
        float lowerY = -0.5f + 4.0f / height;
        float upperY = (sizeY * GUI_SIZE - 4.0f) / height - 0.5f;
        return new float[]{
                lowerX, lowerY,
                lowerX, upperY,
                upperX, lowerY,

                lowerX, upperY,
                upperX, upperY,
                upperX, lowerY
        };
    }

    public static void render() {
        WindowManager window = Launcher.getWindow();

        if (window.isResize()) {
            GL11.glViewport(0, 0, window.getWidth(), window.getHeight());
            window.setResize(true);
        }
        player.render();
        player.getRenderer().render(player.getCamera());
    }

    public static double[][] heightMap(int x, int z) {
        final double FREQUENCY = 1 / 100d;
        double[][] heightMap = new double[CHUNK_SIZE][CHUNK_SIZE];

        for (int mapX = 0; mapX < CHUNK_SIZE; mapX++) {
            for (int mapZ = 0; mapZ < CHUNK_SIZE; mapZ++) {
                int currentX = (x << 5) + mapX;
                int currentZ = (z << 5) + mapZ;
                double value = WATER_LEVEL;
                value += OpenSimplex2S.noise3_ImproveXY(SEED, currentX * FREQUENCY / 4, currentZ * FREQUENCY / 4, 0.0) * 50;
                value += OpenSimplex2S.noise3_ImproveXY(SEED + 1, currentX * FREQUENCY, currentZ * FREQUENCY, 0.0) * 25;
                value += OpenSimplex2S.noise3_ImproveXY(SEED + 2, currentX * FREQUENCY * 2, currentZ * FREQUENCY * 2, 0.0) * 12;
                value += OpenSimplex2S.noise3_ImproveXY(SEED + 3, currentX * FREQUENCY * 4, currentZ * FREQUENCY * 4, 0.0) * 6;
                value += OpenSimplex2S.noise3_ImproveXY(SEED + 4, currentX * FREQUENCY * 8, currentZ * FREQUENCY * 8, 0.0) * 3;
                heightMap[mapX][mapZ] = value;
            }
        }
        return heightMap;
    }

    public static int[][] stoneMap(int x, int z, double[][] heightMap) {
        int[][] stoneMap = new int[CHUNK_SIZE][CHUNK_SIZE];

        for (int mapX = 0; mapX < CHUNK_SIZE; mapX++) {
            for (int mapZ = 0; mapZ < CHUNK_SIZE; mapZ++) {
                int currentX = (x << 5) + mapX;
                int currentZ = (z << 5) + mapZ;

                double biomes = OpenSimplex2S.noise3_ImproveXY(SEED + 1 << 16, currentX / 200d, currentZ / 200d, 0.0);
                biomes = Math.max(0, biomes);
                stoneMap[mapX][mapZ] = (int) (heightMap[mapX][mapZ] * (biomes + 1)) * (biomes != 0 ? 1 : 0);

            }
        }
        return stoneMap;
    }

    public static double[][] featureMap(int x, int z) {
        double[][] featureMap = new double[CHUNK_SIZE][CHUNK_SIZE];

        for (int mapX = 0; mapX < CHUNK_SIZE; mapX++) {
            for (int mapZ = 0; mapZ < CHUNK_SIZE; mapZ++) {
                int currentX = (x << 5) + mapX;
                int currentZ = (z << 5) + mapZ;

                double value = OpenSimplex2S.noise3_ImproveXY(SEED + 1 << 48, currentX, currentZ, 0.0);
                featureMap[mapX][mapZ] = value;
            }
        }
        return featureMap;
    }

    public static byte[][] treeMap(int x, int z, double[][] heightMap, int[][] stoneMap, double[][] featureMap) {
        byte[][] treeMap = new byte[CHUNK_SIZE][CHUNK_SIZE];
        for (int mapX = 2; mapX < CHUNK_SIZE - 2; mapX++) {
            for (int mapZ = 2; mapZ < CHUNK_SIZE - 2; mapZ++) {

                if (treeMap[mapX][mapZ] != 0)
                    continue;
                if (stoneMap[mapX][mapZ] != 0)
                    continue;
                if (heightMap[mapX][mapZ] <= WATER_LEVEL)
                    continue;
                int sandHeight = (int) (Math.abs(featureMap[mapX][mapZ] * 4)) + WATER_LEVEL;
                if (heightMap[mapX][mapZ] <= sandHeight + 2)
                    continue;

                int currentX = (x << 5) + mapX;
                int currentZ = (z << 5) + mapZ;

                double value = OpenSimplex2S.noise3_ImproveXY(SEED + 1 << 32, currentX, currentZ, 0.0);
                if (value > TREE_THRESHOLD) {
                    for (int i = -1; i <= 1; i++)
                        for (int j = -1; j <= 1; j++)
                            treeMap[mapX + i][mapZ + j] = -1;
                    treeMap[mapX][mapZ] = OAK_TREE_VALUE;
                } else {
                    boolean toCloseToChunkEdge = mapX == 2 || mapX == CHUNK_SIZE - 3 || mapZ == 2 || mapZ == CHUNK_SIZE - 3;
                    if (value < -TREE_THRESHOLD) {
                        if (toCloseToChunkEdge)
                            continue;
                        for (int i = -1; i <= 1; i++)
                            for (int j = -1; j <= 1; j++)
                                treeMap[mapX + i][mapZ + j] = -1;
                        treeMap[mapX][mapZ] = SPRUCE_TREE_VALUE;
                    } else if (value < 0.005 && value > -0.005) {
                        if (toCloseToChunkEdge)
                            continue;
                        for (int i = -1; i <= 2; i++)
                            for (int j = -1; j <= 2; j++)
                                treeMap[mapX + i][mapZ + j] = -1;
                        treeMap[mapX][mapZ] = DARK_OAK_TREE_VALUE;
                    }
                }
            }
        }
        return treeMap;
    }

    public static void addToBufferChunk(Chunk chunk) {
        toBufferChunks.add(chunk);
    }

    public static void addToUnloadModel(Model model) {
        toUnloadModels.add(model);
    }

    public static long getChunkId(int x, int y, int z) {
        return (long) (x & MAX_XZ) << 37 | (long) (y & MAX_Y) << 27 | (z & MAX_XZ);
    }

    public static int getChunkIndex(int x, int y, int z) {

        x = (x % RENDERED_WORLD_WIDTH);
        x += x < 0 ? RENDERED_WORLD_WIDTH : 0;

        y = (y % RENDERED_WORLD_HEIGHT);
        y += y < 0 ? RENDERED_WORLD_HEIGHT : 0;

        z = (z % RENDERED_WORLD_WIDTH);
        z += z < 0 ? RENDERED_WORLD_WIDTH : 0;

        return (x * RENDERED_WORLD_HEIGHT + y) * RENDERED_WORLD_WIDTH + z;
    }

    public static void startChunkGenerator() {
        generator.start();
    }

    public static Player getPlayer() {
        return player;
    }

    public static void cleanUp() {
        player.getRenderer().cleanUp();
        ObjectLoader.cleanUp();
        generator.cleanUp();
    }
}