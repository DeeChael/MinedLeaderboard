package net.deechael.minedleaderboard;

import java.util.Objects;

public record Vec3D(String world, int x, int y, int z) {

    @Override
    public int hashCode() {
        return Integer.parseInt(new StringBuilder(String.valueOf(Math.abs(world.hashCode()))).append(Math.abs(x)).append(Math.abs(y)).append(Math.abs(z)).toString()) + x + y + z;
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
