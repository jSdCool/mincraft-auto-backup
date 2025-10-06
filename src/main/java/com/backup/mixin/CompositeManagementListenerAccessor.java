package com.backup.mixin;

import net.minecraft.server.dedicated.management.listener.CompositeManagementListener;
import net.minecraft.server.dedicated.management.listener.ManagementListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CompositeManagementListener.class)
public interface CompositeManagementListenerAccessor {
    @Accessor
    List<ManagementListener> getListeners();
}
