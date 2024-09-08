package com.MBEv2.core.entity;

import com.MBEv2.core.Block;
import com.MBEv2.core.Chunk;
import com.MBEv2.core.ObjectLoader;
import com.MBEv2.core.entity.entities.TNT_Entity;
import com.MBEv2.core.utils.Utils;
import org.joml.Vector3f;

import static com.MBEv2.core.utils.Constants.*;


public abstract class Entity {

    protected Vector3f position;
    protected Vector3f velocity;
    protected float[] aabb;
    protected boolean isDead = false;

    public static void init() {
        TNT_Entity.vao = ObjectLoader.loadVAO(TNT_Entity.TNTEntityVertices());
    }

    public abstract void update();

    public abstract int getVAO();

    public abstract int getVertexCount();

    public abstract void delete();

    public void move() {
        velocity.mul(Player.AIR_FRICTION);
        velocity.y -= Player.GRAVITY_ACCELERATION;

        float minX = position.x + aabb[MIN_X];
        float maxX = position.x + aabb[MAX_X];
        float minY = position.y + aabb[MIN_Y];
        float maxY = position.y + aabb[MAX_Y];
        float minZ = position.z + aabb[MIN_Z];
        float maxZ = position.z + aabb[MAX_Z];

        if (velocity.x != 0.0f && collidesWithBlock(minX + velocity.x, maxX + velocity.x, minY, maxY, minZ, maxZ))
            velocity.x = 0.0f;
        else {
            minX += velocity.x;
            maxX += velocity.x;
        }

        if (velocity.y != 0.0f && collidesWithBlock(minX, maxX, minY + velocity.y, maxY + velocity.y, minZ, maxZ))
            velocity.y = 0.0f;
        else {
            minY += velocity.y;
            maxY += velocity.y;
        }

        if (velocity.z != 0.0f && collidesWithBlock(minX, maxX, minY, maxY, minZ + velocity.z, maxZ + velocity.z))
            velocity.z = 0.0f;

        position.add(velocity);
    }

    public boolean collidesWithBlock(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        for (int x = Utils.floor(minX), maxBlockX = Utils.floor(maxX); x <= maxBlockX; x++)
            for (int y = Utils.floor(minY), maxBlockY = Utils.floor(maxY); y <= maxBlockY; y++)
                for (int z = Utils.floor(minZ), maxBlockZ = Utils.floor(maxZ); z <= maxBlockZ; z++) {
                    short block = Chunk.getBlockInWorld(x, y, z);

                    if (Block.entityIntersectsBlock(minX, maxX, minY, maxY, minZ, maxZ, x, y, z, block))
                        return true;
                }
        return false;
    }

    public boolean isDead() {
        return isDead;
    }

    public void setPosition(Vector3f position) {
        this.position = position;
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getVelocity() {
        return velocity;
    }
}