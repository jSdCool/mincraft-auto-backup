package com.backup.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.dedicated.management.OutgoingRpcMethod;
import net.minecraft.server.dedicated.management.listener.NotificationManagementListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NotificationManagementListener.class)
public interface NotificationManagementListenerAccessor2 {

    @Invoker("notifyAll")
    <Params> void callNotifyAll(RegistryEntry.Reference<? extends OutgoingRpcMethod<Params, ?>> method, Params params);
}
