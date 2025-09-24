package dev.maximus.glasswork.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.world.phys.Vec3;

public class ClientVec3Argument implements ArgumentType<Vec3> {
    public static ClientVec3Argument vec3() {
        return new ClientVec3Argument();
    }

    @Override
    public Vec3 parse(StringReader reader) throws CommandSyntaxException {
        double x = reader.readDouble();
        reader.skipWhitespace();
        double y = reader.readDouble();
        reader.skipWhitespace();
        double z = reader.readDouble();
        return new Vec3(x, y, z);
    }
}
