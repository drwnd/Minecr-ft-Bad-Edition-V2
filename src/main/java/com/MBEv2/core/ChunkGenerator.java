package com.MBEv2.core;

import com.MBEv2.core.utils.Utils;
import com.MBEv2.test.GameLogic;
import org.joml.Vector3f;
import org.joml.Vector4i;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.MBEv2.core.utils.Constants.*;

public class ChunkGenerator {

    private final ThreadPoolExecutor executor;

    private final LinkedList<Vector4i> blockChanges;

    private final GenerationStarter generationStarter;

    private final Thread starterThread;


    public ChunkGenerator() {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(NUMBER_OF_GENERATION_THREADS);
        blockChanges = new LinkedList<>();
        generationStarter = new GenerationStarter(blockChanges, executor);
        starterThread = new Thread(generationStarter);
        starterThread.start();
    }

    public void start() {
        Vector3f playerPosition = GameLogic.getPlayer().getCamera().getPosition();
        int playerX = Utils.floor(playerPosition.x) >> CHUNK_SIZE_BITS;
        int playerY = Utils.floor(playerPosition.y) >> CHUNK_SIZE_BITS;
        int playerZ = Utils.floor(playerPosition.z) >> CHUNK_SIZE_BITS;
        generationStarter.restart(NONE, playerX, playerY, playerZ);
        synchronized (starterThread) {
            starterThread.notify();
        }
    }

    public void restart(int direction) {
        Vector3f playerPosition = GameLogic.getPlayer().getCamera().getPosition();
        int playerX = Utils.floor(playerPosition.x) >> CHUNK_SIZE_BITS;
        int playerY = Utils.floor(playerPosition.y) >> CHUNK_SIZE_BITS;
        int playerZ = Utils.floor(playerPosition.z) >> CHUNK_SIZE_BITS;
        generationStarter.restart(direction, playerX, playerY, playerZ);
        synchronized (starterThread) {
            starterThread.notify();
        }
    }

    public void addBlockChange(Vector4i blockChange) {
        blockChanges.add(blockChange);
    }

    public void cleanUp() {
        generationStarter.stop();
        synchronized (starterThread) {
            starterThread.notify();
        }
        executor.getQueue().clear();
        executor.shutdown();
    }

    static class Generator implements Runnable {

        private final int chunkX, playerY, chunkZ;

        public Generator(int chunkX, int playerY, int chunkZ) {
            this.chunkX = chunkX;
            this.playerY = playerY;
            this.chunkZ = chunkZ;
        }

        @Override
        public void run() {
            double[][] heightMap = WorldGeneration.heightMap(chunkX, chunkZ);
            double[][] temperatureMap = WorldGeneration.temperatureMap(chunkX, chunkZ);
            double[][] humidityMap = WorldGeneration.humidityMap(chunkX, chunkZ);
            double[][] erosionMap = WorldGeneration.erosionMap(chunkX, chunkZ);
            double[][] featureMap = WorldGeneration.featureMap(chunkX, chunkZ);

            for (int chunkY = playerY + RENDER_DISTANCE_Y; chunkY >= playerY - RENDER_DISTANCE_Y; chunkY--) {
                final long expectedId = GameLogic.getChunkId(chunkX, chunkY, chunkZ);
                Chunk chunk = Chunk.getChunk(chunkX, chunkY, chunkZ);

                if (chunk == null) {
                    if (Chunk.containsSavedChunk(expectedId))
                        chunk = Chunk.removeSavedChunk(expectedId);
                    else
                        chunk = new Chunk(chunkX, chunkY, chunkZ);

                    Chunk.storeChunk(chunk);
                    if (!chunk.isGenerated())
                        WorldGeneration.generate(chunk, heightMap, temperatureMap, humidityMap, erosionMap, featureMap);
                } else if (chunk.getId() != expectedId) {
                    System.out.println("expected: " + chunkX + " " + chunkY + " " + chunkZ);
                    System.out.println("found:    " + chunk.getX() + " " + chunk.getY() + " " + chunk.getZ());
                    GameLogic.addToUnloadChunk(chunk);

                    if (chunk.isModified())
                        Chunk.putSavedChunk(chunk);

                    if (Chunk.containsSavedChunk(expectedId))
                        chunk = Chunk.removeSavedChunk(expectedId);
                    else
                        chunk = new Chunk(chunkX, chunkY, chunkZ);

                    Chunk.storeChunk(chunk);
                    if (!chunk.isGenerated())
                        WorldGeneration.generate(chunk, heightMap, temperatureMap, humidityMap, erosionMap, featureMap);
                } else if (!chunk.isGenerated()) {
                    WorldGeneration.generate(chunk, heightMap, temperatureMap, humidityMap, erosionMap, featureMap);
                }
            }
        }
    }

    static class MeshHandler implements Runnable {

        private final int chunkX, playerY, chunkZ, travelDirection;

        public MeshHandler(int chunkX, int playerY, int chunkZ, int travelDirection) {
            this.chunkX = chunkX;
            this.playerY = playerY;
            this.chunkZ = chunkZ;
            this.travelDirection = travelDirection;
        }

        @Override
        public void run() {
            if (travelDirection == BOTTOM) handleSkyLightBottom();
            else handleSkyLightTop();

            for (int chunkY = playerY + RENDER_DISTANCE_Y; chunkY >=  playerY - RENDER_DISTANCE_Y; chunkY--) {
                Chunk chunk = Chunk.getChunk(chunkX, chunkY, chunkZ);
                if (chunk == null) {
                    System.out.println("fuck");
                    continue;
                }
                if (!chunk.isGenerated()) {
                    System.out.println("fuck2");
                    WorldGeneration.generate(chunk);
                }
                if (!chunk.hasPropagatedBlockLight()) {
                    chunk.propagateBlockLight();
                    chunk.setHasPropagatedBlockLight();
                }
                if (chunk.isMeshed()) continue;
                meshChunk(chunk);
            }
        }

        private void meshChunk(Chunk chunk) {
            chunk.generateMesh();
            boolean shouldBuffer = false;
            for (int side = 0; side < 6; side++) {
                if (chunk.getVertices(side).length != 0) {
                    shouldBuffer = true;
                    break;
                }
            }
            if (shouldBuffer || chunk.getWaterVertices().length != 0)
                GameLogic.addToBufferChunk(chunk);
        }

        private void handleSkyLightBottom() {
            int minY = Integer.MAX_VALUE;
            for (int chunkY = 0; chunkY < RENDERED_WORLD_HEIGHT; chunkY++) {
                Chunk chunk = Chunk.getChunk(chunkX, chunkY, chunkZ);
                if (chunk == null || !chunk.isGenerated()) continue;
                if (chunk.getY() < minY) minY = chunk.getY();
            }
            if (minY == Integer.MAX_VALUE) return;

            LightLogic.propagateChunkSkyLight(chunkX, minY, chunkZ);
        }

        private void handleSkyLightTop() {
            int maxY = Integer.MIN_VALUE;
            for (int chunkY = RENDERED_WORLD_HEIGHT; chunkY > 0; chunkY--) {
                Chunk chunk = Chunk.getChunk(chunkX, chunkY, chunkZ);
                if (chunk == null || !chunk.isGenerated()) continue;
                if (chunk.getY() > maxY) maxY = chunk.getY();
            }
            if (maxY == Integer.MIN_VALUE) return;

            LightLogic.setChunkColumnSkyLight(chunkX, maxY, chunkZ);
        }
    }

    class GenerationStarter implements Runnable {

        private final ThreadPoolExecutor executor;
        private final LinkedList<Vector4i> changes;
        private int travelDirection;
        private int playerX, playerY, playerZ;

        private boolean shouldFinish = true;
        private boolean shouldExecute = true;
        private boolean shouldRestart = false;

        public GenerationStarter(LinkedList<Vector4i> changes, ThreadPoolExecutor executor) {
            this.changes = changes;
            this.executor = executor;
        }

        public void restart(int travelDirection, int playerX, int playerY, int playerZ) {
            shouldRestart = true;
            shouldFinish = false;
            this.travelDirection = travelDirection;
            this.playerX = playerX;
            this.playerY = playerY;
            this.playerZ = playerZ;
        }

        @Override
        public void run() {
            while (shouldExecute) {
                try {
                    synchronized (starterThread) {
                        if (!shouldRestart) starterThread.wait();
                        shouldRestart = false;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                shouldFinish = true;

                executor.getQueue().clear();
                unloadChunks(playerX, playerY, playerZ);
                handleBlockChanges();
                submitTasks(playerX, playerY, playerZ, travelDirection);
            }
        }

        private void unloadChunks(int playerX, int playerY, int playerZ) {
            for (Chunk chunk : Chunk.getWorld()) {
                if (chunk == null)
                    continue;

                if (Math.abs(chunk.getX() - playerX) <= RENDER_DISTANCE_XZ + 2 && Math.abs(chunk.getZ() - playerZ) <= RENDER_DISTANCE_XZ + 2 && Math.abs(chunk.getY() - playerY) <= RENDER_DISTANCE_Y + 2)
                    continue;

                if (Math.abs(chunk.getY() - playerY) < RENDER_DISTANCE_Y + 2)
                    Arrays.fill(Chunk.getHeightMap(chunk.getX(), chunk.getZ()), Integer.MIN_VALUE);

                chunk.clearMesh();
                GameLogic.addToUnloadChunk(chunk);

                if (chunk.isModified())
                    Chunk.putSavedChunk(chunk);

                Chunk.setNull(chunk.getIndex());
            }
        }

        private void handleBlockChanges() {
            synchronized (changes) {
                while (!changes.isEmpty()) {

                    Vector4i blockChange = changes.removeFirst();
                    int x = blockChange.x;
                    int y = blockChange.y;
                    int z = blockChange.z;
                    short previousBlock = (short) blockChange.w;

                    short block = Chunk.getBlockInWorld(x, y, z);

                    boolean blockEmitsLight = (Block.getBlockProperties(block) & LIGHT_EMITTING_MASK) != 0;
                    boolean previousBlockEmitsLight = (Block.getBlockProperties(previousBlock) & LIGHT_EMITTING_MASK) != 0;

                    if (blockEmitsLight && !previousBlockEmitsLight)
                        LightLogic.setBlockLight(x, y, z, MAX_BLOCK_LIGHT_VALUE);
                    else if (block == AIR)
                        if (previousBlockEmitsLight)
                            LightLogic.dePropagateBlockLight(x, y, z);
                        else
                            LightLogic.setBlockLight(x, y, z, LightLogic.getMaxSurroundingBlockLight(x, y, z) - 1);
                    else if (!blockEmitsLight)
                        LightLogic.dePropagateBlockLight(x, y, z);

                    if (block == AIR)
                        LightLogic.setSkyLight(x, y, z, LightLogic.getMaxSurroundingSkyLight(x, y, z) - 1);
                    else
                        LightLogic.dePropagateSkyLight(x, y, z);
                }
            }
        }

        private void submitTasks(int playerX, int playerY, int playerZ, int travelDirection) {
            if (shouldFinish && !executor.isShutdown()) executor.submit(new Generator(playerX, playerY, playerZ));
            for (int ring = 1; ring <= RENDER_DISTANCE_XZ && shouldFinish; ring++) {

                for (int chunkX = -ring; chunkX < ring && shouldFinish && !executor.isShutdown(); chunkX++)
                    executor.submit(new Generator(chunkX + playerX, playerY, ring + playerZ));

                for (int chunkZ = ring; chunkZ > -ring && shouldFinish && !executor.isShutdown(); chunkZ--)
                    executor.submit(new Generator(ring + playerX, playerY, chunkZ + playerZ));

                for (int chunkX = ring; chunkX > -ring && shouldFinish && !executor.isShutdown(); chunkX--)
                    executor.submit(new Generator(chunkX + playerX, playerY, -ring + playerZ));

                for (int chunkZ = -ring; chunkZ < ring && shouldFinish && !executor.isShutdown(); chunkZ++)
                    executor.submit(new Generator(-ring + playerX, playerY, chunkZ + playerZ));

                if (ring == 1 && shouldFinish && !executor.isShutdown()) {
                    executor.submit(new MeshHandler(playerX, playerY, playerZ, travelDirection));
                    continue;
                }
                int meshRing = ring - 1;
                for (int chunkX = -meshRing; chunkX < meshRing && shouldFinish && !executor.isShutdown(); chunkX++)
                    executor.submit(new MeshHandler(chunkX + playerX, playerY, meshRing + playerZ, travelDirection));

                for (int chunkZ = meshRing; chunkZ > -meshRing && shouldFinish && !executor.isShutdown(); chunkZ--)
                    executor.submit(new MeshHandler(meshRing + playerX, playerY, chunkZ + playerZ, travelDirection));

                for (int chunkX = meshRing; chunkX > -meshRing && shouldFinish && !executor.isShutdown(); chunkX--)
                    executor.submit(new MeshHandler(chunkX + playerX, playerY, -meshRing + playerZ, travelDirection));

                for (int chunkZ = -meshRing; chunkZ < meshRing && shouldFinish && !executor.isShutdown(); chunkZ++)
                    executor.submit(new MeshHandler(-meshRing + playerX, playerY, chunkZ + playerZ, travelDirection));
            }
        }

        public void stop() {
            shouldFinish = false;
            shouldExecute = false;
        }
    }
}
