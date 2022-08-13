package net.deechael.minedleaderboard;

import java.util.Objects;

public record Vec3D(String world, int x, int y, int z) {

    @Override
    public int hashCode() {
        return world.hashCode() + x + y + z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Vec3D vec3D = (Vec3D) o;

        if (x != vec3D.x) return false;
        if (y != vec3D.y) return false;
        if (z != vec3D.z) return false;
        return Objects.equals(world, vec3D.world);
    }

}
