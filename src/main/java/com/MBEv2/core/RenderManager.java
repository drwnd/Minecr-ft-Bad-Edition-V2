package com.MBEv2.core;

import com.MBEv2.core.entity.*;

import static com.MBEv2.core.utils.Constants.*;
import static com.MBEv2.core.utils.Settings.*;

import com.MBEv2.core.utils.Transformation;
import com.MBEv2.core.utils.Utils;
import com.MBEv2.test.Launcher;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.opengl.*;

import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RenderManager {

    private final WindowManager window;
    private ShaderManager blockShader, waterShader, foliageShader, skyBoxShader, GUIShader, textShader, entityShader;

    private final List<Model> chunkModels = new ArrayList<>();
    private final List<Model> foliageModels = new ArrayList<>();
    private final List<Model> waterModels = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<GUIElement> GUIElements = new ArrayList<>();
    private final Player player;
    private GUIElement inventoryOverlay;
    private SkyBox skyBox;
    private boolean headUnderWater = false;

    private float time = 1.0f;

    public int modelIndexBuffer;
    private int textRowVertexArray;

    private Texture xRayAtlas;
    private Texture atlas;
    private Texture textAtlas;
    private boolean xRay;

    public RenderManager(Player player) {
        window = Launcher.getWindow();
        this.player = player;
    }

    public void init() throws Exception {

        xRayAtlas = new Texture(ObjectLoader.loadTexture("textures/XRayAtlas.png"));
        atlas = new Texture(ObjectLoader.loadTexture("textures/atlas256.png"));
        textAtlas = new Texture(ObjectLoader.loadTexture("textures/textAtlas.png"));

        blockShader = new ShaderManager();
        blockShader.createVertexShader(Utils.loadResources("/shaders/blockVertex.glsl"));
        blockShader.createFragmentShader(Utils.loadResources("/shaders/blockFragment.glsl"));
        blockShader.link();
        blockShader.createUniform("textureSampler");
        blockShader.createUniform("projectionMatrix");
        blockShader.createUniform("viewMatrix");
        blockShader.createUniform("worldPos");
        blockShader.createUniform("time");
        blockShader.createUniform("headUnderWater");
        blockShader.createUniform("cameraPosition");

        waterShader = new ShaderManager();
        waterShader.createVertexShader(Utils.loadResources("/shaders/waterVertex.glsl"));
        waterShader.createFragmentShader(Utils.loadResources("/shaders/waterFragment.glsl"));
        waterShader.link();
        waterShader.createUniform("textureSampler");
        waterShader.createUniform("projectionMatrix");
        waterShader.createUniform("viewMatrix");
        waterShader.createUniform("worldPos");
        waterShader.createUniform("time");
        waterShader.createUniform("headUnderWater");
        waterShader.createUniform("cameraPosition");
        waterShader.createUniform("shouldSimulateWaves");

        foliageShader = new ShaderManager();
        foliageShader.createVertexShader(Utils.loadResources("/shaders/FoliageVertex.glsl"));
        foliageShader.createFragmentShader(Utils.loadResources("/shaders/blockFragment.glsl"));
        foliageShader.link();
        foliageShader.createUniform("textureSampler");
        foliageShader.createUniform("projectionMatrix");
        foliageShader.createUniform("viewMatrix");
        foliageShader.createUniform("worldPos");
        foliageShader.createUniform("time");
        foliageShader.createUniform("headUnderWater");
        foliageShader.createUniform("cameraPosition");
        foliageShader.createUniform("shouldSimulateWind");

        skyBoxShader = new ShaderManager();
        skyBoxShader.createVertexShader(Utils.loadResources("/shaders/skyBoxVertex.glsl"));
        skyBoxShader.createFragmentShader(Utils.loadResources("/shaders/skyBoxFragment.glsl"));
        skyBoxShader.link();
        skyBoxShader.createUniform("textureSampler1");
        skyBoxShader.createUniform("textureSampler2");
        skyBoxShader.createUniform("projectionMatrix");
        skyBoxShader.createUniform("viewMatrix");
        skyBoxShader.createUniform("transformationMatrix");
        skyBoxShader.createUniform("time");

        GUIShader = new ShaderManager();
        GUIShader.createVertexShader(Utils.loadResources("/shaders/GUIVertex.glsl"));
        GUIShader.createFragmentShader(Utils.loadResources("/shaders/GUIFragment.glsl"));
        GUIShader.link();
        GUIShader.createUniform("textureSampler");
        GUIShader.createUniform("position");

        textShader = new ShaderManager();
        textShader.createVertexShader(Utils.loadResources("/shaders/textVertex.glsl"));
        textShader.createFragmentShader(Utils.loadResources("/shaders/textFragment.glsl"));
        textShader.link();
        textShader.createUniform("screenSize");
        textShader.createUniform("charSize");
        textShader.createUniform("string");
        textShader.createUniform("yOffset");
        textShader.createUniform("textureSampler");

        entityShader = new ShaderManager();
        entityShader.createVertexShader(Utils.loadResources("/shaders/EntityVertex.glsl"));
        entityShader.createFragmentShader(Utils.loadResources("/shaders/EntityFragment.glsl"));
        entityShader.link();
        entityShader.createUniform("projectionMatrix");
        entityShader.createUniform("viewMatrix");
        entityShader.createUniform("time");
        entityShader.createUniform("textureSampler");
        entityShader.createUniform("position");
        entityShader.createUniform("lightLevel");

        int[] indices = new int[393216];
        int index = 0;
        for (int i = 0; i < indices.length; i += 6) {
            indices[i] = index;
            indices[i + 1] = index + 1;
            indices[i + 2] = index + 2;
            indices[i + 3] = index + 3;
            indices[i + 4] = index + 2;
            indices[i + 5] = index + 1;
            index += 4;
        }

        modelIndexBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, modelIndexBuffer);
        IntBuffer buffer = Utils.storeDateInIntBuffer(indices);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        textRowVertexArray = ObjectLoader.loadTextRow();
    }

    public void bindModel(Model model) {
        GL30.glBindVertexArray(model.getVao());
        GL20.glEnableVertexAttribArray(0);

        blockShader.setUniform("worldPos", model.getPosition());
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, modelIndexBuffer);
    }

    public void bindSkyBox(SkyBox skyBox, Camera camera) {
        GL30.glBindVertexArray(skyBox.getVao());
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, skyBox.getTexture1().id());
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, skyBox.getTexture2().id());

        skyBoxShader.setUniform("textureSampler1", 0);
        skyBoxShader.setUniform("textureSampler2", 1);
        skyBoxShader.setUniform("time", time);
        skyBoxShader.setUniform("viewMatrix", Transformation.getViewMatrix(camera));
        skyBoxShader.setUniform("projectionMatrix", window.getProjectionMatrix());
        skyBoxShader.setUniform("transformationMatrix", Transformation.createTransformationMatrix(skyBox.getPosition()));
    }

    public void bindGUIElement(GUIElement element) {
        GL30.glBindVertexArray(element.getVao());
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, element.getTexture().id());

        GUIShader.setUniform("textureSampler", 0);
        GUIShader.setUniform("position", element.getPosition());
    }

    public void bindWaterModel(Model model, int chunkX, int chunkY, int chunkZ) {
        GL30.glBindVertexArray(model.getVao());
        GL20.glEnableVertexAttribArray(0);

        Vector3i modelPosition = model.getPosition();
        boolean shouldSimulateWaves = Math.abs(chunkX - (modelPosition.x >> CHUNK_SIZE_BITS)) < 2 &&
                Math.abs(chunkY - (modelPosition.y >> CHUNK_SIZE_BITS)) < 2 &&
                Math.abs(chunkZ - (modelPosition.z >> CHUNK_SIZE_BITS)) < 2;

        waterShader.setUniform("shouldSimulateWaves", shouldSimulateWaves ? 1 : 0);
        waterShader.setUniform("worldPos", modelPosition);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, modelIndexBuffer);
    }

    public void bindFoliageModel(Model model, int chunkX, int chunkY, int chunkZ) {
        GL30.glBindVertexArray(model.getVao());
        GL20.glEnableVertexAttribArray(0);

        Vector3i modelPosition = model.getPosition();
        boolean shouldSimulateWind = Math.abs(chunkX - (modelPosition.x >> CHUNK_SIZE_BITS)) < 2 &&
                Math.abs(chunkY - (modelPosition.y >> CHUNK_SIZE_BITS)) < 2 &&
                Math.abs(chunkZ - (modelPosition.z >> CHUNK_SIZE_BITS)) < 2;

        foliageShader.setUniform("shouldSimulateWind", shouldSimulateWind ? 1 : 0);
        foliageShader.setUniform("worldPos", modelPosition);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, modelIndexBuffer);
    }

    public void unbind() {
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
    }

    public void render(Camera camera, float timeSinceLastTick) {
        Matrix4f projectionMatrix = window.getProjectionMatrix();
        Matrix4f viewMatrix = Transformation.getViewMatrix(camera);

        clear();

        renderSkyBox(camera);

        renderOpaqueChunks(projectionMatrix, viewMatrix);

        renderFoliageChunks(projectionMatrix, viewMatrix);

        renderEntities(projectionMatrix, viewMatrix, timeSinceLastTick);

        renderWaterChunks(projectionMatrix, viewMatrix);

        renderGUIElements();

        if (player.isDebugScreenOpen()) renderDebugText();

        chunkModels.clear();
        foliageModels.clear();
        waterModels.clear();
        GUIElements.clear();
        entities.clear();

        unbind();
    }

    public void renderSkyBox(Camera camera) {
        skyBoxShader.bind();
        bindSkyBox(skyBox, camera);

        GL11.glDrawElements(GL11.GL_TRIANGLES, skyBox.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);

        skyBoxShader.unBind();
    }

    public void renderOpaqueChunks(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        blockShader.bind();
        blockShader.setUniform("projectionMatrix", projectionMatrix);
        blockShader.setUniform("viewMatrix", viewMatrix);
        blockShader.setUniform("textureSampler", 0);
        blockShader.setUniform("time", time);
        blockShader.setUniform("headUnderWater", headUnderWater ? 1 : 0);
        blockShader.setUniform("cameraPosition", player.getCamera().getPosition());

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, isxRay() ? xRayAtlas.id() : atlas.id());

        for (Model model : chunkModels) {
            bindModel(model);

            GL11.glDrawElements(GL11.GL_TRIANGLES, (int) (model.getVertexCount() * 0.75), GL11.GL_UNSIGNED_INT, 0);
        }
        blockShader.unBind();
    }

    public void renderFoliageChunks(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        foliageShader.bind();
        foliageShader.setUniform("projectionMatrix", projectionMatrix);
        foliageShader.setUniform("viewMatrix", viewMatrix);
        foliageShader.setUniform("textureSampler", 0);
        foliageShader.setUniform("time", time);
        foliageShader.setUniform("headUnderWater", headUnderWater ? 1 : 0);
        foliageShader.setUniform("cameraPosition", player.getCamera().getPosition());

        Vector3f playerPosition = player.getCamera().getPosition();
        int chunkX = Utils.floor(playerPosition.x) >> CHUNK_SIZE_BITS;
        int chunkY = Utils.floor(playerPosition.y) >> CHUNK_SIZE_BITS;
        int chunkZ = Utils.floor(playerPosition.z) >> CHUNK_SIZE_BITS;

        GL11.glDisable(GL11.GL_CULL_FACE);

        for (Model foliageModel : foliageModels) {
            bindFoliageModel(foliageModel, chunkX, chunkY, chunkZ);

            GL11.glDrawElements(GL11.GL_TRIANGLES, (int) (foliageModel.getVertexCount() * 0.75), GL11.GL_UNSIGNED_INT, 0);
        }
        foliageShader.unBind();
    }

    public void renderWaterChunks(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        waterShader.bind();
        waterShader.setUniform("projectionMatrix", projectionMatrix);
        waterShader.setUniform("viewMatrix", viewMatrix);
        waterShader.setUniform("textureSampler", 0);
        waterShader.setUniform("time", time);
        waterShader.setUniform("headUnderWater", headUnderWater ? 1 : 0);
        waterShader.setUniform("cameraPosition", player.getCamera().getPosition());

        Vector3f playerPosition = player.getCamera().getPosition();
        int chunkX = Utils.floor(playerPosition.x) >> CHUNK_SIZE_BITS;
        int chunkY = Utils.floor(playerPosition.y) >> CHUNK_SIZE_BITS;
        int chunkZ = Utils.floor(playerPosition.z) >> CHUNK_SIZE_BITS;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);

        for (Model waterModel : waterModels) {
            bindWaterModel(waterModel, chunkX, chunkY, chunkZ);

            GL11.glDrawElements(GL11.GL_TRIANGLES, (int) (waterModel.getVertexCount() * 0.75), GL11.GL_UNSIGNED_INT, 0);
        }
        GL11.glDisable(GL11.GL_BLEND);
        waterShader.unBind();
    }

    public void renderEntities(Matrix4f projectionMatrix, Matrix4f viewMatrix, float timeSinceLastTick) {
        entityShader.bind();
        entityShader.setUniform("projectionMatrix", projectionMatrix);
        entityShader.setUniform("viewMatrix", viewMatrix);
        entityShader.setUniform("time", time);
        entityShader.setUniform("textureSampler", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.id());
        GL11.glEnable(GL11.GL_CULL_FACE);

        for (Entity entity : entities) entity.render(entityShader, this, modelIndexBuffer, timeSinceLastTick);

        GL11.glDisable(GL11.GL_CULL_FACE);
        entityShader.unBind();
    }

    public void renderGUIElements() {
        GUIShader.bind();
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        if (player.isInInventory()) {
            GL11.glEnable(GL11.GL_BLEND);
            bindGUIElement(inventoryOverlay);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, inventoryOverlay.getVertexCount());

            GL11.glDisable(GL11.GL_BLEND);
        }

        for (GUIElement element : GUIElements) {
            bindGUIElement(element);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, element.getVertexCount());
        }
        GUIShader.unBind();
    }

    public void renderDebugText() {
        textShader.bind();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textAtlas.id());

        textShader.setUniform("screenSize", Launcher.getWindow().getWidth() / 2, Launcher.getWindow().getHeight() / 2);
        textShader.setUniform("charSize", TEXT_CHAR_SIZE_X, TEXT_CHAR_SIZE_Y);

        int line = -1;
        final Vector3f position = player.getCamera().getPosition();
        final Vector3f direction = player.getCamera().getDirection();

        Target target = Target.getTarget(position, direction);

        int x = Utils.floor(position.x), y = Utils.floor(position.y), z = Utils.floor(position.z);
        int chunkX = x >> CHUNK_SIZE_BITS, chunkY = y >> CHUNK_SIZE_BITS, chunkZ = z >> CHUNK_SIZE_BITS;
        Chunk chunk = Chunk.getChunk(chunkX, chunkY, chunkZ);

        renderTextLine("Coordinates: X:" + Utils.floor(position.x * 10) / 10f + " Y:" + Utils.floor(position.y * 10) / 10f + " Z:" + Utils.floor(position.z * 10) / 10f, ++line);
        renderTextLine("Chunk coordinates: X:" + chunkX + " Y:" + chunkY + " Z:" + chunkZ, ++line);
        renderTextLine("In Chunk coordinates: X:" + (x & CHUNK_SIZE_MASK) + " Y:" + (y & CHUNK_SIZE_MASK) + " Z:" + (z & CHUNK_SIZE_MASK), ++line);
        renderTextLine("Looking at: X:" + Utils.floor(direction.x * 100) / 100f + " Y:" + Utils.floor(direction.y * 100) / 100f + " Z:" + Utils.floor(direction.z * 100) / 100f, ++line);
        if (chunk != null) {
            renderTextLine("OcclusionCullingData:" + Integer.toBinaryString(chunk.getOcclusionCullingData() & 0x7FFF) + " Damping:" + (chunk.getOcclusionCullingDamper() == 0 ? "false" : "true"), ++line);
            renderTextLine("Block optimized:" + (chunk.isBlockOptimized() ? "true" : "false") + " Light optimized:" + (chunk.isLightOptimized() ? "true" : "false"), ++line);
            renderTextLine("HeightMap:" + Chunk.getHeightMap(chunkX, chunkZ)[(x & CHUNK_SIZE_MASK) << CHUNK_SIZE_BITS | z & CHUNK_SIZE_MASK], ++line);
            renderTextLine("BlockLight:" + Chunk.getBlockLightInWorld(x, y, z) + " SkyLight:" + Chunk.getSkyLightInWorld(x, y, z), ++line);
        }
        if (target != null) {
            renderTextLine("Looking at block: X:" + target.position().x + " Y:" + target.position().y + " Z:" + target.position().z, ++line);
            renderTextLine("Intersection: X:" + target.inBlockPosition().x + " Y:" + target.inBlockPosition().y + " Z:" + target.inBlockPosition().z, ++line);
            if (target.block() < STANDARD_BLOCKS_THRESHOLD)
                renderTextLine("Non standard block:" + target.block(), ++line);
            else
                renderTextLine("Standard block:" + (target.block() >> BLOCK_TYPE_BITS), ++line);
            renderTextLine("Block type:" + Block.getBlockType(target.block()), ++line);
            renderTextLine("Intersected side:" + target.side(), ++line);
        }
        renderTextLine("Seed:" + SEED, ++line);
        renderTextLine("Rendered chunk models:" + chunkModels.size(), ++line);
        renderTextLine("Rendered water models:" + waterModels.size(), ++line);
        renderTextLine("Rendered foliage models:" + foliageModels.size(), ++line);
        renderTextLine("Rendered entities:" + entities.size(), ++line);
        renderTextLine("Rendered GUIElements:" + GUIElements.size(), ++line);
        renderTextLine("Render distance XZ:" + RENDER_DISTANCE_XZ + " Render distance Y:" + RENDER_DISTANCE_Y, ++line);
        renderTextLine("Time:" + time, ++line);
        //This one line literally quarters the fps when debug screen is open
//        renderTextLine("Saved chunks memory:" + FileManager.getSeedFileSize() / 1_000_000 + "MB", ++line);

        textShader.unBind();
    }

    public void renderTextLine(String text, int textLine) {
        textShader.setUniform("string", toIntFormat(text));
        textShader.setUniform("yOffset", textLine * TEXT_LINE_SPACING);

        GL30.glBindVertexArray(textRowVertexArray);
        GL20.glEnableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, modelIndexBuffer);

        GL11.glDrawElements(GL11.GL_TRIANGLES, 384, GL11.GL_UNSIGNED_INT, 0);
    }

    private int[] toIntFormat(String text) {
        int[] array = new int[64];

        byte[] stringBytes = text.getBytes(StandardCharsets.UTF_8);

        for (int index = 0, max = Math.min(text.length(), 64); index < max; index++) {
            array[index] = stringBytes[index];
        }
        return array;
    }

    public void processEntity(Entity entity) {
        entities.add(entity);
    }

    public void processModel(Model model) {
        chunkModels.add(model);
    }

    public void processFoliageModel(Model foliageModel) {
        foliageModels.add(foliageModel);
    }

    public void processWaterModel(Model waterModel) {
        waterModels.add(waterModel);
    }

    public void processSkyBox(SkyBox skyBox) {
        this.skyBox = skyBox;
    }

    public void processGUIElement(GUIElement element) {
        GUIElements.add(element);
    }

    public void setHeadUnderWater(boolean headUnderWater) {
        this.headUnderWater = headUnderWater;
    }

    public void setInventoryOverlay(GUIElement inventoryOverlay) {
        this.inventoryOverlay = inventoryOverlay;
    }

    public void clear() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void cleanUp() {
        blockShader.cleanUp();
        skyBoxShader.cleanUp();
        GUIShader.cleanUp();
    }

    public void setXRay(boolean xRay) {
        this.xRay = xRay;
    }

    public boolean isxRay() {
        return xRay;
    }

    public void incrementTime() {
        time += TIME_SPEED;
        if (time > 1.0f) time -= 2.0f;
    }

    public float getTime() {
        return time;
    }

    public void setTime(float time) {
        this.time = time;
    }
}
